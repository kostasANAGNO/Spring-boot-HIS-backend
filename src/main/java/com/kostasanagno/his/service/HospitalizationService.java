package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.AdmissionRequest;
import com.kostasanagno.his.dto.BedAssignmentResponse;
import com.kostasanagno.his.dto.DischargeRequest;
import com.kostasanagno.his.dto.HospitalizationResponse;
import com.kostasanagno.his.dto.HospitalizationSearchCriteria;
import com.kostasanagno.his.dto.HospitalizationUpdateRequest;
import com.kostasanagno.his.dto.TransferRequest;
import com.kostasanagno.his.security.Caller;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** The {@link Caller} decides whether clinical fields are returned and who may discharge. */
public interface HospitalizationService {

    /** Creates the hospitalization and its first bed assignment in one transaction. */
    HospitalizationResponse admit(AdmissionRequest request, Caller caller);

    HospitalizationResponse getById(Long id, Caller caller);

    /** All bed assignments of the hospitalization, oldest first. */
    List<BedAssignmentResponse> getBedHistory(Long id, Caller caller);

    Page<HospitalizationResponse> search(HospitalizationSearchCriteria criteria, Pageable pageable, Caller caller);

    /** Hospitalizations attended by the doctor profile linked to the caller; the doctorId filter is ignored. */
    Page<HospitalizationResponse> searchForDoctorUser(HospitalizationSearchCriteria criteria, Pageable pageable,
            Caller caller);

    /** Reason, diagnosis and attending doctor; only while ADMITTED. */
    HospitalizationResponse update(Long id, HospitalizationUpdateRequest request, Caller caller);

    /** Moves the patient to another free bed (closes the current assignment, opens a new one). */
    HospitalizationResponse transfer(Long id, TransferRequest request, Caller caller);

    /** Only ADMIN or the attending doctor. */
    HospitalizationResponse discharge(Long id, DischargeRequest request, Caller caller);
}
