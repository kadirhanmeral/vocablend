package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.DeviceWordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Entity.WordProgress;
import com.vocablend.vocablend_be.Dao.Repository.DeviceWordRepository;
import com.vocablend.vocablend_be.Dto.DueWordResponse;
import com.vocablend.vocablend_be.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Service.Word.WordService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DeviceWordServiceImpl implements DeviceWordService {

    private final DeviceWordRepository deviceWordRepository;

    private final WordService wordService;

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
                    deviceWord.getWords().add(new WordProgress(normalizedWord, LeitnerBox.MIN_BOX, LocalDate.now()));
                    deviceWordRepository.save(deviceWord);
                }
            }
        }

        return wordEntity;
    }

    @Override
    public List<WordEntity> getWordList(String deviceId) {
        List<String> words = wordsOf(deviceId);

        if (ObjectUtils.isEmpty(words)) {
            return new ArrayList<>();
        }

        return wordService.getWordListByWords(words);
    }

    @Override
    public List<DueWordResponse> getDueWordList(String deviceId) {
        List<WordProgress> progressList = deviceWordRepository.findByDeviceId(deviceId)
                .map(DeviceWordEntity::getWords)
                .orElse(new ArrayList<>());

        LocalDate today = LocalDate.now();
        List<WordProgress> dueProgress = progressList.stream()
                .filter(progress -> !progress.getNextReviewDate().isAfter(today))
                .toList();

        if (dueProgress.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> dueWords = dueProgress.stream().map(WordProgress::getWord).toList();
        Map<String, WordEntity> wordsByText = wordService.getWordListByWords(dueWords).stream()
                .collect(Collectors.toMap(WordEntity::getWord, w -> w, (first, second) -> first));

        return dueProgress.stream()
                .filter(progress -> wordsByText.containsKey(progress.getWord()))
                .map(progress -> {
                    WordEntity wordEntity = wordsByText.get(progress.getWord());
                    return new DueWordResponse(
                            wordEntity.getId(),
                            wordEntity.getWord(),
                            wordEntity.getMeaningEn(),
                            wordEntity.getMeaningTr(),
                            wordEntity.getExamples(),
                            progress.getBoxLevel(),
                            progress.getNextReviewDate()
                    );
                })
                .toList();
    }

    @Override
    public ReviewResponse recordReview(String deviceId, String word, ReviewOutcome outcome) {
        String normalizedWord = word.toLowerCase();

        DeviceWordEntity deviceWord = deviceWordRepository.findByDeviceId(deviceId)
                .orElse(null);

        if (deviceWord == null) {
            return null;
        }

        WordProgress progress = deviceWord.getWords().stream()
                .filter(p -> p.getWord().equals(normalizedWord))
                .findFirst()
                .orElse(null);

        if (progress == null) {
            return null;
        }

        int newBoxLevel = LeitnerBox.nextBoxLevel(progress.getBoxLevel(), outcome);
        LocalDate newNextReviewDate = LeitnerBox.nextReviewDate(newBoxLevel);

        progress.setBoxLevel(newBoxLevel);
        progress.setNextReviewDate(newNextReviewDate);

        deviceWordRepository.save(deviceWord);

        return new ReviewResponse(normalizedWord, newBoxLevel, newNextReviewDate);
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

    private List<String> wordsOf(String deviceId) {
        return deviceWordRepository.findByDeviceId(deviceId)
                .map(DeviceWordEntity::getWords)
                .orElse(new ArrayList<>())
                .stream()
                .map(WordProgress::getWord)
                .toList();
    }
}
