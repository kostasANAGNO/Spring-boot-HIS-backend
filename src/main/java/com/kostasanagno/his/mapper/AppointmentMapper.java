package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.AppointmentRequest;
import com.kostasanagno.his.dto.AppointmentResponse;
import com.kostasanagno.his.dto.AppointmentUpdateRequest;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.Appointment;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class)
public interface AppointmentMapper {

    /** Patient, doctor and the initial status are set by the service. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Appointment toEntity(AppointmentRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "cancellationReason", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(AppointmentUpdateRequest request, @MappingTarget Appointment appointment);

    AppointmentResponse toResponse(Appointment appointment);

    @Mapping(target = "fullName", expression = "java(patient.getFirstName() + \" \" + patient.getLastName())")
    PatientSummary toPatientSummary(Patient patient);

    @Mapping(target = "fullName", expression = "java(doctor.getFirstName() + \" \" + doctor.getLastName())")
    @Mapping(target = "specialty", source = "specialty.name")
    DoctorSummary toDoctorSummary(Doctor doctor);
}
