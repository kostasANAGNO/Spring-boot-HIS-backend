package com.kostasanagno.his.repository;

import com.kostasanagno.his.entity.ClinicalNoteVersion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClinicalNoteVersionRepository extends JpaRepository<ClinicalNoteVersion, Long> {

    @EntityGraph(attributePaths = "editedBy")
    Optional<ClinicalNoteVersion> findTopByNoteIdOrderByVersionNumberDesc(Long noteId);

    /** Full history of one note, oldest first. */
    @EntityGraph(attributePaths = "editedBy")
    List<ClinicalNoteVersion> findByNoteIdOrderByVersionNumberAsc(Long noteId);

    /** The latest version of each given note, in one query (for a page of notes). */
    @Query("select v from ClinicalNoteVersion v join fetch v.editedBy where v.note.id in :noteIds "
            + "and v.versionNumber = (select max(v2.versionNumber) from ClinicalNoteVersion v2 "
            + "where v2.note.id = v.note.id)")
    List<ClinicalNoteVersion> findLatestByNoteIds(@Param("noteIds") Collection<Long> noteIds);
}
