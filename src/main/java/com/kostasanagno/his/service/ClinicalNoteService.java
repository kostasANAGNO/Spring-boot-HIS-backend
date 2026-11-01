package com.kostasanagno.his.service;

import com.kostasanagno.his.dto.ClinicalNoteAmendRequest;
import com.kostasanagno.his.dto.ClinicalNoteRequest;
import com.kostasanagno.his.dto.ClinicalNoteResponse;
import com.kostasanagno.his.dto.ClinicalNoteSearchCriteria;
import com.kostasanagno.his.dto.ClinicalNoteVersionResponse;
import com.kostasanagno.his.security.Caller;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Clinical notes are append-only: there is no delete, and an amendment adds a version. */
public interface ClinicalNoteService {

    /** Only a doctor with a linked profile can write; that doctor becomes the author. */
    ClinicalNoteResponse create(ClinicalNoteRequest request, Caller caller);

    ClinicalNoteResponse getById(Long id);

    /** Every version of the note, oldest first. */
    List<ClinicalNoteVersionResponse> getVersions(Long id);

    Page<ClinicalNoteResponse> search(ClinicalNoteSearchCriteria criteria, Pageable pageable);

    /** Notes written by the doctor profile linked to the caller; the authorDoctorId filter is ignored. */
    Page<ClinicalNoteResponse> searchForDoctorUser(ClinicalNoteSearchCriteria criteria, Pageable pageable,
            Caller caller);

    /** Adds a new version. Only the author or an admin; {@code baseVersion} must still be the current version. */
    ClinicalNoteResponse amend(Long id, ClinicalNoteAmendRequest request, Caller caller);
}
