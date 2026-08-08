package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Service.DeviceWord.DeviceWordService;
import com.vocablend.vocablend_be.Service.DeviceWord.ReviewOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    void getDeviceWords_returnsProgressAlongsideContent() throws Exception {
        DeviceWordResponse apple = new DeviceWordResponse(
                "apple-id", "apple", "a fruit", "elma", List.of("An apple a day."),
                4, 0, Instant.parse("2026-08-09T10:00:00Z"));

        when(deviceWordService.getWordList("device-1")).thenReturn(List.of(apple));

        mockMvc.perform(get("/api/device-words/device-1/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].word").value("apple"))
                .andExpect(jsonPath("$[0].level").value(4))
                .andExpect(jsonPath("$[0].correctStreak").value(0))
                // Serialized as an ISO-8601 string, not an epoch number - the mobile
                // client parses it with Date.parse.
                .andExpect(jsonPath("$[0].nextReviewAt").value("2026-08-09T10:00:00Z"));
    }

    @Test
    void review_returnsUpdatedProgress() throws Exception {
        ReviewResponse response = new ReviewResponse(
                "apple", 1, 0, Instant.parse("2026-08-08T11:00:00Z"));

        when(deviceWordService.review(eq("device-1"), eq("apple"), eq(ReviewOutcome.GOT_IT)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(post("/api/device-words/device-1/apple/review").param("outcome", "gotIt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.word").value("apple"))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.nextReviewAt").value("2026-08-08T11:00:00Z"));
    }

    @Test
    void review_returns404WhenWordNotSavedForDevice() throws Exception {
        when(deviceWordService.review(eq("device-1"), eq("pear"), eq(ReviewOutcome.GOT_IT)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/device-words/device-1/pear/review").param("outcome", "gotIt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void review_returns400ForUnknownOutcome() throws Exception {
        mockMvc.perform(post("/api/device-words/device-1/apple/review").param("outcome", "maybe"))
                .andExpect(status().isBadRequest());
    }
}
