package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StatsResponse {
    private String machineId;
    private String start;
    private String end;
    private long eventsCount;
    private long defectsCount;
    private double avgDefectRate;
    private String status;
}
