package com.kostasanagno.his.mapper;

import com.kostasanagno.his.dto.ClinicalNoteResponse;
import com.kostasanagno.his.dto.ClinicalNoteVersionResponse;
import com.kostasanagno.his.dto.DoctorSummary;
import com.kostasanagno.his.dto.PatientSummary;
import com.kostasanagno.his.entity.ClinicalNote;
import com.kostasanagno.his.entity.ClinicalNoteVersion;
import com.kostasanagno.his.entity.Doctor;
import com.kostasanagno.his.entity.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface ClinicalNoteMapper {

    /** Header data from the note, content from its current version. */
    @Mapping(target = "id", source = "note.id")
    @Mapping(target = "patient", source = "note.patient")
    @Mapping(target = "authorDoctor", source = "note.authorDoctor")
    @Mapping(target = "appointmentId", source = "note.appointment.id")
    @Mapping(target = "hospitalizationId", source = "note.hospitalization.id")
    @Mapping(target = "version", source = "version.versionNumber")
    @Mapping(target = "history", source = "version.history")
    @Mapping(target = "examination", source = "version.examination")
    @Mapping(target = "assessment", source = "version.assessment")
    @Mapping(target = "plan", source = "version.plan")
    @Mapping(target = "diagnosisCode", source = "version.diagnosisCode")
    @Mapping(target = "lastChangeReason", source = "version.changeReason")
    @Mapping(target = "editedBy", source = "version.editedBy.username")
    @Mapping(target = "createdAt", source = "note.createdAt")
    @Mapping(target = "modifiedAt", source = "version.createdAt")
    ClinicalNoteResponse toResponse(ClinicalNote note, ClinicalNoteVersion version);

    @Mapping(target = "version", source = "versionNumber")
    @Mapping(target = "editedBy", source = "editedBy.username")
    ClinicalNoteVersionResponse toVersionResponse(ClinicalNoteVersion version);

    @Mapping(target = "fullName", expression = "java(patient.getFirstName() + \" \" + patient.getLastName())")
    PatientSummary toPatientSummary(Patient patient);

    @Mapping(target = "fullName", expression = "java(doctor.getFirstName() + \" \" + doctor.getLastName())")
    @Mapping(target = "specialty", source = "specialty.name")
    DoctorSummary toDoctorSummary(Doctor doctor);
}
