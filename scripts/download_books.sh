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
  temporary="$target.$$.part"

  if [[ -e "$target" ]]; then
    if [[ -s "$target" ]] && [[ "$(head -c 5 "$target" || true)" == '%PDF-' ]]; then
      echo "Already present: $filename"
      continue
    fi
    echo "ERROR: refusing to replace an existing file that is not a valid PDF: $target" >&2
    exit 1
  fi
  if [[ -e "$temporary" ]]; then
    echo "ERROR: refusing to overwrite an existing partial download: $temporary" >&2
    exit 1
  fi

  echo "Downloading $filename ..."
  curl --fail --location --retry 3 --retry-all-errors --retry-delay 2 \
    --connect-timeout 30 --max-time 900 \
    "https://drive.usercontent.google.com/u/0/uc?id=${file_id}&export=download" \
    --output "$temporary"

  if [[ "$(head -c 5 "$temporary" || true)" != '%PDF-' ]]; then
    echo "ERROR: $filename did not download as a PDF (the link may require access). Kept $temporary for inspection." >&2
    exit 1
  fi
  if [[ -e "$target" ]] || ! mv -n -- "$temporary" "$target" || [[ -e "$temporary" ]]; then
    echo "ERROR: refusing to overwrite a file that appeared during download: $target" >&2
    exit 1
  fi
done

# Download study PDFs into app/build (ignored by Git/snapshots), where full variants package them as assets.
python3 "$ROOT_DIR/scripts/download_study_assets.py"

# Refuse to package a different English pupil edition than the one paired with the Arabic pages.
python3 "$ROOT_DIR/scripts/verify_prepared_book.py"

# Bundle Surah An-Nur for the Quran reader in full builds. Keep it beside the downloaded books;
# the Gradle source sets deliberately omit app/src/main/assets from the lightweight trial APK.
AUDIO_DIR="$BOOKS_DIR/quran-audio"
AUDIO_NAME='an-nur-abdul-basit-r1.mp3'
AUDIO_URL='https://cdn.mp3quran.net/audio/abdulbasit-abdulsamad/r1/024.mp3'
AUDIO_TARGET="$AUDIO_DIR/$AUDIO_NAME"
AUDIO_TEMP="$AUDIO_TARGET.$$.part"
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

if [[ -e "$AUDIO_TARGET" ]]; then
  if audio_is_valid "$AUDIO_TARGET"; then
    echo "Already present: Quran audio $AUDIO_NAME"
  else
    echo "ERROR: refusing to replace an existing file that is not valid MP3 audio: $AUDIO_TARGET" >&2
    exit 1
  fi
else
  if [[ -e "$AUDIO_TEMP" ]]; then
    echo "ERROR: refusing to overwrite an existing partial download: $AUDIO_TEMP" >&2
    exit 1
  fi
  echo "Downloading Quran audio $AUDIO_NAME ..."
  curl --fail --location --retry 3 --retry-all-errors --retry-delay 2 \
    --connect-timeout 30 --max-time 1800 \
    "$AUDIO_URL" \
    --output "$AUDIO_TEMP"
  if ! audio_is_valid "$AUDIO_TEMP"; then
    echo "ERROR: Quran audio did not download as a valid MP3. Kept $AUDIO_TEMP for inspection." >&2
    exit 1
  fi
  if [[ -e "$AUDIO_TARGET" ]] || ! mv -n -- "$AUDIO_TEMP" "$AUDIO_TARGET" || [[ -e "$AUDIO_TEMP" ]]; then
    echo "ERROR: refusing to overwrite a file that appeared during download: $AUDIO_TARGET" >&2
    exit 1
  fi
fi

# Bundle the second Surah An-Nur recitation (Islam Sobhi) beside Abdul Basit for the full build.
SOBHI_NAME='an-nur-islam-sobhi.mp3'
SOBHI_URL='https://media.way2quran.com/islam-sobhi/hafs-an-asim/24.mp3'
SOBHI_TARGET="$AUDIO_DIR/$SOBHI_NAME"
SOBHI_TEMP="$SOBHI_TARGET.$$.part"
if [[ -e "$SOBHI_TARGET" ]]; then
  if audio_is_valid "$SOBHI_TARGET"; then
    echo "Already present: Quran audio $SOBHI_NAME"
  else
    echo "ERROR: refusing to replace an existing file that is not valid MP3 audio: $SOBHI_TARGET" >&2
    exit 1
  fi
