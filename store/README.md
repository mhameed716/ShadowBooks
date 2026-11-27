# ShadowBooks store content

The in-app store reads one fixed GitHub Release (tag `store`). Its
`catalog.json` lists every content file with:

- `dest` — where the file belongs inside the app, e.g.
  `books/mathematics.pdf` (library), `books/study/summaries/…` (summaries),
  `books/study/exams/…` (exams). The app files each download under exactly
  this destination; placement is decided here, never guessed by the app.
- `size`, `sha256` — verified by the app before a download is accepted.
- `rev` — bumped automatically when a file's bytes change, so installed
  apps can offer an update.

## Publishing (one command)

Run the **Publish Store Content** workflow (Actions tab → Run workflow, or any
push touching this folder). It collects all textbooks and study PDFs from
their existing sources, rebuilds the catalog, uploads only new/changed files,
and publishes `catalog.json` last. GitHub is the durable source of truth for
content; external links are only a first-intake path.

## Adding content

- **By link**: add an entry to `sources.json`:
  `{"dest": "books/study/summaries/13-…/04-….pdf", "url": "https://…"}`.
  The publisher downloads it, verifies it is a real PDF, and catalogues it.
- **By hand** (e.g. a file attached in a work session): upload the PDF to the
  `store` release, then declare its placement in `manual-entries.json`:
  `{"dest": "…", "size": 123, "sha256": "…"}`. The next publish verifies the
  uploaded asset against the declaration and adds it to the catalog.

Nothing here requires an app build: phones see new or revised files the next
time they open the store.
