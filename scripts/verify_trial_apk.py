#!/usr/bin/env python3
"""Fail if the trial APK contains any textbook, a duplicate sample or the wrong five-page PDF."""
from hashlib import sha256
from pathlib import Path
import re
import sys
from zipfile import ZipFile


def verify(apk: Path) -> None:
    reference = Path(__file__).resolve().parents[1] / 'app/src/trial/assets/books/reader-sample.pdf'
    asset = 'assets/books/reader-sample.pdf'
    with ZipFile(apk) as archive:
        pdfs = [name for name in archive.namelist() if name.lower().endswith('.pdf')]
        books = [name for name in archive.namelist() if name.startswith('assets/books/') and not name.endswith('/')]
        if pdfs != [asset] or books != [asset]:
            raise SystemExit(f'Expected only {asset}; found PDFs {pdfs} and book assets {books}')
        sample = archive.read(asset)
        if sample != reference.read_bytes() or not sample.startswith(b'%PDF-'):
            raise SystemExit('The packaged sample differs from the reviewed trial PDF')
        # The checked-in sample uses regular page dictionaries, not compressed object streams.
        if len(re.findall(rb'/Type\s*/Page\b', sample)) != 5:
            raise SystemExit('The trial sample must have exactly five pages')
        if 'assets/licenses/Tajawal-OFL.txt' not in archive.namelist():
            raise SystemExit('Missing shared font license')
    size = apk.stat().st_size
    print(f'{apk.name}: {size:,} bytes ({size / 1_000_000:.2f} MB); 1 PDF, 5 pages, no textbooks.')
    print(f'SHA-256: {sha256(apk.read_bytes()).hexdigest()}')


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python3 scripts/verify_trial_apk.py path/to/app-trial.apk')
    verify(Path(sys.argv[1]))
