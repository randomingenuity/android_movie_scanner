# Movie Scanner

Android app for scanning movie case barcodes and cover art, identifying titles with BYOK LLM providers, matching against TMDB, and exporting a CSV list.

## Requirements

- Android SDK (API 26+)
- JDK 17+
- Three API keys (user-provided in Settings):
  - [Gemini API key](https://aistudio.google.com/apikey) and/or [OpenAI API key](https://platform.openai.com/api-keys)
  - [TMDB API key](https://developer.themoviedb.org/docs/getting-started)

## Build

```bash
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Scan flow

1. **Barcode** (optional): live ML Kit decode or manual capture; Skip allowed
2. **Cover** (required): capture cover photo
3. **Loading**: parallel LLM calls; TMDB search with up to 3 automatic retries
4. **Review**: edit title/year, pick TMDB match, Add / Force Add / Skip
5. Returns to barcode step for the next movie

## Export

- **List** tab: share icon exports CSV via system share sheet
- **Loading** screen: overflow menu → Share list (while waiting on TMDB)

Default filename: `movies_YYYY-MM-DD_HHmmss.csv`
