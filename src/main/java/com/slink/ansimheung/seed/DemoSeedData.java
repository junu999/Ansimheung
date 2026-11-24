package com.slink.ansimheung.seed;

import com.slink.ansimheung.common.type.HazardStatus;
import com.slink.ansimheung.common.type.HazardType;
import com.slink.ansimheung.common.type.TimeSlot;

import java.util.List;

import static com.slink.ansimheung.common.type.HazardStatus.CHECKING;
import static com.slink.ansimheung.common.type.HazardStatus.RECEIVED;
import static com.slink.ansimheung.common.type.HazardStatus.RESOLVED;
import static com.slink.ansimheung.common.type.HazardType.CONSTRUCTION;
import static com.slink.ansimheung.common.type.HazardType.ILLEGAL_PARKING;
import static com.slink.ansimheung.common.type.HazardType.POOR_LIGHTING;
import static com.slink.ansimheung.common.type.HazardType.SIDEWALK_GAP;
import static com.slink.ansimheung.common.type.HazardType.SPEEDING;
import static com.slink.ansimheung.common.type.TimeSlot.AFTERNOON;
import static com.slink.ansimheung.common.type.TimeSlot.ALWAYS;
import static com.slink.ansimheung.common.type.TimeSlot.MORNING;
import static com.slink.ansimheung.common.type.TimeSlot.NIGHT;

/**
 * 시연용 예시 데이터 (#15). 실제 제보가 아니라 시연 흐름을 보여주려고 만든 값이다.
 * <p>
 * 앞 6곳은 시연 출발지({@link #DEMO_START_LAT}, {@link #DEMO_START_LNG})에서 정문까지의 최단 경로(대각선 골목)와
 * 출발지 바로 앞 구간에 놓았다. 그래서 λ가 커질수록 골목을 피해 동쪽 길로 돌아간다. 이 6곳은 모두 "상시"라
 * 조회 시각과 관계없이 결과가 같다. 나머지 24곳은 시연 경로 3개에 영향을 주지 않는 길에 고르게 흩어 두었다.
 * <p>
 * 좌표를 바꾸면 DemoSeederTest 의 시연 경로 기대값(619m / 813m / 915m)이 깨지므로 같이 확인할 것.
 */
public final class DemoSeedData {

    /** 시연 출발지 "북쪽 골목 입구" (labels.js starts.north 와 같은 값). 정문까지 최단 619m */
    public static final double DEMO_START_LAT = 37.344404;
    public static final double DEMO_START_LNG = 126.743579;

    /**
     * 시연 시나리오(발표자료 17쪽)에서 주민이 새로 제보할 위치 — 안전 경로의 첫 구간, 출발지에서 약 30m.
     * 여기에 불법주정차(상시)를 제보하면 안전 경로가 균형 경로와 같은 길로 바뀌고, 관리자가 개선완료하면 원래 길로 돌아온다.
     */
    public static final double DEMO_REPORT_LAT = 37.344635;
    public static final double DEMO_REPORT_LNG = 126.743801;

    /**
     * @param byParent    true 면 시연 주민 계정이 쓴 제보 ("내 제보" 화면용), false 면 작성자 없음
     * @param empathized  시연 주민 계정이 공감했는지 (본인 제보 지점에는 공감 불가 — P-03)
     * @param description 화면 형식 "제목\n설명" (docs/domain.md 8장)
     * @param memo        상태를 바꿀 때 남길 관리자 메모 (접수 상태면 null)
     */
    public record SeedHazard(double lat, double lng, HazardType type, TimeSlot timeSlot, HazardStatus status,
                             boolean byParent, boolean empathized, String description, String memo) {
    }

