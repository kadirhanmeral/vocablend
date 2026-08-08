package com.vocablend.vocablend_be.Dao.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// level 0 is the learning phase: the word is permanently due and must be
// answered correctly ReviewScheduler.LEARNING_TARGET times before it graduates
// onto the review ladder (levels 1-8). correctStreak is only meaningful at
// level 0.
//
// Documents written before this change deserialize with level 0, correctStreak
// 0 and a null nextReviewAt. Null is read as "due now", which is also correct
// semantically - those words have no recorded progress - so no migration is
// needed.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WordProgress {
    private String word;
    private int level;
    private int correctStreak;
    private Instant nextReviewAt;
}
