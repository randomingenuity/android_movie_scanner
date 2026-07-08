# Movie Scanner — Features

## Scan

- Capture a barcode photo, then a cover photo (or skip barcode / manual cover entry).
- Identify the movie via barcode lookup and/or cover recognition, then review and add to the list.
- Review shows **Open Matched** above Title when TMDB returned a single match; tap opens that title on TMDB in the default browser.

## Scan Bulk

- **Scan Bulk** tab: opens the bulk queue when unprocessed pairs remain from an earlier session; otherwise opens bulk capture. On first bulk entry each session (queue or capture), if batch disc type or location is unset, a prompt offers to set them before continuing.
- Header shows **Barcode N** / **Cover N** for the current pair.
- **Done** (top right) is always available to open the bulk queue, including items from earlier sessions.
- **Disc Type** (left of Location when no batch disc type is set) opens a picker with the same options as the review form; once saved, the disc type appears as a clickable link and pre-fills the Disc Type field on each review form during bulk processing.
- **Location** (beside Done when no batch location is set) opens a prompt to name the shelf or bin for this batch; once saved, the location name appears as a clickable link in place of the button and pre-fills the Location field on each review form during bulk processing.
- Queue table: ID, Timestamp, Barcode, Cover (tap for preview), Status (checkmark when reviewed, orange download while recognizing, green timer when recognition is ready, yellow timer while waiting), delete (trash icon per row). Rows are sorted by ID ascending; **Clear Done** (top right) removes all processed rows and their images.
- After each barcode/cover pair is saved, recognition (LLM + TMDB) runs automatically in the background; results are stored on the queue row as JSON.
- **Process** walks unprocessed pairs through review → add using the stored recognition data (no loading screen); **Scan** (to its right) returns to bulk capture to add more pairs (new pairs are recognized automatically as they are saved).
- During processing, a spinner shows the current record ID; Review puts **Stop Processing** in the top bar, plus **Show Cover** and **Rescan** (opens the camera to replace the current pair, re-identify, and return to review on the same queue item).

## List

- Browse saved movies, export/share as CSV.
- Tap a row to open a detail overlay with every saved field; close with the corner icon, the bottom **Close** button, or a tap outside the overlay.

## Settings

- Configure TMDB and LLM API keys required for scanning.
