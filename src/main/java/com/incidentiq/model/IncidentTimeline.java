package com.incidentiq.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "incident_timeline")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private String eventType; // e.g., CREATED, STATUS_CHANGE, ASSIGNED, ESCALATED, RESOLVED, CLOSED

    @Column(nullable = false)
    private String description;

    /** User who performed the action (null for system-generated events like auto-escalation) */
    private Long performedBy;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime timestamp;
}
