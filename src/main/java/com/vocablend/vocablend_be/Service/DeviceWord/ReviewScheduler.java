package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.WordProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// The single source of truth for review scheduling. The interval table and the
// learning-phase threshold exist here and nowhere else on the backend.
@Component
@RequiredArgsConstructor
public class ReviewScheduler {

    public static final int LEARNING_TARGET = 3;
    public static final int MAX_LEVEL = 8;

    // Index 0 is level 1. Hour-granular on purpose: a day-granular ladder leaves
    // a learner with nothing to do for the rest of the day after one session.
    private static final Duration[] INTERVALS = {
            Duration.ofHours(1),
            Duration.ofHours(4),
            Duration.ofHours(12),
            Duration.ofDays(1),
            Duration.ofDays(3),
            Duration.ofDays(7),
            Duration.ofDays(16),
            Duration.ofDays(35),
    };

    private final Clock clock;

    public void apply(WordProgress progress, ReviewOutcome outcome) {
        Instant now = clock.instant();

        if (progress.getLevel() <= 0) {
            applyLearningPhase(progress, outcome, now);
        } else {
            applyReviewPhase(progress, outcome, now);
        }
    }

    private void applyLearningPhase(WordProgress progress, ReviewOutcome outcome, Instant now) {
        if (outcome == ReviewOutcome.GOT_IT) {
            int streak = progress.getCorrectStreak() + 1;

            if (streak >= LEARNING_TARGET) {
                progress.setLevel(1);
                progress.setCorrectStreak(0);
                progress.setNextReviewAt(now.plus(intervalFor(1)));
                return;
            }

            progress.setCorrectStreak(streak);
        } else {
            progress.setCorrectStreak(0);
        }

        // Still learning: stays permanently due so it keeps coming back.
        progress.setLevel(0);
        progress.setNextReviewAt(now);
    }

    private void applyReviewPhase(WordProgress progress, ReviewOutcome outcome, Instant now) {
        // A forgotten word goes back to the bottom of the ladder rather than one
        // rung down - the interval that produced the miss was already too long.
        int level = outcome == ReviewOutcome.GOT_IT
                ? Math.min(clampLevel(progress.getLevel()) + 1, MAX_LEVEL)
                : 1;

        progress.setLevel(level);
        progress.setCorrectStreak(0);
        progress.setNextReviewAt(now.plus(intervalFor(level)));
    }

    private Duration intervalFor(int level) {
        return INTERVALS[clampLevel(level) - 1];
    }

    private int clampLevel(int level) {
        return Math.min(Math.max(level, 1), MAX_LEVEL);
    }
}