else
  echo "Downloading Quran audio $SOBHI_NAME ..."
  curl --fail --location --retry 3 --retry-all-errors --retry-delay 2 \
    --connect-timeout 30 --max-time 1800 \
    "$SOBHI_URL" \
    --output "$SOBHI_TEMP"
  if ! audio_is_valid "$SOBHI_TEMP"; then
    echo "ERROR: Quran audio did not download as a valid MP3. Kept $SOBHI_TEMP for inspection." >&2
    exit 1
  fi
  mv -n -- "$SOBHI_TEMP" "$SOBHI_TARGET"
fi

# Arabic-language exam PDFs (internal 06- prefix, hidden in the UI) for the merged Arabic exams card.
ARABIC_EXAMS_DIR="$BOOKS_DIR/study/exams"
mkdir -p "$ARABIC_EXAMS_DIR"
ARABIC_EXAMS=(
  '06-بنك_اسئله_1.pdf|1KhlrIAp1KlZAol9aBww6haSvM1M15A4_'
  '06-بنك_اسئله_2026.pdf|1dN4SlhYGXvLeHyNs3IDfHb33vU0f3Oz8'
  '06-31_نموذج_وزاري_للغة_العربيةمن_نماذج_أوائل_الجمهورية2025_2026.pdf|1G5Fi-0wuqjY-4NFq3Tffh95dRWkNyfqS'
  '06-نماذج_2025.pdf|1qsaq5Odrkr-0XrhtZD3trnxm8jyQ9a0l'
  '06-نماذج_2024.pdf|1gpajh886kNpLNz1lg0qLeI-8lS6w8Sss'
)
for entry in "${ARABIC_EXAMS[@]}"; do
  filename="${entry%%|*}"
  file_id="${entry#*|}"
  target="$ARABIC_EXAMS_DIR/$filename"
  temporary="$target.part"
  if [[ -s "$target" ]] && [[ "$(head -c 5 "$target" || true)" == '%PDF-' ]]; then
    echo "Already present: $filename"
    continue
  fi
  rm -f "$target" "$temporary"
  echo "Downloading Arabic exam $filename ..."
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

# Islamic exam PDFs (internal 00- prefix, hidden in the UI) shown beside the first file in the Islamic exams card.
ISLAMIC_EXAMS=(
  '00-نماذج_اسلامية_التحديث_الجديد_ثالث_ثانوي_2026_(1).pdf|1oWTZf4ctnsbzIzyJA4J02c7JiFIXKg-7'
  '00-نماذج_2025.pdf|1Ra1GxGSZA1y7TOX92QunTh1jLLHjvip6'
  '00-بنك_الاسئله_في_الاسلاميه_1.pdf|1_ehUmZ7IDi7YDMrD8aPyjtwIF34GfSWB'
  '00-بنك_اسئله_2.pdf|1k_h2_aifNl6ciQHuhlw74jn9N1zQziO4'
  '00-بنك_اسئله_3.pdf|1my2kfOa_AD6TiW1ozmEvhIngaTc3QuSU'
)
for entry in "${ISLAMIC_EXAMS[@]}"; do
  filename="${entry%%|*}"
  file_id="${entry#*|}"
  target="$ARABIC_EXAMS_DIR/$filename"
  temporary="$target.part"
  if [[ -s "$target" ]] && [[ "$(head -c 5 "$target" || true)" == '%PDF-' ]]; then
    echo "Already present: $filename"
    continue
  fi
  rm -f "$target" "$temporary"
  echo "Downloading Islamic exam $filename ..."
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

# Physics exam PDFs (internal 10- prefix, hidden in the UI) shown beside the first file in the Physics exams card.
PHYSICS_EXAMS=(
  '10-بنك_اسئله_3.pdf|1r6ImQHiwGyCyuZluooyc1aPt4P71FCqJ'
  '10-31_نموذج_وزاري_للفيزياء_من_نماذج_أوائل_الجمهورية2025_2026.pdf|1FhraZ1rw3OIM7EkUuR_OlgdvSr0LGkFq'
  '10-بنك_اسئله_2.pdf|1djqQpA3VTOt62bfI684nCuUBNoPQm7pC'
)
for entry in "${PHYSICS_EXAMS[@]}"; do
  filename="${entry%%|*}"
  file_id="${entry#*|}"
  target="$ARABIC_EXAMS_DIR/$filename"
  temporary="$target.part"
  if [[ -s "$target" ]] && [[ "$(head -c 5 "$target" || true)" == '%PDF-' ]]; then
    echo "Already present: $filename"
    continue
  fi
  rm -f "$target" "$temporary"
  echo "Downloading Physics exam $filename ..."
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

