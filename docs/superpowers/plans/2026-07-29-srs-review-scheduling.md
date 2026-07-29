# SRS Review Scheduling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the client-only "Got it / Still learning" tally with a server-side Leitner-box spaced repetition system, so the Practice screen shows only words due for review today instead of a random shuffle of everything.

**Architecture:** `DeviceWordEntity.words` moves from `List<String>` to `List<WordProgress>` (word + box level + next review date). The backend exposes a new `/due` endpoint (filtered by `nextReviewDate <= today`) and a `/review` endpoint that applies fixed Leitner-box transitions. The mobile app deletes its local `practiceStorage.ts` tally and drives the Practice screen entirely from these two new endpoints.

**Tech Stack:** Spring Boot 3.5 / Java 21 / MongoDB (Spring Data) / JUnit 5 + Mockito for the backend; React Native (Expo) / TypeScript / TanStack Query for mobile.

**Repos:** This plan spans two separate git repositories:
- Backend: `/Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend` (package root `com.vocablend.vocablend_be`, under `src/main/java/com/vocablend/vocablend_be/`)
- Mobile: `/Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile` (under `src/`)

All file paths below are relative to one of these two roots — each task states which repo it's in. `cd` into that repo before running commands.

**Spec:** `docs/superpowers/specs/2026-07-29-srs-review-scheduling-design.md` (in the backend repo)

---

### Task 1: Leitner box scheduling logic

**Repo:** backend

**Files:**
- Create: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcome.java`
- Create: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/LeitnerBox.java`
- Test: `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/LeitnerBoxTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.vocablend.vocablend_be.Service.DeviceWord;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeitnerBoxTest {

    @Test
    void gotIt_incrementsBoxLevel() {
        assertEquals(2, LeitnerBox.nextBoxLevel(1, ReviewOutcome.gotIt));
        assertEquals(6, LeitnerBox.nextBoxLevel(5, ReviewOutcome.gotIt));
    }

    @Test
    void gotIt_staysAtMaxBox() {
        assertEquals(6, LeitnerBox.nextBoxLevel(6, ReviewOutcome.gotIt));
    }

    @Test
    void stillLearning_resetsToBoxOne() {
        assertEquals(1, LeitnerBox.nextBoxLevel(4, ReviewOutcome.stillLearning));
        assertEquals(1, LeitnerBox.nextBoxLevel(1, ReviewOutcome.stillLearning));
    }

    @Test
    void nextReviewDate_matchesBoxInterval() {
        LocalDate today = LocalDate.now();
        assertEquals(today.plusDays(1), LeitnerBox.nextReviewDate(1));
        assertEquals(today.plusDays(3), LeitnerBox.nextReviewDate(2));
        assertEquals(today.plusDays(7), LeitnerBox.nextReviewDate(3));
        assertEquals(today.plusDays(16), LeitnerBox.nextReviewDate(4));
        assertEquals(today.plusDays(35), LeitnerBox.nextReviewDate(5));
        assertEquals(today.plusDays(90), LeitnerBox.nextReviewDate(6));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=LeitnerBoxTest`
Expected: FAIL (compile error) — `ReviewOutcome` and `LeitnerBox` don't exist yet.

- [ ] **Step 3: Create the `ReviewOutcome` enum**

```java
package com.vocablend.vocablend_be.Service.DeviceWord;

public enum ReviewOutcome {
    gotIt,
    stillLearning
}
```

- [ ] **Step 4: Create the `LeitnerBox` scheduler**

```java
package com.vocablend.vocablend_be.Service.DeviceWord;

import java.time.LocalDate;
import java.util.Map;

public final class LeitnerBox {

    public static final int MIN_BOX = 1;
    public static final int MAX_BOX = 6;

    private static final Map<Integer, Integer> INTERVAL_DAYS = Map.of(
            1, 1,
            2, 3,
            3, 7,
            4, 16,
            5, 35,
            6, 90
    );

    private LeitnerBox() {
    }

    public static int nextBoxLevel(int currentBoxLevel, ReviewOutcome outcome) {
        if (outcome == ReviewOutcome.gotIt) {
            return Math.min(currentBoxLevel + 1, MAX_BOX);
        }
        return MIN_BOX;
    }

    public static LocalDate nextReviewDate(int boxLevel) {
        return LocalDate.now().plusDays(INTERVAL_DAYS.get(boxLevel));
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `./mvnw test -Dtest=LeitnerBoxTest`
Expected: PASS (4 tests)

- [ ] **Step 6: Commit**

```bash
cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend"
git add src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcome.java \
        src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/LeitnerBox.java \
        src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/LeitnerBoxTest.java
