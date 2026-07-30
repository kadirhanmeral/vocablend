# Random Words Endpoint

Date: 2026-07-30
Status: Approved (design), not yet implemented

## Purpose

The mobile app is adding practice game modes (multiple choice, matching,
fill-in-the-blank) that need "distractor" words — wrong answers to show
alongside the correct one. When a device's own due/saved word list is too
small to supply enough distractors, the game needs to draw extra words from
somewhere else.

This spec adds a single new endpoint that returns a random sample of words
from the global `words` collection (the shared dictionary cache already
populated by `WordRepository`/`WordEntity`), independent of any device.

## Non-goals

- No changes to `DeviceWordController`, `DeviceWordEntity`, or SRS scheduling.
- No pagination, sorting, or filtering beyond exclusion — this is a single
  best-effort random sample, not a general-purpose words listing API.
- No caching layer — `$sample` is cheap enough at current data volumes.
- No authentication — matches the existing no-auth, deviceId-less pattern for
  data that isn't device-specific.

## API

New endpoint, new controller (this data isn't device-scoped, so it doesn't
belong on `DeviceWordController`):

```
GET /api/words/random?count=3&exclude=apple,banana
```

- `count` (required, positive int) — how many words to return.
- `exclude` (optional, comma-separated) — word strings to exclude from the
  sample (typically the correct answer plus any distractors already shown in
  the same session, so the game doesn't repeat or reveal the answer).

Response: `200 OK` with a JSON array of `WordEntity`-shaped objects (`word`,
`meaningEn`, `meaningTr`, `examples`) — the same shape already returned by
existing word-related responses, so the mobile client can reuse its existing
`WordEntity` type.

If fewer than `count` words remain after exclusion, return as many as are
available (including zero) — no error. The caller (mobile) is responsible for
handling a short/empty result (see mobile spec).

## Backend implementation

### Repository

New method on `WordRepository`:

```java
List<WordEntity> findRandomExcluding(List<String> excludeWords, int count);
```

Implemented via a MongoDB aggregation pipeline:

```
db.words.aggregate([
  { $match: { word: { $nin: excludeWords } } },
  { $sample: { size: count } }
])
```

### Controller

New `WordController` (plural `/api/words`, distinct from the singular-device
`/api/device-words/{deviceId}` routes):

```java
@GetMapping("/api/words/random")
public List<WordEntity> getRandomWords(
    @RequestParam int count,
    @RequestParam(required = false) List<String> exclude
)
```

- `exclude` defaults to an empty list when omitted.
- `count <= 0` returns `400 Bad Request` (standard Spring validation).

### Testing

- Repository-level test: seed a handful of `WordEntity` docs, confirm
  `findRandomExcluding` never returns an excluded word, and returns `min(count,
  available)` results.
- Controller test: `count` omitted → `400`; `exclude` omitted → behaves as
  empty exclusion list; happy path returns correctly shaped JSON.

## Edge cases

- Global `words` collection has fewer documents than `count` (small/early
  dataset): return all non-excluded words, no padding or duplication.
- `exclude` list happens to cover the entire collection: return an empty list,
  not an error.
