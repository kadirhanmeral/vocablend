package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dto.DueWordResponse;
import com.vocablend.vocablend_be.Dto.ReviewResponse;

import java.util.List;

public interface DeviceWordService {

    WordEntity addWord(String deviceId, String word);
    List<WordEntity> getWordList(String deviceId);
    List<DueWordResponse> getDueWordList(String deviceId);
    ReviewResponse recordReview(String deviceId, String word, ReviewOutcome outcome);
    void deleteByDeviceIdAndWord(String deviceId, String word);
}
