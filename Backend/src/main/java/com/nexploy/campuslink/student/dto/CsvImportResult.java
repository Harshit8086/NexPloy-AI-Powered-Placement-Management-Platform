package com.nexploy.campuslink.student.dto;

import java.util.List;

/**
 * Outcome of a directory CSV import (V60). {@code imported} rows became active DIRECTORY records;
 * {@code skipped} counts rows left out (duplicates, invalid values), each itemized in
 * {@code errors} with its 1-based data-row number so the officer can fix the source file.
 */
public record CsvImportResult(int imported, int skipped, List<RowError> errors) {

    public record RowError(int row, String reason) {
    }
}
