#!/usr/bin/env python3
"""Verify the full APK includes both bundled Surah An-Nur MP3 recitations."""
from argparse import ArgumentParser
from pathlib import Path
from zipfile import BadZipFile, ZipFile

ASSETS = (
    'assets/books/quran-audio/an-nur-abdul-basit-r1.mp3',
    'assets/books/quran-audio/an-nur-islam-sobhi.mp3',
)


def is_mp3_header(header: bytes) -> bool:
    return header.startswith(b'ID3') or (
        len(header) >= 2 and header[0] == 0xFF and (header[1] & 0xE0) == 0xE0
    )


def verify(apk: Path) -> None:
    try:
        with ZipFile(apk) as archive:
            for asset in ASSETS:
                try:
                    info = archive.getinfo(asset)
                except KeyError as error:
                    raise SystemExit(f'Missing bundled Quran audio: {asset}') from error
                with archive.open(info) as audio:
                    header = audio.read(3)
                if info.file_size <= 1_024 or not is_mp3_header(header):
                    raise SystemExit(f'Bundled Quran audio is not a valid MP3: {info.file_size} bytes')
                print(f'Bundled Quran audio verified: {info.file_size:,} bytes ({asset})')
    except BadZipFile as error:
        raise SystemExit(f'Not a valid APK/ZIP: {apk}') from error


if __name__ == '__main__':
    parser = ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    verify(parser.parse_args().apk)
