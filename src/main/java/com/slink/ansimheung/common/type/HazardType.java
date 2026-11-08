package com.slink.ansimheung.common.type;

/** 위험 유형 5종 (P-06, "기타" 없음). weight = 유형 가중치 W (C-01) */
public enum HazardType {
    SIDEWALK_GAP("보도단절", 2.0),
    ILLEGAL_PARKING("불법주정차", 1.8),
    SPEEDING("과속차량", 1.6),
    CONSTRUCTION("공사구간", 1.4),
    POOR_LIGHTING("조명부족", 1.2);

    private final String label;
    private final double weight;

    HazardType(String label, double weight) {
        this.label = label;
        this.weight = weight;
    }

    public String label() {
        return label;
    }

    public double weight() {
        return weight;
    }
}
