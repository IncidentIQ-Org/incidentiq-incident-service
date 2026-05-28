package com.incidentiq.model;

import com.incidentiq.enums.WarRoomStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a War Room session — a coordinated response channel
 * for major or repeated incidents affecting critical services.
 */
@Entity
@Table(name = "war_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WarRoomStatus status = WarRoomStatus.ACTIVE;

    /** Comma-separated incident IDs linked to this War Room */
    @Column(length = 1000)
    private String linkedIncidentIds;

    /** Comma-separated user IDs of assigned responders */
    @Column(length = 500)
    private String responderIds;

    /** Summary written at resolution time */
    @Column(length = 2000)
    private String resolutionSummary;

    @Column
    private Long createdBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime resolvedAt;
}
