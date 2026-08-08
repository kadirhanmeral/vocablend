package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class DeviceWordServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    @Mock
    private DeviceWordRepository deviceWordRepository;

    @Mock
    private WordService wordService;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private DeviceWordServiceImpl deviceWordService;

    @BeforeEach
    void setUp() {
        deviceWordService = new DeviceWordServiceImpl(
                deviceWordRepository, wordService, clock, new ReviewScheduler(clock));
    }

    @Test
    void getWordList_dedupesDuplicateWordEntriesInGlobalCache() {
        String deviceId = "device-1";
        WordProgress spare = new WordProgress("spare", 0, 0, NOW);
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(spare)));

        WordEntity firstEntry = wordEntity("spare");
        firstEntry.setMeaningEn("first meaning");
        WordEntity secondEntry = wordEntity("spare");
        secondEntry.setMeaningEn("second meaning");

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));
        when(wordService.getWordListByWords(List.of("spare"))).thenReturn(List.of(
                firstEntry, secondEntry
        ));

        List<DeviceWordResponse> words = deviceWordService.getWordList(deviceId);

        assertEquals(1, words.size());
        assertEquals("spare", words.get(0).word());
        // Duplicate WordEntity rows for the same word (see WordServiceImpl.addWord's
        // check-then-insert race) must resolve to the first cached entry, not the last.
        assertEquals("first meaning", words.get(0).meaningEn());
    }

    @Test
    void getWordList_dropsProgressWithNoCachedContent() {
        String deviceId = "device-1";
        WordProgress apple = new WordProgress("apple", 0, 0, NOW);
        WordProgress deleted = new WordProgress("deleted", 0, 0, NOW);
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(apple, deleted)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));
        // "deleted" has progress but no matching WordEntity in the global cache
        // (e.g. removed from `words`, or never successfully persisted).
        when(wordService.getWordListByWords(List.of("apple", "deleted"))).thenReturn(List.of(
                wordEntity("apple")
        ));

        List<DeviceWordResponse> words = deviceWordService.getWordList(deviceId);

        assertEquals(1, words.size());
        assertEquals("apple", words.get(0).word());
    }

    @Test
    void getWordList_joinsProgressAndNormalizesLegacyNullReviewDate() {
        String deviceId = "device-1";
        Instant scheduled = NOW.plusSeconds(3600);
        WordProgress apple = new WordProgress("apple", 4, 0, scheduled);
        WordProgress legacy = new WordProgress("legacy", 0, 0, null);
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(apple, legacy)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));
        when(wordService.getWordListByWords(List.of("apple", "legacy"))).thenReturn(List.of(
                wordEntity("apple"), wordEntity("legacy")
        ));

        List<DeviceWordResponse> words = deviceWordService.getWordList(deviceId);

        assertEquals(2, words.size());

        DeviceWordResponse first = words.get(0);
        assertEquals("apple", first.word());
        assertEquals("apple meaning", first.meaningEn());
        assertEquals(4, first.level());
        assertEquals(scheduled, first.nextReviewAt());

        // A null nextReviewAt means "no recorded progress", which reads as due now.
        assertEquals(NOW, words.get(1).nextReviewAt());
    }

    @Test
    void deleteByDeviceIdAndWord_removesMatchingProgressOnly() {
        String deviceId = "device-1";
        WordProgress apple = new WordProgress("apple", 0, 0, NOW);
        WordProgress banana = new WordProgress("banana", 0, 0, NOW);
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(apple, banana)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));

        deviceWordService.deleteByDeviceIdAndWord(deviceId, "apple");

        assertEquals(1, deviceWord.getWords().size());
        assertEquals("banana", deviceWord.getWords().get(0).getWord());
        verify(deviceWordRepository).save(deviceWord);
    }

    @Test
    void addWord_startsNewWordInLearningPhaseDueNow() {
        String deviceId = "device-1";
        String word = "resilient";

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.empty());
        when(wordService.addWord(word)).thenReturn(wordEntity(word));

        deviceWordService.addWord(deviceId, word);

        ArgumentCaptor<DeviceWordEntity> captor = ArgumentCaptor.forClass(DeviceWordEntity.class);
        verify(deviceWordRepository).save(captor.capture());

        DeviceWordEntity savedEntity = captor.getValue();
        assertEquals(1, savedEntity.getWords().size());

        WordProgress savedWord = savedEntity.getWords().get(0);
        assertEquals(word, savedWord.getWord());
        assertEquals(0, savedWord.getLevel());
        assertEquals(0, savedWord.getCorrectStreak());
        assertEquals(NOW, savedWord.getNextReviewAt());
    }

    @Test
    void review_appliesOutcomeToMatchingWordAndPersists() {
        String deviceId = "device-1";
        WordProgress apple = new WordProgress("apple", 0, 2, NOW);
        WordProgress banana = new WordProgress("banana", 3, 0, NOW);
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(apple, banana)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));

        // Snapshot apple's mutable state at the instant save() is invoked. A mock's
        // save() only records that the reference was passed, not the field values at
        // call time, so this is what actually fails if save() is ever moved ahead of
        // reviewScheduler.apply() - the entity must be fully mutated before it is
        // handed to the repository.
        int[] levelAtSaveTime = new int[1];
        Instant[] nextReviewAtAtSaveTime = new Instant[1];
        doAnswer(invocation -> {
            levelAtSaveTime[0] = apple.getLevel();
            nextReviewAtAtSaveTime[0] = apple.getNextReviewAt();
            return null;
        }).when(deviceWordRepository).save(deviceWord);

        Optional<ReviewResponse> result = deviceWordService.review(deviceId, "apple", ReviewOutcome.GOT_IT);

        assertTrue(result.isPresent());
        assertEquals("apple", result.get().word());
        assertEquals(1, result.get().level());
        assertEquals(0, result.get().correctStreak());
        assertEquals(NOW.plus(Duration.ofHours(1)), result.get().nextReviewAt());

        // The other word is untouched.
        assertEquals(3, banana.getLevel());
        verify(deviceWordRepository).save(deviceWord);

        // Pins the ordering invariant: by the time save() fires, apple must already
        // reflect the post-review state, not the pre-review one.
        assertEquals(1, levelAtSaveTime[0]);
        assertEquals(NOW.plus(Duration.ofHours(1)), nextReviewAtAtSaveTime[0]);
    }

    @Test
    void review_normalizesWordCasing() {
        String deviceId = "device-1";
        WordProgress apple = new WordProgress("apple", 0, 2, NOW);
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(apple)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));

        Optional<ReviewResponse> result = deviceWordService.review(deviceId, "Apple", ReviewOutcome.GOT_IT);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().level());
    }

    @Test
    void review_returnsEmptyAndSavesNothingWhenWordNotSaved() {
        String deviceId = "device-1";
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(new WordProgress("apple", 0, 0, NOW))));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));

        Optional<ReviewResponse> result = deviceWordService.review(deviceId, "pear", ReviewOutcome.GOT_IT);

        assertTrue(result.isEmpty());
        verify(deviceWordRepository, never()).save(deviceWord);
    }

    @Test
    void review_returnsEmptyWhenDeviceUnknown() {
        when(deviceWordRepository.findByDeviceId("ghost")).thenReturn(Optional.empty());

        Optional<ReviewResponse> result = deviceWordService.review("ghost", "apple", ReviewOutcome.GOT_IT);

        assertTrue(result.isEmpty());
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
