# Movie Scanner

Android app for scanning movie case barcodes and cover art, identifying titles with BYOK LLM providers, matching against TMDB, and maintaining an exportable movie list.

## Requirements

- Android SDK (API 26+)
- JDK 17+
- API keys (user-provided in Settings):
  - [Gemini API key](https://aistudio.google.com/apikey) and/or [OpenAI API key](https://platform.openai.com/api-keys) — at least one LLM key is required
  - [TMDB API key](https://developer.themoviedb.org/docs/getting-started)

Keys are stored in encrypted preferences on device and are not backed up. Scanning requires an internet connection.

## Build

```bash
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Navigation

Bottom tabs: **Scan**, **List**, **Settings**.

Scan requires a valid TMDB key and at least one LLM key. Settings validates each key on save.

## Settings

- **Gemini** and **OpenAI** API keys with per-field validation
- **Model pickers** for each configured LLM provider
- **TMDB** API key validation and optional **language override**
- **Preferred LLM** (Gemini or OpenAI) with automatic fallback to the other provider when configured and the primary call fails

## Scan flow

1. **Barcode** (optional): camera opens on the barcode step. Take a photo; ML Kit and ZXing decode UPC/EAN/ISBN from the image. **Skip** moves on without a barcode.
2. **Cover** (required): after barcode capture or skip, the step switches to cover. Take a cover photo, or use **Manual Entry** to type title/year and skip cover recognition.
3. **Loading**: identifies the movie and searches TMDB (see below).
4. **Review**: confirm or edit results, then **Add** / **Replace**, **Force Add**, or **Skip**.
5. Returns to the barcode step for the next movie.

Failed barcode decode stays on the barcode step with an error (no silent skip to cover).

## Loading / identification

When a barcode was captured:

1. **Finding movie with barcode** — LLM looks up title/year from the barcode (and barcode image when available).
2. **Searching movie in TMDB** — if TMDB returns matches, cover recognition is skipped and Review opens immediately.
3. If barcode lookup or TMDB search does not find a match, the app falls back to **Extracting title from cover image** and continues from there.

On the cover path, cover and barcode LLM calls may run in parallel when both are available. Cover title/year take priority; barcode fills gaps. TMDB search retries automatically up to 3 times.

Loading messages include **No internet connection** / **Offline scanning is not supported** when offline. Cover read failures offer **Retake cover**; TMDB failures offer retry.

## Review

- Summary of detected cover title and how the barcode was used for title/year
- Editable **barcode**, **title**, and **year** fields (barcode field auto-focuses; newlines are stripped from barcode input)
- Note showing whether the LLM identified the barcode
- **Barcode suggests** chip when barcode and cover disagree (tap to apply)
- **Re-search TMDB** after editing title/year
- TMDB result list with posters; pick the correct match
- **Add** or **Replace** when the movie is already in the list (matched by TMDB id)
- **Force Add** when no TMDB match is selected (title + year only); also replaces duplicates matched by title and year
- **Skip** discards the scan; back gesture prompts to discard

## List

- Movies persist in a local **Room** database (insertion order)
- Poster, title, year, and barcode (ISBN/UPC label) per row
- **Unmatched** badge for force-added entries
- Swipe to delete an item
- Tap a matched row to open its TMDB page in the browser; tap an unmatched row for details
- **Clear list** with confirmation
- Share icon exports CSV via the system share sheet

## Export

- **List** tab: share icon
- **Loading** screen: overflow menu → Share list (while waiting on TMDB)

Default filename: `YYYYmmdd-HHMM_movies.csv` (for example `20260607-1430_movies.csv`).

CSV columns: `title`, `year`, `barcode`, `tmdb_url`, `tmdb_id`, `poster_url`
