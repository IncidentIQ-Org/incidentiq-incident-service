package com.incidentiq.repository;

import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.enums.EscalationLevel;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.model.Incident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA repository for Incident entities.
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Page<Incident> findByCreatedBy(Long createdBy, Pageable pageable);

    Page<Incident> findByStatusIn(List<IncidentStatus> statuses, Pageable pageable);

    Page<Incident> findByAssignedTo(Long assignedTo, Pageable pageable);

    List<Incident> findAllByAssignedTo(Long assignedTo);

    long countByStatus(IncidentStatus status);

    long countByPriority(IncidentPriority priority);

    long countByCategory(IncidentCategory category);

    List<Incident> findByCategory(IncidentCategory category);

    @Query("SELECT COUNT(i) FROM Incident i WHERE i.dueDate < :now AND i.status NOT IN ('CLOSED', 'RESOLVED')")
    long countOverdue(LocalDateTime now);

    // ── Escalation Queries ──────────────────────────────────────────────

    /** Find CRITICAL incidents that are still OPEN and older than N minutes (no acknowledgment) */
    @Query("SELECT i FROM Incident i WHERE i.priority = 'CRITICAL' AND i.status = 'OPEN' " +
           "AND i.createdAt < :threshold AND i.escalationLevel = 'NONE'")
    List<Incident> findCriticalUnacknowledged(@Param("threshold") LocalDateTime threshold);

    /** Find HIGH/CRITICAL incidents that breached SLA and are not yet ADMIN-escalated */
    @Query("SELECT i FROM Incident i WHERE i.dueDate < :now " +
           "AND i.status NOT IN ('CLOSED', 'RESOLVED') " +
           "AND i.escalationLevel != 'ADMIN'")
    List<Incident> findSlaBreachedNotFullyEscalated(@Param("now") LocalDateTime now);

    /** Find incidents already escalated to MANAGER but still unresolved past grace period */
    @Query("SELECT i FROM Incident i WHERE i.escalationLevel = 'MANAGER' " +
           "AND i.escalatedAt < :threshold " +
           "AND i.status NOT IN ('CLOSED', 'RESOLVED')")
    List<Incident> findManagerEscalatedPastGrace(@Param("threshold") LocalDateTime threshold);

    // ── Similarity Queries ──────────────────────────────────────────────

    /** Full-text LIKE search across title, description and tags */
    @Query("SELECT i FROM Incident i WHERE " +
           "LOWER(i.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(i.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(i.tags) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Incident> findSimilarByKeyword(@Param("keyword") String keyword);

    /** Find incidents with the same category that are recent (last 30 days) */
    @Query("SELECT i FROM Incident i WHERE i.category = :category AND i.createdAt > :since ORDER BY i.createdAt DESC")
    List<Incident> findRecentByCategory(@Param("category") com.incidentiq.enums.IncidentCategory category,
                                         @Param("since") LocalDateTime since);

    /** Find RESOLVED or CLOSED incidents by category - used for similarity knowledge base */
    @Query("SELECT i FROM Incident i WHERE i.category = :category " +
           "AND i.status IN ('RESOLVED', 'CLOSED') ORDER BY i.resolvedAt DESC NULLS LAST")
    List<Incident> findResolvedByCategory(@Param("category") IncidentCategory category);

    /** Find ALL RESOLVED or CLOSED incidents across all categories - cross-category fallback */
    @Query("SELECT i FROM Incident i WHERE i.status IN ('RESOLVED', 'CLOSED') ORDER BY i.resolvedAt DESC NULLS LAST")
    List<Incident> findAllResolved();

    // ── War Room Queries ────────────────────────────────────────────────

    /** Count CRITICAL incidents created in the last hour */
    @Query("SELECT COUNT(i) FROM Incident i WHERE i.priority = 'CRITICAL' AND i.createdAt > :since")
    long countRecentCriticalIncidents(@Param("since") LocalDateTime since);

    /** Find active incidents with a dueDate set that haven't had a 75% SLA warning sent yet */
    @Query("SELECT i FROM Incident i WHERE i.status NOT IN ('CLOSED', 'RESOLVED') " +
           "AND i.dueDate IS NOT NULL AND i.dueDate > :now AND (i.slaAlertSent = false OR i.slaAlertSent IS NULL)")
    List<Incident> findActiveWithDueDateAndNoAlert(@Param("now") LocalDateTime now);
}
