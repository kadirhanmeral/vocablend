package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.DeviceWordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordProgress;
import com.vocablend.vocablend_be.Dao.Repository.DeviceWordRepository;
import com.vocablend.vocablend_be.Service.Word.WordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceWordServiceImplTest {

    @Mock
    private DeviceWordRepository deviceWordRepository;

    @Mock
    private WordService wordService;

    private DeviceWordServiceImpl deviceWordService;

    @BeforeEach
    void setUp() {
        deviceWordService = new DeviceWordServiceImpl(deviceWordRepository, wordService);
    }

    @Test
    void getWordList_dedupesDuplicateWordEntriesInGlobalCache() {
        String deviceId = "device-1";
        WordProgress spare = new WordProgress("spare");
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(spare)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));
        when(wordService.getWordListByWords(List.of("spare"))).thenReturn(List.of(
                wordEntity("spare"), wordEntity("spare")
        ));

        List<WordEntity> words = deviceWordService.getWordList(deviceId);

        assertEquals(1, words.size());
        assertEquals("spare", words.get(0).getWord());
    }

    @Test
    void deleteByDeviceIdAndWord_removesMatchingProgressOnly() {
        String deviceId = "device-1";
        WordProgress apple = new WordProgress("apple");
        WordProgress banana = new WordProgress("banana");
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(apple, banana)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));

        deviceWordService.deleteByDeviceIdAndWord(deviceId, "apple");

        assertEquals(1, deviceWord.getWords().size());
        assertEquals("banana", deviceWord.getWords().get(0).getWord());
        verify(deviceWordRepository).save(deviceWord);
    }

    private WordEntity wordEntity(String word) {
        WordEntity entity = new WordEntity();
        entity.setId(word + "-id");
        entity.setWord(word);
        entity.setMeaningEn(word + " meaning");
        entity.setMeaningTr(word + " anlam");
        entity.setExamples(List.of("Example with " + word));
        return entity;
    }
}
