package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.DeviceWordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Repository.DeviceWordRepository;
import com.vocablend.vocablend_be.Service.Word.WordService;
import io.netty.util.internal.ObjectUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DeviceWordServiceImpl implements DeviceWordService{

    private final DeviceWordRepository deviceWordRepository;

    private final WordService wordService;

    @Override
    public WordEntity addWord(String deviceId, String word) {
        WordEntity wordEntity = new WordEntity();
        DeviceWordEntity deviceWord = deviceWordRepository.findByDeviceId(deviceId)
                .orElse(new DeviceWordEntity(null, deviceId, new ArrayList<>()));

        if (!deviceWord.getWords().contains(word)) {

            wordEntity = wordService.addWord(word);

            deviceWord.getWords().add(word);
            deviceWordRepository.save(deviceWord);
        }

        return wordEntity;
    }

    @Override
    public List<WordEntity> getWordList(String deviceId) {
        List<WordEntity> wordListByWords = new ArrayList<>();
        List<String> words = deviceWordRepository.findByDeviceId(deviceId)
                .map(DeviceWordEntity::getWords)
                .orElse(new ArrayList<>());

        if(!ObjectUtils.isEmpty(words)){
            wordListByWords = wordService.getWordListByWords(words);
        }

        return wordListByWords;

    }
}
