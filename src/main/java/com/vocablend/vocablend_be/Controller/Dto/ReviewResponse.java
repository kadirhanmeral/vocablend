package com.vocablend.vocablend_be.Controller.Dto;

import java.time.Instant;

public record ReviewResponse(
        String word,
        int level,
        int correctStreak,
        Instant nextReviewAt
) {
}
