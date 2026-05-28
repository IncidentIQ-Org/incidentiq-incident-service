package com.incidentiq.repository;

import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.enums.EscalationLevel;
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

    Page<Incident> findByAssignedTo(Long assignedTo, Pageable pageable);

    long countByStatus(IncidentStatus status);

    long countByPriority(IncidentPriority priority);

    long countByCategory(com.incidentiq.enums.IncidentCategory category);

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

    // ── War Room Queries ────────────────────────────────────────────────

    /** Count CRITICAL incidents created in the last hour */
    @Query("SELECT COUNT(i) FROM Incident i WHERE i.priority = 'CRITICAL' AND i.createdAt > :since")
    long countRecentCriticalIncidents(@Param("since") LocalDateTime since);
}
