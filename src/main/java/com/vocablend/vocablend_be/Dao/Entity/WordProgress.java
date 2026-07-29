package com.vocablend.vocablend_be.Dao.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WordProgress {
    private String word;
    private int boxLevel;
    private LocalDate nextReviewDate;
}
