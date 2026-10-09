package com.nexploy.campuslink.document.repository;

import com.nexploy.campuslink.document.entity.DocumentFile;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * File bytes for registered documents — one row per {@link com.nexploy.campuslink.document.entity.Document}
 * that actually has a file attached (legacy metadata-only registrations have none). Inherited
 * {@code existsById} powers the single-doc hasFile flag; {@link #findIdsByIdIn} the batched case
 * as an id-only projection so a listing never loads the blobs just to set the flag.
 */
public interface DocumentFileRepository extends JpaRepository<DocumentFile, String> {

    /** Ids of the given documents that have a file row — no bytea content is loaded. */
    @Query("select df.id from DocumentFile df where df.id in :ids")
    List<String> findIdsByIdIn(@Param("ids") Collection<String> ids);
}
