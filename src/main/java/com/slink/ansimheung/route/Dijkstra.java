package com.slink.ansimheung.route;

import com.slink.ansimheung.route.Graph.Link;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

/**
 * 최소 비용 경로 (PriorityQueue Dijkstra). 순수 Java — Spring·DB 없음.
 * 비용은 호출하는 쪽이 정한다: {@code cost(e) = length_m + λ × edgeRisk(e)} (docs/domain.md 7장).
 * 비용은 0 이상이어야 한다 (길이 ≥ 0, λ ≥ 0, 점수 ≥ 0).
 */
public final class Dijkstra {

    /** 지나는 노드(출발 → 도착)와 구간. 출발 = 도착이면 노드 1개, 구간 0개 */
    public record Path(List<Long> nodeIds, List<Link> links, double cost) {
    }

    private record Entry(long nodeId, double cost) {
    }

    private Dijkstra() {
    }

    /** 이어지지 않으면 empty */
    public static Optional<Path> shortestPath(Graph graph, long from, long to, ToDoubleFunction<Link> cost) {
        if (graph.vertex(from) == null || graph.vertex(to) == null) {
            return Optional.empty();
        }
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Link> cameBy = new HashMap<>();     // 노드 → 그 노드에 도착한 구간
        Map<Long, Long> cameFrom = new HashMap<>();
        PriorityQueue<Entry> queue = new PriorityQueue<>((a, b) -> a.cost() != b.cost()
                ? Double.compare(a.cost(), b.cost()) : Long.compare(a.nodeId(), b.nodeId()));
        best.put(from, 0.0);
        queue.add(new Entry(from, 0.0));

        while (!queue.isEmpty()) {
            Entry cur = queue.poll();
            if (cur.cost() > best.get(cur.nodeId())) {
                continue;   // 더 싼 길로 이미 처리됨
            }
            if (cur.nodeId() == to) {
                break;
            }
            for (Link link : graph.links(cur.nodeId())) {
                double next = cur.cost() + cost.applyAsDouble(link);
                if (next < best.getOrDefault(link.toNodeId(), Double.POSITIVE_INFINITY)) {
                    best.put(link.toNodeId(), next);
                    cameBy.put(link.toNodeId(), link);
                    cameFrom.put(link.toNodeId(), cur.nodeId());
                    queue.add(new Entry(link.toNodeId(), next));
                }
            }
        }
        if (!best.containsKey(to)) {
            return Optional.empty();
        }

        List<Long> nodes = new ArrayList<>();
        List<Link> links = new ArrayList<>();
        for (long n = to; n != from; n = cameFrom.get(n)) {
            nodes.add(n);
            links.add(cameBy.get(n));
        }
        nodes.add(from);
        Collections.reverse(nodes);
        Collections.reverse(links);
        return Optional.of(new Path(List.copyOf(nodes), List.copyOf(links), best.get(to)));
    }
}
