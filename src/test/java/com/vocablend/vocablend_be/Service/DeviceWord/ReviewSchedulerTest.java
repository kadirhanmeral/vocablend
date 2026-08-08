package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.WordProgress;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    private final ReviewScheduler scheduler = new ReviewScheduler(Clock.fixed(NOW, ZoneOffset.UTC));

    private WordProgress progress(int level, int correctStreak) {
        return new WordProgress("resilient", level, correctStreak, NOW);
    }

    @Test
    void learningPhase_firstCorrectAnswerAdvancesStreakAndStaysDue() {
        WordProgress word = progress(0, 0);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(0, word.getLevel());
        assertEquals(1, word.getCorrectStreak());
        assertEquals(NOW, word.getNextReviewAt());
    }

    @Test
    void learningPhase_secondCorrectAnswerStillDoesNotGraduate() {
        WordProgress word = progress(0, 1);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(0, word.getLevel());
        assertEquals(2, word.getCorrectStreak());
        assertEquals(NOW, word.getNextReviewAt());
    }

    @Test
    void learningPhase_thirdCorrectAnswerGraduatesToLevelOneInOneHour() {
        WordProgress word = progress(0, 2);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(1, word.getLevel());
        assertEquals(0, word.getCorrectStreak());
        assertEquals(NOW.plus(Duration.ofHours(1)), word.getNextReviewAt());
    }

    @Test
    void learningPhase_wrongAnswerResetsStreakAndKeepsWordDue() {
        WordProgress word = progress(0, 2);

        scheduler.apply(word, ReviewOutcome.STILL_LEARNING);

        assertEquals(0, word.getLevel());
        assertEquals(0, word.getCorrectStreak());
        assertEquals(NOW, word.getNextReviewAt());
    }

    @Test
    void reviewPhase_correctAnswerClimbsOneLevelWithMatchingInterval() {
        assertLevelUp(1, 2, Duration.ofHours(4));
        assertLevelUp(2, 3, Duration.ofHours(12));
        assertLevelUp(3, 4, Duration.ofDays(1));
        assertLevelUp(4, 5, Duration.ofDays(3));
        assertLevelUp(5, 6, Duration.ofDays(7));
        assertLevelUp(6, 7, Duration.ofDays(16));
        assertLevelUp(7, 8, Duration.ofDays(35));
    }

    private void assertLevelUp(int from, int expectedLevel, Duration expectedInterval) {
        WordProgress word = progress(from, 0);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(expectedLevel, word.getLevel());
        assertEquals(NOW.plus(expectedInterval), word.getNextReviewAt());
    }

    @Test
    void reviewPhase_correctAnswerAtTopLevelStaysAtTopLevel() {
        WordProgress word = progress(8, 0);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(8, word.getLevel());
        assertEquals(NOW.plus(Duration.ofDays(35)), word.getNextReviewAt());
    }

    @Test
    void reviewPhase_wrongAnswerResetsToLevelOne() {
        WordProgress word = progress(7, 0);

        scheduler.apply(word, ReviewOutcome.STILL_LEARNING);

        assertEquals(1, word.getLevel());
        assertEquals(0, word.getCorrectStreak());
        assertEquals(NOW.plus(Duration.ofHours(1)), word.getNextReviewAt());
    }

    @Test
    void reviewPhase_outOfRangeLevelIsClampedToTopInterval() {
        WordProgress word = progress(12, 0);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(8, word.getLevel());
        assertEquals(NOW.plus(Duration.ofDays(35)), word.getNextReviewAt());
    }

    @Test
    void legacyProgressWithNullNextReviewAtIsTreatedAsLearningPhase() {
        WordProgress word = new WordProgress("legacy", 0, 0, null);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(0, word.getLevel());
        assertEquals(1, word.getCorrectStreak());
        assertEquals(NOW, word.getNextReviewAt());
    }
}
