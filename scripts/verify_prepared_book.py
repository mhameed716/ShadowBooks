#!/usr/bin/env python3
"""Verify the prepared Arabic reading asset from source or generated full-build assets.
Standard library only. PDF rendering and editorial tests are separate.
"""
import argparse
from hashlib import sha256
import json
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets'
GENERATED_ASSETS = ROOT / 'app/build/generated/study-assets'
SOURCE = 'books/english-student-book.pdf'
PDF = 'reviewed/pupil6-ar.pdf'
INDEX = 'reviewed/pupil6-ar.json'
LICENSE = 'licenses/Noto-Naskh-OFL.txt'
RETIRED_BYTES = 95_612_901  # Raw bytes of the previous three language files, not APK size.


def reject_retired_assets(names):
    for name in names:
        leaf = name.rsplit('/', 1)[-1].lower()
        if (leaf in {'model.bin', 'sentencepiece.model', 'shared_vocabulary.txt', 'libenar.so', 'libshadowbooks_enar.so'}
                or leaf.endswith(('.argosmodel', '.traineddata')) or leaf.startswith(('libctranslate', 'libsentencepiece'))
                or name.startswith(('assets/translation/', 'assets/mlkit'))):
            raise ValueError(f'Unexpected retired language asset: {name}')


def verify(apk=None):
    reference = json.loads((ROOT/'editorial/english/pupil6/007-lost.json').read_text(encoding='utf-8'))
    reviewed_dir = ASSETS/'reviewed'
    reviewed_names = {p.name for p in reviewed_dir.iterdir() if p.is_file()}
    if reviewed_names - {Path(PDF).name, Path(INDEX).name} or Path(INDEX).name not in reviewed_names:
        raise ValueError('Unexpected checked-in contents of reviewed/')
    prepared_pdf = ASSETS/PDF
    if not prepared_pdf.is_file():
        prepared_pdf = GENERATED_ASSETS/PDF
    if not prepared_pdf.is_file():
        raise ValueError('Prepared Arabic PDF is missing; run scripts/download_books.sh first')
    files = {
        name: (prepared_pdf if name == PDF else ASSETS/name).read_bytes()
        for name in (SOURCE, PDF, INDEX, LICENSE)
    }
    manifest = json.loads(files[INDEX])
    if manifest['version'] != 1 or sha256(files[SOURCE]).hexdigest() != reference['sourceSha256'] or (
        manifest['sourceSha256'] != reference['sourceSha256'] or sha256(files[PDF]).hexdigest() != manifest['arabicSha256']
    ):
        raise ValueError('Edition or prepared PDF does not match its index')
    pages = manifest['pages']
    keep = {2, 3, 5, 55, 69, 81}
    if len(pages) != 81 or {n for n, s in enumerate(pages, 1) if s is None} != keep:
        raise ValueError('Incomplete page index')
    if any(not isinstance(s, str) or not s.strip() for s in pages if s is not None):
        raise ValueError('Invalid page text')
    if apk:
        with ZipFile(apk) as archive:
            names = archive.namelist()
            reject_retired_assets(names)
            reviewed = sorted(n for n in names if n.startswith('assets/reviewed/') and not n.endswith('/'))
            if reviewed != sorted(['assets/'+PDF, 'assets/'+INDEX]):
                raise ValueError('Unexpected prepared assets in full APK')
            for name, expected in files.items():
                if archive.read('assets/'+name) != expected:
                    raise ValueError(f'{name}: packaged bytes differ from the reviewed file')
            stored = sum(archive.getinfo('assets/'+f).compress_size for f in (PDF, INDEX))
        print(f'Full APK: {apk.stat().st_size:,} bytes. Its two prepared entries occupy {stored:,} bytes before ZIP overhead.')
    total = len(files[PDF]) + len(files[INDEX])
    print(f'81 sheets (75 translated, 6 retained); PDF: {len(files[PDF]):,}; text index: {len(files[INDEX]):,} bytes.')
    print(f'Prepared language data: {total:,} bytes ({total/1_000_000:.3f} MB; {total/(1<<20):.3f} MiB). '
          f'{RETIRED_BYTES-total:,} bytes smaller than the retired {RETIRED_BYTES:,}-byte pack '
          f'({100*(1-total/RETIRED_BYTES):.2f}% smaller). Excludes the original book, font notice and APK overhead.')
    return total


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, help='Optional actual full APK to check/measure')
    verify(parser.parse_args().apk)
