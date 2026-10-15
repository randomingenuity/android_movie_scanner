# Movie Scanner — Features

## Scan

- Capture a barcode photo, then a cover photo (or skip barcode / manual cover entry).
- Identify the movie via barcode lookup and/or cover recognition, then review and add to the list. Cover recognition always runs (including when barcode lookup finds TMDB) to capture **above-the-title** star billing; on add, billing is fuzzy-matched to TMDB cast (movie credits) so CSV matched column uses TMDB names. When barcode lookup succeeds, title and year on review still come from the barcode path.
- Review always shows **Parameters from** and **Matched** under the cover title; **Matched** shows the TMDB id (linked to TMDB in the default browser). When title and year are known but the match was not loaded yet, review searches TMDB automatically. When neither a barcode nor a cover title could be read from the photos, review shows a warning to enter the movie name and year and tap **Refresh**.
- Movies require **Main Feature Disc Type** and **Edition** selections before **Add** is enabled; **Edition** appears under **Main Feature Disc Type** for movies only. When **Add** (or **Replace**) is disabled, a red reason appears below the Back / Add / Skip row (missing fields, TMDB selection, duplicate list entry, or no changes compared with the saved row). **Replace** / **Force Replace** stay disabled until at least one stored field (disc type, edition, location, barcode, TMDB match, etc.) differs from the list entry.
- **Refresh** below **Title** / **Year** re-runs TMDB search when those fields change or to retry after a search error; it is disabled while results are already in sync.

## Scan Bulk

- **Scan Bulk** tab: opens the bulk queue when unprocessed pairs remain from an earlier session; otherwise opens bulk capture. On first bulk entry each session (queue or capture), if batch disc type or location is unset, a prompt offers to set them before continuing.
- Header shows **Barcode N** / **Cover N** for the current pair. On the barcode step, **Skip** (same as the main Scan tab) goes straight to cover capture; cover-only rows store no barcode image (queue shows **—** instead of a barcode preview) and background recognition uses the cover photo and TMDB only. **Skip** is also available when rescanning a pair to replace only the cover.
- **Exit** (top right, red) returns to the main Scan tab without clearing the bulk queue. **Done** (below **Exit**) opens the bulk queue, including items from earlier sessions.
- **Main Feature Disc Type** (left of Location when no batch disc type is set) opens a picker with the same options as the review form; once saved, the disc type appears as a clickable link and pre-fills the Main Feature Disc Type field on each review form during bulk processing.
- **Location** (beside Done when no batch location is set) opens a prompt to name the shelf or bin for this batch; once saved, the location name appears as a clickable link in place of the button and pre-fills the Location field on each review form during bulk processing.
- Queue table: ID, Timestamp, Barcode, Cover (tap for preview), Status (checkmark when reviewed, orange download while recognizing, green timer when recognition is ready, yellow timer while waiting; blue barcode icon when recognition finished from barcode lookup alone; purple restart-alt icon when barcode lookup did not finish the row—tap to rescan barcode and cover), delete (trash icon per row). Rows whose recognized barcode already exists on the **List** screen appear grayed out (barcode/cover preview and rescan disabled); **Process** skips them; delete stays enabled. Matching uses the saved list UPC after each app launch. Rows are sorted by ID ascending.
- After each barcode/cover pair is saved, recognition (LLM + TMDB) runs automatically in the background; results are stored on the queue row as JSON.
- **Process** walks unprocessed pairs through review → add using the stored recognition data (no loading screen); **Scan** (to its right) returns to bulk capture to add more pairs (new pairs are recognized automatically as they are saved). **Exit** (red, always on the queue footer when not processing) returns to the main Scan tab; when the queue has rows, it asks for confirmation then removes every queue row and its images before leaving.
- During processing, a spinner shows the current record ID; Review puts **Stop Processing** in the top bar, plus **Show Cover** and **Rescan** (opens the camera to replace the current pair, re-identify, and return to review on the same queue item). **Back** (left of **Add**) discards the current item’s unsaved changes and reloads the last movie added to the list for editing.

## List

- Browse saved movies; each row shows TMDB-matched and unmatched **above-the-title** actor lines under the title when stored. **Share List** and **Save List** (download icon to the right of share) prompt for **CSV Only** or **ZIP With Covers** (full catalog CSV plus captured cover images under `covers/` in the ZIP, with a `cover_filename` column in the ZIP CSV). Cover files are named by TMDB ID (`{id}.jpg` for movies, `{id}_{season}.jpg` for TV, or `covers/manual/{capture filename}` when there is no TMDB ID). Covers are saved when items are added or replaced from scan review (not backfilled for older list rows). CSV columns include **above_the_title_actor_names_matched**, **above_the_title_actor_tmdb_ids_matched**, and **above_the_title_actors_unmatched**; matched names use TMDB cast spelling and IDs from fuzzy match at add time (same scan order in both matched columns).
- **Location** filter at the top defaults to **(All)**; other choices are each distinct saved location plus **UNLOCATED** for blank locations, with an item count suffix (e.g. `Shelf A (3)`).
- Long lists are paginated (25 rows per page) with previous/next controls and a page summary (e.g. `Page 2 of 5`, `26–50 of 120`).
- Tap a row to open a detail overlay with saved catalog fields (e.g. **Season** only for TV, **Edition** only for movies, matching the review form); close with the corner icon, the bottom **Close** button, or a tap outside the overlay.

## Settings

- Configure TMDB and LLM API keys required for scanning.
