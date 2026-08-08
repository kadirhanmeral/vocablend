package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Service.DeviceWord.DeviceWordService;
import com.vocablend.vocablend_be.Service.DeviceWord.ReviewOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/device-words")
@RequiredArgsConstructor
public class DeviceWordController {

    private final DeviceWordService deviceWordService;

    @PostMapping("/{deviceId}")
    public ResponseEntity<WordEntity> addWord(
            @PathVariable String deviceId,
            @RequestParam String word
    ) {
        WordEntity addedWord = deviceWordService.addWord(deviceId, word);
        return ResponseEntity.status(201).body(addedWord);
    }

    @GetMapping("/{deviceId}/list")
    public ResponseEntity<List<DeviceWordResponse>> getDeviceWords(@PathVariable String deviceId) {
        List<DeviceWordResponse> words = deviceWordService.getWordList(deviceId);
        return ResponseEntity.ok(words);
    }

    @PostMapping("/{deviceId}/{word}/review")
    public ResponseEntity<ReviewResponse> review(
            @PathVariable String deviceId,
            @PathVariable String word,
            @RequestParam String outcome
    ) {
        ReviewOutcome parsedOutcome;

        try {
            parsedOutcome = ReviewOutcome.fromParam(outcome);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome must be gotIt or stillLearning");
        }

        return deviceWordService.review(deviceId, word, parsedOutcome)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "word is not saved for this device"));
    }

    @DeleteMapping("/{deviceId}/{word}")
    public ResponseEntity<Void> deleteWord(
            @PathVariable String deviceId,
            @PathVariable String word) {

        deviceWordService.deleteByDeviceIdAndWord(deviceId, word);
        return ResponseEntity.noContent().build();
    }
}
