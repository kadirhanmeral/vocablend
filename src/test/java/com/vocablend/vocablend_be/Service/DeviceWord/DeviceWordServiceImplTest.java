package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.DeviceWordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordProgress;
import com.vocablend.vocablend_be.Dao.Repository.DeviceWordRepository;
import com.vocablend.vocablend_be.Dto.DueWordResponse;
import com.vocablend.vocablend_be.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Service.Word.WordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    void getDueWordList_returnsAllSavedWordsRegardlessOfNextReviewDate() {
        String deviceId = "device-1";
        WordProgress dueYesterday = new WordProgress("apple", 2, LocalDate.now().minusDays(1));
        WordProgress dueToday = new WordProgress("banana", 1, LocalDate.now());
        WordProgress dueTomorrow = new WordProgress("cherry", 3, LocalDate.now().plusDays(1));

        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(dueYesterday, dueToday, dueTomorrow)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));
        when(wordService.getWordListByWords(List.of("apple", "banana", "cherry"))).thenReturn(List.of(
                wordEntity("apple"), wordEntity("banana"), wordEntity("cherry")
        ));

        List<DueWordResponse> due = deviceWordService.getDueWordList(deviceId);

        assertEquals(3, due.size());
        assertTrue(due.stream().anyMatch(w -> w.getWord().equals("apple")));
        assertTrue(due.stream().anyMatch(w -> w.getWord().equals("banana")));
        assertTrue(due.stream().anyMatch(w -> w.getWord().equals("cherry")));
    }

    @Test
    void getDueWordList_toleratesDuplicateWordEntriesInGlobalCache() {
        String deviceId = "device-1";
        WordProgress dueToday = new WordProgress("spare", 1, LocalDate.now());
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(dueToday)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));
        when(wordService.getWordListByWords(List.of("spare"))).thenReturn(List.of(
                wordEntity("spare"), wordEntity("spare")
        ));

        List<DueWordResponse> due = deviceWordService.getDueWordList(deviceId);

        assertEquals(1, due.size());
        assertEquals("spare", due.get(0).getWord());
    }

    @Test
    void getWordList_dedupesDuplicateWordEntriesInGlobalCache() {
        String deviceId = "device-1";
        WordProgress spare = new WordProgress("spare", 1, LocalDate.now());
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
    void recordReview_gotIt_incrementsBoxAndPushesNextReviewDateForward() {
        String deviceId = "device-1";
        WordProgress progress = new WordProgress("apple", 2, LocalDate.now());
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(progress)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));

        ReviewResponse result = deviceWordService.recordReview(deviceId, "apple", ReviewOutcome.gotIt);

        assertEquals(3, result.getBoxLevel());
        assertEquals(LocalDate.now().plusDays(7), result.getNextReviewDate());
        verify(deviceWordRepository).save(deviceWord);
    }

    @Test
    void recordReview_stillLearning_resetsBoxToOne() {
        String deviceId = "device-1";
        WordProgress progress = new WordProgress("apple", 5, LocalDate.now());
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(progress)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));

        ReviewResponse result = deviceWordService.recordReview(deviceId, "apple", ReviewOutcome.stillLearning);

        assertEquals(1, result.getBoxLevel());
        assertEquals(LocalDate.now().plusDays(1), result.getNextReviewDate());
    }

    @Test
    void deleteByDeviceIdAndWord_removesMatchingProgressOnly() {
        String deviceId = "device-1";
        WordProgress apple = new WordProgress("apple", 2, LocalDate.now());
        WordProgress banana = new WordProgress("banana", 1, LocalDate.now());
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
