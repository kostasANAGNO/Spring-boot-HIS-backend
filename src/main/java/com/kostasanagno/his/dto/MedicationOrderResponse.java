package com.kostasanagno.his.dto;

import com.kostasanagno.his.audit.Auditable;
import com.kostasanagno.his.entity.MedicationOrderStatus;
import com.kostasanagno.his.entity.MedicationRoute;
import com.kostasanagno.his.entity.MedicationSchedule;
import java.math.BigDecimal;
import java.time.Instant;

/** An order with the patient and where the patient is now (so the pharmacy knows where to send it). */
public record MedicationOrderResponse(
        Long id,
        Long hospitalizationId,
        PatientSummary patient,
        BedSummary currentBed,
        MedicationSummary medication,
        DoctorSummary prescribedBy,
        BigDecimal doseAmount,
        String doseUnit,
        MedicationRoute route,
        MedicationSchedule scheduleType,
        Integer intervalHours,
        Instant startTime,
        Instant endTime,
        String instructions,
        MedicationOrderStatus status,
        String closedReason,
        Instant closedAt,
        Instant createdAt,
        Instant updatedAt) implements Auditable {

    /** Static (not an instance wither) so MapStruct does not mistake it for a setter. */
    public static MedicationOrderResponse withBed(MedicationOrderResponse source, BedSummary bed) {
        return new MedicationOrderResponse(source.id, source.hospitalizationId, source.patient, bed,
                source.medication, source.prescribedBy, source.doseAmount, source.doseUnit, source.route,
                source.scheduleType, source.intervalHours, source.startTime, source.endTime, source.instructions,
                source.status, source.closedReason, source.closedAt, source.createdAt, source.updatedAt);
    }

    @Override
    public Long auditResourceId() {
        return id;
    }

    @Override
    public Long auditPatientId() {
        return patient.id();
    }
}
