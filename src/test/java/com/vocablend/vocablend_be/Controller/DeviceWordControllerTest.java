package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dto.DueWordResponse;
import com.vocablend.vocablend_be.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Service.DeviceWord.DeviceWordService;
import com.vocablend.vocablend_be.Service.DeviceWord.ReviewOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceWordController.class)
class DeviceWordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceWordService deviceWordService;

    @Test
    void getDueWords_returnsDueList() throws Exception {
        DueWordResponse due = new DueWordResponse(
                "1", "apple", "a fruit", "elma", List.of("I ate an apple."), 2, LocalDate.now());
        when(deviceWordService.getDueWordList("device-1")).thenReturn(List.of(due));

        mockMvc.perform(get("/api/device-words/device-1/due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].word").value("apple"))
                .andExpect(jsonPath("$[0].boxLevel").value(2));
    }

    @Test
    void reviewWord_returnsUpdatedProgress() throws Exception {
        ReviewResponse response = new ReviewResponse("apple", 3, LocalDate.now().plusDays(7));
        when(deviceWordService.recordReview("device-1", "apple", ReviewOutcome.gotIt)).thenReturn(response);

        mockMvc.perform(post("/api/device-words/device-1/apple/review?outcome=gotIt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boxLevel").value(3));
    }

    @Test
    void reviewWord_unknownWord_returnsNotFound() throws Exception {
        when(deviceWordService.recordReview(eq("device-1"), eq("ghost"), eq(ReviewOutcome.gotIt))).thenReturn(null);

        mockMvc.perform(post("/api/device-words/device-1/ghost/review?outcome=gotIt"))
                .andExpect(status().isNotFound());
    }
}
