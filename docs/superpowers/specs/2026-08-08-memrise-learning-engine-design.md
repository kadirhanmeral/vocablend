# Memrise-Style Learning Engine (1/3)

Date: 2026-08-08
Status: Approved (design), not yet implemented
Scope: backend (`vocablend-be`) + mobile (`vocablend-mobile`)

## Purpose

Vocablend has five practice modes (Flashcard, Multiple Choice, Matching, Typing,
Fill-in-the-blank) and no memory. `PracticeScreen` shuffles *every* saved word on
every visit, and answer outcomes (`gotIt` / `stillLearning`) are held in a local
`Set` only to decide when a 15-word batch is finished. Nothing is persisted, so a
word the user has mastered appears exactly as often as one they have never seen.

`WordProgress` once carried a Leitner `boxLevel` / `nextReviewDate`; the class
comment records that it was removed because the scheduling "was never actually
used to filter due words." This spec reintroduces per-word scheduling and — the
part that was missing last time — actually wires it into what the Practice screen
shows.

This is the first of three specs. It builds the engine: per-word learning state,
the scheduling rules, the API to read and update it, and a Practice screen that
serves due words instead of everything.

## Non-goals

These are deliberately deferred, each to its own spec:

- **Adaptive question-type escalation (spec 2).** The mode selector stays; the
  user still picks how they want to be tested. The system does not yet choose the
  question type based on a word's level, and there is no within-session recycling
  of a word that was just answered.
- **Gamification (spec 3).** No XP, no daily goal, no day streak, no session
  summary, no growth indicator on `MyWordsListScreen`. The data this layer needs
  (`level`, `correctStreak`) is produced here, but nothing consumes it beyond the
  Practice screen.
- **Flashcard enrichment.** No text-to-speech, no swipe gestures, no "mark as
  difficult", no user-authored mnemonics ("mems"). Valuable, unrelated to the
  engine.
- **Offline durability.** Review mutations use TanStack Query's default
  `networkMode: 'online'`, so they pause while offline and flush on reconnect.
  Surviving an app restart while paused requires the mutation persistence
  described in `vocablend-mobile/docs/superpowers/specs/2026-07-31-offline-practice-support-design.md`
  and is that spec's job, not this one.
- **A mobile test runner.** The project has no jest/testing setup; adding one is
  out of scope. Mobile verification is manual, as in prior specs.
- **Data migration.** Existing `device_words` documents are handled by read-time
  normalization (below), not a migration script.

## The scheduling model

Two phases. A word is either being learned or being reviewed.

### Phase A — Learning (`level = 0`)

Every newly saved word starts here. While in this phase the word is permanently
due (`nextReviewAt = now`), so it stays in the deck until it graduates.

- `gotIt` → `correctStreak + 1`. When the streak reaches **3**, the word
  graduates: `level = 1`, `correctStreak = 0`, `nextReviewAt = now + 1 hour`.
  Below 3: `nextReviewAt = now`.
- `stillLearning` → `correctStreak = 0`, `nextReviewAt = now`.

The 3-correct requirement is the point of this phase. A single correct answer —
which on a four-option multiple-choice question can be a lucky guess — must never
be enough to push a word onto a multi-hour interval.

### Phase B — Review (`level ≥ 1`)

- `gotIt` → `level = min(level + 1, 8)`, then `nextReviewAt = now + interval(level)`
- `stillLearning` → `level = 1`, `nextReviewAt = now + 1 hour`

A wrong answer resets to the bottom of the ladder rather than stepping down one
rung: if the word was forgotten, the interval that produced that outcome was
wrong, and the cheapest correction is to start the ladder over.

### Interval table

| Level | Next review | | Level | Next review |
|---|---|---|---|---|
| 0 | immediately (learning phase) | | 5 | 3 days |
| 1 | 1 hour | | 6 | 7 days |
| 2 | 4 hours | | 7 | 16 days |
| 3 | 12 hours | | 8 | 35 days (ceiling) |
| 4 | 1 day | | | |

Reaching the ceiling takes **10 correct answers over ~27 days**: 3 to graduate,
then 7 more to climb from level 1 to level 8.

Intervals are hour-granular on purpose. A day-granular ladder means a user who
practices in the morning has nothing to do for the rest of the day — which, for a
learner with a small vocabulary, is an empty screen most of the time. Hour-granular
intervals also make `Instant` the natural type instead of `LocalDate`, which
removes the midnight/timezone ambiguity entirely rather than declaring it out of
scope.

### Known limitation

In this spec the user still chooses the practice mode, so all three graduating
correct answers can come from the same mode — three multiple-choice questions in a
row. The guess-your-way-out hole is narrowed, not closed. Closing it is spec 2's
question-type escalation.

A constraint like "the 3 correct answers must come from at least 2 distinct modes"
is deliberately *not* added to the data model: spec 2 removes mode selection
altogether, so such a field would be dead on the day it shipped.

## Backend

### Data model

`WordProgress` gains three fields:

