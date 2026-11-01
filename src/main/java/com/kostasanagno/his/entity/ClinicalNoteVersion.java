package com.kostasanagno.his.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One immutable state of a clinical note (version 1 is the original, every amendment adds a row). The text
 * is clinical data: it is deliberately absent from {@code toString} and must never be logged.
 */
@Entity
@Table(name = "clinical_note_versions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
public class ClinicalNoteVersion extends IdentifiedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "note_id", nullable = false, updatable = false)
    private ClinicalNote note;

    @ToString.Include
    @Column(name = "version_number", nullable = false, updatable = false)
    private int versionNumber;

    @Column(length = 4000, updatable = false)
    private String history;

    @Column(length = 4000, updatable = false)
    private String examination;

    @Column(length = 4000, updatable = false)
    private String assessment;

    @Column(length = 4000, updatable = false)
    private String plan;

    /** ICD-10 style code, e.g. {@code I20.9}. */
    @Column(name = "diagnosis_code", length = 10, updatable = false)
    private String diagnosisCode;

    /** Why the note was amended; null for version 1. */
    @Column(name = "change_reason", length = 500, updatable = false)
    private String changeReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "edited_by_user_id", nullable = false, updatable = false)
    private User editedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
