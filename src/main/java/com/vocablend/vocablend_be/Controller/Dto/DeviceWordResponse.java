package com.vocablend.vocablend_be.Controller.Dto;

import java.time.Instant;
import java.util.List;

// Word content joined with this device's progress. Flat rather than nested so
// the mobile app's WordEntity consumers keep working against an extension of
// the shape they already read.
public record DeviceWordResponse(
        String id,
        String word,
        String meaningEn,
        String meaningTr,
        List<String> examples,
        int level,
        int correctStreak,
        Instant nextReviewAt
) {
}
