package com.incidentiq.service;

import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DuplicateDetectionService {

    private final IncidentRepository incidentRepository;

    public List<Incident> findPotentialDuplicates(String title) {
        String query = title.toLowerCase();
        return incidentRepository.findAll().stream()
                .filter(i -> i.getTitle().toLowerCase().contains(query) || query.contains(i.getTitle().toLowerCase()))
                .limit(5)
                .collect(Collectors.toList());
    }
}
