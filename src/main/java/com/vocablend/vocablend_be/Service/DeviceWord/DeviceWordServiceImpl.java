package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
import com.vocablend.vocablend_be.Dao.Entity.DeviceWordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordProgress;
import com.vocablend.vocablend_be.Dao.Repository.DeviceWordRepository;
import com.vocablend.vocablend_be.Service.Word.WordService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceWordServiceImpl implements DeviceWordService {

    private final DeviceWordRepository deviceWordRepository;

    private final WordService wordService;

    private final Clock clock;

    @Override
    public WordEntity addWord(String deviceId, String word) {
        WordEntity wordEntity = new WordEntity();
        DeviceWordEntity deviceWord = deviceWordRepository.findByDeviceId(deviceId)
                .orElse(new DeviceWordEntity(null, deviceId, new ArrayList<>()));

        if (StringUtils.hasText(word)) {

            String normalizedWord = word.toLowerCase();

            boolean alreadySaved = deviceWord.getWords().stream()
                    .anyMatch(progress -> progress.getWord().equals(normalizedWord));

            if (!alreadySaved) {

                wordEntity = wordService.addWord(normalizedWord);

                if (!ObjectUtils.isEmpty(wordEntity.getExamples())) {
                    // New words start in the learning phase and are due immediately.
                    deviceWord.getWords().add(new WordProgress(normalizedWord, 0, 0, clock.instant()));
                    deviceWordRepository.save(deviceWord);
                }
            }
        }

        return wordEntity;
    }

    @Override
    public List<DeviceWordResponse> getWordList(String deviceId) {
        List<WordProgress> progresses = progressesOf(deviceId);

        if (ObjectUtils.isEmpty(progresses)) {
            return new ArrayList<>();
        }

        List<String> words = progresses.stream().map(WordProgress::getWord).toList();

        // The global word cache can contain duplicate entries for the same word text
        // (see WordServiceImpl.addWord's check-then-insert race), so keep the first
        // entry per word when building the lookup. Map order is irrelevant here since
        // the response order comes from `progresses`, not from iterating this map.
        Map<String, WordEntity> contentByWord = wordService.getWordListByWords(words).stream()
                .collect(Collectors.toMap(WordEntity::getWord, w -> w, (first, second) -> first));

        Instant now = clock.instant();

        return progresses.stream()
                .map(progress -> toResponse(progress, contentByWord.get(progress.getWord()), now))
                .filter(Objects::nonNull)
                .toList();
    }

    private DeviceWordResponse toResponse(WordProgress progress, WordEntity content, Instant now) {
        if (content == null) {
            return null;
        }

        // A null nextReviewAt comes from documents written before scheduling
        // existed; those words have no recorded progress, so they are due now.
        Instant nextReviewAt = progress.getNextReviewAt() != null ? progress.getNextReviewAt() : now;

        return new DeviceWordResponse(
                content.getId(),
                content.getWord(),
                content.getMeaningEn(),
                content.getMeaningTr(),
                content.getExamples(),
                progress.getLevel(),
                progress.getCorrectStreak(),
                nextReviewAt);
    }

    @Override
    public void deleteByDeviceIdAndWord(String deviceId, String word) {

        if (!StringUtils.hasText(word) || !StringUtils.hasText(deviceId)) {
            return;
        }

        DeviceWordEntity deviceWord = deviceWordRepository.findByDeviceId(deviceId)
                .orElse(null);

        if (deviceWord == null) {
            return;
        }

        String normalizedWord = word.toLowerCase();
        boolean removed = deviceWord.getWords().removeIf(progress -> progress.getWord().equals(normalizedWord));

        if (removed) {
            deviceWordRepository.save(deviceWord);
        }
    }

    private List<WordProgress> progressesOf(String deviceId) {
        return deviceWordRepository.findByDeviceId(deviceId)
                .map(DeviceWordEntity::getWords)
                .orElse(new ArrayList<>());
    }
}
