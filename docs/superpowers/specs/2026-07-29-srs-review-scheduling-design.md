# Spaced Repetition (SRS) Review Scheduling

Date: 2026-07-29
Status: Approved (design), not yet implemented

## Purpose

Vocablend currently tracks practice results ("Got it" / "Still learning") only as a
local tally in the mobile app's `AsyncStorage` (`practiceStorage.ts`), and the
Practice screen shows a randomly shuffled deck of *every* saved word every time.
There is no notion of "this word is due for review" — users re-see words they
already know well just as often as words they're struggling with.

This spec introduces a Leitner-box-based spaced repetition system (SRS), with
scheduling state stored server-side (MongoDB) instead of on-device. The Practice
screen switches from "shuffle everything" to "review what's due today."

## Non-goals

- No SM-2 / variable ease-factor algorithm — fixed Leitner box intervals only.
- No migration of existing `device_words` documents — the schema change is
  applied directly, no backward-compatible dual-read path.
- No change to `MyWordsListScreen` — it continues to show the full word list via
  the existing `/list` endpoint, with no due/box information surfaced there.
- No "practice anyway when nothing is due" fallback mode — the empty state is
  informational only.

## Backend

### Data model

`DeviceWordEntity.words` changes from `List<String>` to `List<WordProgress>`:

```java
public class WordProgress {
    private String word;
    private int boxLevel;          // 1-6
    private LocalDate nextReviewDate;
}
```

Box level → interval mapping (fixed, not configurable):

| Box | Interval |
|-----|----------|
| 1   | 1 day    |
| 2   | 3 days   |
| 3   | 7 days   |
| 4   | 16 days  |
| 5   | 35 days  |
| 6   | 90 days (max, stays at 6) |

Transition rules, applied on a review outcome:
- `gotIt` → `boxLevel = min(boxLevel + 1, 6)`
- `stillLearning` → `boxLevel = 1`
- `nextReviewDate = LocalDate.now() + interval(boxLevel)` after the transition

New words are created with `boxLevel = 1` and `nextReviewDate = LocalDate.now()`,
so they're immediately due.

No migration is written for existing `device_words` documents — since there is no
production data to preserve, the schema change applies directly to new documents
going forward.

### API changes

- `POST /api/device-words/{deviceId}?word=...` — behavior unchanged from the
  caller's perspective; internally, adding a word now creates a `WordProgress`
  entry (box 1, due today) instead of appending a bare string.
- `GET /api/device-words/{deviceId}/list` — unchanged. Still returns all
  `WordEntity` content for the device's saved words, regardless of due status.
- `GET /api/device-words/{deviceId}/due` (new) — returns the subset of the
  device's words where `nextReviewDate <= today`, each combined with its word
  content (`meaningEn`, `meaningTr`, `examples`) and its `boxLevel` /
  `nextReviewDate`, so the mobile app can render both the flashcard and a
  "Box X/6 · next review in Nd" indicator without a second round trip.
- `POST /api/device-words/{deviceId}/{word}/review?outcome=gotIt|stillLearning`
  (new) — applies the box transition above to that word's `WordProgress` and
  returns the updated `boxLevel` / `nextReviewDate`.
- `DELETE /api/device-words/{deviceId}/{word}` — unchanged externally; internally
  removes the matching `WordProgress` entry instead of a bare string.

Existing helpers like `WordService.getWordListByWords(List<String>)` keep their
signature; callers extract `word` strings from `WordProgress` entries before
calling them.

### Testing

- Unit tests for the box transition logic (`gotIt` / `stillLearning` cases,
  including the box-6 ceiling).
- Service-level test for the `/due` filtering logic (words with past/today
  `nextReviewDate` included, future-dated words excluded).

## Mobile

- `src/lib/practiceStorage.ts` is deleted — review progress no longer lives in
  `AsyncStorage`.
- API client gains `getDueWords(deviceId)` and `postReview(deviceId, word, outcome)`.
- `PracticeScreen` fetches the due-word list (new hook, e.g. `useDuePractice`)
  instead of the full word list. "Shuffle" still reorders the fetched deck.
  "Got it" / "Still learning" call the review mutation; the returned
  `boxLevel` / `nextReviewDate` replace the old "Got it: N · Still learning: N"
  text with a "Box X/6 · next review in Nd" indicator for the current card.
- Empty state (no words due today): informational message only —
  "🎉 Bugün tekrar edilecek kelime yok! Yarın yeni kelimeler hazır olacak." —
  with a button back to My Words. No "practice anyway" fallback.
- `MyWordsListScreen` is unchanged.

### Edge cases

- A word answered mid-session is not removed from the current deck — same as
  today's behavior, where the user advances manually via the arrow buttons.
  Only its box/next-review indicator updates immediately after answering.
- Due-date comparison uses the backend's local date (`LocalDate.now()`);
  timezone edge cases around midnight are accepted as out of scope for this
  iteration.

## Testing (mobile)

Manual verification via Expo: add a word, practice it, confirm box progression
on "Got it" vs reset on "Still learning", and confirm the empty-state message
appears once all due words for the day are exhausted.
