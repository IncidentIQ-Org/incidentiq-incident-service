package com.incidentiq.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Entity for SLA extension requests.
 * When an incident cannot be resolved within the SLA deadline,
 * the assigned user can request an extension with manager/admin approval.
 */
@Entity
@Table(name = "sla_extension_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaExtensionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long incidentId;

    @Column(nullable = false)
    private Long requestedBy;

    /** Reason for the delay/extension request */
    @Column(nullable = false, length = 2000)
    private String delayReason;

    /** Additional time requested (in hours) */
    @Column(nullable = false)
    private Integer additionalHoursRequested;

    /** Expected completion date after extension */
    @Column(nullable = false)
    private LocalDateTime expectedCompletionDate;

    /** Current deadline before extension */
    @Column
    private LocalDateTime originalDueDate;

    /** New deadline after extension (if approved) */
    @Column
    private LocalDateTime newDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExtensionStatus status = ExtensionStatus.PENDING;

    /** ID of the manager/admin who approved/rejected */
    @Column
    private Long approvedBy;

    /** Reason for approval/rejection */
    @Column(length = 1000)
    private String approvalReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    /** Timestamp when the request was approved/rejected */
    @Column
    private LocalDateTime reviewedAt;

    public enum ExtensionStatus {
        PENDING,
        APPROVED,
        REJECTED
    }
}
