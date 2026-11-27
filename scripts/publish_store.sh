#!/usr/bin/env bash
# Publish staged store content to the fixed GitHub Release tagged "store".
#
# Reads app/build/store-content/catalog.json (from build_store_catalog.py) and
# the staged PDFs beside it. Every catalog entry maps to one release asset whose
# name is the destination path with '/' replaced by '__'. Assets whose published
# SHA-256 already matches are skipped; new or changed files are uploaded with
# --clobber. catalog.json is uploaded LAST, so a phone can never read a catalog
# that points at a file which is not there yet.
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
  name="$(asset_name "$dest")"
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

if [[ "$failed" -gt 0 ]]; then
  echo "ERROR: $failed store file(s) failed; catalog.json was NOT updated." >&2
  exit 1
fi

echo "Uploading catalog.json (always last) ..."
cp "$CATALOG" "$UPLOAD_DIR/catalog.json"
gh release upload "$TAG" "$UPLOAD_DIR/catalog.json" --clobber --repo "$REPO"

echo "Store publish complete: $added added, $updated updated, $skipped unchanged."
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  {
    echo "## ShadowBooks store publish"
    echo ""
    echo "- Added: $added"
    echo "- Updated: $updated"
    echo "- Unchanged: $skipped"
  } >> "$GITHUB_STEP_SUMMARY"
fi
