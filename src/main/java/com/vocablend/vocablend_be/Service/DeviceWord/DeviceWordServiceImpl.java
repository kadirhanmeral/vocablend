package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.DeviceWordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Repository.DeviceWordRepository;
import com.vocablend.vocablend_be.Service.Word.WordService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        if(StringUtils.hasText(word)){

            word = word.toLowerCase();

            if (!deviceWord.getWords().contains(word)) {

                wordEntity = wordService.addWord(word);

                if(!ObjectUtils.isEmpty(wordEntity.getExamples())){
                    deviceWord.getWords().add(word);
                    deviceWordRepository.save(deviceWord);
                }
            }
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

    @Override
    public void deleteByDeviceIdAndWord(String deviceId, String word) {

        if(!StringUtils.hasText(word) || !StringUtils.hasText(deviceId)){
            return;
        }

        DeviceWordEntity deviceWord = deviceWordRepository.findByDeviceId(deviceId)
                .orElse(null);

        if (deviceWord == null) {
            return;
        }

        List<String> words = deviceWord.getWords();
        word = word.toLowerCase();

        if (!ObjectUtils.isEmpty(words) && words.contains(word)) {
            words.remove(word);
            deviceWord.setWords(words);

            deviceWordRepository.save(deviceWord);
        }


    }
}
