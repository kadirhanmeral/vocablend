# Memrise-Style Learning Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give every saved word a persistent learning state — a 3-correct learning phase followed by an 8-level hour-granular review ladder — and make the Practice screen serve words that are actually due instead of shuffling the entire list.

**Architecture:** The backend owns scheduling. `WordProgress` gains `level`, `correctStreak` and `nextReviewAt`; a single `ReviewScheduler` unit holds the interval table and the transition rules. `GET /list` returns word content joined with progress in one flat DTO, and a new `POST .../review` endpoint applies an outcome. The mobile app filters "due" client-side and mirrors the interval table only so it can update optimistically; the server response always wins.

**Tech Stack:** Spring Boot 3.5 / Java 21 / MongoDB / JUnit 5 + Mockito (backend); Expo 57 / React Native 0.86 / TypeScript / TanStack Query v5 / react-native-paper (mobile).

**Spec:** `docs/superpowers/specs/2026-08-08-memrise-learning-engine-design.md`

**Two repositories.** They are separate git repos and each task commits to its own:
- Backend: `/Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend`
- Mobile: `/Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile`

**Testing reality.** The backend has JUnit 5 + Mockito and every backend task below is test-first. The mobile project has **no test runner** (no jest in `package.json`); adding one is out of scope per the spec. Mobile tasks are verified with `npx tsc --noEmit` (which currently passes cleanly) plus the manual checklist in Task 13. Do not claim a mobile task is "tested" — it is type-checked.

---

## File Structure

**Backend — create:**
- `src/main/java/com/vocablend/vocablend_be/Config/ClockConfig.java` — exposes a `java.time.Clock` bean so scheduling is testable with pinned time
- `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcome.java` — the `gotIt` / `stillLearning` enum plus wire-value parsing
- `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewScheduler.java` — the only place the interval table and transition rules live
- `src/main/java/com/vocablend/vocablend_be/Controller/Dto/DeviceWordResponse.java` — word content joined with progress
- `src/main/java/com/vocablend/vocablend_be/Controller/Dto/ReviewResponse.java` — the updated state returned by a review
- `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewSchedulerTest.java`
- `src/test/java/com/vocablend/vocablend_be/Controller/DeviceWordControllerTest.java`

**Backend — modify:**
- `Dao/Entity/WordProgress.java` — three new fields
- `Service/DeviceWord/DeviceWordService.java` — `getWordList` return type, new `review`
- `Service/DeviceWord/DeviceWordServiceImpl.java` — join, review, new dependencies
- `Controller/DeviceWordController.java` — new endpoint, new response type
- `src/test/java/.../Service/DeviceWord/DeviceWordServiceImplTest.java` — updated constructor and fixtures

**Mobile — create:**
- `src/practice/schedule.ts` — `isDue`, `applyOutcome`, label formatting; the client mirror of the interval table

**Mobile — modify:**
- `src/api/types.ts` — `DeviceWord`, `ReviewResult`
- `src/api/client.ts` — `getWordList` return type, `postReview`
- `src/lib/queryPersister.ts` — cache buster bump, since the persisted `deviceWords` shape changes
- `src/hooks/useDeviceWords.ts` — `reviewMutation` with optimistic update
- `src/components/Flashcard.tsx` — optional `progressLabel` prop
- `src/screens/PracticeScreen.tsx` — explicit session construction, due-filtered decks, grading, empty state, free practice

---

## Task 1: Clock bean and ReviewOutcome enum

Run everything in the **backend** repo: `/Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend`