git commit -m "Add Leitner box scheduling logic for SRS review intervals"
```

---

### Task 2: Data model and `DeviceWordService` rewrite for SRS

**Repo:** backend

**Files:**
- Create: `src/main/java/com/vocablend/vocablend_be/Dao/Entity/WordProgress.java`
- Create: `src/main/java/com/vocablend/vocablend_be/Dto/DueWordResponse.java`
- Create: `src/main/java/com/vocablend/vocablend_be/Dto/ReviewResponse.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Dao/Entity/DeviceWordEntity.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordService.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImpl.java`
- Test: `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java`

This task changes the shape of `DeviceWordEntity.words`, so the entity, DTOs, interface, implementation, and tests all land together — the build won't compile with only some of them applied.

- [ ] **Step 1: Create the `WordProgress` embedded entity**

```java
package com.vocablend.vocablend_be.Dao.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WordProgress {
    private String word;
    private int boxLevel;
    private LocalDate nextReviewDate;
}
```

- [ ] **Step 2: Change `DeviceWordEntity.words` to `List<WordProgress>`**

Replace the full contents of `DeviceWordEntity.java`:

```java
package com.vocablend.vocablend_be.Dao.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "device_words")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceWordEntity {

    @Id
    private String id;
    private String deviceId;
    private List<WordProgress> words;
}
```

- [ ] **Step 3: Create the `DueWordResponse` and `ReviewResponse` DTOs**

```java
package com.vocablend.vocablend_be.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DueWordResponse {
    private String id;
    private String word;
    private String meaningEn;
    private String meaningTr;
    private List<String> examples;
    private int boxLevel;
    private LocalDate nextReviewDate;
}
```

```java
package com.vocablend.vocablend_be.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReviewResponse {
    private String word;
    private int boxLevel;
    private LocalDate nextReviewDate;
}
```

- [ ] **Step 4: Update the `DeviceWordService` interface**

```java
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
```

- [ ] **Step 5: Write the failing test for the new/changed behavior**

```java
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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void getDueWordList_onlyReturnsWordsDueTodayOrEarlier() {
        String deviceId = "device-1";
        WordProgress dueYesterday = new WordProgress("apple", 2, LocalDate.now().minusDays(1));
        WordProgress dueToday = new WordProgress("banana", 1, LocalDate.now());
        WordProgress dueTomorrow = new WordProgress("cherry", 3, LocalDate.now().plusDays(1));

        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(dueYesterday, dueToday, dueTomorrow)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));
        when(wordService.getWordListByWords(List.of("apple", "banana"))).thenReturn(List.of(
                wordEntity("apple"), wordEntity("banana")
        ));

        List<DueWordResponse> due = deviceWordService.getDueWordList(deviceId);

        assertEquals(2, due.size());
        assertTrue(due.stream().anyMatch(w -> w.getWord().equals("apple")));
        assertTrue(due.stream().anyMatch(w -> w.getWord().equals("banana")));
        assertFalse(due.stream().anyMatch(w -> w.getWord().equals("cherry")));
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
```

- [ ] **Step 6: Run the test to verify it fails**

Run: `./mvnw test -Dtest=DeviceWordServiceImplTest`
Expected: FAIL (compile error) — `DeviceWordServiceImpl` doesn't implement `getDueWordList`/`recordReview` yet, and its constructor/field types don't match `List<WordProgress>`.

- [ ] **Step 7: Rewrite `DeviceWordServiceImpl`**

Replace the full contents of `DeviceWordServiceImpl.java`:

```java
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

            word = word.toLowerCase();

            boolean alreadySaved = deviceWord.getWords().stream()
                    .anyMatch(progress -> progress.getWord().equals(word));

            if (!alreadySaved) {

                wordEntity = wordService.addWord(word);

                if (!ObjectUtils.isEmpty(wordEntity.getExamples())) {
                    deviceWord.getWords().add(new WordProgress(word, LeitnerBox.MIN_BOX, LocalDate.now()));
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
                .collect(Collectors.toMap(WordEntity::getWord, w -> w));

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
```

- [ ] **Step 8: Run the test to verify it passes**

Run: `./mvnw test -Dtest=DeviceWordServiceImplTest`
Expected: PASS (4 tests)

- [ ] **Step 9: Run the full test suite to catch any other breakage**

Run: `./mvnw test`
Expected: PASS (`VocablendBeApplicationTests`, `LeitnerBoxTest`, `DeviceWordServiceImplTest` all green)

- [ ] **Step 10: Commit**

```bash
cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend"
git add src/main/java/com/vocablend/vocablend_be/Dao/Entity/WordProgress.java \
        src/main/java/com/vocablend/vocablend_be/Dao/Entity/DeviceWordEntity.java \
        src/main/java/com/vocablend/vocablend_be/Dto/DueWordResponse.java \
        src/main/java/com/vocablend/vocablend_be/Dto/ReviewResponse.java \
        src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordService.java \
        src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImpl.java \
        src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java
git commit -m "Store per-word SRS progress and compute due words server-side"
```

---

### Task 3: Controller endpoints for due list and review

**Repo:** backend

**Files:**
- Modify: `src/main/java/com/vocablend/vocablend_be/Controller/DeviceWordController.java`
- Test: `src/test/java/com/vocablend/vocablend_be/Controller/DeviceWordControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dto.DueWordResponse;
import com.vocablend.vocablend_be.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Service.DeviceWord.DeviceWordService;
import com.vocablend.vocablend_be.Service.DeviceWord.ReviewOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceWordController.class)
class DeviceWordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceWordService deviceWordService;

    @Test
    void getDueWords_returnsDueList() throws Exception {
        DueWordResponse due = new DueWordResponse(
                "1", "apple", "a fruit", "elma", List.of("I ate an apple."), 2, LocalDate.now());
        when(deviceWordService.getDueWordList("device-1")).thenReturn(List.of(due));

        mockMvc.perform(get("/api/device-words/device-1/due"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].word").value("apple"))
                .andExpect(jsonPath("$[0].boxLevel").value(2));
    }

    @Test
    void reviewWord_returnsUpdatedProgress() throws Exception {
        ReviewResponse response = new ReviewResponse("apple", 3, LocalDate.now().plusDays(7));
        when(deviceWordService.recordReview("device-1", "apple", ReviewOutcome.gotIt)).thenReturn(response);

        mockMvc.perform(post("/api/device-words/device-1/apple/review?outcome=gotIt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boxLevel").value(3));
    }

    @Test
    void reviewWord_unknownWord_returnsNotFound() throws Exception {
        when(deviceWordService.recordReview(eq("device-1"), eq("ghost"), eq(ReviewOutcome.gotIt))).thenReturn(null);

        mockMvc.perform(post("/api/device-words/device-1/ghost/review?outcome=gotIt"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw test -Dtest=DeviceWordControllerTest`
Expected: FAIL — 404s for `/due` and `/review`, since those routes don't exist on the controller yet.

- [ ] **Step 3: Add the two endpoints to `DeviceWordController`**

```java
package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dto.DueWordResponse;
import com.vocablend.vocablend_be.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Service.DeviceWord.DeviceWordService;
import com.vocablend.vocablend_be.Service.DeviceWord.ReviewOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/device-words")
@RequiredArgsConstructor
public class DeviceWordController {

    private final DeviceWordService deviceWordService;

    @PostMapping("/{deviceId}")
    public ResponseEntity<WordEntity> addWord(
            @PathVariable String deviceId,
            @RequestParam String word
    ) {
        WordEntity addedWord = deviceWordService.addWord(deviceId, word);
        return ResponseEntity.status(201).body(addedWord);
    }

    @GetMapping("/{deviceId}/list")
    public ResponseEntity<List<WordEntity>> getDeviceWords(@PathVariable String deviceId) {
        List<WordEntity> words = deviceWordService.getWordList(deviceId);
        return ResponseEntity.ok(words);
    }

    @GetMapping("/{deviceId}/due")
    public ResponseEntity<List<DueWordResponse>> getDueWords(@PathVariable String deviceId) {
        List<DueWordResponse> dueWords = deviceWordService.getDueWordList(deviceId);
        return ResponseEntity.ok(dueWords);
    }

    @PostMapping("/{deviceId}/{word}/review")
    public ResponseEntity<ReviewResponse> reviewWord(
            @PathVariable String deviceId,
            @PathVariable String word,
            @RequestParam ReviewOutcome outcome
    ) {
        ReviewResponse result = deviceWordService.recordReview(deviceId, word, outcome);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{deviceId}/{word}")
    public ResponseEntity<Void> deleteWord(
            @PathVariable String deviceId,
            @PathVariable String word) {

        deviceWordService.deleteByDeviceIdAndWord(deviceId, word);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw test -Dtest=DeviceWordControllerTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Run the full test suite**

Run: `./mvnw test`
Expected: PASS (all test classes green)

- [ ] **Step 6: Commit**

```bash
cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend"
git add src/main/java/com/vocablend/vocablend_be/Controller/DeviceWordController.java \
        src/test/java/com/vocablend/vocablend_be/Controller/DeviceWordControllerTest.java
git commit -m "Expose /due and /review endpoints for SRS-driven practice"
```

---

### Task 4: Mobile API types and client functions

**Repo:** mobile

**Files:**
- Modify: `src/api/types.ts`
- Modify: `src/api/client.ts`

- [ ] **Step 1: Add the new types**

In `src/api/types.ts`, add after the existing `WordEntity` interface:

```ts
export type ReviewOutcome = 'gotIt' | 'stillLearning';

export interface DueWordEntity extends WordEntity {
  boxLevel: number;
  nextReviewDate: string;
}

export interface ReviewResult {
  word: string;
  boxLevel: number;
  nextReviewDate: string;
}
```

- [ ] **Step 2: Add the client functions**

In `src/api/client.ts`, update the import line and append two functions:

```ts
import { AddWordResult, ApiError, DueWordEntity, ReviewOutcome, ReviewResult, WordEntity } from './types';
```

```ts
export async function getDueWords(deviceId: string): Promise<DueWordEntity[]> {
  const response = await request(`/api/device-words/${encodeURIComponent(deviceId)}/due`);
  return (await response.json()) as DueWordEntity[];
}

export async function postReview(
  deviceId: string,
  word: string,
  outcome: ReviewOutcome,
): Promise<ReviewResult> {
  const response = await request(
    `/api/device-words/${encodeURIComponent(deviceId)}/${encodeURIComponent(word)}/review?outcome=${outcome}`,
    { method: 'POST' },
  );
  return (await response.json()) as ReviewResult;
}
```

- [ ] **Step 3: Type-check**

Run: `npx tsc --noEmit`
Expected: no errors

- [ ] **Step 4: Commit**

```bash
cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile"
git add src/api/types.ts src/api/client.ts
git commit -m "Add due-words and review API client functions"
```

---

### Task 5: `useDuePractice` hook and next-review formatting

**Repo:** mobile

**Files:**
- Modify: `src/lib/queryClient.ts`
- Create: `src/hooks/useDuePractice.ts`
- Create: `src/lib/reviewFormat.ts`

- [ ] **Step 1: Add a query key for due words**

In `src/lib/queryClient.ts`, append:

```ts
export function dueWordsKey(deviceId: string) {
  return ['dueWords', deviceId] as const;
}
```

- [ ] **Step 2: Create the `useDuePractice` hook**

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getDueWords, postReview } from '../api/client';
import { ReviewOutcome } from '../api/types';
import { dueWordsKey } from '../lib/queryClient';

export function useDuePractice(deviceId: string) {
  const queryClient = useQueryClient();
  const queryKey = dueWordsKey(deviceId);

  const dueWordsQuery = useQuery({
    queryKey,
    queryFn: () => getDueWords(deviceId),
  });

  const reviewMutation = useMutation({
    mutationFn: ({ word, outcome }: { word: string; outcome: ReviewOutcome }) =>
      postReview(deviceId, word, outcome),
  });

  return { dueWordsQuery, reviewMutation };
}
```

Note: `queryClient` here refers to the `useQueryClient()` result, unrelated to the `queryClient` singleton exported from `src/lib/queryClient.ts` — no import collision because only `dueWordsKey` is imported from that module.

- [ ] **Step 3: Create the next-review date formatter**

```ts
export function daysUntil(dateIso: string): number {
  const target = new Date(`${dateIso}T00:00:00`);
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diffMs = target.getTime() - today.getTime();
  return Math.round(diffMs / (1000 * 60 * 60 * 24));
}

export function formatNextReview(dateIso: string): string {
  const days = daysUntil(dateIso);
  if (days <= 0) {
    return 'today';
  }
  if (days === 1) {
    return 'in 1 day';
  }
  return `in ${days} days`;
}
```

- [ ] **Step 4: Type-check**

Run: `npx tsc --noEmit`
Expected: no errors

- [ ] **Step 5: Commit**

```bash
cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile"
git add src/lib/queryClient.ts src/hooks/useDuePractice.ts src/lib/reviewFormat.ts
git commit -m "Add useDuePractice hook and next-review date formatting"
```

---

### Task 6: Rewrite `PracticeScreen` for due-based practice

**Repo:** mobile

**Files:**
- Modify: `src/screens/PracticeScreen.tsx`
- Delete: `src/lib/practiceStorage.ts`

- [ ] **Step 1: Replace the full contents of `PracticeScreen.tsx`**

```tsx
import React, { useEffect, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { ActivityIndicator, Button, IconButton, Text } from 'react-native-paper';

import { DueWordEntity } from '../api/types';
import { Flashcard } from '../components/Flashcard';
import { useDeviceId } from '../context/DeviceIdContext';
import { useDuePractice } from '../hooks/useDuePractice';
import { formatNextReview } from '../lib/reviewFormat';
import { shuffle } from '../lib/shuffle';

export function PracticeScreen() {
  const deviceId = useDeviceId();
  const { dueWordsQuery, reviewMutation } = useDuePractice(deviceId);
  const navigation = useNavigation();

  const [deck, setDeck] = useState<DueWordEntity[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);

  useEffect(() => {
    if (dueWordsQuery.data) {
      setDeck(shuffle(dueWordsQuery.data));
      setCurrentIndex(0);
    }
  }, [dueWordsQuery.data]);

  const handleShuffle = () => {
    setDeck((prev) => shuffle(prev));
    setCurrentIndex(0);
  };

  const currentWord = deck[currentIndex];

  const handleResult = (outcome: 'gotIt' | 'stillLearning') => {
    if (!currentWord?.word) {
      return;
    }
    const answeredIndex = currentIndex;
    reviewMutation.mutate(
      { word: currentWord.word, outcome },
      {
        onSuccess: (result) => {
          setDeck((prev) =>
            prev.map((item, index) =>
              index === answeredIndex
                ? { ...item, boxLevel: result.boxLevel, nextReviewDate: result.nextReviewDate }
                : item,
            ),
          );
        },
      },
    );
  };

  if (dueWordsQuery.isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (deck.length === 0) {
    return (
      <View style={styles.centered}>
        <Text variant="titleMedium" style={styles.emptyText}>
          🎉 Bugün tekrar edilecek kelime yok!
        </Text>
        <Text variant="bodyMedium" style={styles.emptyText}>
          Yarın yeni kelimeler hazır olacak.
        </Text>
        <Button mode="contained" onPress={() => navigation.navigate('MyWords' as never)}>
          Yeni kelime ekle
        </Button>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text variant="labelLarge" style={styles.progressLabel}>
        Card {currentIndex + 1} of {deck.length}
      </Text>

      <Flashcard key={currentWord.id ?? currentIndex} word={currentWord} />

      <Text variant="bodySmall" style={styles.tally}>
        Box {currentWord.boxLevel}/6 · next review {formatNextReview(currentWord.nextReviewDate)}
      </Text>

      <View style={styles.scoreRow}>
        <Button
          mode="outlined"
          onPress={() => handleResult('stillLearning')}
          loading={reviewMutation.isPending}
        >
          Still learning
        </Button>
        <Button mode="contained" onPress={() => handleResult('gotIt')} loading={reviewMutation.isPending}>
          Got it
        </Button>
      </View>

      <View style={styles.navRow}>
        <IconButton
          icon="chevron-left"
          size={32}
          disabled={currentIndex === 0}
          onPress={() => setCurrentIndex((i) => Math.max(0, i - 1))}
        />
        <Button mode="text" onPress={handleShuffle}>
          Shuffle
        </Button>
        <IconButton
          icon="chevron-right"
          size={32}
          disabled={currentIndex === deck.length - 1}
          onPress={() => setCurrentIndex((i) => Math.min(deck.length - 1, i + 1))}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  centered: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingHorizontal: 24,
  },
  emptyText: {
    textAlign: 'center',
  },
  progressLabel: {
    textAlign: 'center',
    marginBottom: 8,
    opacity: 0.7,
  },
  tally: {
    textAlign: 'center',
    marginTop: 12,
    opacity: 0.7,
  },
  scoreRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 12,
    marginTop: 20,
  },
  navRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 'auto',
    marginBottom: 8,
  },
});
```

- [ ] **Step 2: Delete the now-unused local progress storage**

```bash
cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile"
rm src/lib/practiceStorage.ts
```

- [ ] **Step 3: Type-check**

Run: `npx tsc --noEmit`
Expected: no errors (confirms no other file still imports `practiceStorage`)

- [ ] **Step 4: Commit**

```bash
cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile"
git add -u src/lib/practiceStorage.ts
git add src/screens/PracticeScreen.tsx
git commit -m "Drive Practice screen from due-word queue instead of local tally"
```

---

### Task 7: Manual end-to-end verification

**Repo:** both (run backend first, then mobile against it)

- [ ] **Step 1: Start the backend**

Run: `cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend" && ./mvnw spring-boot:run`
(Requires MongoDB reachable and a real `GEMINI_API_KEY`, per the project's `CLAUDE.md`.)

- [ ] **Step 2: Start the Expo app**

Run: `cd "/Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile" && npx expo start`

- [ ] **Step 3: Add a word and confirm it's immediately due**

In My Words, add a new word. Switch to Practice — the word should appear (new words start at box 1, due today).

- [ ] **Step 4: Confirm "Got it" advances the box**

Tap "Got it" on the card. Confirm the on-card indicator changes from "Box 1/6 · next review today" to "Box 2/6 · next review in 3 days".

- [ ] **Step 5: Confirm "Still learning" resets the box**

On a different due word (or re-add one), tap "Still learning". Confirm the indicator reads "Box 1/6 · next review in 1 day".

- [ ] **Step 6: Confirm the empty state**

Once every currently-due word in the account has been reviewed (or by testing against an account with no words due — e.g. wait for the day boundary, or manually inspect the `device_words` collection to confirm `nextReviewDate` moved to a future date), reload the Practice tab and confirm it shows "🎉 Bugün tekrar edilecek kelime yok!" with a working "Yeni kelime ekle" button back to My Words.

- [ ] **Step 7: Confirm My Words is unaffected**

Confirm the My Words tab still lists all saved words (not just due ones) and that add/delete still work as before.

---

## Notes

- No data migration is included — per the approved spec, existing `device_words` documents (if any) are not converted; the schema change applies going forward only.
- Timezone handling uses the backend JVM's local date (`LocalDate.now()`) for due-date comparisons; this is an accepted simplification per the spec, not a bug to fix here.
