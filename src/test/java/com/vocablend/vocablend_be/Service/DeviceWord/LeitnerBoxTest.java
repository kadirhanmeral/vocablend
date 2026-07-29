package com.vocablend.vocablend_be.Service.DeviceWord;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeitnerBoxTest {

    @Test
    void gotIt_incrementsBoxLevel() {
        assertEquals(2, LeitnerBox.nextBoxLevel(1, ReviewOutcome.gotIt));
        assertEquals(6, LeitnerBox.nextBoxLevel(5, ReviewOutcome.gotIt));
    }

    @Test
    void gotIt_staysAtMaxBox() {
        assertEquals(6, LeitnerBox.nextBoxLevel(6, ReviewOutcome.gotIt));
    }

    @Test
    void stillLearning_resetsToBoxOne() {
        assertEquals(1, LeitnerBox.nextBoxLevel(4, ReviewOutcome.stillLearning));
        assertEquals(1, LeitnerBox.nextBoxLevel(1, ReviewOutcome.stillLearning));
    }

    @Test
    void nextReviewDate_matchesBoxInterval() {
        LocalDate today = LocalDate.now();
        assertEquals(today.plusDays(1), LeitnerBox.nextReviewDate(1));
        assertEquals(today.plusDays(3), LeitnerBox.nextReviewDate(2));
        assertEquals(today.plusDays(7), LeitnerBox.nextReviewDate(3));
        assertEquals(today.plusDays(16), LeitnerBox.nextReviewDate(4));
        assertEquals(today.plusDays(35), LeitnerBox.nextReviewDate(5));
        assertEquals(today.plusDays(90), LeitnerBox.nextReviewDate(6));
    }
}
