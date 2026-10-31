package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.BedAssignmentResponse;
import com.kostasanagno.his.dto.HospitalizationResponse;
import com.kostasanagno.his.dto.HospitalizationUpdateRequest;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.BedAssignment;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class, uses = BedMapper.class)
public interface HospitalizationMapper {

    /** The current bed is added by the service ({@code HospitalizationResponse.withCurrentBed}); the doctor is set by the service. */
    @Mapping(target = "currentBed", ignore = true)
    @Mapping(target = "attendingDoctor", source = "attendingDoctor")
    @Mapping(target = "appointmentId", source = "appointment.id")
    HospitalizationResponse toResponse(Hospitalization hospitalization);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "attendingDoctor", ignore = true)
    @Mapping(target = "appointment", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "admissionTime", ignore = true)
    @Mapping(target = "dischargeTime", ignore = true)
    @Mapping(target = "dischargeNotes", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(HospitalizationUpdateRequest request, @MappingTarget Hospitalization hospitalization);

    @Mapping(target = "bed", source = "bed")
    BedAssignmentResponse toAssignmentResponse(BedAssignment assignment);

    @Mapping(target = "fullName", expression = "java(patient.getFirstName() + \" \" + patient.getLastName())")
    PatientSummary toPatientSummary(Patient patient);

    @Mapping(target = "fullName", expression = "java(doctor.getFirstName() + \" \" + doctor.getLastName())")
    @Mapping(target = "specialty", source = "specialty.name")
    DoctorSummary toDoctorSummary(Doctor doctor);
}
