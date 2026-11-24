#!/usr/bin/env python3
"""Verify pinned PDFs and checked-in study PDFs are present in a full APK."""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import sys
from zipfile import BadZipFile, ZipFile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
from download_study_assets import SOURCE_ASSET_ROOT, git_blob_sha1, load_manifest  # noqa: E402

CHUNK_SIZE = 1024 * 1024


def apk_blob_sha1(archive: ZipFile, asset_path: str, expected_size: int, expected_blob: str) -> None:
    zip_path = f"assets/{asset_path}"
    matching = [info for info in archive.infolist() if info.filename == zip_path]
    if len(matching) != 1:
        raise ValueError(f"Expected exactly one {zip_path!r} in the APK; found {len(matching)}")
    info = matching[0]
    if info.file_size != expected_size:
        raise ValueError(
            f"{zip_path}: expected {expected_size:,} bytes, APK contains {info.file_size:,}"
        )

    digest = hashlib.sha1()
    digest.update(f"blob {expected_size}\0".encode("ascii"))
    with archive.open(info) as stream:
        header = stream.read(5)
        if header != b"%PDF-":
            raise ValueError(f"{zip_path}: missing PDF header")
        digest.update(header)
        while chunk := stream.read(CHUNK_SIZE):
            digest.update(chunk)
    if digest.hexdigest() != expected_blob:
        raise ValueError(f"{zip_path}: APK bytes do not match the pinned Git blob")


def verify(apk_path: Path) -> int:
    _, entries = load_manifest()
    expected: dict[str, tuple[int, str]] = {
        str(entry["path"]): (int(entry["size"]), str(entry["git_blob_sha"]))
        for entry in entries
    }

    checked_in_count = 0
    checked_in_study = SOURCE_ASSET_ROOT / "books" / "study"
    if checked_in_study.exists():
        for source in sorted(checked_in_study.rglob("*.pdf")):
            relative = source.relative_to(SOURCE_ASSET_ROOT).as_posix()
            if relative in expected:
                raise ValueError(f"Study asset appears both in the manifest and checked-in assets: {relative}")
            with source.open("rb") as stream:
                if stream.read(5) != b"%PDF-":
                    raise ValueError(f"Checked-in study asset is not a PDF: {source}")
            expected[relative] = (source.stat().st_size, git_blob_sha1(source))
            checked_in_count += 1

    try:
        with ZipFile(apk_path) as archive:
            for path, (size, blob) in sorted(expected.items()):
                apk_blob_sha1(archive, path, size, blob)
    except (BadZipFile, OSError) as exc:
        raise SystemExit(f"Cannot inspect APK {apk_path}: {exc}") from exc

    print(
        f"Verified {len(entries)} pinned manifest PDFs and {checked_in_count} checked-in study PDFs "
        f"({len(expected)} total) in {apk_path}; all are valid and byte-identical."
    )
    return len(expected)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True, help="Full APK to inspect")
    args = parser.parse_args()
    verify(args.apk)
