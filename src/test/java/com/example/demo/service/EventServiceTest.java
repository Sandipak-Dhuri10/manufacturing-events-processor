package com.example.demo.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mockito;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.dto.EventRequest;
import com.example.demo.model.Event;
import com.example.demo.repository.EventRepository;

public class EventServiceTest {

    private EventRepository repo;
    private EventService service;

    @BeforeEach
    void setup() {
        repo = Mockito.mock(EventRepository.class);
        service = new EventService(repo);
    }

    private EventRequest req(String id, int defect, long durationMs) {
        EventRequest r = new EventRequest();
        r.setEventId(id);
        r.setEventTime("2026-01-15T10:12:03.123Z");
        r.setReceivedTime("2026-01-15T10:12:04.500Z");
        r.setMachineId("M-001");
        r.setFactoryId("F01");
        r.setLineId("L01");
        r.setDurationMs(durationMs);
        r.setDefectCount(defect);
        return r;
    }

    @Test
    void identicalDuplicateGetsDeduped() {
        EventRequest req = req("E-1", 1, 1000);

        Event existing = Event.builder()
                .eventId("E-1")
                .eventTime(Instant.parse(req.getEventTime()))
                .receivedTime(Instant.parse(req.getReceivedTime()))
                .machineId("M-001")
                .factoryId("F01")
                .lineId("L01")
                .durationMs(1000)
                .defectCount(1)
                .build();

        when(repo.findById("E-1")).thenReturn(Optional.of(existing));

        var result = service.processBatch(List.of(req));

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getDeduped());
    }

    @Test
    void newerPayloadUpdatesExisting() {
        EventRequest req = req("E-1", 2, 2000);
        req.setReceivedTime("2026-01-15T10:12:10.000Z");

        Event existing = Event.builder()
                .eventId("E-1")
                .eventTime(Instant.parse("2026-01-15T10:11:00.000Z"))
                .receivedTime(Instant.parse("2026-01-15T10:12:04.000Z"))
                .machineId("M-001")
                .factoryId("F01")
                .lineId("L01")
                .durationMs(1000)
                .defectCount(1)
                .build();

        when(repo.findById("E-1")).thenReturn(Optional.of(existing));

        var result = service.processBatch(List.of(req));

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getUpdated());
    }

    @Test
    void olderPayloadIgnored() {
        EventRequest req = req("E-1", 2, 2000);
        req.setReceivedTime("2026-01-15T10:12:01.000Z"); // older

        Event existing = Event.builder()
                .eventId("E-1")
                .eventTime(Instant.parse("2026-01-15T10:12:03.000Z"))
                .receivedTime(Instant.parse("2026-01-15T10:12:04.500Z"))
                .machineId("M-001")
                .factoryId("F01")
                .lineId("L01")
                .durationMs(1000)
                .defectCount(1)
                .build();

        when(repo.findById("E-1")).thenReturn(Optional.of(existing));

        var result = service.processBatch(List.of(req));

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getDeduped());
    }

    @Test
    void invalidDurationRejected() {
        EventRequest req = req("E-1", 1, -10);

        var result = service.processBatch(List.of(req));

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getRejected());
    }

    @Test
    void futureEventRejected() {
        EventRequest req = req("E-1", 1, 1000);
        req.setEventTime(Instant.now().plusSeconds(16 * 60).toString());

        var result = service.processBatch(List.of(req));

        assertEquals(0, result.getAccepted());
        assertEquals(1, result.getRejected());
    }

    @Test
    void defectMinusOneAcceptedButIgnored() {
        EventRequest req = req("E-1", -1, 1000);

        var result = service.processBatch(List.of(req));

        assertEquals(1, result.getAccepted());
    }

    @Test
    void startInclusiveEndExclusive() {
        Event e = Event.builder()
                .eventId("E-1")
                .eventTime(Instant.parse("2026-01-15T10:00:00Z"))
                .defectCount(1)
                .machineId("M-001")
                .factoryId("F01")
                .lineId("L01")
                .build();

        when(repo.findByMachineIdAndEventTimeBetween(
                eq("M-001"),
                any(),
                any())).thenReturn(List.of(e));

        var stats = service.getStats("M-001",
                "2026-01-15T00:00:00Z",
                "2026-01-16T00:00:00Z");

        assertEquals(1, stats.getEventsCount());
    }

    @Test
    void concurrentIngestionWorks() throws InterruptedException {
        when(repo.findById(anyString())).thenReturn(Optional.empty());

        List<EventRequest> requests = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> req("E-" + i, 0, 1000))
                .toList();

        Runnable task = () -> service.processBatch(requests);

        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        verify(repo, atLeast(100)).save(any(Event.class));
    }


}
