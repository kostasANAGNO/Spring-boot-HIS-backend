package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.WardRequest;
import com.kostasanagno.his.dto.WardResponse;
import java.util.List;

public interface WardService {

    WardResponse create(WardRequest request);

    WardResponse getById(Long id);

    /** Active wards unless {@code active=false} is requested; sorted by name. */
    List<WardResponse> findAll(Boolean active);

    WardResponse update(Long id, WardRequest request);

    /** Rejected while any bed of the ward is occupied. */
    WardResponse deactivate(Long id);
}
