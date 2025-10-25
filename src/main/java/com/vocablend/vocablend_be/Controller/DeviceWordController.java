package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Service.DeviceWord.DeviceWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<List<WordEntity>> getDeviceWords(@PathVariable String deviceId) {
        List<WordEntity> words = deviceWordService.getWordList(deviceId);
        return ResponseEntity.ok(words);
    }

    @DeleteMapping("/{deviceId}/{word}")
    public ResponseEntity<Void> deleteWord(
            @PathVariable String deviceId,
            @PathVariable String word) {

        deviceWordService.deleteByDeviceIdAndWord(deviceId, word);
        return ResponseEntity.noContent().build();
    }
}
