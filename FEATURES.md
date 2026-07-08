# Movie Scanner — Features

## Scan

- Capture a barcode photo, then a cover photo (or skip barcode / manual cover entry).
- Identify the movie via barcode lookup and/or cover recognition, then review and add to the list.

## Scan Bulk

- **Scan Bulk** tab: opens the bulk queue when unprocessed pairs remain from an earlier session; otherwise opens bulk capture. Capture barcode and cover photos for many movies in sequence without stopping to identify each one.
- Header shows **Barcode N** / **Cover N** for the current pair.
- **Done** (top right) is always available to open the bulk queue, including items from earlier sessions.
- **Set Location** (map-pin icon beside Done) opens a prompt to name the shelf or bin for this batch; the saved name appears as a link beside the icon and pre-fills the Location field on each review form during bulk processing.
- Queue table: ID, Timestamp, Barcode, Cover (tap for preview), Processed? (checkmark or timer), delete (trash icon per row). Rows are sorted by ID ascending; **Clear Done** (top right) removes all processed rows and their images.
- **Process** runs each unprocessed pair through the same identify → review → add flow as single Scan; **Scan** (to its right) returns to bulk capture to add more pairs.
- During processing, a spinner shows the current item number; Review puts **Stop Processing** in the top bar, plus **Show Cover** and **Rescan** (opens the camera to replace the current pair, re-identify, and return to review on the same queue item).

## List

- Browse saved movies, export/share as CSV.

## Settings

- Configure TMDB and LLM API keys required for scanning.
