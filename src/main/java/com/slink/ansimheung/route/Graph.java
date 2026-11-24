package com.slink.ansimheung.route;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 메모리에 올린 도보 그래프 (인접 리스트). 만든 뒤에는 바뀌지 않는다 → 여러 요청이 동시에 읽어도 안전.
 * 구간은 방향이 없어서 양쪽 노드에 모두 {@link Link} 를 넣는다. 만드는 곳은 {@link GraphLoader}.
 * DB·Spring 없이 만들 수 있어서 Dijkstra(#13) 단위 테스트에도 그대로 쓴다.
 */
public final class Graph {

    /** 노드 = 교차점 · 길 끝 · 긴 구간 중간점 */
    public record Vertex(long id, double lat, double lng) {
    }

    /** 구간 (DB edges 한 행) */
    public record Segment(long id, long fromNodeId, long toNodeId, double lengthM) {
    }

    /** 한 노드에서 이웃 노드로 가는 길 — {@code edgeId} 로 구간 위험도(edgeRisk)를 찾는다 */
    public record Link(long edgeId, long toNodeId, double lengthM) {
    }

    public static final Graph EMPTY = new Graph(Map.of(), Map.of(), 0);

    private final Map<Long, Vertex> vertices;
    private final Map<Long, List<Link>> links;
    private final int edgeCount;

    private Graph(Map<Long, Vertex> vertices, Map<Long, List<Link>> links, int edgeCount) {
        this.vertices = vertices;
        this.links = links;
        this.edgeCount = edgeCount;
    }

    /** @throws IllegalArgumentException 구간이 없는 노드를 가리킬 때 */
    public static Graph of(Collection<Vertex> vertices, Collection<Segment> segments) {
        Map<Long, Vertex> vs = new HashMap<>();
        Map<Long, List<Link>> ls = new HashMap<>();
        for (Vertex v : vertices) {
            vs.put(v.id(), v);
            ls.put(v.id(), new ArrayList<>());
        }
        for (Segment s : segments) {
            if (!vs.containsKey(s.fromNodeId()) || !vs.containsKey(s.toNodeId())) {
                throw new IllegalArgumentException("구간 " + s.id() + " 이(가) 없는 노드를 가리킴");
            }
            ls.get(s.fromNodeId()).add(new Link(s.id(), s.toNodeId(), s.lengthM()));
            ls.get(s.toNodeId()).add(new Link(s.id(), s.fromNodeId(), s.lengthM()));
        }
        ls.replaceAll((id, list) -> List.copyOf(list));
        return new Graph(Map.copyOf(vs), Map.copyOf(ls), segments.size());
    }

    public boolean isEmpty() {
        return vertices.isEmpty();
    }

    public int nodeCount() {
        return vertices.size();
    }

    public int edgeCount() {
        return edgeCount;
    }

    public Collection<Vertex> vertices() {
        return vertices.values();
    }

    /** 없는 노드면 null */
    public Vertex vertex(long nodeId) {
        return vertices.get(nodeId);
    }

    /** 없는 노드면 빈 목록 */
    public List<Link> links(long nodeId) {
        return links.getOrDefault(nodeId, List.of());
    }

    /** 이웃이 하나도 없는 노드 수 (F-DATA-01 완료 기준: 0) */
    public long isolatedNodeCount() {
        return links.values().stream().filter(List::isEmpty).count();
    }

    /** 모든 노드가 한 덩어리로 이어져 있나 (빈 그래프는 false) */
    public boolean isConnected() {
        if (isEmpty()) {
            return false;
        }
        long start = vertices.keySet().iterator().next();
        Set<Long> seen = new HashSet<>(List.of(start));
        Deque<Long> todo = new ArrayDeque<>(List.of(start));
        while (!todo.isEmpty()) {
            for (Link l : links(todo.pop())) {
                if (seen.add(l.toNodeId())) {
                    todo.push(l.toNodeId());
                }
            }
        }
        return seen.size() == vertices.size();
    }
}
