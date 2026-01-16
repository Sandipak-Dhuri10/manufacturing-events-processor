package com.example.demo.service;

import com.example.demo.dto.BatchResponse;
import com.example.demo.dto.EventRequest;
import com.example.demo.dto.StatsResponse;
import com.example.demo.dto.TopLineResponse;
import com.example.demo.model.Event;
import com.example.demo.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    private static final long MAX_DURATION_MS = 6 * 60 * 60 * 1000;  

    public BatchResponse processBatch(List<EventRequest> requests) {

        int accepted = 0;
        int deduped = 0;
        int updated = 0;
        int rejected = 0;
        var rejectionList = new java.util.ArrayList<BatchResponse.Rejection>();

        for (EventRequest req : requests) {

            
            if (!isValid(req)) {
                rejected++;
                rejectionList.add(BatchResponse.Rejection.builder()
                        .eventId(req.getEventId())
                        .reason("INVALID")
                        .build());
                continue;
            }

            Instant eventTime = Instant.parse(req.getEventTime());
            Instant receivedTime = parseOrNow(req.getReceivedTime());

            
            Event existing = eventRepository.findById(req.getEventId()).orElse(null);

            if (existing != null) {

                
                if (isSame(existing, req)) {
                    deduped++;
                    continue;
                }

                
                if (receivedTime.isAfter(existing.getReceivedTime())) {
                    
                    updated++;
                    updateEventFromRequest(existing, req, receivedTime);
                    eventRepository.save(existing);
                } else {
                    
                    deduped++;
                }

                continue;
            }

            
            Event newEvent = mapToEntity(req, eventTime, receivedTime);
            eventRepository.save(newEvent);
            accepted++;
        }

        return BatchResponse.builder()
                .accepted(accepted)
                .updated(updated)
                .deduped(deduped)
                .rejected(rejected)
                .rejections(rejectionList)
                .build();
    }

    public StatsResponse getStats(String machineId, String start, String end) {

        var startTime = Instant.parse(start);
        var endTime = Instant.parse(end);

        var events = eventRepository.findByMachineIdAndEventTimeBetween(
            machineId, startTime, endTime);

        long eventsCount = events.size();

        long defectsCount = events.stream()
            .filter(e -> e.getDefectCount() >= 0)
            .mapToLong(Event::getDefectCount)
            .sum();

        double hours = (endTime.getEpochSecond() - startTime.getEpochSecond()) / 3600.0;
        double avgRate = hours > 0 ? defectsCount / hours : 0.0;

        String status = avgRate < 2.0 ? "Healthy" : "Warning";

        return StatsResponse.builder()
            .machineId(machineId)
            .start(start)
            .end(end)
            .eventsCount(eventsCount)
            .defectsCount(defectsCount)
            .avgDefectRate(avgRate)
            .status(status)
            .build();
    }

    public List<TopLineResponse> getTopDefectLines(
            String factoryId,
            String from,
            String to,
            int limit) {

        var start = Instant.parse(from);
        var end = Instant.parse(to);

        var events = eventRepository.findByFactoryIdAndEventTimeBetween(factoryId, start, end);

        
        var grouped = events.stream()
            .collect(java.util.stream.Collectors.groupingBy(Event::getLineId));

        var result = new java.util.ArrayList<TopLineResponse>();

        for (var entry : grouped.entrySet()) {
            var lineId = entry.getKey();
            var evts = entry.getValue();

            long eventCount = evts.size();
            long defects = evts.stream()
                .filter(e -> e.getDefectCount() >= 0)
                .mapToLong(Event::getDefectCount)
                .sum();

            double percent = eventCount > 0 ? (defects * 100.0) / eventCount : 0.0;

            result.add(TopLineResponse.builder()
                .lineId(lineId)
                .eventCount(eventCount)
                .totalDefects(defects)
                .defectsPercent(Math.round(percent * 100.0) / 100.0) // round 2 decimals
                .build());
        }

        
        return result.stream()
            .sorted((a, b) -> Long.compare(b.getTotalDefects(), a.getTotalDefects()))
            .limit(limit)
            .toList();
    }



    
    private boolean isValid(EventRequest req) {
        try {
            Instant eventTime = Instant.parse(req.getEventTime());

            
            if (eventTime.isAfter(Instant.now().plus(Duration.ofMinutes(15)))) {
                return false;
            }

            
            if (req.getDurationMs() < 0 || req.getDurationMs() > MAX_DURATION_MS) {
                return false;
            }

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    

    private Instant parseOrNow(String time) {
        try { return Instant.parse(time); }
        catch (Exception ex) { return Instant.now(); }
    }

    private boolean isSame(Event existing, EventRequest req) {
        return existing.getMachineId().equals(req.getMachineId())
                && existing.getFactoryId().equals(req.getFactoryId())
                && existing.getLineId().equals(req.getLineId())
                && existing.getDurationMs() == req.getDurationMs()
                && existing.getDefectCount() == req.getDefectCount()
                && existing.getEventTime().equals(Instant.parse(req.getEventTime()));
    }

    private Event mapToEntity(EventRequest req, Instant eventTime, Instant receivedTime) {
        return Event.builder()
                .eventId(req.getEventId())
                .eventTime(eventTime)
                .receivedTime(receivedTime)
                .machineId(req.getMachineId())
                .factoryId(req.getFactoryId())
                .lineId(req.getLineId())
                .durationMs(req.getDurationMs())
                .defectCount(req.getDefectCount())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private void updateEventFromRequest(Event event, EventRequest req, Instant receivedTime) {
        event.setEventTime(Instant.parse(req.getEventTime()));
        event.setReceivedTime(receivedTime);
        event.setMachineId(req.getMachineId());
        event.setFactoryId(req.getFactoryId());
        event.setLineId(req.getLineId());
        event.setDurationMs(req.getDurationMs());
        event.setDefectCount(req.getDefectCount());
        event.setUpdatedAt(Instant.now());
    }
}