```java
public class WordProgress {
    private String word;
    private int level;            // 0 = learning phase, 1-8 = review ladder
    private int correctStreak;    // meaningful only at level 0, range 0-3
    private Instant nextReviewAt;
}
```

`@AllArgsConstructor` now takes four arguments. Existing call sites —
`DeviceWordServiceImpl.addWord` and `DeviceWordServiceImplTest`, which both use
`new WordProgress("spare")` — must be updated.

**Read-time normalization.** Documents written before this change deserialize with
`level = 0`, `correctStreak = 0`, `nextReviewAt = null`. A `null` `nextReviewAt` is
treated as "due now", which is also exactly right semantically: those words have no
recorded progress, so they belong in the learning phase. No migration script.

Level 0 never consults the interval table — the learning phase sets
`nextReviewAt = now` directly. A stored `level` above 8, or negative (corrupt or
future data), is clamped into 1–8 before an interval is looked up.

### ReviewScheduler

New unit: `Service/DeviceWord/ReviewScheduler`. One job — given a word's current
state and an outcome, produce its next state.

```
apply(level, correctStreak, outcome, now) -> (level, correctStreak, nextReviewAt)
```

It is the only place the interval table and the `LEARNING_TARGET = 3` threshold
exist on the backend. It holds no repository or service dependencies, and takes a
`java.time.Clock` (registered as a Spring bean in `Config`) so tests can pin time
instead of asserting against wall-clock arithmetic.

### API

**`GET /api/device-words/{deviceId}/list`** — response shape changes. Returns
`Controller/Dto/DeviceWordResponse` (a Java record) instead of `WordEntity`, with
word content and progress flattened into one object:

```json
{
  "id": "...",
  "word": "resilient",
  "meaningEn": "...",
  "meaningTr": "...",
  "examples": ["..."],
  "level": 0,
  "correctStreak": 2,
  "nextReviewAt": "2026-08-08T11:00:00Z"
}
```

Flat rather than nested (`{word: {...}, progress: {...}}`) because the mobile app's
`WordEntity` type is consumed by `Flashcard`, `WordCard` and every question
generator; a flat extension keeps all of them working unchanged.

The project currently returns Mongo entities directly from controllers. Introducing
a DTO here is a targeted improvement, not a general refactor: this response joins
two documents (`WordEntity` + `WordProgress`), so no single entity can represent it.

`correctStreak` is included because spec 3's growth indicator ("learning 2/3") will
render exactly this value.

The existing duplicate-`WordEntity` dedupe in `getWordList` (working around the
check-then-insert race in `WordServiceImpl.addWord`) is preserved and applies after
the join.

**`POST /api/device-words/{deviceId}/{word}/review?outcome=gotIt|stillLearning`**
(new) — applies the transition and returns the updated state:

```json
{ "word": "resilient", "level": 1, "correctStreak": 0, "nextReviewAt": "2026-08-08T12:00:00Z" }
```

- Word not in that device's list → 404
- Unrecognized `outcome` → 400

The Java enum is named `GOT_IT` / `STILL_LEARNING` with a static
`fromParam(String)` used by the controller, so Java naming stays conventional while
the wire contract keeps the `'gotIt' | 'stillLearning'` values the mobile
`AnswerOutcome` type already uses.

**`POST /api/device-words/{deviceId}`** — unchanged from the caller's perspective;
internally creates the `WordProgress` with `level = 0`, `correctStreak = 0`,
`nextReviewAt = now`.

**`DELETE /api/device-words/{deviceId}/{word}`** and **`GET /api/words/random`** —
unchanged.

### Backend tests

JUnit 5 + Mockito, matching the existing `@ExtendWith(MockitoExtension.class)` +
constructor-injection style.

`ReviewScheduler`, with a fixed `Clock`:
- learning phase: 1st and 2nd correct answers keep `level = 0` and leave the word due now
- learning phase: 3rd correct answer graduates to `level = 1`, resets the streak, schedules +1 hour
- learning phase: a wrong answer resets `correctStreak` to 0
- review phase: each level maps to its documented interval
- review phase: correct at level 8 stays at level 8 (ceiling)
- review phase: a wrong answer at any level resets to level 1 / +1 hour
- `nextReviewAt = null` normalizes to due-now
- an out-of-range stored `level` (above 8, or negative) clamps into 1–8

`DeviceWordServiceImpl`:
- review updates and persists the matching `WordProgress` and leaves others untouched
- review on a word not in the device's list produces the not-found outcome
- `list` joins word content with progress and preserves the existing dedupe

`DeviceWordController`:
- an unrecognized `outcome` value returns 400

## Mobile

### Types and client

`src/api/types.ts` gains:

