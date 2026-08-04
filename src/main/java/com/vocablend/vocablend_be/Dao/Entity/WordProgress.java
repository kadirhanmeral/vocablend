package com.vocablend.vocablend_be.Dao.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Historically also carried a Leitner-box boxLevel/nextReviewDate (spaced-repetition
// scheduling), removed since that scheduling was never actually used to filter
// due words. Existing Mongo documents may still have those fields on disk — Spring
// Data's mapper ignores unknown fields on read, so this stays backward-compatible
// without a migration; the extra fields simply won't be written back on next save.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WordProgress {
    private String word;
}
