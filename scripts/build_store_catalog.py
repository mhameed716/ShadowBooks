#!/usr/bin/env python3
"""Build the ShadowBooks store catalog from staged content files.

The store is a fixed GitHub Release (tag ``store``). Its ``catalog.json`` is the
single source of truth the app reads: every entry names a content file by its
destination path inside the app (``books/...``), its exact size, its SHA-256 and
a revision. The app files each download under exactly that destination, so the
placement of every file is decided here on GitHub, never guessed by the app.

Inputs (all paths relative to the repository root):
  * ``app/build/store-content/`` — staged PDFs mirroring asset paths
    (``books/<textbook>.pdf``, ``books/study/summaries/...``, ``books/study/exams/...``).
  * ``store/sources.json`` — link-based intake: entries declare a URL and the
    destination the file belongs to; this script downloads and verifies them.
  * ``store/manual-entries.json`` — files uploaded to the store release by hand
    (e.g. attached in a chat session); only their placement is declared here.
    They are verified against the release in publish_store.sh.

Output: ``app/build/store-content/catalog.json`` plus a human-readable report.
Revisions are preserved across runs: an entry whose SHA-256 is unchanged keeps
its revision (fetched from the currently published catalog); a changed file
bumps it, which is how installed apps learn an update exists.
"""
from __future__ import annotations

import hashlib
import json
import subprocess
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAGING = ROOT / "app" / "build" / "store-content"
SOURCES_PATH = ROOT / "store" / "sources.json"
MANUAL_PATH = ROOT / "store" / "manual-entries.json"
CATALOG_URL = (
    "https://github.com/mhameed716/ShadowBooks/releases/download/store/catalog.json"
)


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def asset_name(dest: str) -> str:
    """Release asset name for a destination.

    GitHub rewrites uploaded asset names, replacing every run of characters
    outside [A-Za-z0-9._-] with dots — which destroyed every Arabic file name
    on the store release (the app asked for the Arabic name, the release held
    a dotted one, and downloads died with HTTP 404). Asset names are therefore
    pure ASCII derived from the destination itself: stable across runs (so
    unchanged files are still skipped) and identical for publisher and app,
    which reads the name from this catalog and never guesses.
    """
    return "f-" + hashlib.sha256(dest.encode("utf-8")).hexdigest() + ".pdf"


def is_pdf(path: Path) -> bool:
    if not path.is_file() or path.stat().st_size <= 1024:
        return False
    with path.open("rb") as stream:
        return stream.read(5) == b"%PDF-"


def load_json(path: Path, default):
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return default


def fetch_previous_revisions() -> dict[str, dict[str, object]]:
    """Map dest -> {sha256, rev} from the published catalog, if one exists."""
    try:
        request = urllib.request.Request(
            CATALOG_URL, headers={"User-Agent": "ShadowBooks-Store-Publish"}
        )
        with urllib.request.urlopen(request, timeout=60) as response:
            catalog = json.loads(response.read().decode("utf-8"))
        return {
            str(entry["dest"]): {
                "sha256": str(entry.get("sha256", "")),
                "rev": int(entry.get("rev", 1)),
            }
            for entry in catalog.get("entries", [])
        }
    except Exception as error:  # First publish or offline: everything is rev 1.
        print(f"No previous catalog ({error}); starting revisions at 1.")
        return {}


def download_sources() -> list[str]:
    """Download every sources.json link into staging; return failure messages."""
    failures: list[str] = []
    sources = load_json(SOURCES_PATH, {"entries": []}).get("entries", [])
    for entry in sources:
        dest = str(entry.get("dest", "")).strip()
        url = str(entry.get("url", "")).strip()
        if not dest.startswith("books/") or not dest.endswith(".pdf") or not url:
            failures.append(f"Invalid source entry skipped: {entry!r}")
            continue
        target = STAGING / dest
        target.parent.mkdir(parents=True, exist_ok=True)
        if is_pdf(target):
            print(f"Source already staged: {dest}")
            continue
        temporary = target.with_suffix(".part")
        print(f"Downloading source {dest} ...")
        result = subprocess.run(
            [
                "curl", "--fail", "--location", "--retry", "3",
                "--retry-all-errors", "--retry-delay", "2",
                "--connect-timeout", "30", "--max-time", "1200",
                url, "--output", str(temporary),
            ],
            check=False,
        )
        if result.returncode != 0 or not is_pdf(temporary):
            failures.append(f"Source download failed or not a PDF: {dest} ({url})")
            temporary.unlink(missing_ok=True)
            continue
        temporary.rename(target)
    return failures


def main() -> int:
    STAGING.mkdir(parents=True, exist_ok=True)
    failures = download_sources()

    pdfs = sorted(
        path for path in STAGING.rglob("*.pdf") if path.is_file()
    )
    if not pdfs:
        print("No staged PDFs found; refusing to publish an empty catalog.")
        return 1

    previous = fetch_previous_revisions()
    entries: list[dict[str, object]] = []
    seen: set[str] = set()
    for pdf in pdfs:
        dest = pdf.relative_to(STAGING).as_posix()
        if not is_pdf(pdf):
            failures.append(f"Staged file is not a valid PDF: {dest}")
            continue
        size = pdf.stat().st_size
        digest = sha256_file(pdf)
        old = previous.get(dest)
        rev = int(old["rev"]) if old and old["sha256"] == digest else (
            int(old["rev"]) + 1 if old else 1
        )
        entries.append(
            {
                "dest": dest,
                "size": size,
                "sha256": digest,
                "rev": rev,
                "asset": asset_name(dest),
            }
        )
        seen.add(dest)

    # Manually uploaded files are not staged locally; their declared placement
    # joins the catalog verbatim (publish_store.sh verifies them on the release).
    manual = load_json(MANUAL_PATH, {"entries": []}).get("entries", [])
    for entry in manual:
        dest = str(entry.get("dest", "")).strip()
        size = entry.get("size")
        digest = str(entry.get("sha256", "")).strip().lower()
        if (
            not dest.startswith("books/")
            or not dest.endswith(".pdf")
            or dest in seen
            or not isinstance(size, int)
            or size <= 0
            or len(digest) != 64
        ):
            failures.append(f"Invalid manual entry skipped: {entry!r}")
            continue
        old = previous.get(dest)
        rev = int(entry.get("rev", 1))
        if old and old["sha256"] == digest:
            rev = int(old["rev"])
        elif old:
            rev = max(rev, int(old["rev"]) + 1)
        entries.append(
            {
                "dest": dest,
                "size": size,
                "sha256": digest,
                "rev": rev,
                "asset": asset_name(dest),
            }
        )
        seen.add(dest)

    entries.sort(key=lambda item: str(item["dest"]))
    catalog = {"version": 1, "entries": entries}
    catalog_path = STAGING / "catalog.json"
    catalog_path.write_text(
        json.dumps(catalog, ensure_ascii=False, indent=1), encoding="utf-8"
    )

    total = sum(int(entry["size"]) for entry in entries)
    print(f"Catalog: {len(entries)} entries, {total / 1_000_000:.1f} MB total.")
    for entry in entries:
        print(f"  {entry['dest']}  {int(entry['size']) / 1_000_000:.1f} MB  rev {entry['rev']}")
    if failures:
        print("\nFAILURES:")
        for failure in failures:
            print(f"  - {failure}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
