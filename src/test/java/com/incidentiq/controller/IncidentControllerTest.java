package com.incidentiq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incidentiq.dto.request.CreateIncidentRequest;
import com.incidentiq.dto.request.UpdateIncidentRequest;
import com.incidentiq.dto.response.IncidentResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.enums.IncidentStatus;
import com.incidentiq.advice.GlobalExceptionHandler;
import com.incidentiq.exception.IncidentNotFoundException;
import com.incidentiq.exception.InvalidStatusTransitionException;
import com.incidentiq.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link IncidentController}.
 * Uses MockMvc to test HTTP request/response handling without starting the full server.
 */
@WebMvcTest(IncidentController.class)
@Import(GlobalExceptionHandler.class)
@WithMockUser(roles = "ADMIN")
class IncidentControllerTest {

    private static final String BASE_URL = "";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IncidentService incidentService;

    @MockitoBean
    private com.incidentiq.service.TimelineService timelineService;

    @MockitoBean
    private com.incidentiq.service.AuditService auditService;

    @MockitoBean
    private com.incidentiq.service.SeveritySuggestionService severitySuggestionService;

    @MockitoBean
    private com.incidentiq.service.DuplicateDetectionService duplicateDetectionService;

    @MockitoBean
    private com.incidentiq.service.SimilarityDetectionService similarityDetectionService;

    @MockitoBean(name = "authService")
    private com.incidentiq.security.AuthorizationService authService;

    private IncidentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleResponse = IncidentResponse.builder()
                .id(1L)
                .title("Server Down")
                .description("Production server not responding")
                .category("CLOUD")
                .priority("HIGH")
                .status("OPEN")
                .createdBy(1L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ──────────────────────────────────────────────
    //  POST /api/v1/incidents
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/incidents")
    class CreateIncidentTests {

        @Test
        @DisplayName("Should return 201 when creating valid incident")
        void shouldReturn201ForValidRequest() throws Exception {
            CreateIncidentRequest request = CreateIncidentRequest.builder()
                    .title("Server Down")
                    .description("Production server not responding")
                    .category(IncidentCategory.CLOUD)
                    .priority(IncidentPriority.HIGH)
                    .build();

            when(incidentService.createIncident(any(CreateIncidentRequest.class)))
                    .thenReturn(sampleResponse);

            mockMvc.perform(post(BASE_URL + "/create")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("Server Down"))
                    .andExpect(jsonPath("$.status").value("OPEN"));
        }

        @Test
        @DisplayName("Should return 400 when title is missing")
        void shouldReturn400WhenTitleMissing() throws Exception {
            CreateIncidentRequest request = CreateIncidentRequest.builder()
                    .description("Some description")
                    .category(IncidentCategory.BACKEND)
                    .priority(IncidentPriority.LOW)
                    .build();

            mockMvc.perform(post(BASE_URL + "/create")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.title").exists());
        }

        @Test
        @DisplayName("Should return 400 when body is empty")
        void shouldReturn400WhenBodyEmpty() throws Exception {
            mockMvc.perform(post(BASE_URL + "/create")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ──────────────────────────────────────────────
    //  GET /api/v1/incidents/{id}
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/incidents/{id}")
    class GetIncidentByIdTests {

        @Test
        @DisplayName("Should return 200 when incident exists")
        void shouldReturn200WhenFound() throws Exception {
            when(incidentService.getIncidentById(1L)).thenReturn(sampleResponse);

            mockMvc.perform(get(BASE_URL + "/view/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.title").value("Server Down"));
        }

        @Test
        @DisplayName("Should return 404 when incident does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            when(incidentService.getIncidentById(99L))
                    .thenThrow(new IncidentNotFoundException("Incident not found with id: 99"));

            mockMvc.perform(get(BASE_URL + "/view/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Incident not found with id: 99"));
        }
    }

    // ──────────────────────────────────────────────
    //  GET /api/v1/incidents (Paginated)
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/incidents")
    class GetAllIncidentsTests {

        @Test
        @DisplayName("Should return 200 with paginated results")
        void shouldReturnPaginatedResults() throws Exception {
            Page<IncidentResponse> page = new PageImpl<>(List.of(sampleResponse));
            when(incidentService.getAllIncidents(any(Pageable.class))).thenReturn(page);

            mockMvc.perform(get(BASE_URL + "/all")
                            .param("page", "0")
                            .param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(1))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    // ──────────────────────────────────────────────
    //  PUT /api/v1/incidents/{id}
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/v1/incidents/{id}")
    class UpdateIncidentTests {

        @Test
        @DisplayName("Should return 200 for valid update")
        void shouldReturn200ForValidUpdate() throws Exception {
            UpdateIncidentRequest request = UpdateIncidentRequest.builder()
                    .status(IncidentStatus.IN_PROGRESS)
                    .assignedTo(10L)
                    .build();

            IncidentResponse updatedResponse = IncidentResponse.builder()
                    .id(1L)
                    .title("Server Down")
                    .description("Production server not responding")
                    .category("CLOUD")
                    .priority("HIGH")
                    .status("IN_PROGRESS")
                    .createdBy(1L)
                    .assignedTo(10L)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            when(incidentService.updateIncident(eq(1L), any(UpdateIncidentRequest.class)))
                    .thenReturn(updatedResponse);

            mockMvc.perform(put(BASE_URL + "/update/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                    .andExpect(jsonPath("$.assignedTo").value(10));
        }

        @Test
        @DisplayName("Should return 400 for invalid status transition")
        void shouldReturn400ForInvalidTransition() throws Exception {
            UpdateIncidentRequest request = UpdateIncidentRequest.builder()
                    .status(IncidentStatus.RESOLVED)
                    .build();

            when(incidentService.updateIncident(eq(1L), any(UpdateIncidentRequest.class)))
                    .thenThrow(new InvalidStatusTransitionException(
                            "Invalid status transition from OPEN to RESOLVED. Allowed transitions: [IN_PROGRESS]"));

            mockMvc.perform(put(BASE_URL + "/update/1")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(
                            "Invalid status transition from OPEN to RESOLVED. Allowed transitions: [IN_PROGRESS]"));
        }
    }

    // ──────────────────────────────────────────────
    //  DELETE /api/v1/incidents/{id}
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/v1/incidents/{id}")
    class DeleteIncidentTests {

        @Test
        @DisplayName("Should return 204 when incident deleted")
        void shouldReturn204WhenDeleted() throws Exception {
            doNothing().when(incidentService).deleteIncident(1L);

            mockMvc.perform(delete(BASE_URL + "/delete/1")
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 404 when incident does not exist")
        void shouldReturn404WhenNotFound() throws Exception {
            doThrow(new IncidentNotFoundException("Incident not found with id: 99"))
                    .when(incidentService).deleteIncident(99L);

            mockMvc.perform(delete(BASE_URL + "/delete/99")
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}
