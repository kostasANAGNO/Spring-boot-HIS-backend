package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.ClinicRequest;
import com.kostasanagno.his.dto.ClinicResponse;
import java.util.List;

public interface ClinicService {

    ClinicResponse create(ClinicRequest request);

    ClinicResponse getById(Long id);

    /** Active clinics unless {@code active=false} is requested; sorted by name. */
    List<ClinicResponse> findAll(Boolean active);

    ClinicResponse update(Long id, ClinicRequest request);

    /** Rejected while any bed of any ward of the clinic is occupied. */
    ClinicResponse deactivate(Long id);
}
