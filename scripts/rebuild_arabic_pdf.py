#!/usr/bin/env python3
"""Rebuild the reviewed Arabic companion PDF with correctly shaped text.

The shipped pupil6-ar.pdf was typeset by drawing Arabic glyphs one by one
without OpenType shaping, so letters appear detached and some words are
mangled (كلام shows as كالم). The checked-in pupil6-ar.json holds the clean
logical text of every page. This script keeps each page's artwork, photos
and coloured panels, removes only the broken text runs, and re-typesets the
JSON text into the very same line slots — same place, same size, same
colour — using MuPDF's own Arabic shaping (HarfBuzz) via insert_htmlbox, so
letters join properly and embedded Latin tokens (activity letters like (A))
keep the same colour and direction as the Arabic around them.

Usage:
    python3 scripts/rebuild_arabic_pdf.py \
        --pdf pupil6-ar-orig.pdf \
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
import json
from pathlib import Path
import re
import sys

import fitz

ROOT = Path(__file__).resolve().parents[1]
FONT_DIR = ROOT / "app/src/main/res/font"
DIACRITICS = re.compile(r"[ً-ٰٟـ]")
ARABIC = re.compile(r"[ء-ي]")


def norm(text: str) -> str:
    """Loose normalisation used only for matching old runs to clean lines."""
    t = DIACRITICS.sub("", text)
    t = t.replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
    t = t.replace("ى", "ي").replace("ة", "ه").replace("ؤ", "و").replace("ئ", "ي")
    t = re.sub(r"[^0-9A-Za-zء-ي]", "", t)
    return t


def page_slots(page) -> list[dict]:
    slots = []
    pw, ph = page.rect.width, page.rect.height
    for b in page.get_text("dict")["blocks"]:
        if b["type"] != 0:
            continue
        for line in b["lines"]:
            spans = [s for s in line["spans"] if s["text"].strip()]
            if not spans:
                continue
            text = "".join(s["text"] for s in spans)
            if not ARABIC.search(text):
                continue
            rect = fitz.Rect(line["bbox"])
            sizes = sorted(s["size"] for s in spans)
            size = sizes[len(sizes) // 2]
            # Page furniture / watermark art (giant rotated banners) is not
            # reading text: never let it swallow a real line.
            if size > 30 or (rect.width > 0.92 * pw and rect.height > 0.35 * ph):
                continue
            chars_color: dict[int, int] = {}
            chars_font: dict[str, int] = {}
            for s in spans:
                chars_color[s["color"]] = chars_color.get(s["color"], 0) + len(s["text"])
                chars_font[s["font"]] = chars_font.get(s["font"], 0) + len(s["text"])
            bold = sum(
                n for f, n in chars_font.items() if "Bold" in f
            ) >= sum(chars_font.values()) / 2
            slots.append(
                {
                    "rect": rect,
                    "text": text,
                    "color": max(chars_color, key=chars_color.get),
                    "bold": bold,
                    "size": size,
                }
            )
    for i, s in enumerate(slots):
        s["ord"] = i
    return slots


def orderings(slots: list[dict], page_width: float) -> dict[str, list[dict]]:
    """Candidate reading orders; the matcher keeps whichever fits best."""
    flow = sorted(slots, key=lambda s: (round(s["rect"].y0 / 4), -s["rect"].x0))
    out = {"flow": flow, "dict": list(slots)}
    if len(slots) >= 10:
        centers = sorted(s["rect"].x0 + s["rect"].width / 2 for s in slots)
        split, widest = None, 0.0
        for a, b in zip(centers, centers[1:]):
            if 0.2 * page_width < a and b < 0.85 * page_width and b - a > widest:
                split, widest = (a + b) / 2, b - a
        if split is not None and widest > 0.07 * page_width:
            right_n = sum(
                1 for s in slots if (s["rect"].x0 + s["rect"].width / 2) > split
            )
            if 0.3 * len(slots) <= right_n <= 0.7 * len(slots):
                out["cols"] = sorted(
                    slots,
                    key=lambda s: (
                        0 if (s["rect"].x0 + s["rect"].width / 2) > split else 1,
                        round(s["rect"].y0 / 4),
                        -s["rect"].x0,
                    ),
                )
    return out


def match_lines(json_lines, slots):
    """DP: a JSON line takes 1..3 consecutive slots; slots may be skipped."""
    J, S = len(json_lines), len(slots)
    jn = [norm(t) for t in json_lines]
    sn = [norm(s["text"]) for s in slots]
    NEG = -1e9

    def sim(i, j, k):
        joined = "".join(sn[j : j + k])
        if not joined or not jn[i]:
            return 0.0
        return difflib.SequenceMatcher(None, joined, jn[i]).ratio()

    dp = [[NEG] * (S + 1) for _ in range(J + 1)]
    act = [[None] * (S + 1) for _ in range(J + 1)]
    for j in range(S + 1):
        dp[J][j] = 0.0
    for i in range(J - 1, -1, -1):
        for j in range(S, -1, -1):
            best, how = NEG, None
            if j < S:
                v = dp[i][j + 1] - 0.04
                if v > best:
                    best, how = v, ("skip",)
            v = dp[i + 1][j] - 0.6
            if v > best:
                best, how = v, ("drop",)
            for k in (1, 2, 3, 4, 5, 6, 7, 8):
                if j + k <= S:
                    v = sim(i, j, k) + dp[i + 1][j + k]
                    if v > best:
                        best, how = v, ("take", k)
            dp[i][j], act[i][j] = best, how
    total_score = dp[0][0]
    pairs, dropped = [], []
    used_slots = set()
    i = j = 0
    while i < J and j <= S:
        how = act[i][j]
        if how is None:
            break
        if how[0] == "skip":
            j += 1
        elif how[0] == "drop":
            dropped.append(i)
            i += 1
        else:
            group = list(range(j, j + how[1]))
            pairs.append((i, group))
            used_slots.update(group)
            i += 1
            j += how[1]
    while i < J:
        dropped.append(i)
        i += 1
    # Second pass: captions and side notes sit outside the reading flow;
    # place any dropped line into still-unused consecutive slots by similarity.
    free_set = {k for k in range(S) if k not in used_slots}
    rest = []
    for i in dropped:
        best, best_r = None, 0.34 if len(jn[i]) <= 8 else 0.45
        for j in sorted(free_set):
            joined = ""
            for k in range(1, 9):
                if j + k - 1 not in free_set:
                    break
                joined += sn[j + k - 1]
                r = difflib.SequenceMatcher(None, joined, jn[i]).ratio()
                if r > best_r:
                    best, best_r = list(range(j, j + k)), r
        if best is not None:
            pairs.append((i, best))
            free_set -= set(best)
        else:
            rest.append(i)
    # Known running furniture with no JSON twin: replace with clean text.
    FURNITURE = {norm("الإدارة العامة للمناهج"): "الإدارة العامة للمناهج"}
    for k in sorted(free_set):
        clean = FURNITURE.get(sn[k])
        if clean:
            pairs.append((clean, [k]))
            free_set.discard(k)
    return pairs, rest, sorted(free_set), total_score


def css_for(slot, scale, centered, leading):
    color = "#%02x%02x%02x" % (
        (slot["color"] >> 16) & 255,
        (slot["color"] >> 8) & 255,
        slot["color"] & 255,
    )
    return (
        "@font-face { font-family: taj; src: url(tajawal_regular.ttf); }"
        "@font-face { font-family: tajb; src: url(tajawal_extrabold.ttf); }"
        "body { font-family: %s; direction: rtl; text-align: %s; color: %s; }"
        "p { margin: 0; font-size: %.2fpx; line-height: %.3f; }"
        % (
            "tajb" if slot["bold"] else "taj",
            "center" if centered else "right",
            color,
            slot["size"] * scale,
            leading,
        )
    )


def escape(text: str) -> str:
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def repair_page(page, json_text: str, report: list[str]) -> None:
    raw_slots = page_slots(page)
    json_lines = json_text.split("\n")
    # Try every plausible reading order; keep the one the text itself prefers.
    best = None
    for ordered in orderings(raw_slots, page.rect.width).values():
        candidate = match_lines(json_lines, ordered)
        if best is None or candidate[3] > best[0][3]:
            best = (candidate, ordered)
    (pairs, dropped, free, _score), slots = best
    for i in dropped:
        report.append(f"page {page.number + 1}: UNPLACED: {json_lines[i][:60]}")
    for k in free:
        if len(norm(slots[k]["text"])) > 14:
            report.append(
                f"page {page.number + 1}: untouched: {slots[k]['text'][:60]!r}"
            )
    if not pairs:
        return
    pix = page.get_pixmap(dpi=72)
    zoom = pix.width / page.rect.width

    def bg_of(rect):
        pts = []
        x0, x1 = int(rect.x0 * zoom), int(rect.x1 * zoom)
        y0, y1 = int(rect.y0 * zoom), int(rect.y1 * zoom)
        for x in range(max(0, x0 - 2), min(pix.width, x1 + 3), 2):
            for y in (max(0, y0 - 2), min(pix.height - 1, y1 + 2)):
                pts.append(pix.pixel(x, y))
        for y in range(max(0, y0 - 2), min(pix.height, y1 + 3), 2):
            for x in (max(0, x0 - 2), min(pix.width - 1, x1 + 2)):
                pts.append(pix.pixel(x, y))
        if not pts:
            return (1.0, 1.0, 1.0)
        c = collections.Counter(pts).most_common(1)[0][0]
        return tuple(v / 255 for v in c)

    for _, group in pairs:
        for k in group:
            page.add_redact_annot(slots[k]["rect"] + (-0.5, -0.5, 0.5, 0.5), fill=False)
    page.apply_redactions(
        images=fitz.PDF_REDACT_IMAGE_NONE,
        graphics=fitz.PDF_REDACT_LINE_ART_NONE,
        text=fitz.PDF_REDACT_TEXT_REMOVE,
    )
    archive = fitz.Archive(str(FONT_DIR))
    for key, group in pairs:
        text_line = key if isinstance(key, str) else json_lines[key]
        gs = [slots[k] for k in group]
        union = gs[0]["rect"]
        for s in gs[1:]:
            union |= s["rect"]
        first = gs[0]
        rect = fitz.Rect(union) + (-2, -2, 2, 3)
        page.draw_rect(rect, color=None, fill=bg_of(union), overlay=True)
        if len(gs) > 1 and gs[1]["rect"].y0 > gs[0]["rect"].y0:
            pitch = gs[1]["rect"].y0 - gs[0]["rect"].y0
            leading = min(1.8, max(1.12, pitch / first["size"]))
        else:
            leading = 1.35
        centered = first["size"] >= 15
        html = f"<body><p>{escape(text_line)}</p></body>"
        scale = 1.0
        while True:
            tight = max(1.02, leading - (1.0 - scale) * 0.6)
            unused, _ = page.insert_htmlbox(
                rect, html,
                css=css_for(first, scale, centered, tight),
                archive=archive,
            )
            if unused >= 0 or scale <= 0.5:
                break
            page.draw_rect(rect, color=None, fill=bg_of(union), overlay=True)
            scale -= 0.06
        if unused < 0:
            report.append(
                f"page {page.number + 1}: overflow: {text_line[:50]!r}"
            )


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
    print(f"wrote {args.out} ({args.out.stat().st_size:,} bytes), arabicSha256={digest}")
    unplaced = [r for r in report if "UNPLACED" in r]
    overflows = [r for r in report if "overflow" in r]
    untouched = [r for r in report if "untouched" in r]
    print(
        f"notes: unplaced={len(unplaced)} overflow={len(overflows)} "
        f"untouched={len(untouched)}"
    )
    for line in (unplaced + overflows + untouched)[:40]:
        print(" -", line)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