```ts
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

Because `DeviceWord` extends `WordEntity`, every existing consumer keeps working.

`src/api/client.ts`: `getWordList` now returns `DeviceWord[]`; new
`postReview(deviceId, word, outcome): Promise<ReviewResult>`.

### src/practice/schedule.ts (new)

Mirrors the backend table so the client can update optimistically:

- `INTERVALS` — level → milliseconds
- `LEARNING_TARGET = 3`
- `isDue(entry, now)` — `nextReviewAt <= now`
- `applyOutcome(entry, outcome, now)` — the same transition rules, returning the
  new `{ level, correctStreak, nextReviewAt }`

The duplication is deliberate and bounded: the server remains authoritative and its
response overwrites the optimistic value. An eight-entry constant table is a cheaper
price than either round-tripping every answer before the UI can respond, or leaving
the deck stale while offline.

### useDeviceWords

Adds `reviewMutation`:

- `onMutate` — optimistically patches the cached `DeviceWord` via `applyOutcome`,
  so the deck behaves correctly even with no connection
- `onSuccess` — replaces the optimistic value with the server's authoritative one
- `onError` — rolls back to the pre-mutation snapshot and invalidates the list
- `networkMode` — left at the default (`'online'`), so mutations pause offline and
  flush on reconnect

### PracticeScreen

**Session construction moves into an explicit `startSession(mode)`.** Today the
deck is built in `useEffect(..., [wordsQuery.data])`. Optimistic updates change
that object's identity on every answer, which would re-shuffle the deck and reset
the index mid-session. (This is already a latent bug: `useFocusEffect`'s refetch
re-shuffles the deck every time the user returns to the screen.) After this change,
the deck is built only on: first successful load, "Devam et" from the batch
interstitial, "Yine de pratik yap", a mode change, and regaining focus with no
active session. Cache updates no longer rebuild it.

**The deck depends on the mode:**

- Flashcard → **all** saved words, shuffled. It is a study tool, not a test; locking
  it to the due list would mean waiting 35 days to look at a word you have
  mastered. It also stays usable when nothing is due.
- The four auto-graded modes → `words.filter(isDue)`, shuffled.

`BATCH_SIZE = 15` and the existing batch-interstitial flow are unchanged; they now
apply to whichever deck the mode selected.

**Grading.** Every answer in the four auto-graded modes fires
`reviewMutation.mutate({ word, outcome })`. Two concerns that are currently tangled
get separated: `handleAutoGrade` today swallows Matching's `stillLearning` with an
early return, because a miss there does not retire the card. That early return must
only govern the batch-completion set — a Matching miss is still a wrong answer and
must be recorded as `stillLearning`.

**Flashcard is not graded.** It writes nothing and never calls `reviewMutation`,
with a persistent note on screen: *"Çalışma modu — ilerlemeni etkilemez."*

Self-grading buttons ("Biliyorum" / "Hatırlamadım") were considered and rejected.
They measure recognition, not recall — seeing the answer and judging that you knew
it is exactly the self-deception the 3-correct learning phase exists to prevent.
They would also be throwaway work: spec 2 turns Flashcard into the ungraded
introduction step for level-0 words.

Instead, the card shows the word's state underneath it, drawn from data this spec
already produces:
- level 0 → "Öğrenme 2/3"
- level ≥ 1 → "Seviye 4 · sonraki tekrar 6 gün sonra"

**Empty state** (no due words; applies to the four test modes only, since Flashcard
always has a deck): an informational message with the wait until the earliest
`nextReviewAt` — *"Şu an tekrar edilecek kelime yok. Sonraki tekrar ~3 saat
sonra."* — plus two buttons, **Yine de pratik yap** and **Kelimelerim'e dön**.

**Free practice.** "Yine de pratik yap" starts a session over all words in the
selected test mode with grading disabled — `reviewMutation` is never called, so
nothing is written and no schedule moves. A persistent note reads *"Serbest pratik
— ilerlemeni etkilemez."* This is the simple equivalent of Memrise's Speed Review:
the user can drill as much as they want without corrupting the spacing.

## Edge cases

- **Word deleted mid-session.** The review call returns 404; the error is swallowed
  and the word list is invalidated.
- **Device clock skew.** `isDue` compares against device time, so a skewed clock
  shows words slightly early or late. `nextReviewAt` is always produced by the
  server, so skew cannot accumulate into permanent corruption. Accepted.
- **Rapid consecutive answers.** Mutations are sent in order and the server
  re-reads state before each write, so level changes accumulate correctly.
- **Free practice must write nothing.** Explicitly verified.
- **First launch offline.** Unchanged from today: with no cached list there is
  nothing to practice, and the existing empty state covers it.

## Mobile verification (manual)

1. Add a new word → it appears in the deck immediately and shows "Öğrenme 0/3".
2. Answer it correctly three times in a graded mode → the indicator advances 1/3,
   2/3, then the word graduates and shows "Seviye 1 · sonraki tekrar 1 saat sonra"
   and drops out of the due deck.
3. Answer a graduated word incorrectly → it returns to level 1 with a 1-hour
   interval.
4. Miss a card in Matching → confirm it is recorded as `stillLearning` and that the
   batch does not count it as complete.
5. Exhaust the due deck → the empty state appears with a correct wait estimate.
6. Use "Yine de pratik yap" → answer several questions, return, and confirm no
   levels or review times changed.
7. Open Flashcard with nothing due → confirm it still shows all words with their
   progress indicators.
8. Answer several questions in a row → confirm the deck does not re-shuffle and the
   card index does not reset.
