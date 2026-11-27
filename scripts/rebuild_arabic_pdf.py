#!/usr/bin/env python3
"""Re-typeset the reviewed Arabic pupil PDF in the FIRST edition's own slots.

The first shipped pupil6-ar.pdf (v1) put every Arabic line in the right
place, at the right size and in the right colour, but drew the glyphs
one by one without OpenType shaping, so letters appear detached. The
second attempt (v2) re-derived slots heuristically, reordered some
pages, shrank text, and painted sampled background patches over
coloured panels — which washed the text out on those panels.

This engine (v3) trusts v1 completely for layout:

* every Arabic line drawn in v1 becomes one slot: same rect, same
  size, same colour, same weight, same order — nothing is re-sorted,
  re-wrapped or re-coloured;
* the clean JSON text is aligned to v1's drawn lines word by word
  (normalised token alignment), so each slot keeps exactly its own
  words — only now shaped properly by MuPDF/HarfBuzz;
* only the broken glyph runs are redacted (fill=False, images and
  line art untouched). NO background is ever painted: coloured panels,
  photos and artwork stay byte-identical to v1.

Usage:
    python3 scripts/rebuild_arabic_pdf.py \
        --pdf pupil6-ar-v1.pdf \
        --index app/src/main/assets/reviewed/pupil6-ar.json \
        --out app/src/main/assets/reviewed/pupil6-ar.pdf

The index file is updated in place with the new arabicSha256 on success.
Requires: pymupdf (pip install pymupdf).
"""
from __future__ import annotations

import argparse
import collections
import difflib
from hashlib import sha256
from html import escape
import json
from pathlib import Path
import re
import sys
import unicodedata

import fitz

ROOT = Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / "app/src/main/res/font"
NASKH_FONT = ROOT / "scripts/fonts/NotoNaskhArabic.ttf"


def font_archive() -> fitz.Archive:
    """One archive dir holding Tajawal (headings) + Noto Naskh (body)."""
    import shutil
    import tempfile

    d = Path(tempfile.mkdtemp(prefix="sbfonts-"))
    for f in (FONT_DIR / "tajawal_regular.ttf", FONT_DIR / "tajawal_extrabold.ttf", NASKH_FONT):
        shutil.copy(f, d / f.name)
    return fitz.Archive(str(d))
DIACRITICS = re.compile(r"[ً-ٰٟـ]")
ARABIC = re.compile(r"[ء-ي]")
PRESENTATION = re.compile(r"[ﭐ-﷿ﹰ-﻿]")
DIGITS = str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")
EDGE_PUNCT = ".,;:!?؟،؛«»\"'()[]{}<>*-–—…"


def norm_token(tok: str) -> str:
    """Loose normalisation used only to align v1's drawn words to the JSON."""
    t = unicodedata.normalize("NFKC", tok)
    t = DIACRITICS.sub("", t).translate(DIGITS)
    for a, b in (
        ("أ", "ا"),
        ("إ", "ا"),
        ("آ", "ا"),
        ("ٱ", "ا"),
        ("ى", "ي"),
        ("ة", "ه"),
        ("ؤ", "و"),
        ("ئ", "ي"),
    ):
        t = t.replace(a, b)
    return t.strip(EDGE_PUNCT)


def is_arabic(text: str) -> bool:
    return bool(ARABIC.search(unicodedata.normalize("NFKC", text)))