    public static final List<SeedHazard> HAZARDS = List.of(
            new SeedHazard(37.344173, 126.743357, ILLEGAL_PARKING, ALWAYS, RECEIVED, false, false,
                    "골목 입구 불법주차\n골목으로 들어가는 입구 양쪽에 차가 늘 서 있어서 아이들이 차 사이로 나와요", null),  // 시연 출발지 바로 앞 (최단·균형 경로가 지남)
            new SeedHazard(37.343671, 126.742312, ILLEGAL_PARKING, ALWAYS, RECEIVED, false, true,
                    "골목 안 양쪽 주차\n차가 양쪽에 붙어 있어 사람이 지나갈 폭이 좁아요", null),  // 시연 최단 경로 골목
            new SeedHazard(37.343188, 126.741843, SIDEWALK_GAP, ALWAYS, RECEIVED, false, true,
                    "보도가 끊긴 골목\n중간부터 보도가 없어 차도로 걸어야 해요", null),  // 시연 최단 경로 골목
            new SeedHazard(37.342942, 126.741604, ILLEGAL_PARKING, ALWAYS, RECEIVED, false, false,
                    "상가 앞 이중주차\n짐을 내리는 차가 길을 막아요", null),  // 시연 최단 경로 골목
            new SeedHazard(37.342544, 126.741219, SPEEDING, ALWAYS, RECEIVED, false, true,
                    "골목 과속 차량\n큰길로 빠지는 차들이 골목에서 속도를 내요", null),  // 시연 최단 경로 골목
            new SeedHazard(37.342023, 126.740713, POOR_LIGHTING, ALWAYS, RECEIVED, false, false,
                    "어두운 골목 구간\n가로등이 하나 꺼져 있고 건물 그늘이 져서 낮에도 어두워요", null),  // 시연 최단 경로 골목
            new SeedHazard(37.337975, 126.746586, ILLEGAL_PARKING, MORNING, RECEIVED, false, false,
                    "등교 시간 정차 차량\n아침마다 아이를 내려주는 차가 줄지어 서요", null),
            new SeedHazard(37.337348, 126.740448, SPEEDING, AFTERNOON, RECEIVED, true, false,
                    "하교 시간 과속\n오후에 학원 차량이 빠르게 지나가요", null),
            new SeedHazard(37.341993, 126.747685, POOR_LIGHTING, NIGHT, RECEIVED, false, false,
                    "가로등 고장\n저녁에 학원 끝나고 오는 길이 너무 어두워요", null),
            new SeedHazard(37.339802, 126.738007, SIDEWALK_GAP, ALWAYS, CHECKING, false, true,
                    "보도블록 파손\n보도블록이 들떠 있어 넘어질 뻔했어요", "현장 확인 일정을 잡았어요"),
            new SeedHazard(37.340547, 126.744332, CONSTRUCTION, MORNING, RECEIVED, false, false,
                    "공사 자재 적치\n공사 자재가 보도를 막고 있어요", null),
            new SeedHazard(37.33814, 126.743361, ILLEGAL_PARKING, AFTERNOON, RECEIVED, false, true,
                    "횡단보도 앞 주차\n횡단보도 바로 앞에 차가 서 있어 건너는 아이가 안 보여요", null),
            new SeedHazard(37.341992, 126.737913, SPEEDING, MORNING, RECEIVED, true, false,
                    "등교 시간 신호 위반\n아침에 신호를 무시하고 지나가는 차가 있어요", null),
            new SeedHazard(37.339938, 126.740569, POOR_LIGHTING, NIGHT, RECEIVED, false, true,
                    "공원 옆 어두운 길\n공원 옆 길에 조명이 거의 없어요", null),
            new SeedHazard(37.343744, 126.745857, SIDEWALK_GAP, NIGHT, CHECKING, false, false,
                    "보도 없는 이면도로\n보도가 따로 없어 밤에는 차와 섞여 걸어요", "시청 도로과에 확인 요청했어요"),
            new SeedHazard(37.340077, 126.747312, CONSTRUCTION, ALWAYS, RECEIVED, false, true,
                    "건물 공사 구간\n공사장 가림막 때문에 길이 좁아졌어요", null),
            new SeedHazard(37.343498, 126.739239, ILLEGAL_PARKING, MORNING, RECEIVED, false, false,
                    "아파트 정문 앞 정차\n등교 시간에 정문 앞이 차로 붐벼요", null),
            new SeedHazard(37.342385, 126.74387, SPEEDING, AFTERNOON, RESOLVED, false, true,
                    "교차로 과속\n오후에 교차로를 빠르게 지나는 차가 많아요", "과속방지턱이 설치됐어요"),
            new SeedHazard(37.338653, 126.739556, POOR_LIGHTING, NIGHT, RECEIVED, false, false,
                    "주차장 출입구 주변 어두움\n출입구 주변이 어두워 나오는 차가 잘 안 보여요", null),
            new SeedHazard(37.339268, 126.745258, SIDEWALK_GAP, AFTERNOON, RECEIVED, false, true,
                    "보도 위 적치물\n가게 물건이 보도에 나와 있어 차도로 돌아가요", null),
            new SeedHazard(37.337171, 126.745037, CONSTRUCTION, NIGHT, RECEIVED, true, false,
                    "도로 굴착 공사\n도로를 파 놓은 구간에 밤에는 안내등이 약해요", null),
            new SeedHazard(37.336861, 126.742543, ILLEGAL_PARKING, ALWAYS, RECEIVED, false, true,
                    "인도 위 주차\n인도에 바퀴를 올려 주차한 차가 많아요", null),
            new SeedHazard(37.33948, 126.742746, SPEEDING, MORNING, RECEIVED, false, false,
                    "등굣길 차량 통행 많음\n아침에 출근 차량이 빠르게 지나가요", null),
            new SeedHazard(37.341073, 126.745994, POOR_LIGHTING, NIGHT, RECEIVED, false, true,
                    "상가 뒷길 조명 부족\n상가가 문을 닫으면 뒷길이 깜깜해져요", null),
            new SeedHazard(37.340942, 126.739084, SIDEWALK_GAP, MORNING, RESOLVED, false, false,
                    "보도 턱 높음\n보도 턱이 높아 아이들이 차도로 내려가요", "보도 턱을 낮추는 공사가 끝났어요"),
            new SeedHazard(37.342401, 126.746112, CONSTRUCTION, AFTERNOON, RECEIVED, false, true,
                    "보도 공사 중\n보도 공사로 임시 통로가 좁아요", null),
            new SeedHazard(37.344944, 126.741983, ILLEGAL_PARKING, NIGHT, RECEIVED, false, false,
                    "밤샘 주차 차량\n저녁마다 골목에 큰 차가 서 있어 시야를 가려요", null),
            new SeedHazard(37.341663, 126.742398, SPEEDING, ALWAYS, RECEIVED, false, true,
                    "학교 근처 과속\n어린이보호구역인데도 속도를 줄이지 않는 차가 있어요", null),
            new SeedHazard(37.338802, 126.741266, POOR_LIGHTING, NIGHT, RECEIVED, false, false,
                    "놀이터 옆 어두운 길\n놀이터 옆 길 조명이 꺼져 있어요", null),
            new SeedHazard(37.344819, 126.744569, SIDEWALK_GAP, AFTERNOON, RECEIVED, false, true,
                    "보도 끊김 구간\n건물 앞에서 보도가 끊겨 차도로 걸어요", null)
    );

    private DemoSeedData() {
    }
}
