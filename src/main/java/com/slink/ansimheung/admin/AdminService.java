package com.slink.ansimheung.admin;

import com.slink.ansimheung.admin.dto.AdminHazardDetail;
import com.slink.ansimheung.admin.dto.AdminHazardItem;
import com.slink.ansimheung.admin.dto.AdminHazardPage;
import com.slink.ansimheung.admin.dto.StatusChangeResponse;
import com.slink.ansimheung.auth.User;
import com.slink.ansimheung.auth.UserRepository;
import com.slink.ansimheung.common.error.ApiException;
import com.slink.ansimheung.common.error.ErrorCode;
import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.TimeSlot;
import com.slink.ansimheung.hazard.Hazard;
import com.slink.ansimheung.hazard.HazardRepository;
import com.slink.ansimheung.hazard.PhotoRepository;
import com.slink.ansimheung.hazard.Report;
import com.slink.ansimheung.hazard.ReportRepository;
import com.slink.ansimheung.hazard.StatusHistory;
import com.slink.ansimheung.hazard.StatusHistoryRepository;
import com.slink.ansimheung.risk.RiskService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 관리자 목록·상세·처리상태 변경 (#14, F-ADM-01 · F-ADM-03) */
@Service
public class AdminService {

    /** sort 파라미터 — 생략하면 최신순 */
    public static final String SORT_OLDEST = "OLDEST";
    public static final String SORT_SCORE = "SCORE";
    static final String HIDDEN_COUNT = "HIDDEN";

    private final HazardRepository hazards;
    private final ReportRepository reports;
    private final PhotoRepository photos;
    private final StatusHistoryRepository histories;
    private final UserRepository users;
    private final RiskService riskService;

    public AdminService(HazardRepository hazards, ReportRepository reports, PhotoRepository photos,
                        StatusHistoryRepository histories, UserRepository users, RiskService riskService) {
        this.hazards = hazards;
        this.reports = reports;
        this.photos = photos;
        this.histories = histories;
        this.users = users;
        this.riskService = riskService;
    }

    /**
     * 시범 지역 규모(지점 수십 개)라 전부 읽어서 메모리에서 거르고 정렬한다.
     * SCORE 정렬은 조회 시각의 시간대(T)를 곱한 점수라 DB 정렬로는 할 수 없다.
     */
    @Transactional(readOnly = true)
    public AdminHazardPage list(HazardStatus status, Boolean hidden, String sort, int page, int size,
                                Optional<TimeSlot> querySlot) {
        List<Hazard> all = hazards.findAll();
        Map<Long, Double> scores = all.stream()
                .collect(Collectors.toMap(Hazard::getId, h -> riskService.scoreAt(h, querySlot)));

        Comparator<Hazard> order = switch (sort == null ? "" : sort) {
            case SORT_OLDEST -> Comparator.comparing(Hazard::getCreatedAt).thenComparing(Hazard::getId);
            case SORT_SCORE -> Comparator.<Hazard>comparingDouble(h -> scores.get(h.getId())).reversed()
                    .thenComparing(Hazard::getId);
            default -> Comparator.comparing(Hazard::getCreatedAt).reversed()
                    .thenComparing(Comparator.comparing(Hazard::getId).reversed());
        };
        List<Hazard> filtered = all.stream()
                .filter(h -> status == null || h.getStatus() == status)
                .filter(h -> hidden == null || h.isHidden() == hidden)
                .sorted(order)
                .toList();

        int from = (int) Math.min((long) page * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<AdminHazardItem> content = filtered.subList(from, to).stream()
                .map(h -> item(h, scores.get(h.getId())))
                .toList();
        int totalPages = (filtered.size() + size - 1) / size;
        return new AdminHazardPage(counts(all), content, page, size, filtered.size(), totalPages);
    }

    @Transactional(readOnly = true)
    public AdminHazardDetail detail(Long id, Optional<TimeSlot> querySlot) {
        Hazard h = find(id);
        List<Report> rs = reports.findByHazardIdAndDeletedFalseOrderByCreatedAtAsc(id);
        List<StatusHistory> hs = histories.findByHazardIdOrderByCreatedAtAsc(id).stream()   // 같은 시각이면 저장 순서
                .sorted(Comparator.comparing(StatusHistory::getCreatedAt).thenComparing(StatusHistory::getId))
                .toList();

        Set<Long> userIds = new HashSet<>();
        rs.stream().map(Report::getUserId).filter(Objects::nonNull).forEach(userIds::add);
        hs.stream().map(StatusHistory::getAdminId).filter(Objects::nonNull).forEach(userIds::add);
        Map<Long, String> nicknames = users.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
        Function<Long, String> nickname = userId -> userId == null ? null : nicknames.get(userId);

        List<AdminHazardDetail.ReportItem> reportItems = rs.stream()
                .map(r -> new AdminHazardDetail.ReportItem(r.getId(), nickname.apply(r.getUserId()),
                        r.getDescription(), r.getCreatedAt()))
                .toList();
        List<AdminHazardDetail.PhotoItem> photoItems = rs.isEmpty() ? List.of()
                : photos.findByReportIdIn(rs.stream().map(Report::getId).toList()).stream()
                .map(p -> new AdminHazardDetail.PhotoItem(p.getId(), p.isApproved()))
                .toList();
        List<AdminHazardDetail.HistoryItem> historyItems = hs.stream()
                .map(s -> new AdminHazardDetail.HistoryItem(s.getFromStatus(), s.getToStatus(), s.getMemo(),
                        nickname.apply(s.getAdminId()), s.getCreatedAt()))
                .toList();

        return new AdminHazardDetail(h.getId(), h.getLat(), h.getLng(), h.getType(), h.getTimeSlot(), h.getStatus(),
                h.isHidden(), round1(riskService.scoreAt(h, querySlot)), h.getEmpathyCount(), h.getReportCount(),
                List.copyOf(h.getStatus().allowedNext()), reportItems, photoItems, historyItems);
    }

    /** 허용된 전이만 (아니면 409 STATE_CONFLICT) → 이력 저장 → 점수 재계산. 처리결과 알림은 #35 */
    @Transactional
    public StatusChangeResponse changeStatus(Long id, HazardStatus next, String memo, Long adminId) {
        Hazard h = find(id);
        HazardStatus before = h.changeStatus(next);
        histories.save(StatusHistory.of(h.getId(), before, next, adminId, blankToNull(memo)));
        riskService.refresh(h);
        return new StatusChangeResponse(h.getId(), h.getStatus());
    }

    private Hazard find(Long id) {
        return hazards.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private AdminHazardItem item(Hazard h, double score) {
        return new AdminHazardItem(h.getId(), h.getType(), h.getTimeSlot(), h.getStatus(), round1(score),
                riskService.levelOf(h, score), h.getEmpathyCount(), h.getReportCount(), h.isHidden(),
                h.getCreatedAt(), h.getLastActivityAt());
    }

    private static Map<String, Long> counts(List<Hazard> all) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (HazardStatus s : HazardStatus.values()) {
            counts.put(s.name(), all.stream().filter(h -> !h.isHidden() && h.getStatus() == s).count());
        }
        counts.put(HIDDEN_COUNT, all.stream().filter(Hazard::isHidden).count());
        return counts;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
