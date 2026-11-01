package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.MedicationRequest;
import com.kostasanagno.his.dto.MedicationResponse;
import com.kostasanagno.his.dto.MedicationSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MedicationService {

    MedicationResponse create(MedicationRequest request);

    MedicationResponse getById(Long id);

    Page<MedicationResponse> search(MedicationSearchCriteria criteria, Pageable pageable);

    /** Changes the catalog entry only; existing orders keep pointing at it. */
    MedicationResponse update(Long id, MedicationRequest request);

    /** Removes the entry from the catalog for new orders; existing orders are unaffected. */
    MedicationResponse deactivate(Long id);
}
