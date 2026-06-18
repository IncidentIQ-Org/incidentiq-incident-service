package com.incidentiq.service;

import com.incidentiq.model.Incident;

import java.util.List;

/**
 * Service interface for detecting potential duplicate incidents.
 */
public interface DuplicateDetectionService {

    /**
     * Finds potential duplicate incidents based on title similarity.
     *
     * @param title the incident title to search for duplicates
     * @return list of potential duplicate incidents
     */
    List<Incident> findPotentialDuplicates(String title);
}
