#!/usr/bin/env bash
# Publish staged store content to the fixed GitHub Release tagged "store".
#
# Reads app/build/store-content/catalog.json (from build_store_catalog.py) and
# the staged PDFs beside it. Every catalog entry carries its own release asset
# name ("asset": pure-ASCII, derived from the destination — GitHub rewrites
# uploaded names containing Arabic/Spaces into dotted ones, which broke every
# Arabic-named download with HTTP 404). Assets whose published SHA-256 already
# matches are skipped; new or changed files are uploaded with --clobber under
# exactly that name. catalog.json is uploaded LAST, so a phone can never read
# a catalog that points at a file which is not there yet, and only then are
# orphaned assets (e.g. the old dotted names) deleted.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
STAGING="$ROOT_DIR/app/build/store-content"
CATALOG="$STAGING/catalog.json"
REPO="mhameed716/ShadowBooks"
TAG="store"

if [[ ! -f "$CATALOG" ]]; then
  echo "ERROR: $CATALOG not found; run build_store_catalog.py first." >&2
  exit 1
fi

# Create the fixed store release once (empty releases are fine; assets follow).
if ! gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then
  echo "Creating store release '$TAG' ..."
  gh release create "$TAG" --repo "$REPO" \
    --title "ShadowBooks Store" \
    --notes "Content files for the in-app ShadowBooks store (books, summaries, exams). Managed by the Publish Store Content workflow; see catalog.json."
fi

# Snapshot of what is already published: "name size digest" per line.
REMOTE_FILE="$(mktemp)"
trap 'rm -f "$REMOTE_FILE"' EXIT
gh api "repos/$REPO/releases/tags/$TAG" \
  --jq '.assets[] | "\(.name) \(.size) \(.digest // "")"' > "$REMOTE_FILE" || true

asset_name() { printf '%s' "$1" | sed 's|/|__|g'; }

remote_field() { # name, column(2=size,3=digest)
  awk -v n="$1" -v c="$2" '$1 == n { print $c }' "$REMOTE_FILE" | tail -n 1
}

added=0; updated=0; skipped=0; failed=0
UPLOAD_DIR="$(mktemp -d)"
trap 'rm -rf "$UPLOAD_DIR" "$REMOTE_FILE"' EXIT

while IFS= read -r entry; do
  dest="$(printf '%s' "$entry" | python3 -c 'import json,sys; print(json.load(sys.stdin)["dest"])')"
  size="$(printf '%s' "$entry" | python3 -c 'import json,sys; print(json.load(sys.stdin)["size"])')"
  sha="$(printf '%s' "$entry" | python3 -c 'import json,sys; print(json.load(sys.stdin)["sha256"])')"
  name="$(printf '%s' "$entry" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("asset") or "")')"
  if [[ -z "$name" ]]; then name="$(asset_name "$dest")"; fi
  remote_size="$(remote_field "$name" 2 || true)"
  remote_digest="$(remote_field "$name" 3 || true)"
  remote_sha="${remote_digest#sha256:}"

  local_file="$STAGING/$dest"
  if [[ ! -f "$local_file" ]]; then
    # Manual entry: the file was uploaded by hand earlier; verify it is intact.
    if [[ "$remote_size" == "$size" && "$remote_sha" == "$sha" ]]; then
      echo "Manual entry verified on release: $dest"
      skipped=$((skipped + 1))
    else
      echo "ERROR: manual entry missing or mismatched on the store release: $dest" >&2
      failed=$((failed + 1))
    fi
    continue
  fi

  if [[ "$remote_size" == "$size" && "$remote_sha" == "$sha" ]]; then
    echo "Unchanged, skipped: $dest"
    skipped=$((skipped + 1))
    continue
  fi

  staged_upload="$UPLOAD_DIR/$name"
  cp "$local_file" "$staged_upload"
  echo "Uploading $dest ($(numfmt --to=iec-size --suffix=B "$size" 2>/dev/null || echo "$size bytes")) ..."
  if gh release upload "$TAG" "$staged_upload" --clobber --repo "$REPO"; then
    if [[ -n "$remote_size" ]]; then updated=$((updated + 1)); else added=$((added + 1)); fi
  else
    echo "ERROR: upload failed: $dest" >&2
    failed=$((failed + 1))
  fi
  rm -f "$staged_upload"