**Files:**
- Create: `src/main/java/com/vocablend/vocablend_be/Config/ClockConfig.java`
- Create: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcome.java`
- Create: `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcomeTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcomeTest.java`:

```java
package com.vocablend.vocablend_be.Service.DeviceWord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReviewOutcomeTest {

    @Test
    void fromParam_parsesWireValues() {
        assertEquals(ReviewOutcome.GOT_IT, ReviewOutcome.fromParam("gotIt"));
        assertEquals(ReviewOutcome.STILL_LEARNING, ReviewOutcome.fromParam("stillLearning"));
    }

    @Test
    void fromParam_rejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class, () -> ReviewOutcome.fromParam("maybe"));
    }

    @Test
    void fromParam_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> ReviewOutcome.fromParam(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=ReviewOutcomeTest`
Expected: FAIL — compilation error, `ReviewOutcome` does not exist.

- [ ] **Step 3: Write the enum**

Create `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcome.java`:

```java
package com.vocablend.vocablend_be.Service.DeviceWord;

// The wire values stay camelCase because the mobile app's AnswerOutcome type
// already uses 'gotIt' | 'stillLearning'; the Java constants keep Java naming.
public enum ReviewOutcome {

    GOT_IT("gotIt"),
    STILL_LEARNING("stillLearning");

    private final String param;

    ReviewOutcome(String param) {
        this.param = param;
    }

    public String getParam() {
        return param;
    }

    public static ReviewOutcome fromParam(String value) {
        for (ReviewOutcome outcome : values()) {
            if (outcome.param.equals(value)) {
                return outcome;
            }
        }
        throw new IllegalArgumentException("Unknown review outcome: " + value);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=ReviewOutcomeTest`
Expected: PASS — 3 tests, 0 failures.

- [ ] **Step 5: Add the Clock bean**

Create `src/main/java/com/vocablend/vocablend_be/Config/ClockConfig.java`:

```java
package com.vocablend.vocablend_be.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// Injected wherever scheduling happens so tests can pin time with Clock.fixed
// instead of asserting against wall-clock arithmetic.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
```

- [ ] **Step 6: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend
git add src/main/java/com/vocablend/vocablend_be/Config/ClockConfig.java \
        src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcome.java \
        src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewOutcomeTest.java
git commit -m "Add ReviewOutcome enum and Clock bean for review scheduling"
```

---

## Task 2: WordProgress gains scheduling fields

**Files:**
- Modify: `src/main/java/com/vocablend/vocablend_be/Dao/Entity/WordProgress.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImpl.java:44`
- Modify: `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java:41,59,60`

This task only widens the data model and fixes the call sites so everything compiles. Behavior changes land in Task 3 onward.

- [ ] **Step 1: Add the fields**

Replace the whole of `src/main/java/com/vocablend/vocablend_be/Dao/Entity/WordProgress.java` with:

```java
package com.vocablend.vocablend_be.Dao.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// level 0 is the learning phase: the word is permanently due and must be
// answered correctly ReviewScheduler.LEARNING_TARGET times before it graduates
// onto the review ladder (levels 1-8). correctStreak is only meaningful at
// level 0.
//
// Documents written before this change deserialize with level 0, correctStreak
// 0 and a null nextReviewAt. Null is read as "due now", which is also correct
// semantically - those words have no recorded progress - so no migration is
// needed.
@Data
@AllArgsConstructor
@NoArgsConstructor
public class WordProgress {
    private String word;
    private int level;
    private int correctStreak;
    private Instant nextReviewAt;
}
```

- [ ] **Step 2: Run the build to see the broken call sites**

Run: `./mvnw test-compile`
Expected: FAIL — `constructor WordProgress in class WordProgress cannot be applied to given types` at `DeviceWordServiceImpl.java` and `DeviceWordServiceImplTest.java`.

- [ ] **Step 3: Fix the production call site**

In `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImpl.java`, add the imports:

```java
import java.time.Clock;
import java.time.Instant;
```

Add the `Clock` dependency next to the existing fields:

```java
    private final DeviceWordRepository deviceWordRepository;

    private final WordService wordService;

    private final Clock clock;
```

Replace line 44, `deviceWord.getWords().add(new WordProgress(normalizedWord));`, with:

```java
                    // New words start in the learning phase and are due immediately.
                    deviceWord.getWords().add(new WordProgress(normalizedWord, 0, 0, clock.instant()));
```

- [ ] **Step 4: Fix the test call sites**

In `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java`, add the imports:

```java
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
```

Add a fixed clock field and pass it to the constructor in `setUp`:

```java
    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private DeviceWordServiceImpl deviceWordService;

    @BeforeEach
    void setUp() {
        deviceWordService = new DeviceWordServiceImpl(deviceWordRepository, wordService, clock);
    }
```

Replace the three `new WordProgress(...)` fixtures:

- line 41: `WordProgress spare = new WordProgress("spare", 0, 0, NOW);`
- line 59: `WordProgress apple = new WordProgress("apple", 0, 0, NOW);`
- line 60: `WordProgress banana = new WordProgress("banana", 0, 0, NOW);`

- [ ] **Step 5: Run the full suite to verify it compiles and still passes**

Run: `./mvnw test -Dtest='DeviceWordServiceImplTest,WordServiceImplTest,ReviewOutcomeTest'`
Expected: PASS — no failures. (The full `./mvnw test` also runs `VocablendBeApplicationTests.contextLoads`, which needs a reachable MongoDB; use the scoped command above while iterating.)

- [ ] **Step 6: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend
git add src/main/java/com/vocablend/vocablend_be/Dao/Entity/WordProgress.java \
        src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImpl.java \
        src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java
git commit -m "Add level, correctStreak and nextReviewAt to WordProgress"
```

---

## Task 3: ReviewScheduler

**Files:**
- Create: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewScheduler.java`
- Create: `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewSchedulerTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewSchedulerTest.java`:

```java
package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.WordProgress;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    private final ReviewScheduler scheduler = new ReviewScheduler(Clock.fixed(NOW, ZoneOffset.UTC));

    private WordProgress progress(int level, int correctStreak) {
        return new WordProgress("resilient", level, correctStreak, NOW);
    }

    @Test
    void learningPhase_firstCorrectAnswerAdvancesStreakAndStaysDue() {
        WordProgress word = progress(0, 0);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(0, word.getLevel());
        assertEquals(1, word.getCorrectStreak());
        assertEquals(NOW, word.getNextReviewAt());
    }

    @Test
    void learningPhase_secondCorrectAnswerStillDoesNotGraduate() {
        WordProgress word = progress(0, 1);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(0, word.getLevel());
        assertEquals(2, word.getCorrectStreak());
        assertEquals(NOW, word.getNextReviewAt());
    }

    @Test
    void learningPhase_thirdCorrectAnswerGraduatesToLevelOneInOneHour() {
        WordProgress word = progress(0, 2);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(1, word.getLevel());
        assertEquals(0, word.getCorrectStreak());
        assertEquals(NOW.plus(Duration.ofHours(1)), word.getNextReviewAt());
    }

    @Test
    void learningPhase_wrongAnswerResetsStreakAndKeepsWordDue() {
        WordProgress word = progress(0, 2);

        scheduler.apply(word, ReviewOutcome.STILL_LEARNING);

        assertEquals(0, word.getLevel());
        assertEquals(0, word.getCorrectStreak());
        assertEquals(NOW, word.getNextReviewAt());
    }

    @Test
    void reviewPhase_correctAnswerClimbsOneLevelWithMatchingInterval() {
        assertLevelUp(1, 2, Duration.ofHours(4));
        assertLevelUp(2, 3, Duration.ofHours(12));
        assertLevelUp(3, 4, Duration.ofDays(1));
        assertLevelUp(4, 5, Duration.ofDays(3));
        assertLevelUp(5, 6, Duration.ofDays(7));
        assertLevelUp(6, 7, Duration.ofDays(16));
        assertLevelUp(7, 8, Duration.ofDays(35));
    }

    private void assertLevelUp(int from, int expectedLevel, Duration expectedInterval) {
        WordProgress word = progress(from, 0);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(expectedLevel, word.getLevel());
        assertEquals(NOW.plus(expectedInterval), word.getNextReviewAt());
    }

    @Test
    void reviewPhase_correctAnswerAtTopLevelStaysAtTopLevel() {
        WordProgress word = progress(8, 0);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(8, word.getLevel());
        assertEquals(NOW.plus(Duration.ofDays(35)), word.getNextReviewAt());
    }

    @Test
    void reviewPhase_wrongAnswerResetsToLevelOne() {
        WordProgress word = progress(7, 0);

        scheduler.apply(word, ReviewOutcome.STILL_LEARNING);

        assertEquals(1, word.getLevel());
        assertEquals(0, word.getCorrectStreak());
        assertEquals(NOW.plus(Duration.ofHours(1)), word.getNextReviewAt());
    }

    @Test
    void reviewPhase_outOfRangeLevelIsClampedToTopInterval() {
        WordProgress word = progress(12, 0);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(8, word.getLevel());
        assertEquals(NOW.plus(Duration.ofDays(35)), word.getNextReviewAt());
    }

    @Test
    void legacyProgressWithNullNextReviewAtIsTreatedAsLearningPhase() {
        WordProgress word = new WordProgress("legacy", 0, 0, null);

        scheduler.apply(word, ReviewOutcome.GOT_IT);

        assertEquals(0, word.getLevel());
        assertEquals(1, word.getCorrectStreak());
        assertEquals(NOW, word.getNextReviewAt());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=ReviewSchedulerTest`
Expected: FAIL — compilation error, `ReviewScheduler` does not exist.

- [ ] **Step 3: Write the scheduler**

Create `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewScheduler.java`:

```java
package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Dao.Entity.WordProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

// The single source of truth for review scheduling. The interval table and the
// learning-phase threshold exist here and nowhere else on the backend.
@Component
@RequiredArgsConstructor
public class ReviewScheduler {

    public static final int LEARNING_TARGET = 3;
    public static final int MAX_LEVEL = 8;

    // Index 0 is level 1. Hour-granular on purpose: a day-granular ladder leaves
    // a learner with nothing to do for the rest of the day after one session.
    private static final Duration[] INTERVALS = {
            Duration.ofHours(1),
            Duration.ofHours(4),
            Duration.ofHours(12),
            Duration.ofDays(1),
            Duration.ofDays(3),
            Duration.ofDays(7),
            Duration.ofDays(16),
            Duration.ofDays(35),
    };

    private final Clock clock;

    public void apply(WordProgress progress, ReviewOutcome outcome) {
        Instant now = clock.instant();

        if (progress.getLevel() <= 0) {
            applyLearningPhase(progress, outcome, now);
        } else {
            applyReviewPhase(progress, outcome, now);
        }
    }

    private void applyLearningPhase(WordProgress progress, ReviewOutcome outcome, Instant now) {
        if (outcome == ReviewOutcome.GOT_IT) {
            int streak = progress.getCorrectStreak() + 1;

            if (streak >= LEARNING_TARGET) {
                progress.setLevel(1);
                progress.setCorrectStreak(0);
                progress.setNextReviewAt(now.plus(intervalFor(1)));
                return;
            }

            progress.setCorrectStreak(streak);
        } else {
            progress.setCorrectStreak(0);
        }

        // Still learning: stays permanently due so it keeps coming back.
        progress.setLevel(0);
        progress.setNextReviewAt(now);
    }

    private void applyReviewPhase(WordProgress progress, ReviewOutcome outcome, Instant now) {
        // A forgotten word goes back to the bottom of the ladder rather than one
        // rung down - the interval that produced the miss was already too long.
        int level = outcome == ReviewOutcome.GOT_IT
                ? Math.min(clampLevel(progress.getLevel()) + 1, MAX_LEVEL)
                : 1;

        progress.setLevel(level);
        progress.setCorrectStreak(0);
        progress.setNextReviewAt(now.plus(intervalFor(level)));
    }

    private Duration intervalFor(int level) {
        return INTERVALS[clampLevel(level) - 1];
    }

    private int clampLevel(int level) {
        return Math.min(Math.max(level, 1), MAX_LEVEL);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=ReviewSchedulerTest`
Expected: PASS — 10 tests, 0 failures.

- [ ] **Step 5: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend
git add src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewScheduler.java \
        src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/ReviewSchedulerTest.java
git commit -m "Add ReviewScheduler with learning phase and 8-level review ladder"
```

---

## Task 4: Response DTOs and the list join

**Files:**
- Create: `src/main/java/com/vocablend/vocablend_be/Controller/Dto/DeviceWordResponse.java`
- Create: `src/main/java/com/vocablend/vocablend_be/Controller/Dto/ReviewResponse.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordService.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImpl.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Controller/DeviceWordController.java`
- Modify: `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java`

- [ ] **Step 1: Write the failing tests**

In `DeviceWordServiceImplTest.java`, add this import:

```java
import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
```

Replace the existing `getWordList_dedupesDuplicateWordEntriesInGlobalCache` test (lines 38–54) with these two tests:

```java
    @Test
    void getWordList_dedupesDuplicateWordEntriesInGlobalCache() {
        String deviceId = "device-1";
        WordProgress spare = new WordProgress("spare", 0, 0, NOW);
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(spare)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));
        when(wordService.getWordListByWords(List.of("spare"))).thenReturn(List.of(
                wordEntity("spare"), wordEntity("spare")
        ));

        List<DeviceWordResponse> words = deviceWordService.getWordList(deviceId);

        assertEquals(1, words.size());
        assertEquals("spare", words.get(0).word());
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=DeviceWordServiceImplTest`
Expected: FAIL — compilation error, `DeviceWordResponse` does not exist.

- [ ] **Step 3: Create the DTOs**

Create `src/main/java/com/vocablend/vocablend_be/Controller/Dto/DeviceWordResponse.java`:

```java
package com.vocablend.vocablend_be.Controller.Dto;

import java.time.Instant;
import java.util.List;

// Word content joined with this device's progress. Flat rather than nested so
// the mobile app's WordEntity consumers keep working against an extension of
// the shape they already read.
public record DeviceWordResponse(
        String id,
        String word,
        String meaningEn,
        String meaningTr,
        List<String> examples,
        int level,
        int correctStreak,
        Instant nextReviewAt
) {
}
```

Create `src/main/java/com/vocablend/vocablend_be/Controller/Dto/ReviewResponse.java`:

```java
package com.vocablend.vocablend_be.Controller.Dto;

import java.time.Instant;

public record ReviewResponse(
        String word,
        int level,
        int correctStreak,
        Instant nextReviewAt
) {
}
```

- [ ] **Step 4: Change the service interface**

Replace `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordService.java` with:

```java
package com.vocablend.vocablend_be.Service.DeviceWord;

import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;

import java.util.List;

public interface DeviceWordService {

    WordEntity addWord(String deviceId, String word);
    List<DeviceWordResponse> getWordList(String deviceId);
    void deleteByDeviceIdAndWord(String deviceId, String word);
}
```

- [ ] **Step 5: Rewrite the join in the implementation**

In `DeviceWordServiceImpl.java`, add these imports:

```java
import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;

import java.util.Map;
import java.util.Objects;
```

Replace the whole `getWordList` method and the `wordsOf` helper with:

```java
    @Override
    public List<DeviceWordResponse> getWordList(String deviceId) {
        List<WordProgress> progresses = progressesOf(deviceId);

        if (ObjectUtils.isEmpty(progresses)) {
            return new ArrayList<>();
        }

        List<String> words = progresses.stream().map(WordProgress::getWord).toList();

        // The global word cache can contain duplicate entries for the same word text
        // (see WordServiceImpl.addWord's check-then-insert race), so keep the first
        // entry per word when building the lookup.
        Map<String, WordEntity> contentByWord = wordService.getWordListByWords(words).stream()
                .collect(Collectors.toMap(WordEntity::getWord, w -> w, (first, second) -> first, LinkedHashMap::new));

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

    private List<WordProgress> progressesOf(String deviceId) {
        return deviceWordRepository.findByDeviceId(deviceId)
                .map(DeviceWordEntity::getWords)
                .orElse(new ArrayList<>());
    }
```

- [ ] **Step 6: Update the controller's return type**

In `DeviceWordController.java`, add the import:

```java
import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
```

Replace the `getDeviceWords` method with:

```java
    @GetMapping("/{deviceId}/list")
    public ResponseEntity<List<DeviceWordResponse>> getDeviceWords(@PathVariable String deviceId) {
        List<DeviceWordResponse> words = deviceWordService.getWordList(deviceId);
        return ResponseEntity.ok(words);
    }
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `./mvnw test -Dtest='DeviceWordServiceImplTest,ReviewSchedulerTest,ReviewOutcomeTest,WordServiceImplTest'`
Expected: PASS — 0 failures.

- [ ] **Step 8: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend
git add src/main/java/com/vocablend/vocablend_be/Controller/Dto \
        src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordService.java \
        src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImpl.java \
        src/main/java/com/vocablend/vocablend_be/Controller/DeviceWordController.java \
        src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java
git commit -m "Return word content joined with progress from the device word list"
```

---

## Task 5: The review service method

**Files:**
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordService.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImpl.java`
- Modify: `src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java`

- [ ] **Step 1: Write the failing tests**

In `DeviceWordServiceImplTest.java`, add these imports:

```java
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
```

`java.util.Optional` is already imported in this file — do not add it again.

The scheduler is a real collaborator here, not a mock — its behavior is already covered by `ReviewSchedulerTest` and stubbing it would test nothing. Add it to `setUp`:

```java
    @BeforeEach
    void setUp() {
        deviceWordService = new DeviceWordServiceImpl(
                deviceWordRepository, wordService, clock, new ReviewScheduler(clock));
    }
```

Add these tests:

```java
    @Test
    void review_appliesOutcomeToMatchingWordAndPersists() {
        String deviceId = "device-1";
        WordProgress apple = new WordProgress("apple", 0, 2, NOW);
        WordProgress banana = new WordProgress("banana", 3, 0, NOW);
        DeviceWordEntity deviceWord = new DeviceWordEntity(
                "1", deviceId, new ArrayList<>(List.of(apple, banana)));

        when(deviceWordRepository.findByDeviceId(deviceId)).thenReturn(Optional.of(deviceWord));

        Optional<ReviewResponse> result = deviceWordService.review(deviceId, "apple", ReviewOutcome.GOT_IT);

        assertTrue(result.isPresent());
        assertEquals("apple", result.get().word());
        assertEquals(1, result.get().level());
        assertEquals(0, result.get().correctStreak());
        assertEquals(NOW.plus(Duration.ofHours(1)), result.get().nextReviewAt());

        // The other word is untouched.
        assertEquals(3, banana.getLevel());
        verify(deviceWordRepository).save(deviceWord);
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=DeviceWordServiceImplTest`
Expected: FAIL — compilation error, no `review` method on `DeviceWordServiceImpl`.

- [ ] **Step 3: Add the method to the interface**

In `DeviceWordService.java`, add the import and the method:

```java
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;

import java.util.Optional;
```

```java
    Optional<ReviewResponse> review(String deviceId, String word, ReviewOutcome outcome);
```

The method returns `Optional` rather than throwing so the HTTP concern (404) stays in the controller and the service stays testable without a web layer.

- [ ] **Step 4: Implement it**

In `DeviceWordServiceImpl.java`, add the imports:

```java
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;

import java.util.Optional;
```

Add `ReviewScheduler` to the fields:

```java
    private final Clock clock;

    private final ReviewScheduler reviewScheduler;
```

Add the method after `getWordList`:

```java
    @Override
    public Optional<ReviewResponse> review(String deviceId, String word, ReviewOutcome outcome) {
        if (!StringUtils.hasText(deviceId) || !StringUtils.hasText(word) || outcome == null) {
            return Optional.empty();
        }

        DeviceWordEntity deviceWord = deviceWordRepository.findByDeviceId(deviceId).orElse(null);

        if (deviceWord == null) {
            return Optional.empty();
        }

        String normalizedWord = word.toLowerCase();

        Optional<WordProgress> match = deviceWord.getWords().stream()
                .filter(progress -> progress.getWord().equals(normalizedWord))
                .findFirst();

        if (match.isEmpty()) {
            return Optional.empty();
        }

        WordProgress progress = match.get();
        reviewScheduler.apply(progress, outcome);
        deviceWordRepository.save(deviceWord);

        return Optional.of(new ReviewResponse(
                progress.getWord(),
                progress.getLevel(),
                progress.getCorrectStreak(),
                progress.getNextReviewAt()));
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./mvnw test -Dtest='DeviceWordServiceImplTest,ReviewSchedulerTest,ReviewOutcomeTest,WordServiceImplTest'`
Expected: PASS — 0 failures.

- [ ] **Step 6: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend
git add src/main/java/com/vocablend/vocablend_be/Service/DeviceWord \
        src/test/java/com/vocablend/vocablend_be/Service/DeviceWord/DeviceWordServiceImplTest.java
git commit -m "Apply review outcomes to saved word progress"
```

---

## Task 6: The review endpoint

**Files:**
- Modify: `src/main/java/com/vocablend/vocablend_be/Controller/DeviceWordController.java`
- Create: `src/test/java/com/vocablend/vocablend_be/Controller/DeviceWordControllerTest.java`

- [ ] **Step 1: Write the failing tests**

Create `src/test/java/com/vocablend/vocablend_be/Controller/DeviceWordControllerTest.java`. It follows the `@WebMvcTest` + `@MockBean` style already used by `WordControllerTest`:

```java
package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Controller.Dto.DeviceWordResponse;
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Service.DeviceWord.DeviceWordService;
import com.vocablend.vocablend_be.Service.DeviceWord.ReviewOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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
    void getDeviceWords_returnsProgressAlongsideContent() throws Exception {
        DeviceWordResponse apple = new DeviceWordResponse(
                "apple-id", "apple", "a fruit", "elma", List.of("An apple a day."),
                4, 0, Instant.parse("2026-08-09T10:00:00Z"));

        when(deviceWordService.getWordList("device-1")).thenReturn(List.of(apple));

        mockMvc.perform(get("/api/device-words/device-1/list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].word").value("apple"))
                .andExpect(jsonPath("$[0].level").value(4))
                .andExpect(jsonPath("$[0].correctStreak").value(0))
                // Serialized as an ISO-8601 string, not an epoch number - the mobile
                // client parses it with Date.parse.
                .andExpect(jsonPath("$[0].nextReviewAt").value("2026-08-09T10:00:00Z"));
    }

    @Test
    void review_returnsUpdatedProgress() throws Exception {
        ReviewResponse response = new ReviewResponse(
                "apple", 1, 0, Instant.parse("2026-08-08T11:00:00Z"));

        when(deviceWordService.review(eq("device-1"), eq("apple"), eq(ReviewOutcome.GOT_IT)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(post("/api/device-words/device-1/apple/review").param("outcome", "gotIt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.word").value("apple"))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.nextReviewAt").value("2026-08-08T11:00:00Z"));
    }

    @Test
    void review_returns404WhenWordNotSavedForDevice() throws Exception {
        when(deviceWordService.review(eq("device-1"), eq("pear"), eq(ReviewOutcome.GOT_IT)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/device-words/device-1/pear/review").param("outcome", "gotIt"))
                .andExpect(status().isNotFound());
    }

    @Test
    void review_returns400ForUnknownOutcome() throws Exception {
        mockMvc.perform(post("/api/device-words/device-1/apple/review").param("outcome", "maybe"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./mvnw test -Dtest=DeviceWordControllerTest`
Expected: FAIL — the two review tests return 405/404 because the endpoint does not exist yet.

- [ ] **Step 3: Add the endpoint**

In `DeviceWordController.java`, add the imports:

```java
import com.vocablend.vocablend_be.Controller.Dto.ReviewResponse;
import com.vocablend.vocablend_be.Service.DeviceWord.ReviewOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
```

Add the method after `getDeviceWords`:

```java
    @PostMapping("/{deviceId}/{word}/review")
    public ResponseEntity<ReviewResponse> review(
            @PathVariable String deviceId,
            @PathVariable String word,
            @RequestParam String outcome
    ) {
        ReviewOutcome parsedOutcome;

        try {
            parsedOutcome = ReviewOutcome.fromParam(outcome);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "outcome must be gotIt or stillLearning");
        }

        return deviceWordService.review(deviceId, word, parsedOutcome)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "word is not saved for this device"));
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./mvnw test -Dtest=DeviceWordControllerTest`
Expected: PASS — 4 tests, 0 failures.

- [ ] **Step 5: Run every backend test that does not need MongoDB**

Run: `./mvnw test -Dtest='DeviceWordControllerTest,WordControllerTest,DeviceWordServiceImplTest,WordServiceImplTest,ReviewSchedulerTest,ReviewOutcomeTest'`
Expected: PASS — 0 failures.

- [ ] **Step 6: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend
git add src/main/java/com/vocablend/vocablend_be/Controller/DeviceWordController.java \
        src/test/java/com/vocablend/vocablend_be/Controller/DeviceWordControllerTest.java
git commit -m "Add POST /api/device-words/{deviceId}/{word}/review endpoint"
```

---

## Task 7: Mobile types and API client

Run everything from here on in the **mobile** repo: `/Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile`

**Files:**
- Modify: `src/api/types.ts`
- Modify: `src/api/client.ts`

- [ ] **Step 1: Add the types**

In `src/api/types.ts`, add after the `WordEntity` interface:

```ts
// The device's saved word: dictionary content plus this device's progress.
// Extends WordEntity so every existing consumer (Flashcard, WordCard, the
// question generators) keeps working unchanged.
export interface DeviceWord extends WordEntity {
  level: number;
  correctStreak: number;
  nextReviewAt: string; // ISO 8601
}

export interface ReviewResult {
  word: string;
  level: number;
  correctStreak: number;
  nextReviewAt: string;
}
```

- [ ] **Step 2: Update the client**

In `src/api/client.ts`, change the import line to:

```ts
import { AddWordResult, ApiError, DeviceWord, ReviewResult, WordEntity } from './types';
```

Replace `getWordList` with:

```ts
export async function getWordList(deviceId: string): Promise<DeviceWord[]> {
  const response = await request(`/api/device-words/${encodeURIComponent(deviceId)}/list`);
  return (await response.json()) as DeviceWord[];
}
```

Add after `deleteWord`:

```ts
export async function postReview(
  deviceId: string,
  word: string,
  outcome: 'gotIt' | 'stillLearning',
): Promise<ReviewResult> {
  const response = await request(
    `/api/device-words/${encodeURIComponent(deviceId)}/${encodeURIComponent(word)}/review?outcome=${outcome}`,
    { method: 'POST' },
  );
  return (await response.json()) as ReviewResult;
}
```

- [ ] **Step 3: Invalidate the persisted query cache**

`src/lib/queryPersister.ts` writes the query cache to AsyncStorage, and its own
comment says to bump the buster when the shape of cached data changes. This task
changes the `deviceWords` cache entries from `WordEntity` to `DeviceWord`, so a
rehydrated v1 entry would arrive with `level` and `nextReviewAt` undefined and
render as "Seviye undefined".

In `src/lib/queryPersister.ts`, change:

```ts
const CACHE_BUSTER = 'v1';
```

to:

```ts
// v2: deviceWords entries gained level/correctStreak/nextReviewAt.
const CACHE_BUSTER = 'v2';
```

- [ ] **Step 4: Type-check**

Run: `npx tsc --noEmit`
Expected: exits 0 with no output.

- [ ] **Step 5: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile
git add src/api/types.ts src/api/client.ts src/lib/queryPersister.ts
git commit -m "Add DeviceWord progress fields and postReview to the API client"
```

---

## Task 8: The client-side schedule mirror

**Files:**
- Create: `src/practice/schedule.ts`

This mirrors `ReviewScheduler` so the UI can respond to an answer without waiting for the server, and so the deck behaves correctly while offline. The server response always overwrites the optimistic value, so the mirror can never drift into being authoritative.

- [ ] **Step 1: Write the module**

Create `src/practice/schedule.ts`:

```ts
import { AnswerOutcome } from './generateQuestions';

export const LEARNING_TARGET = 3;
export const MAX_LEVEL = 8;

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

// Mirrors ReviewScheduler.INTERVALS on the backend (index 0 is level 1). The
// server stays authoritative - this exists so an answer can update the UI
// immediately and so the deck stays correct while a review is queued offline.
const INTERVALS_MS = [
  1 * HOUR_MS,
  4 * HOUR_MS,
  12 * HOUR_MS,
  1 * DAY_MS,
  3 * DAY_MS,
  7 * DAY_MS,
  16 * DAY_MS,
  35 * DAY_MS,
];

export interface ProgressState {
  level: number;
  correctStreak: number;
  nextReviewAt: string;
}

function clampLevel(level: number): number {
  return Math.min(Math.max(level, 1), MAX_LEVEL);
}

function intervalFor(level: number): number {
  return INTERVALS_MS[clampLevel(level) - 1];
}

export function isDue(entry: ProgressState, now: number): boolean {
  const dueAt = Date.parse(entry.nextReviewAt);
  // An unparseable or missing date means no recorded progress, which is due now.
  return Number.isNaN(dueAt) || dueAt <= now;
}

export function applyOutcome(entry: ProgressState, outcome: AnswerOutcome, now: number): ProgressState {
  if (entry.level <= 0) {
    if (outcome === 'gotIt') {
      const streak = entry.correctStreak + 1;

      if (streak >= LEARNING_TARGET) {
        return {
          level: 1,
          correctStreak: 0,
          nextReviewAt: new Date(now + intervalFor(1)).toISOString(),
        };
      }

      return { level: 0, correctStreak: streak, nextReviewAt: new Date(now).toISOString() };
    }

    return { level: 0, correctStreak: 0, nextReviewAt: new Date(now).toISOString() };
  }

  const level = outcome === 'gotIt' ? Math.min(clampLevel(entry.level) + 1, MAX_LEVEL) : 1;

  return {
    level,
    correctStreak: 0,
    nextReviewAt: new Date(now + intervalFor(level)).toISOString(),
  };
}

export function formatWait(dueAt: number, now: number): string {
  const minutes = Math.max(1, Math.round((dueAt - now) / 60000));

  if (minutes < 60) {
    return `~${minutes} dakika`;
  }

  const hours = Math.round(minutes / 60);

  if (hours < 24) {
    return `~${hours} saat`;
  }

  return `~${Math.round(hours / 24)} gün`;
}

export function formatProgressLabel(entry: ProgressState, now: number): string {
  if (entry.level <= 0) {
    return `Öğrenme ${entry.correctStreak}/${LEARNING_TARGET}`;
  }

  const dueAt = Date.parse(entry.nextReviewAt);

  if (Number.isNaN(dueAt) || dueAt <= now) {
    return `Seviye ${entry.level} · tekrar zamanı geldi`;
  }

  return `Seviye ${entry.level} · sonraki tekrar ${formatWait(dueAt, now)} sonra`;
}
```

- [ ] **Step 2: Type-check**

Run: `npx tsc --noEmit`
Expected: exits 0 with no output.

- [ ] **Step 3: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile
git add src/practice/schedule.ts
git commit -m "Add client-side review schedule mirror"
```

---

## Task 9: reviewMutation in useDeviceWords

**Files:**
- Modify: `src/hooks/useDeviceWords.ts`

- [ ] **Step 1: Add the mutation**

Replace the import block at the top of `src/hooks/useDeviceWords.ts` with:

```ts
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { addWord, deleteWord, getWordList, postReview } from '../api/client';
import { DeviceWord } from '../api/types';
import { deviceWordsKey } from '../lib/queryClient';
import { AnswerOutcome } from '../practice/generateQuestions';
import { applyOutcome } from '../practice/schedule';
```

Add this mutation after `deleteWordMutation`:

```ts
  const reviewMutation = useMutation({
    mutationFn: ({ word, outcome }: { word: string; outcome: AnswerOutcome }) =>
      postReview(deviceId, word, outcome),
    // networkMode is left at the default ('online'), so a review submitted while
    // offline pauses and flushes on reconnect rather than failing. The optimistic
    // patch below is what keeps the deck correct in the meantime.
    onMutate: async ({ word, outcome }) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<DeviceWord[]>(queryKey);
      const now = Date.now();

      queryClient.setQueryData<DeviceWord[]>(queryKey, (current) =>
        (current ?? []).map((entry) =>
          entry.word === word ? { ...entry, ...applyOutcome(entry, outcome, now) } : entry,
        ),
      );

      return { previous };
    },
    onSuccess: (result) => {
      queryClient.setQueryData<DeviceWord[]>(queryKey, (current) =>
        (current ?? []).map((entry) =>
          entry.word === result.word
            ? {
                ...entry,
                level: result.level,
                correctStreak: result.correctStreak,
                nextReviewAt: result.nextReviewAt,
              }
            : entry,
        ),
      );
    },
    onError: (_error, variables, context) => {
      // Roll back only this word - a whole-array restore would clobber the
      // optimistic patches of any other answers still in flight. The error is
      // not surfaced to the user: the realistic cause is a 404 from a word
      // deleted mid-session, and the invalidate below already repairs the list.
      const previousEntry = context?.previous?.find((entry) => entry.word === variables.word);

      if (previousEntry) {
        queryClient.setQueryData<DeviceWord[]>(queryKey, (current) =>
          (current ?? []).map((entry) => (entry.word === variables.word ? previousEntry : entry)),
        );
      }

      queryClient.invalidateQueries({ queryKey });
    },
  });
```

Change the return statement to:

```ts
  return { wordsQuery, addWordMutation, deleteWordMutation, reviewMutation };
```

- [ ] **Step 2: Type-check**

Run: `npx tsc --noEmit`
Expected: exits 0 with no output.

- [ ] **Step 3: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile
git add src/hooks/useDeviceWords.ts
git commit -m "Add reviewMutation with optimistic progress updates"
```

---

## Task 10: Flashcard progress label

**Files:**
- Modify: `src/components/Flashcard.tsx`

The label is passed in as a string rather than computed from a `DeviceWord`, so `Flashcard` stays a dumb presentational component that knows nothing about scheduling.

- [ ] **Step 1: Add the prop**

In `src/components/Flashcard.tsx`, replace the props interface and the component signature:

```tsx
interface FlashcardProps {
  word: WordEntity;
  progressLabel?: string;
}

export function Flashcard({ word, progressLabel }: FlashcardProps) {
```

Replace the hint `Text` at the bottom of the `Pressable` with:

```tsx
      {progressLabel ? (
        <Text variant="bodySmall" style={styles.progressLabel}>
          {progressLabel}
        </Text>
      ) : null}
      <Text variant="bodySmall" style={styles.hint}>
        Tap the card to {isFlipped ? 'hide' : 'reveal'} the meaning
      </Text>
```

Add the style next to `hint`:

```tsx
  progressLabel: {
    textAlign: 'center',
    marginTop: 12,
    opacity: 0.75,
  },
```

- [ ] **Step 2: Type-check**

Run: `npx tsc --noEmit`
Expected: exits 0 with no output.

- [ ] **Step 3: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile
git add src/components/Flashcard.tsx
git commit -m "Show an optional progress label on the flashcard"
```

---

## Task 11: PracticeScreen serves due words

**Files:**
- Modify: `src/screens/PracticeScreen.tsx`

This is the task that makes the engine visible. Four changes land together because they are all one file and one coherent behavior:

1. Deck construction moves out of a `useEffect` keyed on `wordsQuery.data` and into an explicit `startSession`. Optimistic review updates change that object's identity on every answer, which would re-shuffle the deck and reset the index mid-session. (This is already a latent bug today: the `useFocusEffect` refetch re-shuffles the deck every time the user returns to the screen.)
2. Test modes draw from due words only; Flashcard draws from the whole list.
3. Every answer in a test mode submits a review.
4. Empty state and free practice.

- [ ] **Step 1: Replace the file**

Replace the entire contents of `src/screens/PracticeScreen.tsx` with:

```tsx
import React, { useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { useFocusEffect, useNavigation } from '@react-navigation/native';
import { ActivityIndicator, Button, IconButton, Text } from 'react-native-paper';

import { DeviceWord } from '../api/types';
import { Flashcard } from '../components/Flashcard';
import { useDeviceId } from '../context/DeviceIdContext';
import { useDeviceWords } from '../hooks/useDeviceWords';
import { shuffle } from '../lib/shuffle';
import { AnswerOutcome } from '../practice/generateQuestions';
import { formatProgressLabel, formatWait, isDue } from '../practice/schedule';
import { FillBlankSession } from '../practice/modes/FillBlankSession';
import { MatchingSession } from '../practice/modes/MatchingSession';
import { MultipleChoiceSession } from '../practice/modes/MultipleChoiceSession';
import { TypingSession } from '../practice/modes/TypingSession';

type PracticeMode = 'flashcard' | 'multipleChoice' | 'matching' | 'typing' | 'fillBlank';

const MODE_OPTIONS: { value: PracticeMode; label: string }[] = [
  { value: 'flashcard', label: 'Flashcard' },
  { value: 'multipleChoice', label: 'Çoktan Seçmeli' },
  { value: 'matching', label: 'Eşleştirme' },
  { value: 'typing', label: 'Yazma' },
  { value: 'fillBlank', label: 'Boşluk Doldurma' },
];

// Flashcard is a study tool, not a test: it never grades, and it draws from the
// whole word list rather than the due subset - locking it to the schedule would
// mean waiting 35 days to look at a word you have mastered.
const STUDY_MODE: PracticeMode = 'flashcard';

const BATCH_SIZE = 15;
// Long enough for a mode's own last-answer feedback (✅/❌, color highlight, the
// 1400ms ADVANCE_DELAY_MS in MultipleChoiceSession/FillBlankSession) to finish
// before the batch-complete interstitial swaps the content out.
const BATCH_COMPLETE_DELAY_MS = 1600;

export function PracticeScreen() {
  const deviceId = useDeviceId();
  const { wordsQuery, reviewMutation } = useDeviceWords(deviceId);
  const navigation = useNavigation();

  const [mode, setMode] = useState<PracticeMode>('flashcard');
  const [deck, setDeck] = useState<DeviceWord[]>([]);
  const [batchStart, setBatchStart] = useState(0);
  const [currentBatch, setCurrentBatch] = useState<DeviceWord[]>([]);
  const [gradedWords, setGradedWords] = useState<Set<string>>(new Set());
  const [showInterstitial, setShowInterstitial] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [sessionActive, setSessionActive] = useState(false);
  const [freePractice, setFreePractice] = useState(false);

  const words = wordsQuery.data;

  // The deck is built here and nowhere else. Deriving it from wordsQuery.data's
  // identity would rebuild it on every optimistic review update.
  const startSession = useCallback((nextMode: PracticeMode, source: DeviceWord[], free: boolean) => {
    const now = Date.now();
    const pool =
      nextMode === STUDY_MODE || free ? source : source.filter((entry) => isDue(entry, now));
    const shuffled = shuffle(pool);

    setDeck(shuffled);
    setBatchStart(0);
    setCurrentBatch(shuffled.slice(0, BATCH_SIZE));
    setGradedWords(new Set());
    setShowInterstitial(false);
    setCurrentIndex(0);
    setFreePractice(free);
    setSessionActive(true);
  }, []);

  useEffect(() => {
    if (words && !sessionActive) {
      startSession(mode, words, false);
    }
  }, [words, sessionActive, mode, startSession]);

  useFocusEffect(
    useCallback(() => {
      wordsQuery.refetch();
    }, [wordsQuery.refetch]),
  );

  const handleModeChange = (nextMode: PracticeMode) => {
    setMode(nextMode);
    startSession(nextMode, words ?? [], false);
  };

  const handleShuffle = () => {
    setCurrentBatch((prev) => shuffle(prev));
    setCurrentIndex(0);
  };

  const currentWord = currentBatch[currentIndex];
  const remainingCount = Math.max(0, deck.length - (batchStart + BATCH_SIZE));

  const handleAutoGrade = (word: string, outcome: AnswerOutcome) => {
    // Free practice and the study mode never touch the schedule.
    if (!freePractice && mode !== STUDY_MODE) {
      reviewMutation.mutate({ word, outcome });
    }

    if (mode === 'matching' && outcome === 'stillLearning') {
      // A Matching miss doesn't retire the card - only a correct match does - so
      // it must not count toward "this batch is done". It is still recorded as a
      // wrong answer above.
      return;
    }

    setGradedWords((prev) => (prev.has(word) ? prev : new Set(prev).add(word)));
  };

  useEffect(() => {
    if (showInterstitial || currentBatch.length === 0) {
      return;
    }
    const allGraded = currentBatch.every((item) => !item.word || gradedWords.has(item.word));
    if (!allGraded) {
      return;
    }
    const timer = setTimeout(() => setShowInterstitial(true), BATCH_COMPLETE_DELAY_MS);
    return () => clearTimeout(timer);
  }, [gradedWords, currentBatch, showInterstitial]);

  const handleContinueBatch = () => {
    const nextStart = batchStart + BATCH_SIZE;
    setBatchStart(nextStart);
    setCurrentBatch(deck.slice(nextStart, nextStart + BATCH_SIZE));
    setGradedWords(new Set());
    setShowInterstitial(false);
    setCurrentIndex(0);
  };

  const handleFinishSession = () => {
    // Dropping the session means returning to this tab starts a fresh one from
    // whatever is due at that point.
    setSessionActive(false);
    navigation.navigate('MyWords' as never);
  };

  const handleFreePractice = () => startSession(mode, words ?? [], true);

  const handleFlashcardNext = () => {
    if (currentIndex < currentBatch.length - 1) {
      setCurrentIndex((i) => i + 1);
    } else {
      setShowInterstitial(true);
    }
  };

  if (wordsQuery.isLoading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  const allWords = words ?? [];

  if (allWords.length === 0) {
    return (
      <View style={styles.centered}>
        <Text variant="titleMedium" style={styles.emptyText}>
          Henüz kelime eklemedin
        </Text>
        <Text variant="bodyMedium" style={styles.emptyText}>
          Pratik yapmak için önce birkaç kelime ekle.
        </Text>
        <Button mode="contained" onPress={() => navigation.navigate('MyWords' as never)}>
          Yeni kelime ekle
        </Button>
      </View>
    );
  }

  const noteText =
    mode === STUDY_MODE
      ? 'Çalışma modu — ilerlemeni etkilemez'
      : freePractice
        ? 'Serbest pratik — ilerlemeni etkilemez'
        : null;

  const renderContent = () => {
    // Only reachable in a test mode: the study deck is the full word list, which
    // is non-empty by the check above.
    if (deck.length === 0) {
      const now = Date.now();
      const dueTimes = allWords
        .map((entry) => Date.parse(entry.nextReviewAt))
        .filter((value) => !Number.isNaN(value) && value > now);
      const nextDueAt = dueTimes.length > 0 ? Math.min(...dueTimes) : null;

      return (
        <View style={styles.centered}>
          <Text variant="titleMedium" style={styles.emptyText}>
            Şu an tekrar edilecek kelime yok.
          </Text>
          {nextDueAt !== null ? (
            <Text variant="bodyMedium" style={styles.emptyText}>
              Sonraki tekrar {formatWait(nextDueAt, now)} sonra.
            </Text>
          ) : null}
          <Button mode="contained" onPress={handleFreePractice}>
            Yine de pratik yap
          </Button>
          <Button mode="outlined" onPress={handleFinishSession}>
            Kelimelerim'e dön
          </Button>
        </View>
      );
    }

    if (showInterstitial) {
      return (
        <View style={styles.centered}>
          <Text variant="titleMedium">Bu grup bitti!</Text>
          {remainingCount > 0 ? (
            <>
              <Text variant="bodyMedium">{remainingCount} kelime daha var.</Text>
              <Button mode="contained" onPress={handleContinueBatch}>
                Devam et
              </Button>
              <Button mode="outlined" onPress={handleFinishSession}>
                Bitir
              </Button>
            </>
          ) : (
            <>
              <Text variant="bodyMedium">Bugünkü tüm kelimeleri tamamladın! 🎉</Text>
              <Button mode="contained" onPress={handleFinishSession}>
                Bitir
              </Button>
            </>
          )}
        </View>
      );
    }

    if (mode === 'flashcard') {
      return (
        <ScrollView style={styles.flashcardContainer} contentContainerStyle={styles.flashcardContent}>
          <Text variant="labelLarge" style={styles.progressLabel}>
            Card {currentIndex + 1} of {currentBatch.length}
          </Text>

          <Flashcard
            key={currentWord.id ?? currentIndex}
            word={currentWord}
            progressLabel={formatProgressLabel(currentWord, Date.now())}
          />

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
            <IconButton icon="chevron-right" size={32} onPress={handleFlashcardNext} />
          </View>
        </ScrollView>
      );
    }

    if (mode === 'multipleChoice') {
      return <MultipleChoiceSession key={batchStart} words={currentBatch} onAnswered={handleAutoGrade} />;
    }

    if (mode === 'matching') {
      return <MatchingSession key={batchStart} words={currentBatch} onAnswered={handleAutoGrade} />;
    }

    if (mode === 'typing') {
      return <TypingSession key={batchStart} words={currentBatch} onAnswered={handleAutoGrade} />;
    }

    return <FillBlankSession key={batchStart} words={currentBatch} onAnswered={handleAutoGrade} />;
  };

  return (
    <View style={styles.container}>
      {/* The mode selector stays mounted above every state, including the
          empty state - otherwise a user with nothing due would be unable to
          switch to a mode that does have a deck. */}
      <ScrollView
        horizontal
        showsHorizontalScrollIndicator={false}
        style={styles.modeSelector}
        contentContainerStyle={styles.modeSelectorContent}
      >
        {MODE_OPTIONS.map((option) => (
          <Button
            key={option.value}
            mode={mode === option.value ? 'contained' : 'outlined'}
            style={styles.modeButton}
            onPress={() => handleModeChange(option.value)}
          >
            {option.label}
          </Button>
        ))}
      </ScrollView>

      {noteText ? (
        <Text variant="bodySmall" style={styles.noteText}>
          {noteText}
        </Text>
      ) : null}

      {renderContent()}
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
  modeSelector: {
    marginBottom: 16,
    flexGrow: 0,
  },
  modeSelectorContent: {
    gap: 8,
    paddingHorizontal: 2,
  },
  modeButton: {
    marginRight: 0,
  },
  noteText: {
    textAlign: 'center',
    marginBottom: 12,
    opacity: 0.7,
  },
  flashcardContainer: {
    flex: 1,
  },
  flashcardContent: {},
  progressLabel: {
    textAlign: 'center',
    marginBottom: 8,
    opacity: 0.7,
  },
  navRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 24,
    marginBottom: 8,
  },
});
```

- [ ] **Step 2: Type-check**

Run: `npx tsc --noEmit`
Expected: exits 0 with no output.

- [ ] **Step 3: Commit**

```bash
cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile
git add src/screens/PracticeScreen.tsx
git commit -m "Serve due words in Practice and record every answer"
```

---

## Task 12: Full backend suite

**Files:** none — this is a verification gate before manual testing.

- [ ] **Step 1: Start MongoDB or confirm the configured instance is reachable**

`src/main/resources/application.properties` points at a MongoDB Atlas cluster. `VocablendBeApplicationTests.contextLoads` needs it to be reachable.

- [ ] **Step 2: Run the whole suite**

Run: `cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend/vocablend && ./mvnw test`
Expected: BUILD SUCCESS, 0 failures, 0 errors.

If `contextLoads` is the only failure and it fails on a Mongo connection timeout, that is an environment problem rather than a regression from this plan — note it and continue.

- [ ] **Step 3: Start the backend for manual testing**

Run: `./mvnw spring-boot:run`
Expected: `Started VocablendBeApplication` in the log; leave it running for Task 13.

---

## Task 13: Manual verification

**Files:** none.

The mobile project has no test runner, so this checklist is the only behavioral verification the mobile changes get. Run it end to end before considering the feature done.

- [ ] **Step 1: Launch the app**

Run: `cd /Users/kadirhanmeral/Personal/vocablend-app/vocablend-mobile && npm start`
Then open it on a device or simulator. Confirm `.env` points `API_BASE_URL` at the backend started in Task 12.

- [ ] **Step 2: New word enters the learning phase**

Add a new word on My Words, then open Practice in Flashcard mode.
Expected: the card shows **"Öğrenme 0/3"** and the note **"Çalışma modu — ilerlemeni etkilemez"**.

- [ ] **Step 3: Three correct answers graduate it**

Switch to Çoktan Seçmeli and answer that word correctly. Return to Flashcard between answers to read the label.
Expected: the label advances **Öğrenme 1/3 → 2/3**, and after the third correct answer it reads **"Seviye 1 · sonraki tekrar ~1 saat sonra"** and the word no longer appears in the Çoktan Seçmeli deck.

- [ ] **Step 4: A wrong answer resets to level 1**

Take a word that has reached level 2 or higher (or edit one directly in Mongo) and answer it incorrectly in a test mode.
Expected: its Flashcard label returns to **"Seviye 1 · sonraki tekrar ~1 saat sonra"**.

- [ ] **Step 5: A Matching miss is recorded**

In Eşleştirme, deliberately pick a wrong pair for a word, then match it correctly.
Expected: the miss does not end the batch early, and the word's level drops (or its learning streak resets) — confirm via its Flashcard label.

- [ ] **Step 6: The empty state appears with a wait estimate**

Answer every due word in a test mode.
Expected: **"Şu an tekrar edilecek kelime yok."** plus **"Sonraki tekrar ~N saat sonra."**, with the mode selector still usable above it.

- [ ] **Step 7: Free practice writes nothing**

From the empty state, tap **Yine de pratik yap**, answer several questions, then check the Flashcard labels.
Expected: the note reads **"Serbest pratik — ilerlemeni etkilemez"** and no level or review time changed.

- [ ] **Step 8: Flashcard works with nothing due**

With no due words, switch to Flashcard.
Expected: every saved word is available with its progress label — no empty state.

- [ ] **Step 9: The deck does not reshuffle mid-session**

In Çoktan Seçmeli, answer several questions in a row without leaving the screen.
Expected: the `N / M` counter advances monotonically; the deck order and card index never reset.

- [ ] **Step 10: Offline answers survive**

Turn off networking on the device, answer a few questions, then turn it back on.
Expected: the deck responds immediately while offline (answered words drop out), and after reconnecting the levels persist across an app reload.

Note: a review submitted offline is held in memory only. Killing the app before reconnecting loses it — that is the known limitation recorded in the spec, and closing it is the offline-practice-support spec's job.

- [ ] **Step 11: Commit any fixes**

If the checklist surfaced defects, fix them in the relevant repo and commit with a descriptive message.
