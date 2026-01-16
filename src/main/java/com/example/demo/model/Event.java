
package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    private String eventId;

    private Instant eventTime;

    private Instant receivedTime;

    private String machineId;

    private String factoryId;

    private String lineId;

    private long durationMs;

    private int defectCount;

    private Instant createdAt;

    private Instant updatedAt;
}


