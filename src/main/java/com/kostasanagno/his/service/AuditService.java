package com.kostasanagno.his.service;

import com.kostasanagno.his.audit.AuditEntry;
import com.kostasanagno.his.dto.AuditEventResponse;
import com.kostasanagno.his.dto.AuditEventSearchCriteria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditService {

    /** Stores one event per target of the entry, in its own transaction so it survives a rolled-back request. */
    void record(AuditEntry entry);

    Page<AuditEventResponse> search(AuditEventSearchCriteria criteria, Pageable pageable);
}
