package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.WardRequest;
import com.kostasanagno.his.dto.WardResponse;
import com.kostasanagno.his.dto.WardSearchCriteria;
import java.util.List;

public interface WardService {

    WardResponse create(WardRequest request);

    WardResponse getById(Long id);

    /** Active wards unless {@code active=false} is requested, optionally of one clinic; sorted by name. */
    List<WardResponse> findAll(WardSearchCriteria criteria);

    /** Renames the ward or moves it to another clinic (not while any of its beds is occupied). */
    WardResponse update(Long id, WardRequest request);

    /** Rejected while any bed of the ward is occupied. */
    WardResponse deactivate(Long id);
}
