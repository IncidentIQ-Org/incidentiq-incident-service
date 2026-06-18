package com.incidentiq.service.impl;

import com.incidentiq.dto.request.CreateIncidentRequest;
import com.incidentiq.dto.request.UpdateIncidentRequest;
import com.incidentiq.dto.response.IncidentResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.exception.IncidentNotFoundException;
import com.incidentiq.exception.InvalidStatusTransitionException;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.service.TimelineService;
import com.incidentiq.service.AuditService;
import com.incidentiq.security.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IncidentServiceImpl}.
 * Uses Mockito to isolate the service layer from the database.
 */
@ExtendWith(MockitoExtension.class)
class IncidentServiceImplTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private TimelineService timelineService;

    @Mock
    private AuditService auditService;

    @Mock
    private com.incidentiq.service.SimilarityDetectionService similarityDetectionService;

    @Mock
    private AuthorizationService authService;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private IncidentServiceImpl incidentService;

    private Incident sampleIncident;

    @BeforeEach
    void setUp() {
        // Stub getCurrentUserId for security logic added to service
        org.mockito.Mockito.lenient().when(authService.getCurrentUserId()).thenReturn(1L);
        
        // Lenient stub for RestTemplate to avoid issues in tests that don't hit auto-assignment
        org.mockito.Mockito.lenient().when(restTemplate.getForObject(any(String.class), any()))
                .thenReturn(null);
        
        sampleIncident = Incident.builder()
                .id(1L)
                .title("Server Down")
                .description("Production server not responding")
                .category(IncidentCategory.CLOUD)
                .priority(IncidentPriority.HIGH)
                .status(IncidentStatus.OPEN)
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ──────────────────────────────────────────────
    //  Create Incident
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Create Incident")
    class CreateIncidentTests {

        @Test
        @DisplayName("Should create incident with OPEN status")
        void shouldCreateIncidentSuccessfully() {
            CreateIncidentRequest request = CreateIncidentRequest.builder()
                    .title("Server Down")
                    .description("Production server not responding")
                    .category(IncidentCategory.CLOUD)
                    .priority(IncidentPriority.HIGH)
                    .build();

            when(incidentRepository.save(any(Incident.class))).thenReturn(sampleIncident);

            IncidentResponse response = incidentService.createIncident(request);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getTitle()).isEqualTo("Server Down");
            assertThat(response.getStatus()).isEqualTo("OPEN");
            verify(incidentRepository).save(any(Incident.class));
        }
    }

    // ──────────────────────────────────────────────
    //  Get Incident by ID
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Get Incident by ID")
    class GetIncidentByIdTests {

        @Test
        @DisplayName("Should return incident when found")
        void shouldReturnIncidentWhenFound() {
            when(incidentRepository.findById(1L)).thenReturn(Optional.of(sampleIncident));

            IncidentResponse response = incidentService.getIncidentById(1L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
            assertThat(response.getTitle()).isEqualTo("Server Down");
        }

        @Test
        @DisplayName("Should throw IncidentNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentService.getIncidentById(99L))
                    .isInstanceOf(IncidentNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ──────────────────────────────────────────────
    //  Get All Incidents (Paginated)
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Get All Incidents")
    class GetAllIncidentsTests {

        @Test
        @DisplayName("Should return paginated incidents")
        void shouldReturnPaginatedIncidents() {
            Pageable pageable = PageRequest.of(0, 5);
            Page<Incident> page = new PageImpl<>(List.of(sampleIncident), pageable, 1);

            when(incidentRepository.findAll(pageable)).thenReturn(page);

            Page<IncidentResponse> result = incidentService.getAllIncidents(pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    // ──────────────────────────────────────────────
    //  Update Incident
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Update Incident")
    class UpdateIncidentTests {

        @Test
        @DisplayName("Should update title and description")
        void shouldUpdateFieldsSuccessfully() {
            UpdateIncidentRequest request = UpdateIncidentRequest.builder()
                    .title("Updated Title")
                    .description("Updated Description")
                    .build();

            when(incidentRepository.findById(1L)).thenReturn(Optional.of(sampleIncident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(sampleIncident);

            IncidentResponse response = incidentService.updateIncident(1L, request);

            assertThat(response).isNotNull();
            verify(incidentRepository).save(any(Incident.class));
        }

        @Test
        @DisplayName("Should allow valid status transition OPEN → IN_PROGRESS")
        void shouldAllowValidStatusTransition() {
            UpdateIncidentRequest request = UpdateIncidentRequest.builder()
                    .status(IncidentStatus.IN_PROGRESS)
                    .assignedTo(10L)
                    .build();

            when(incidentRepository.findById(1L)).thenReturn(Optional.of(sampleIncident));
            when(incidentRepository.save(any(Incident.class))).thenReturn(sampleIncident);

            IncidentResponse response = incidentService.updateIncident(1L, request);

            assertThat(response).isNotNull();
            verify(incidentRepository).save(any(Incident.class));
        }

        @Test
        @DisplayName("Should reject invalid status transition OPEN → RESOLVED")
        void shouldRejectInvalidStatusTransition() {
            UpdateIncidentRequest request = UpdateIncidentRequest.builder()
                    .status(IncidentStatus.RESOLVED)
                    .build();

            when(incidentRepository.findById(1L)).thenReturn(Optional.of(sampleIncident));

            assertThatThrownBy(() -> incidentService.updateIncident(1L, request))
                    .isInstanceOf(InvalidStatusTransitionException.class)
                    .hasMessageContaining("OPEN")
                    .hasMessageContaining("RESOLVED");

            verify(incidentRepository, never()).save(any(Incident.class));
        }

        @Test
        @DisplayName("Should reject invalid status transition OPEN → CLOSED")
        void shouldRejectOpenToClosed() {
            UpdateIncidentRequest request = UpdateIncidentRequest.builder()
                    .status(IncidentStatus.CLOSED)
                    .build();

            when(incidentRepository.findById(1L)).thenReturn(Optional.of(sampleIncident));

            assertThatThrownBy(() -> incidentService.updateIncident(1L, request))
                    .isInstanceOf(InvalidStatusTransitionException.class);
        }

        @Test
        @DisplayName("Should throw IncidentNotFoundException on update of non-existent incident")
        void shouldThrowWhenUpdatingNonExistent() {
            UpdateIncidentRequest request = UpdateIncidentRequest.builder()
                    .title("Updated")
                    .build();

            when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentService.updateIncident(99L, request))
                    .isInstanceOf(IncidentNotFoundException.class);
        }
    }

    // ──────────────────────────────────────────────
    //  Delete Incident
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("Delete Incident")
    class DeleteIncidentTests {

        @Test
        @DisplayName("Should delete existing incident")
        void shouldDeleteSuccessfully() {
            when(incidentRepository.findById(1L)).thenReturn(Optional.of(sampleIncident));

            incidentService.deleteIncident(1L);

            verify(incidentRepository).delete(sampleIncident);
        }

        @Test
        @DisplayName("Should throw IncidentNotFoundException on delete of non-existent incident")
        void shouldThrowWhenDeletingNonExistent() {
            when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentService.deleteIncident(99L))
                    .isInstanceOf(IncidentNotFoundException.class);
        }
    }
}
