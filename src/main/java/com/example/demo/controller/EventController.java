package com.example.demo.controller;

import com.example.demo.dto.BatchResponse;
import com.example.demo.dto.EventRequest;
import com.example.demo.dto.StatsResponse;
import com.example.demo.dto.TopLineResponse;
import com.example.demo.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events")
public class EventController {

    private final EventService eventService;

    @PostMapping("/batch")
    public BatchResponse ingestBatch(@RequestBody List<EventRequest> events) {
        return eventService.processBatch(events);
    }

    @GetMapping("/stats")
    public StatsResponse getStats(
            @RequestParam String machineId,
            @RequestParam String start,
            @RequestParam String end) {
        return eventService.getStats(machineId, start, end);
    }

    @GetMapping("/stats/top-defect-lines")
    public List<TopLineResponse> getTopLines(
            @RequestParam String factoryId,
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "10") int limit) {

        return eventService.getTopDefectLines(factoryId, from, to, limit);
    }


}
