#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BOOKS_DIR="$ROOT_DIR/app/src/main/assets/books"
mkdir -p "$BOOKS_DIR"

# filename|public Google Drive file id
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

pdf_count="$(find "$BOOKS_DIR" -maxdepth 1 -type f -name '*.pdf' | wc -l | tr -d ' ')"
if [[ "$pdf_count" != "15" ]]; then
  echo "ERROR: expected 15 PDF files in $BOOKS_DIR, found $pdf_count." >&2
  exit 1
fi

echo "All 15 PDF files are ready in $BOOKS_DIR."
