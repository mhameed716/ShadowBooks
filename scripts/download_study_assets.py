#!/usr/bin/env python3
"""Download pinned study PDFs into ignored generated assets for full APK builds.

The GitHub source branch stores these large files as ordinary Git blobs. The active
session branch intentionally keeps only their small manifest, so a clean checkout
can prepare them immediately before a full build without adding the blobs to Git or
Arena's workspace snapshot. The APK still embeds the PDFs for offline reading.
"""
from __future__ import annotations

import hashlib
import json
import os
import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "scripts" / "study-assets-manifest.json"
SOURCE_ASSET_ROOT = ROOT / "app" / "src" / "main" / "assets"
ASSET_ROOT = ROOT / "app" / "build" / "generated" / "study-assets"
REPOSITORY = "mhameed716/ShadowBooks"
CHUNK_SIZE = 1024 * 1024


def git_blob_sha1(path: Path) -> str:
    """Return Git's content-addressed blob ID without writing an object to .git."""
    size = path.stat().st_size
    digest = hashlib.sha1()
    digest.update(f"blob {size}\0".encode("ascii"))
    with path.open("rb") as stream:
        while chunk := stream.read(CHUNK_SIZE):
            digest.update(chunk)
    return digest.hexdigest()


def is_valid_pdf(path: Path, expected_size: int, expected_blob: str) -> bool:
    if not path.is_file() or path.stat().st_size != expected_size:
        return False
    with path.open("rb") as stream:
        if stream.read(5) != b"%PDF-":
            return False
    return git_blob_sha1(path) == expected_blob


def load_manifest() -> tuple[str, list[dict[str, object]]]:
    try:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise SystemExit(f"Cannot read study asset manifest {MANIFEST_PATH}: {exc}") from exc

    commit = manifest.get("source_commit")
    entries = manifest.get("files")
    if (
        not isinstance(commit, str)
        or len(commit) != 40
        or any(char not in "0123456789abcdef" for char in commit.lower())
        or not isinstance(entries, list)
        or not entries
    ):
        raise SystemExit("Study asset manifest has an invalid commit or empty file list")
    for entry in entries:
        if not isinstance(entry, dict):
            raise SystemExit("Study asset manifest contains a malformed entry")
        rel = Path(str(entry.get("path", "")))
        sha = str(entry.get("git_blob_sha", ""))
        size = entry.get("size")
        if (
            rel.is_absolute()
            or not rel.parts
            or ".." in rel.parts
            or rel.suffix.lower() != ".pdf"
            or len(sha) != 40
            or any(char not in "0123456789abcdef" for char in sha.lower())
            or not isinstance(size, int)
            or isinstance(size, bool)
            or size <= 0
        ):
            raise SystemExit(f"Invalid study asset manifest entry: {entry!r}")
    return commit, entries


def download_entry(commit: str, entry: dict[str, object], token: str | None) -> None:
    relative = Path(str(entry["path"]))
    expected_size = int(entry["size"])
    expected_blob = str(entry["git_blob_sha"])
    checked_in_asset = SOURCE_ASSET_ROOT / relative
    target = ASSET_ROOT / relative

    # Reuse any pinned PDF already present in src/main/assets after verifying it,
    # instead of downloading or creating a duplicate APK path.
    if checked_in_asset.exists():
        if is_valid_pdf(checked_in_asset, expected_size, expected_blob):
            print(f"Already included and verified: {relative}")
            return
        raise SystemExit(
            f"A checked-in asset conflicts with the pinned manifest; refusing to overwrite it: "
            f"{checked_in_asset}"
        )

    if target.exists():
        if is_valid_pdf(target, expected_size, expected_blob):
            print(f"Already verified: {relative}")
            return
        raise SystemExit(
            f"Refusing to overwrite an existing generated asset that fails verification: {target}"
        )

    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(f".{target.name}.{os.getpid()}.part")
    if temporary.exists():
        raise SystemExit(f"Refusing to overwrite an existing partial download: {temporary}")

    # Manifest paths are relative to app/src/main/assets, while the Contents API
    # paths are relative to the repository root.
    repository_path = f"app/src/main/assets/{relative.as_posix()}"
    encoded_path = quote(repository_path, safe="/")
    url = (
        f"https://api.github.com/repos/{REPOSITORY}/contents/{encoded_path}"
        f"?ref={commit}"
    )
    headers = {
        "Accept": "application/vnd.github.raw",
        "User-Agent": "ShadowBooks-study-asset-preparer",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    request = Request(url, headers=headers)
    print(f"Downloading {relative} ({expected_size:,} bytes) ...")
    try:
        with urlopen(request, timeout=180) as response:
            if response.headers.get_content_type() == "application/json":
                raise SystemExit(
                    "GitHub returned JSON instead of the raw PDF. Check the API media type and token."
                )
            with temporary.open("xb") as output:
                while chunk := response.read(CHUNK_SIZE):
                    output.write(chunk)
    except (HTTPError, URLError, TimeoutError, OSError) as exc:
        raise SystemExit(f"Failed to download {relative}: {exc}") from exc

    if not is_valid_pdf(temporary, expected_size, expected_blob):
        raise SystemExit(
            f"Downloaded asset failed size, PDF-header, or Git-blob verification: {relative}. "
            f"The temporary file was left at {temporary} for inspection."
        )
    if target.exists():
        raise SystemExit(f"Target appeared during download; refusing to replace it: {target}")
    temporary.rename(target)


def main() -> int:
    commit, entries = load_manifest()
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    total_bytes = sum(int(entry["size"]) for entry in entries)
    print(
        f"Preparing {len(entries)} pinned study PDFs ({total_bytes:,} bytes) "
        f"from {commit[:12]} into {ASSET_ROOT.relative_to(ROOT)}"
    )
    for entry in entries:
        download_entry(commit, entry, token)
    print(f"All {len(entries)} manifest PDFs are verified and available for the full APK.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