done < <(python3 -c '
import json, sys
catalog = json.load(open(sys.argv[1], encoding="utf-8"))
for entry in catalog["entries"]:
    print(json.dumps(entry, ensure_ascii=False))
' "$CATALOG")

# Cover thumbnails (polish): uploaded under their catalog names too. A cover
# failure never blocks content publishing — the app falls back gracefully and
# the next publish retries, since the remote digest will not match.
covers_uploaded=0; covers_skipped=0
while IFS= read -r entry; do
  cover="$(printf '%s' "$entry" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("cover") or "")')"
  [[ -z "$cover" ]] && continue
  csize="$(printf '%s' "$entry" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("coverSize") or "")')"
  csha="$(printf '%s' "$entry" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("coverSha256") or "")')"
  remote_size="$(remote_field "$cover" 2 || true)"
  remote_digest="$(remote_field "$cover" 3 || true)"
  if [[ "$remote_size" == "$csize" && "${remote_digest#sha256:}" == "$csha" ]]; then
    covers_skipped=$((covers_skipped + 1))
    continue
  fi
  local_cover="$STAGING/covers/$cover"
  if [[ ! -f "$local_cover" ]]; then
    echo "WARNING: cover missing locally, skipped: $cover" >&2
    continue
  fi
  cp "$local_cover" "$UPLOAD_DIR/$cover"
  if gh release upload "$TAG" "$UPLOAD_DIR/$cover" --clobber --repo "$REPO"; then
    covers_uploaded=$((covers_uploaded + 1))
  else
    echo "WARNING: cover upload failed: $cover" >&2
  fi
  rm -f "$UPLOAD_DIR/$cover"
done < <(python3 -c '
import json, sys
catalog = json.load(open(sys.argv[1], encoding="utf-8"))
for entry in catalog["entries"]:
    print(json.dumps(entry, ensure_ascii=False))
' "$CATALOG")
echo "Covers: $covers_uploaded uploaded, $covers_skipped unchanged."

if [[ "$failed" -gt 0 ]]; then
  echo "ERROR: $failed store file(s) failed; catalog.json was NOT updated." >&2
  exit 1
fi

echo "Uploading catalog.json (always last) ..."
cp "$CATALOG" "$UPLOAD_DIR/catalog.json"
gh release upload "$TAG" "$UPLOAD_DIR/catalog.json" --clobber --repo "$REPO"

# Retire assets the current catalog no longer references (the pre-fix release
# holds dotted-name duplicates of every Arabic file). Only after the new
# catalog is live, so no phone can be left pointing at a deleted name.
echo "Removing orphaned assets ..."
EXPECTED_FILE="$(mktemp)"; REMOTE_NAMES="$(mktemp)"
trap 'rm -rf "$UPLOAD_DIR" "$REMOTE_FILE" "$EXPECTED_FILE" "$REMOTE_NAMES"' EXIT
{
  python3 -c '
import json, sys
catalog = json.load(open(sys.argv[1], encoding="utf-8"))
for entry in catalog["entries"]:
    print(entry.get("asset") or "")
    print(entry.get("cover") or "")
' "$CATALOG"
  echo "catalog.json"
} | grep -v '^$' | LC_ALL=C sort -u > "$EXPECTED_FILE"
gh api "repos/$REPO/releases/tags/$TAG" --jq '.assets[].name' | LC_ALL=C sort -u > "$REMOTE_NAMES" || true
removed=0
while IFS= read -r orphan; do
  [[ -z "$orphan" ]] && continue
  echo "Deleting orphaned asset: $orphan"
  if gh release delete-asset "$TAG" "$orphan" --repo "$REPO" --yes; then
    removed=$((removed + 1))
  else
    echo "WARNING: could not delete orphaned asset: $orphan" >&2
  fi
done < <(LC_ALL=C comm -13 "$EXPECTED_FILE" "$REMOTE_NAMES")

echo "Store publish complete: $added added, $updated updated, $skipped unchanged, $removed orphans removed."
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## ShadowBooks store publish"
    echo ""
    echo "- Added: $added"
    echo "- Updated: $updated"
    echo "- Unchanged: $skipped"
  } >> "$GITHUB_STEP_SUMMARY"
fi
