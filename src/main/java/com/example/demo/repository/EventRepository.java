package com.example.demo.repository;

import com.example.demo.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, String> {

    List<Event> findByMachineIdAndEventTimeBetween(
            String machineId, Instant start, Instant end);

    List<Event> findByFactoryIdAndEventTimeBetween(
            String factoryId, Instant start, Instant end);
}
