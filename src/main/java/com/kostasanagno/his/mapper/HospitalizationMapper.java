package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.AdmissionRecordResponse;
import com.kostasanagno.his.dto.BedAssignmentResponse;
import com.kostasanagno.his.dto.DischargeRecordResponse;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.HospitalizationResponse;
import com.kostasanagno.his.dto.HospitalizationUpdateRequest;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.AdmissionRecord;
import com.kostasanagno.his.entity.BedAssignment;
import com.kostasanagno.his.entity.DischargeRecord;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Hospitalization;
import com.kostasanagno.his.entity.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = CentralMapperConfig.class, uses = BedMapper.class)
public interface HospitalizationMapper {

    /** The current bed and the two documents are added by the service ({@code HospitalizationResponse.assemble}). */
    @Mapping(target = "currentBed", ignore = true)
    @Mapping(target = "admission", ignore = true)
    @Mapping(target = "discharge", ignore = true)
    @Mapping(target = "attendingDoctor", source = "attendingDoctor")
    @Mapping(target = "appointmentId", source = "appointment.id")
    HospitalizationResponse toResponse(Hospitalization hospitalization);

    AdmissionRecordResponse toAdmissionResponse(AdmissionRecord record);

    DischargeRecordResponse toDischargeResponse(DischargeRecord record);

    /** Only what may be corrected while the patient is admitted; type, source and facility stay as written. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hospitalization", ignore = true)
    @Mapping(target = "admissionType", ignore = true)
    @Mapping(target = "admissionSource", ignore = true)
    @Mapping(target = "referringFacility", ignore = true)
    @Mapping(target = "reason", source = "admissionReason")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateAdmission(HospitalizationUpdateRequest request, @MappingTarget AdmissionRecord record);

    @Mapping(target = "bed", source = "bed")
    BedAssignmentResponse toAssignmentResponse(BedAssignment assignment);

    @Mapping(target = "fullName", expression = "java(patient.getFirstName() + \" \" + patient.getLastName())")
    PatientSummary toPatientSummary(Patient patient);

    @Mapping(target = "fullName", expression = "java(doctor.getFirstName() + \" \" + doctor.getLastName())")
    @Mapping(target = "specialty", source = "specialty.name")
    DoctorSummary toDoctorSummary(Doctor doctor);
}
