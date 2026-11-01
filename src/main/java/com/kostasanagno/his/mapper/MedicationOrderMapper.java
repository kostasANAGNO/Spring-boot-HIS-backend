package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.AdministrationResponse;
import com.kostasanagno.his.dto.DispensingResponse;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.MedicationOrderRequest;
import com.kostasanagno.his.dto.MedicationOrderResponse;
import com.kostasanagno.his.dto.MedicationSummary;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Medication;
import com.kostasanagno.his.entity.MedicationAdministration;
import com.kostasanagno.his.entity.MedicationDispensing;
import com.kostasanagno.his.entity.MedicationOrder;
import com.kostasanagno.his.entity.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface MedicationOrderMapper {

    /** Hospitalization, medication, doctor and the initial status are set by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hospitalization", ignore = true)
    @Mapping(target = "medication", ignore = true)
    @Mapping(target = "prescribedBy", ignore = true)
    @Mapping(target = "startTime", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "closedReason", ignore = true)
    @Mapping(target = "closedAt", ignore = true)
    @Mapping(target = "closedBy", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    MedicationOrder toEntity(MedicationOrderRequest request);

    /** The current bed is added by the service ({@code MedicationOrderResponse.withBed}). */
    @Mapping(target = "currentBed", ignore = true)
    @Mapping(target = "hospitalizationId", source = "hospitalization.id")
    @Mapping(target = "patient", source = "hospitalization.patient")
    MedicationOrderResponse toResponse(MedicationOrder order);

    @Mapping(target = "dispensedBy", source = "dispensedBy.username")
    DispensingResponse toDispensingResponse(MedicationDispensing dispensing);

    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "hospitalizationId", source = "order.hospitalization.id")
    @Mapping(target = "patientId", source = "order.hospitalization.patient.id")
    @Mapping(target = "administeredBy", source = "administeredBy.username")
    AdministrationResponse toAdministrationResponse(MedicationAdministration administration);

    MedicationSummary toMedicationSummary(Medication medication);

    @Mapping(target = "fullName", expression = "java(patient.getFirstName() + \" \" + patient.getLastName())")
    PatientSummary toPatientSummary(Patient patient);

    @Mapping(target = "fullName", expression = "java(doctor.getFirstName() + \" \" + doctor.getLastName())")
    @Mapping(target = "specialty", source = "specialty.name")
    DoctorSummary toDoctorSummary(Doctor doctor);
}
