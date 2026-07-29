package com.vocablend.vocablend_be.Service.DeviceWord;

import java.time.LocalDate;
import java.util.Map;

public final class LeitnerBox {

    public static final int MIN_BOX = 1;
    public static final int MAX_BOX = 6;

    private static final Map<Integer, Integer> INTERVAL_DAYS = Map.of(
            1, 1,
            2, 3,
            3, 7,
            4, 16,
            5, 35,
            6, 90
    );

    private LeitnerBox() {
    }

    public static int nextBoxLevel(int currentBoxLevel, ReviewOutcome outcome) {
        if (outcome == ReviewOutcome.gotIt) {
            return Math.min(currentBoxLevel + 1, MAX_BOX);
        }
        return MIN_BOX;
    }

    public static LocalDate nextReviewDate(int boxLevel) {
        return LocalDate.now().plusDays(INTERVAL_DAYS.get(boxLevel));
    }
}
