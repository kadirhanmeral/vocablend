package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;

import java.util.List;
import java.util.Optional;

public interface DeviceWordService {

    WordEntity addWord(String deviceId, String word);
    List<DeviceWordResponse> getWordList(String deviceId);
    void deleteByDeviceIdAndWord(String deviceId, String word);
    Optional<ReviewResponse> review(String deviceId, String word, ReviewOutcome outcome);
}