# Mathematics exam PDFs (internal 12- prefix, hidden in the UI) shown beside the first file in the Mathematics exams card.
MATH_EXAMS=(
  '12-31_نموذج_وزاري_للتفاضل_والتكامل_من_نماذج_أوائل_الجمهورية2025_2026.pdf|1RdnM8EPGs72HQ4r7iIPjFVYG9IlDvNJ9'
  '12-بنك_اسئله_5.pdf|1QlhO0BiyB1VIGL9Opwof8MYEkYAZ-Cwb'
  '12-بنك_اسئله4.pdf|1MzZ9VDah75LLRWOoX9UcRrngHIhEFSkE'
)
for entry in "${MATH_EXAMS[@]}"; do
  filename="${entry%%|*}"
  file_id="${entry#*|}"
  target="$ARABIC_EXAMS_DIR/$filename"
  temporary="$target.part"
  if [[ -s "$target" ]] && [[ "$(head -c 5 "$target" || true)" == '%PDF-' ]]; then
    echo "Already present: $filename"
    continue
  fi
  rm -f "$target" "$temporary"
  echo "Downloading Mathematics exam $filename ..."
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

# English exam PDFs (internal 13- prefix, hidden in the UI) shown beside the first file in the English exams card.
ENGLISH_EXAMS=(
  '13-31_نموذج_وزاري_للانجليزي_من_نماذج_أوائل_الجمهورية2025_2026.pdf|1KQxwEJmCCVizLEvYxGAWukDm-OYf4Xd9'
  '13-بنك_اسئله_3.pdf|1C4BgOwyA7hKXb5P5YMNRor6EzV_N0qrT'
  '13-بنك_اسئله_2.pdf|1tUHUcSgK_uj1udTxFWHC3SdIQeihDXbn'
)
for entry in "${ENGLISH_EXAMS[@]}"; do
  filename="${entry%%|*}"
  file_id="${entry#*|}"
  target="$ARABIC_EXAMS_DIR/$filename"
  temporary="$target.part"
  if [[ -s "$target" ]] && [[ "$(head -c 5 "$target" || true)" == '%PDF-' ]]; then
    echo "Already present: $filename"
    continue
  fi
  rm -f "$target" "$temporary"
  echo "Downloading English exam $filename ..."
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

# Chemistry exam PDFs (internal 09- prefix, hidden in the UI) shown beside the first file in the Chemistry exams card.
CHEMISTRY_EXAMS=(
  '09-بنك_اسئله_1.pdf|1Nv_ZewuBDcq-jlllYNa9qlI9bFAZYNpT'
  '09-بنك_اسئله_2.pdf|1Ht4vL-DB7-zYMaSfRYmZu7wlR9WO6yxW'
  '09-بنك_اسئله_3.pdf|1SMcix50U5FxfkMmMS7y0FCW6Lgbj8vNC'
)
for entry in "${CHEMISTRY_EXAMS[@]}"; do
  filename="${entry%%|*}"
  file_id="${entry#*|}"
  target="$ARABIC_EXAMS_DIR/$filename"
  temporary="$target.part"
  if [[ -s "$target" ]] && [[ "$(head -c 5 "$target" || true)" == '%PDF-' ]]; then
    echo "Already present: $filename"
    continue
  fi
  rm -f "$target" "$temporary"
  echo "Downloading Chemistry exam $filename ..."
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

pdf_count="$(find "$BOOKS_DIR" -maxdepth 1 -type f -name '*.pdf' | wc -l | tr -d ' ')"
if [[ "$pdf_count" != "15" ]]; then
  echo "ERROR: expected 15 PDF files in $BOOKS_DIR, found $pdf_count." >&2
  exit 1
fi

echo "All 15 textbooks, 20 generated study PDFs, the reviewed PDF, both Quran recitations, and the Arabic exam PDFs are ready for the full build."
