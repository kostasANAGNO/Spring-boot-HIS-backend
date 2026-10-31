package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.AppointmentRequest;
import com.kostasanagno.his.dto.AppointmentResponse;
import com.kostasanagno.his.dto.AppointmentSearchCriteria;
import com.kostasanagno.his.dto.AppointmentUpdateRequest;
import com.kostasanagno.his.dto.CancelAppointmentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AppointmentService {

    AppointmentResponse create(AppointmentRequest request);

    AppointmentResponse getById(Long id);

    Page<AppointmentResponse> search(AppointmentSearchCriteria criteria, Pageable pageable);

    /** Appointments of the doctor profile linked to this username; the doctorId filter is ignored. */
    Page<AppointmentResponse> searchForDoctorUser(String username, AppointmentSearchCriteria criteria, Pageable pageable);

    /** Reschedule/update; only while SCHEDULED. */
    AppointmentResponse update(Long id, AppointmentUpdateRequest request);

    AppointmentResponse cancel(Long id, CancelAppointmentRequest request);

    /** Only the assigned doctor (resolved from {@code username}) or an admin may complete. */
    AppointmentResponse complete(Long id, String username, boolean admin);

    AppointmentResponse markNoShow(Long id);
}
