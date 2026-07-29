package com.vocablend.vocablend_be.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReviewResponse {
    private String word;
    private int boxLevel;
    private LocalDate nextReviewDate;
}
