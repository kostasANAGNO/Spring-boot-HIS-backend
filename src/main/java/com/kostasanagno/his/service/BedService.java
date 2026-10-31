package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.BedRequest;
import com.kostasanagno.his.dto.BedResponse;
import com.kostasanagno.his.dto.BedSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BedService {

    BedResponse create(BedRequest request);

    BedResponse getById(Long id);

    Page<BedResponse> search(BedSearchCriteria criteria, Pageable pageable);

    BedResponse update(Long id, BedRequest request);

    /** Rejected while the bed is occupied. */
    BedResponse deactivate(Long id);
}
