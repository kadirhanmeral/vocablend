package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Service.DeviceWord.DeviceWordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device-words")
@RequiredArgsConstructor
public class DeviceWordController {

    private final DeviceWordService deviceWordService;


    @PostMapping("/{deviceId}")
    public WordEntity addWord(
            @PathVariable String deviceId,
            @RequestParam String word
    ) {
        return deviceWordService.addWord(deviceId, word);
    }

    @GetMapping("/{deviceId}/list")
    public List<WordEntity> getDeviceWords(@PathVariable String deviceId) {
        return deviceWordService.getWordList(deviceId);
    }

}
