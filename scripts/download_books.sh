#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOKS_DIR="$ROOT_DIR/app/src/main/assets/books"
mkdir -p "$BOOKS_DIR"

# All fifteen textbook PDFs: filename|public Google Drive file id.
# Existing valid PDFs are reused; the English pupil edition is checked against its prepared Arabic pages below.
BOOKS=(
  'quran-and-its-sciences.pdf|1-rZkVdr_L1Hic3O2ReeWXePQxQ_yo0vl'
  'faith-and-purification.pdf|1OxZJHGBx2LKIPBkp0N-oxyVDLfBBCS75'
  'fiqh.pdf|1JyEYWVE5lhXnNKDhlsvPhUelwe2ilzW2'
  'hadith-part-1.pdf|14V64TVQ0KKgSrn7-dEM1DhzCixi-WxUs'
  'hadith-part-2.pdf|1azLcY4ZMfcSuhKEFg072b1mjuuuCg0MK'
  'seerah.pdf|1n02Wsuc0X9Hg7cqj9m41BvVHg7vLwKKu'
  'grammar-and-morphology.pdf|18HTEjeHlZfAgGcfwk-fMOPNygzhh7X2W'
  'reading-and-expression.pdf|1U7Ix8l5qwHSKqOcnsfCcuTTxf1iLp93T'
  'literature-and-criticism.pdf|1CYl-oQl43rkEeXiBmFD-ZazXKcsMqL4q'
  'chemistry.pdf|1C7cpavDGscb8sTk1NDhQpBHYZBgkRITl'
  'physics.pdf|1ydW9Sf53oxj-0xoOSfEPVJJqVXFQ0Is7'
  'biology.pdf|13g3h5s3EUHrwlV813lmvc78OR5QNAj_P'
  'mathematics.pdf|1l36EoDEwtCEZ9j51mJw6tLebBl_ti7lV'
  'english-student-book.pdf|1pB8yZPOqsOB1d1d7KiGtkCC_zaOHZ6KQ'
  'english-workbook.pdf|1gHUthUtRekuVn2DjHZq4OOUlQ7uK458k'
)

for entry in "${BOOKS[@]}"; do
  filename="${entry%%|*}"
  file_id="${entry#*|}"
  target="$BOOKS_DIR/$filename"
  temporary="$target.part"

  if [[ -s "$target" ]] && [[ "$(head -c 5 "$target" || true)" == '%PDF-' ]]; then
    echo "Already present: $filename"
    continue
  fi

  rm -f "$target" "$temporary"
  echo "Downloading $filename ..."
  curl --fail --location --retry 3 --retry-all-errors --retry-delay 2 \
    --connect-timeout 30 --max-time 900 \
    "https://drive.usercontent.google.com/u/0/uc?id=${file_id}&export=download" \
    --output "$temporary"

  if [[ "$(head -c 5 "$temporary" || true)" != '%PDF-' ]]; then
    echo "ERROR: $filename did not download as a PDF (the link may require access)." >&2
    rm -f "$temporary"
    exit 1
  fi
  mv "$temporary" "$target"
done

# Refuse to package a different English pupil edition than the one paired with the Arabic pages.
python3 "$ROOT_DIR/scripts/verify_prepared_book.py"

# Bundle Surah An-Nur for the Quran reader in full builds. Keep it beside the downloaded books;
# the Gradle source sets deliberately omit app/src/main/assets from the lightweight trial APK.
AUDIO_DIR="$BOOKS_DIR/quran-audio"
AUDIO_NAME='an-nur-abdul-basit-r1.mp3'
AUDIO_URL='https://cdn.mp3quran.net/audio/abdulbasit-abdulsamad/r1/024.mp3'
AUDIO_TARGET="$AUDIO_DIR/$AUDIO_NAME"
AUDIO_TEMP="$AUDIO_TARGET.part"
mkdir -p "$AUDIO_DIR"

audio_is_valid() {
  python3 - "$1" <<'PY'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
if not path.is_file() or path.stat().st_size <= 1024:
    raise SystemExit(1)
with path.open('rb') as audio:
    header = audio.read(3)
is_mp3 = header == b'ID3' or (len(header) >= 2 and header[0] == 0xFF and (header[1] & 0xE0) == 0xE0)
raise SystemExit(0 if is_mp3 else 1)
PY
}

if audio_is_valid "$AUDIO_TARGET"; then
  echo "Already present: Quran audio $AUDIO_NAME"
else
  rm -f "$AUDIO_TARGET" "$AUDIO_TEMP"
  echo "Downloading Quran audio $AUDIO_NAME ..."
  curl --fail --location --retry 3 --retry-all-errors --retry-delay 2 \
    --connect-timeout 30 --max-time 1800 \
    "$AUDIO_URL" \
    --output "$AUDIO_TEMP"
  if ! audio_is_valid "$AUDIO_TEMP"; then
    echo "ERROR: Quran audio did not download as a valid MP3." >&2
    rm -f "$AUDIO_TEMP"
    exit 1
  fi
  mv "$AUDIO_TEMP" "$AUDIO_TARGET"
fi

pdf_count="$(find "$BOOKS_DIR" -maxdepth 1 -type f -name '*.pdf' | wc -l | tr -d ' ')"
if [[ "$pdf_count" != "15" ]]; then
  echo "ERROR: expected 15 PDF files in $BOOKS_DIR, found $pdf_count." >&2
  exit 1
fi

echo "All 15 PDF files and the Quran audio are ready in $BOOKS_DIR."
