package com.incidentiq.model;

import com.incidentiq.enums.Complexity;
import com.incidentiq.enums.IncidentPriority;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * SLA target for a (priority, complexity) pair. Uniqueness of the composite
 * key is enforced by Flyway migration V4 (constraint uq_sla_priority_complexity).
 */
@Entity
@Table(name = "sla_configs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Complexity complexity;

    @Column(name = "target_hours", nullable = false)
    private Integer targetHours;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
