package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class BatchResponse {

    private int accepted;
    private int deduped;
    private int updated;
    private int rejected;

    @Builder.Default
    private List<Rejection> rejections = new ArrayList<>();

    @Data
    @Builder
    public static class Rejection {
        private String eventId;
        private String reason;
    }
}
