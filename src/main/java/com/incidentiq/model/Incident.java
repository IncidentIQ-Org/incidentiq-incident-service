package com.incidentiq.model;

import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.enums.EscalationLevel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * JPA entity representing an incident in the system.
 */
@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private IncidentPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(nullable = false)
    private Long createdBy;

    @Column
    private Long assignedTo;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column
    private LocalDateTime dueDate;

    // ── Escalation Fields ──────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    @Builder.Default
    private EscalationLevel escalationLevel = EscalationLevel.NONE;

    /** Timestamp when the incident was first escalated */
    @Column
    private LocalDateTime escalatedAt;

    /** Number of times this incident has been escalated */
    @Column
    @Builder.Default
    private Integer escalationCount = 0;

    // ── War Room Fields ────────────────────────────────
    /** True if this incident is part of an active War Room session */
    @Column
    @Builder.Default
    private boolean inWarRoom = false;

    @Column
    private Long warRoomId;

    // ── Similarity / Dedup Fields ──────────────────────
    /** Keyword tags for similarity matching (comma-separated) */
    @Column(length = 500)
    private String tags;
}
