package com.incidentiq.security;


import com.incidentiq.repository.IncidentRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service("authService")
public class AuthorizationService {

    private final IncidentRepository incidentRepository;

    public AuthorizationService(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    public boolean isOwner(Long incidentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) return false;
        
        Long currentUserId = getCurrentUserId();
        return incidentRepository.findById(incidentId)
                .map(incident -> incident.getCreatedBy().equals(currentUserId))
                .orElse(false);
    }

    public boolean isManagerOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_MANAGER") || a.getAuthority().equals("ROLE_ADMIN"));
    }

    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
    
    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Map) {
            Map<?, ?> details = (Map<?, ?>) auth.getDetails();
            Object userId = details.get("userId");
            if (userId instanceof Long) {
                return (Long) userId;
            } else if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            }
        }
        return null;
    }

    public boolean isAssignee(Long incidentId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) return false;
        
        Long currentUserId = getCurrentUserId();
        return incidentRepository.findById(incidentId)
                .map(incident -> incident.getAssignedTo() != null && incident.getAssignedTo().equals(currentUserId))
                .orElse(false);
    }
}
