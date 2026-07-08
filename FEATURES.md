# Movie Scanner — Features

## Scan

- Capture a barcode photo, then a cover photo (or skip barcode / manual cover entry).
- Identify the movie via barcode lookup and/or cover recognition, then review and add to the list.

## Scan Bulk

- **Scan Bulk** tab: opens the bulk queue when unprocessed pairs remain from an earlier session; otherwise opens bulk capture. Capture barcode and cover photos for many movies in sequence without stopping to identify each one.
- Header shows **Barcode N** / **Cover N** for the current pair.
- **Done With Scanning** (top right) is always available to open the bulk queue, including items from earlier sessions.
- Queue table: ID, Timestamp, Barcode, Cover (tap for preview), Processed? (checkmark or timer), delete (trash icon per row). Processed rows appear after pending rows; **Clear Done** below them removes all processed rows and their images.
- **Process** runs each unprocessed pair through the same identify → review → add flow as single Scan; **Scan** (to its right) returns to bulk capture to add more pairs.
- During processing, a spinner shows the current item number; Review adds **Show Cover** and **Stop Processing** (closes review without saving and halts the queue).

## List

- Browse saved movies, export/share as CSV.

## Settings

- Configure TMDB and LLM API keys required for scanning.
