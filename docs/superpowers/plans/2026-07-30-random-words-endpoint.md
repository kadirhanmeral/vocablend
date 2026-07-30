# Random Words Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `GET /api/words/random?count=N&exclude=w1,w2` returning a random sample of the global `words` collection, for use as multiple-choice distractors in the mobile app's upcoming practice game modes.

**Architecture:** Follows the existing `Controller → Service → Repository` layering (`Dao/Repository/WordRepository.java` → `Service/Word/WordService(Impl).java` → new `Controller/WordController.java`), the same shape as the existing `WordService.addWord`/`getWordListByWords` methods.

**Tech Stack:** Spring Boot 3.5 (Java 21), Spring Data MongoDB (`@Aggregation` repository method), JUnit 5 + Mockito (`@ExtendWith(MockitoExtension.class)`), `@WebMvcTest` + MockMvc for controller tests.

---

## Note on testing approach (deviation from spec)

The design spec calls for a "repository-level test: seed a handful of `WordEntity` docs, confirm `findRandomExcluding` never returns an excluded word." This repo has **no Mongo integration test infrastructure** (no embedded Mongo, no Testcontainers) — none of the existing repository methods (`findByWord`, `findAllByWordIn`) have a direct repository-level test either; they're only exercised indirectly through Service/Controller tests with mocked repositories.

