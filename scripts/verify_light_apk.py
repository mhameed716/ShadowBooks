#!/usr/bin/env python3
"""Verify the store-based (light) APK bundles only what must stay on-device.

Required: the pupil translation pair, both Quran recitations, and the two
ministry-model guides (students must read those before downloading anything).
Forbidden: every other textbook PDF and study PDF — those ship via the store.
"""
from __future__ import annotations

import argparse
import sys
from zipfile import ZipFile

GUIDES = {
    "assets/books/study/exams/كيف_تستفيد_من_النماذج_الوزارية.pdf",
    "assets/books/study/exams/الفرق_بين_النموذج_وبنك_الأسئلة.pdf",
}

REQUIRED = {
    "assets/reviewed/pupil6-ar.pdf",
    "assets/reviewed/pupil6-ar.json",
    "assets/books/quran-audio/an-nur-abdul-basit-r1.mp3",
    "assets/books/quran-audio/an-nur-islam-sobhi.mp3",
} | GUIDES


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--apk", required=True)
    args = parser.parse_args()

    with ZipFile(args.apk) as archive:
        names = set(archive.namelist())
        sizes = {info.filename: info.file_size for info in archive.infolist()}

    missing = sorted(REQUIRED - names)
    if missing:
        print(f"ERROR: light APK is missing bundled assets: {missing}")
        return 1

    forbidden = sorted(
        name
        for name in names
        if name not in GUIDES
        and (
            name.startswith("assets/books/study/")
            or (name.startswith("assets/books/") and name.endswith(".pdf"))
        )
    )
    if forbidden:
        print("ERROR: light APK still bundles store content:")
        for name in forbidden:
            print(f"  {name} ({sizes.get(name, 0):,} bytes)")
        return 1

    total = sum(sizes[name] for name in REQUIRED)
    print(
        f"Light APK verified: translation, recitations and the two guides bundled "
        f"({total / 1_000_000:.1f} MB), no textbooks or other study PDFs packaged."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
