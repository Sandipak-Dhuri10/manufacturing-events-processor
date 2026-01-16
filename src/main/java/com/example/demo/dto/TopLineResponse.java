package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TopLineResponse {
    private String lineId;
    private long eventCount;
    private long totalDefects;
    private double defectsPercent;
}
