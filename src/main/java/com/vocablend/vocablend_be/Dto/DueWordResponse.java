package com.vocablend.vocablend_be.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DueWordResponse {
    private String id;
    private String word;
    private String meaningEn;
    private String meaningTr;
    private List<String> examples;
    private int boxLevel;
    private LocalDate nextReviewDate;
}