Adding Testcontainers/embedded-Mongo just for this one query is out of scope for a small endpoint (and isn't requested anywhere else in the spec). This plan follows the existing convention instead:
- The Service-layer test verifies `WordServiceImpl.getRandomWords` calls `WordRepository.findRandomExcluding` with the right arguments and returns its result (Mockito, no real Mongo).
- Task 4 adds a manual `curl`-based verification step against a locally running instance with real MongoDB, to confirm the aggregation pipeline itself (the one piece no unit test touches) behaves correctly end-to-end.

If real repository-level testing is wanted later, that's a separate, repo-wide testing-infrastructure task, not specific to this endpoint.

---

## File Structure

- Modify: `src/main/java/com/vocablend/vocablend_be/Dao/Repository/WordRepository.java` — add `findRandomExcluding`
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/Word/WordService.java` — add `getRandomWords` to the interface
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/Word/WordServiceImpl.java` — implement `getRandomWords`
- Create: `src/main/java/com/vocablend/vocablend_be/Controller/WordController.java` — new controller, `GET /api/words/random`
- Create: `src/test/java/com/vocablend/vocablend_be/Service/Word/WordServiceImplTest.java`
- Create: `src/test/java/com/vocablend/vocablend_be/Controller/WordControllerTest.java`

---

### Task 1: Repository — random sampling method

**Files:**
- Modify: `src/main/java/com/vocablend/vocablend_be/Dao/Repository/WordRepository.java`

This method has no automated test (see "Note on testing approach" above) — it's verified manually in Task 4. There is no failing-test step for this task; it's a single additive change to an interface.

- [ ] **Step 1: Add the aggregation method to `WordRepository`**

Replace the full file contents:

```java
package com.vocablend.vocablend_be.Dao.Repository;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WordRepository extends MongoRepository<WordEntity, String> {
    WordEntity findByWord(String word);
    List<WordEntity> findAllByWordIn(List<String> word);

    @Aggregation(pipeline = {
            "{ $match: { word: { $nin: ?0 } } }",
            "{ $sample: { size: ?1 } }"
    })
    List<WordEntity> findRandomExcluding(List<String> excludeWords, int count);
}
```

- [ ] **Step 2: Compile to confirm the annotation/pipeline is syntactically valid**

Run: `./mvnw -q compile`
Expected: `BUILD SUCCESS`, no errors about `WordRepository.java`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/vocablend/vocablend_be/Dao/Repository/WordRepository.java
git commit -m "Add findRandomExcluding aggregation query to WordRepository"
```

---

### Task 2: Service — `getRandomWords`

**Files:**
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/Word/WordService.java`
- Modify: `src/main/java/com/vocablend/vocablend_be/Service/Word/WordServiceImpl.java`
- Test: `src/test/java/com/vocablend/vocablend_be/Service/Word/WordServiceImplTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/vocablend/vocablend_be/Service/Word/WordServiceImplTest.java`:

```java
package com.vocablend.vocablend_be.Service.Word;

import com.google.genai.Client;
import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Dao.Repository.WordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WordServiceImplTest {

    @Mock
    private WordRepository wordRepository;

    @Mock
    private Client geminiClient;

    private WordServiceImpl wordService;

    @BeforeEach
    void setUp() {
        wordService = new WordServiceImpl(wordRepository, geminiClient);
    }

    @Test
    void getRandomWords_delegatesToRepositoryAndReturnsResult() {
        WordEntity banana = new WordEntity();
        banana.setWord("banana");
        List<String> exclude = List.of("apple");

        when(wordRepository.findRandomExcluding(exclude, 3)).thenReturn(List.of(banana));

        List<WordEntity> result = wordService.getRandomWords(3, exclude);

        assertEquals(List.of(banana), result);
        verify(wordRepository).findRandomExcluding(exclude, 3);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=WordServiceImplTest`
Expected: FAIL — compilation error, `getRandomWords` is not a method on `WordServiceImpl`/`WordService`

- [ ] **Step 3: Add `getRandomWords` to the `WordService` interface**

Replace the full file contents of `src/main/java/com/vocablend/vocablend_be/Service/Word/WordService.java`:

```java
package com.vocablend.vocablend_be.Service.Word;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;

import java.util.List;

public interface WordService {

    WordEntity addWord(String wordText);
    List<WordEntity> getWordListByWords(List<String> words);
    List<WordEntity> getRandomWords(int count, List<String> excludeWords);
}
```

- [ ] **Step 4: Implement it in `WordServiceImpl`**

In `src/main/java/com/vocablend/vocablend_be/Service/Word/WordServiceImpl.java`, add this method (after `getWordListByWords`, before `fetchFromAI`):

```java
    public List<WordEntity> getRandomWords(int count, List<String> excludeWords) {
        return wordRepository.findRandomExcluding(excludeWords, count);
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./mvnw test -Dtest=WordServiceImplTest`
Expected: PASS — `Tests run: 1, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/vocablend/vocablend_be/Service/Word/WordService.java \
        src/main/java/com/vocablend/vocablend_be/Service/Word/WordServiceImpl.java \
        src/test/java/com/vocablend/vocablend_be/Service/Word/WordServiceImplTest.java
git commit -m "Add WordService.getRandomWords"
```

---

### Task 3: Controller — `GET /api/words/random`

**Files:**
- Create: `src/main/java/com/vocablend/vocablend_be/Controller/WordController.java`
- Test: `src/test/java/com/vocablend/vocablend_be/Controller/WordControllerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/com/vocablend/vocablend_be/Controller/WordControllerTest.java`:

```java
package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Service.Word.WordService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WordController.class)
class WordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WordService wordService;

    @Test
    void getRandomWords_returnsWordsFromService() throws Exception {
        WordEntity banana = new WordEntity();
        banana.setWord("banana");
        when(wordService.getRandomWords(3, List.of("apple"))).thenReturn(List.of(banana));

        mockMvc.perform(get("/api/words/random?count=3&exclude=apple"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].word").value("banana"));
    }

    @Test
    void getRandomWords_excludeOmitted_passesEmptyListToService() throws Exception {
        when(wordService.getRandomWords(eq(2), eq(List.of()))).thenReturn(List.of());

        mockMvc.perform(get("/api/words/random?count=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getRandomWords_countMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/words/random"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getRandomWords_countZeroOrLess_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/words/random?count=0"))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./mvnw test -Dtest=WordControllerTest`
Expected: FAIL — compilation error, `com.vocablend.vocablend_be.Controller.WordController` does not exist

- [ ] **Step 3: Create `WordController`**

Create `src/main/java/com/vocablend/vocablend_be/Controller/WordController.java`:

```java
package com.vocablend.vocablend_be.Controller;

import com.vocablend.vocablend_be.Dao.Entity.WordEntity;
import com.vocablend.vocablend_be.Service.Word.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordController {

    private final WordService wordService;

    @GetMapping("/random")
    public ResponseEntity<List<WordEntity>> getRandomWords(
            @RequestParam int count,
            @RequestParam(required = false) List<String> exclude
    ) {
        if (count <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "count must be positive");
        }
        List<String> excludeWords = exclude == null ? List.of() : exclude;
        List<WordEntity> words = wordService.getRandomWords(count, excludeWords);
        return ResponseEntity.ok(words);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./mvnw test -Dtest=WordControllerTest`
Expected: PASS — `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/vocablend/vocablend_be/Controller/WordController.java \
        src/test/java/com/vocablend/vocablend_be/Controller/WordControllerTest.java
git commit -m "Add GET /api/words/random endpoint"
```

---

### Task 4: Manual end-to-end verification

**Files:** none (no code changes — this task only runs and verifies the app)

This replaces the spec's "repository-level test" — it's the one check that exercises the real MongoDB `$sample`/`$nin` aggregation, which no unit test in Tasks 1-3 touches.

- [ ] **Step 1: Start MongoDB locally** (skip if already running)

Run: `docker run -d -p 27017:27017 --name vocablend-mongo mongo:7`
Expected: container starts; `docker ps` shows `vocablend-mongo` as `Up`

- [ ] **Step 2: Seed a few words directly into Mongo**

Run:
```bash
docker exec -it vocablend-mongo mongosh vocablend --eval '
db.words.insertMany([
  { word: "apple", meaningEn: "a fruit", meaningTr: "elma", examples: ["I ate an apple."] },
  { word: "banana", meaningEn: "a fruit", meaningTr: "muz", examples: ["The banana is yellow."] },
  { word: "car", meaningEn: "a vehicle", meaningTr: "araba", examples: ["I drive a car."] },
  { word: "dog", meaningEn: "an animal", meaningTr: "köpek", examples: ["The dog barks."] }
])
'
```
Expected: `{ acknowledged: true, insertedIds: { ... 4 entries ... } }`

- [ ] **Step 3: Run the app**

Run: `SPRING_DATA_MONGODB_URI=mongodb://localhost:27017/vocablend ./mvnw spring-boot:run`
Expected: log line `Started VocablendBeApplication` with no errors

- [ ] **Step 4: Call the endpoint, confirm exclusion and count behavior**

In a second terminal:
```bash
curl -s "http://localhost:8080/api/words/random?count=2&exclude=apple,banana" | python3 -m json.tool
```
Expected: a JSON array of exactly 2 objects, each with `word` equal to `"car"` or `"dog"` — never `"apple"` or `"banana"`.

```bash
curl -s "http://localhost:8080/api/words/random?count=10&exclude=apple,banana,car,dog"
```
Expected: `[]` (nothing left to sample after excluding all 4 seeded words) — confirms the "fewer than count available" edge case from the spec returns an empty/short list rather than an error.

- [ ] **Step 5: Stop the app and clean up the seeded data**

Stop the running app (Ctrl+C), then:
```bash
docker exec -it vocablend-mongo mongosh vocablend --eval 'db.words.deleteMany({ word: { $in: ["apple","banana","car","dog"] } })'
docker stop vocablend-mongo && docker rm vocablend-mongo
```
Expected: deletion count of 4, container stopped and removed (skip the stop/rm if you're keeping the container for other local work).

No commit for this task — it's verification only, no files changed.

---

## Self-Review

**Spec coverage:**
- Endpoint shape (`GET /api/words/random?count=&exclude=`) — Task 3 ✓
- `count`/`exclude` semantics, `count<=0` → 400 — Task 3 ✓
- Repository aggregation (`$match`/`$nin` + `$sample`) — Task 1 ✓
- "Fewer than count available → return what's there, no error" — Task 4, Step 4 ✓ (verified manually since it's a real-Mongo behavior, not something a mocked-repository unit test can meaningfully assert)
- New `WordController`, distinct from `DeviceWordController` — Task 3 ✓
- Testing section from spec — addressed via the "Note on testing approach" deviation above, with equivalent coverage via Service-mock test + manual verification

**Placeholder scan:** no TBD/TODO; every step has complete, runnable code.

**Type consistency:** `getRandomWords(int count, List<String> excludeWords)` signature is identical across the interface (Task 2, Step 3), implementation (Task 2, Step 4), its test (Task 2, Step 1), and the controller call site (Task 3, Step 3).