def page_slots(page) -> list[dict]:
    """v1's Arabic lines, in drawn order, with their own size/colour/weight."""
    slots = []
    for b in page.get_text("dict")["blocks"]:
        if b["type"] != 0:
            continue
        for line in b["lines"]:
            spans = [s for s in line["spans"] if s["text"].strip()]
            if not spans:
                continue
            text = "".join(s["text"] for s in spans)
            if not is_arabic(text):
                continue
            chars_color: dict[int, int] = {}
            chars_font: dict[str, int] = {}
            for s in spans:
                chars_color[s["color"]] = chars_color.get(s["color"], 0) + len(s["text"])
                chars_font[s["font"]] = chars_font.get(s["font"], 0) + len(s["text"])
            sizes = sorted(s["size"] for s in spans)
            bold = (
                sum(n for f, n in chars_font.items() if "Bold" in f)
                >= sum(chars_font.values()) / 2
            )
            rect = fitz.Rect(line["bbox"])
            size = sizes[len(sizes) // 2]
            # A few furniture spans are drawn through a page-wide matrix:
            # their reported bbox/size are fiction (e.g. 53pt over 489pt).
            # Touching them would spray text across the page — keep v1's
            # own drawing for those, byte for byte.
            if rect.height > max(3.2 * size, 26) or rect.height > 0.30 * page.rect.height:
                continue
            slots.append(
                {
                    "rect": rect,
                    "text": text,
                    "color": max(chars_color, key=chars_color.get),
                    "bold": bold,
                    "size": size,
                }
            )
    return slots


def letter_stream(text: str) -> str:
    """Continuous letters-only stream: immune to word gluing, spacing and
    punctuation differences between v1's drawn runs and the JSON."""
    t = unicodedata.normalize("NFKC", text)
    t = DIACRITICS.sub("", t).translate(DIGITS)
    for a, b in (
        ("أ", "ا"),
        ("إ", "ا"),
        ("آ", "ا"),
        ("ٱ", "ا"),
        ("ى", "ي"),
        ("ة", "ه"),
        ("ؤ", "و"),
        ("ئ", "ي"),
    ):
        t = t.replace(a, b)
    t = re.sub(r"[^0-9A-Za-zء-ي]", "", t)
    t = re.sub(r"ا{2,}", "ا", t)
    return t.lower()


def grams(s: str, n: int = 4) -> collections.Counter:
    if len(s) < n:
        return collections.Counter({s: 1}) if s else collections.Counter()
    return collections.Counter(s[i : i + n] for i in range(len(s) - n + 1))


# Page furniture repeated under the emblem on most pages; the JSON omits it
# deliberately, so it is re-typeset from a canned string in its own slot.
FURNITURE = {"الادارة العامة للمناهج": "الإدارة العامة للمناهج"}


def align(slots: list[dict], json_text: str, pno: int, report: list[str]):
    """Sequence DP: a JSON paragraph takes 1..6 consecutive slots (drawn
    order is kept on both sides), scored by continuous letter-stream
    overlap. Each paragraph's words are then shared across its slots in
    proportion to how many words v1 drew in each slot."""
    paras = [p for p in json_text.split("\n")]
    n, m = len(slots), len(paras)
    slot_grams = [grams(letter_stream(s["text"])) for s in slots]
    para_grams = [grams(letter_stream(p)) for p in paras]
    slot_tok = [max(1, len(s["text"].split())) for s in slots]

    def gain(i: int, j: int, k: int) -> int:
        g = collections.Counter()
        for x in range(i, i + k):
            g += slot_grams[x]
        inter = sum((g & para_grams[j]).values())
        return 2 * inter

    SKIP = 6
    neg = float("-inf")
    dp = [[neg] * (m + 1) for _ in range(n + 1)]
    act = [[""] * (m + 1) for _ in range(n + 1)]
    dp[0][0] = 0
    for i in range(n + 1):
        for j in range(m + 1):
            if dp[i][j] == neg:
                continue
            if i < n and dp[i][j] - SKIP > dp[i + 1][j]:
                dp[i + 1][j] = dp[i][j] - SKIP
                act[i + 1][j] = "skip-slot"
            if j < m and dp[i][j] - SKIP > dp[i][j + 1]:
                dp[i][j + 1] = dp[i][j] - SKIP
                act[i][j + 1] = "skip-para"
            for k in range(1, 17):
                if i + k <= n and j < m:
                    v = dp[i][j] + gain(i, j, k)
                    if v > dp[i + k][j + 1]:
                        dp[i + k][j + 1] = v
                        act[i + k][j + 1] = f"take{k}"
    # backtrack
    pairs: list[tuple[int, list[int]]] = []  # (para idx, [slot idx])
    removed: list[int] = []
    unplaced_paras: list[int] = []
    i, j = n, m
    while i > 0 or j > 0:
        a = act[i][j]
        if a.startswith("take"):
            k = int(a[4:])
            pairs.append((j - 1, list(range(i - k, i))))
            i, j = i - k, j - 1
        elif a == "skip-slot":
            removed.append(i - 1)
            i -= 1
        elif a == "skip-para":
            unplaced_paras.append(j - 1)
            j -= 1
        else:  # pragma: no cover - unreachable on a filled table
            break
    pairs.reverse()
    removed_set = set(removed)
    for pj in unplaced_paras:
        report.append(f"page {pno}: UNPLACED-PARA: {paras[pj][:50]!r}")
    texts: dict[int, str] = {}
    for pj, group in pairs:
        raw = paras[pj].split()
        total = sum(slot_tok[x] for x in group)
        pos = 0
        shares: list[list[str]] = []
        for idx, li in enumerate(group):
            if idx == len(group) - 1:
                share = raw[pos:]
            else:
                c = round(len(raw) * slot_tok[li] / total)
                share = raw[pos : pos + c]
                pos += c
            shares.append(share)
        for pos_i, li in enumerate(group):
            if not shares[pos_i] and slot_tok[li] >= 1 and len(raw) >= len(group):
                donor = max(range(len(shares)), key=lambda d: len(shares[d]))
                if len(shares[donor]) > 1:
                    shares[pos_i] = [shares[donor].pop()]
        for li, share in zip(group, shares):
            if share:
                texts[li] = " ".join(share)
    for li in removed:
        joined = " ".join(
            norm_token(t) for t in unicodedata.normalize("NFKC", slots[li]["text"]).split()
        )
        joined = re.sub(r"ا{2,}", "ا", joined)
        if joined in FURNITURE:
            texts[li] = FURNITURE[joined]
            removed_set.discard(li)
    placed = sum(len(v.split()) for v in texts.values())
    total_words = sum(len(p.split()) for p in paras)
    return texts, removed_set, placed, total_words

CSS = (
    "@font-face {font-family: naskh; src: url(NotoNaskhArabic.ttf);}"
    "@font-face {font-family: tajab; src: url(tajawal_extrabold.ttf);}"
    "body {margin:0; padding:0;} "
    "p {font-family:%s; font-size:%.2fpx; color:#%06x; line-height:1.04;"
    " text-align:right; margin:0; padding:0;}"
)

FONTS = {}
scratch_archive = None


def slot_font(slot) -> fitz.Font:
    name = "tajab" if slot["bold"] else "naskh"
    if name not in FONTS:
        path = FONT_DIR / "tajawal_extrabold.ttf" if slot["bold"] else NASKH_FONT
        FONTS[name] = fitz.Font(fontfile=str(path))
    return FONTS[name]


# MuPDF's HarfBuzz shaping renders Arabic ~21% narrower than the raw
# advance sum fitz.Font.text_length reports (measured on 60 real slots).
SHAPING = {"naskh": 0.79, "tajab": 0.82}


def fit_scale(slot: dict, rect, text: str) -> float:
    fam = "tajab" if slot["bold"] else "naskh"
    eff = SHAPING[fam] * slot_font(slot).text_length(text, slot["size"])
    if eff <= 0:
        return 1.0
    return max(0.80, min(1.0, (rect.width * 0.985) / eff))


def repair_page(page, json_text: str, report: list[str]) -> None:
    global scratch_archive
    pno = page.number + 1
    slots = page_slots(page)
    if not slots:
        report.append(f"page {pno}: NO-SLOTS (json has {len(json_text.split())} words)")
        return
    texts, removed, placed, total = align(slots, json_text, pno, report)
    if total and placed < total:
        report.append(f"page {pno}: coverage {placed}/{total}")
    for li, s in enumerate(slots):
        if li in removed:
            report.append(f"page {pno}: REMOVED-LINE: {s['text'][:50]!r}")
        elif li not in texts:
            report.append(f"page {pno}: UNREPLACED-LINE: {s['text'][:50]!r}")
            removed.add(li)
    for li in sorted(set(texts) | removed):
        page.add_redact_annot(slots[li]["rect"] + (-0.6, -0.6, 0.6, 0.6), fill=False)
    page.apply_redactions(
        images=fitz.PDF_REDACT_IMAGE_NONE,
        graphics=fitz.PDF_REDACT_LINE_ART_NONE,
        text=fitz.PDF_REDACT_TEXT_REMOVE,
    )
    if scratch_archive is None:
        scratch_archive = font_archive()
    archive = scratch_archive
    for li, text in sorted(texts.items()):
        s = slots[li]
        rect = s["rect"] + (-1.0, -s["size"] * 0.10, 1.0, s["size"] * 0.30)
        html = f"<body><p>{escape(text)}</p></body>"
        scale = fit_scale(s, fitz.Rect(rect), text)
        if scale < 0.985:
            report.append(f"page {pno}: scaled {scale:.2f}: {text[:40]!r}")
        unused, _ = page.insert_htmlbox(
            rect, html,
            css=CSS % ("tajab" if s["bold"] else "naskh", s["size"] * scale, s["color"]),
            archive=archive,
        )
        if unused < 0:
            report.append(f"page {pno}: OVERFLOW: {text[:50]!r}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", type=Path, required=True)
    parser.add_argument("--index", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()

    index = json.loads(args.index.read_text(encoding="utf-8"))
    doc = fitz.open(args.pdf)
    pages = index["pages"]
    if len(pages) != doc.page_count:
        print(
            f"page count mismatch: index {len(pages)} vs pdf {doc.page_count}",
            file=sys.stderr,
        )
        return 1
    report: list[str] = []
    for i, text in enumerate(pages):
        if text is None:
            continue
        repair_page(doc[i], text, report)
    doc.subset_fonts()
    doc.save(args.out, garbage=4, deflate=True)
    digest = sha256(args.out.read_bytes()).hexdigest()
    index["arabicSha256"] = digest
    args.index.write_text(
        json.dumps(index, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    # Second pass: v1's own Arabic font names may not survive on covered
    # pages (inserted text extracts with presentation-form codepoints, so
    # fonts are the reliable tell). The emblem's matrix-drawn caption —
    # identified by its absurd geometry — is kept deliberately.
    check = fitz.open(args.out)
    residual = 0
    v1_fonts = ("NotoNaskhArabic", "TajawalBold", "PTBoldHeading")
    for i, text in enumerate(pages):
        if text is None:
            continue
        for b in check[i].get_text("dict")["blocks"]:
            if b["type"] != 0:
                continue
            for line in b["lines"]:
                t = "".join(s["text"] for s in line["spans"])
                if not is_arabic(t):
                    continue
                if any(s["font"].startswith(v1_fonts) for s in line["spans"]):
                    bb = fitz.Rect(line["bbox"])
                    if bb.height > 26:  # matrix-drawn furniture, kept on purpose
                        continue
                    residual += 1
                    if residual <= 12:
                        report.append(f"page {i + 1}: RESIDUAL-GLYPHS: {t[:50]!r}")
    print(f"wrote {args.out} ({args.out.stat().st_size:,} bytes), arabicSha256={digest}")
    kinds = {}
    for r in report:
        kinds[r.split(":")[1].strip().split(":")[0]] = kinds.get(
            r.split(":")[1].strip().split(":")[0], 0
        ) + 1
    print("report kinds:", kinds)
    for r in report:
        print(" ", r)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
