package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;

import java.util.List;

public interface DeviceWordService {

    WordEntity addWord(String deviceId, String word);
    List<WordEntity> getWordList(String deviceId);
    void deleteByDeviceIdAndWord(String deviceId, String word);
}
