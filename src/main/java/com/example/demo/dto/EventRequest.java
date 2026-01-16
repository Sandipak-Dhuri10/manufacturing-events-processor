package com.example.demo.dto;

import lombok.Data;

@Data
public class EventRequest {
    private String eventId;
    private String eventTime;
    private String receivedTime;
    private String machineId;
    private String factoryId;
    private String lineId;
    private long durationMs;
    private int defectCount;
}
