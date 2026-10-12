#!/bin/sh
# Scriptorium translation check, version 1.
# Source: https://github.com/whereareiam/scriptorium/blob/dev/scripts/i18n-check.sh
#
# Copy this file to docs/i18n/check.sh in a project that Scriptorium publishes. It keeps
# translations under docs/i18n/<language>/ in step with the pages under docs/content/.
#
# Every translated page records, as `sourceHash` in its frontmatter, the git blob hash of the
# docs/content page it was translated from. When that page changes, the hash no longer
# matches and the translation is outdated. Pages without a translation are fine: the site
# shows the original until someone translates them.
#
# Usage:
#   docs/i18n/check.sh                  check every translation
#   docs/i18n/check.sh --staged         check the translations a commit touches (pre-commit)
#   docs/i18n/check.sh stamp FILE...    record the current original in translated FILEs
#   docs/i18n/check.sh status           count translated, outdated and untranslated pages
#   docs/i18n/check.sh update           replace this script with the latest version
#
# Needs only git and a POSIX shell.

set -eu

UPDATE_URL="https://raw.githubusercontent.com/whereareiam/scriptorium/dev/scripts/i18n-check.sh"

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
project_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
cd "$project_root"

content_dir="docs/content"
i18n_dir="docs/i18n"

fail_usage() {
  echo "i18n: $1" >&2
  echo "usage: docs/i18n/check.sh [--staged | stamp FILE... | status | update]" >&2
  exit 2
}

# The docs/content page a translation belongs to: docs/i18n/ru/a/b.mdx -> docs/content/a/b.mdx
original_of() {
  rest=${1#"$i18n_dir"/}
  echo "$content_dir/${rest#*/}"
}

is_page() {
  case "$1" in
    *.md | *.mdx) return 0 ;;
    *) return 1 ;;
  esac
}

# The sourceHash recorded in the frontmatter of a translation read from stdin.
read_source_hash() {
  awk '
    NR == 1 && $0 !~ /^---[ \t]*$/ { exit }
    NR > 1 && $0 ~ /^---[ \t]*$/ { exit }
    /^sourceHash:/ {
      value = $0
      sub(/^sourceHash:[ \t]*/, "", value)
      gsub(/["'\'' \t\r]/, "", value)
      print value
      exit
    }
  '
}

translation_files() {
  [ -d "$i18n_dir" ] || return 0
  find "$i18n_dir" -mindepth 2 -type f \( -name '*.md' -o -name '*.mdx' \) | LC_ALL=C sort
}

# Prints the problem with one translation, if any. $2 is "worktree" or "index".
check_translation() {
  translation=$1
  mode=$2
  original=$(original_of "$translation")

  if [ "$mode" = index ]; then
    current=$(git rev-parse -q --verify ":$prefix$original" 2>/dev/null || true)
    recorded=$(git show ":$prefix$translation" 2>/dev/null | read_source_hash || true)
  else
    current=""
    [ -f "$original" ] && current=$(git hash-object "$original")
    recorded=$(read_source_hash < "$translation")
  fi

  if [ -z "$current" ]; then
    echo "orphaned: $translation"
    echo "    $original no longer exists. Delete the translation, or move it along with the page."
    return 0
  fi

  [ "$recorded" = "$current" ] && return 0

  if [ -z "$recorded" ]; then
    echo "unstamped: $translation"
    echo "    It records no sourceHash, so nobody can tell which version of $original it follows."
  else
    echo "outdated: $translation"
    if git cat-file -e "$recorded" 2>/dev/null; then
      echo "    $original changed since it was translated:"
      old=$(mktemp)
      new=$(mktemp)
      git cat-file -p "$recorded" > "$old"
      if [ "$mode" = index ]; then git cat-file -p "$current" > "$new"; else cp "$original" "$new"; fi
      diff -u -L "translated from" -L "$original now" "$old" "$new" | sed 's/^/    /' || true
      rm -f "$old" "$new"
    else
      echo "    $original changed since it was translated (the old version is not in this clone, so no diff)."
    fi
  fi
  echo "    Update the translation to match, then run: docs/i18n/check.sh stamp $translation"
}

report() {
  if [ -z "$1" ]; then
    return 0
  fi

  printf '%s\n' "$1"
  echo
  echo "i18n: translations are behind docs/content. Fix them in the same change as the original."
  return 1
}

command_check() {
  report "$(translation_files | while IFS= read -r translation; do
    check_translation "$translation" worktree
  done)"
}

command_check_staged() {
  prefix=$(git rev-parse --show-prefix)
  changed=$(git diff --cached --name-only --diff-filter=ACMRD --relative -- "$content_dir" "$i18n_dir")
  [ -n "$changed" ] || return 0

  translations=$(git ls-files --cached -- "$i18n_dir" | while IFS= read -r file; do
    rest=${file#"$i18n_dir"/}
    [ "${rest#*/}" != "$rest" ] || continue
    is_page "$file" || continue
    printf '%s\n' "$changed" | grep -Fqx -e "$file" -e "$(original_of "$file")" && echo "$file"
  done)

  report "$(printf '%s\n' "$translations" | while IFS= read -r translation; do
    [ -n "$translation" ] && check_translation "$translation" index
  done)"
}

command_stamp() {
  [ "$#" -gt 0 ] || fail_usage "stamp needs at least one translation file"

  for translation in "$@"; do
    translation=${translation#./}
    case "$translation" in
      "$i18n_dir"/*/*) ;;
      *) fail_usage "$translation is not under $i18n_dir/<language>/" ;;
    esac
    is_page "$translation" || fail_usage "$translation is not a .md or .mdx page"
    [ -f "$translation" ] || fail_usage "$translation does not exist"

    original=$(original_of "$translation")
    [ -f "$original" ] || fail_usage "$original does not exist, so $translation cannot follow it"

    # -w keeps this version in the object store, so a later check can show what changed.
    hash=$(git hash-object -w "$original")
    stamped=$(mktemp)
    awk -v hash="$hash" '
      NR == 1 && $0 ~ /^---[ \t]*$/ { print; open = 1; next }
      NR == 1 { print "---"; print "sourceHash: " hash; print "---"; print ""; print; next }
      open && /^sourceHash:/ { if (!written) print "sourceHash: " hash; written = 1; next }
      open && $0 ~ /^---[ \t]*$/ { if (!written) print "sourceHash: " hash; written = 1; open = 0; print; next }
      { print }
    ' "$translation" > "$stamped"
    cat "$stamped" > "$translation"
    rm -f "$stamped"
    echo "stamped: $translation follows $original ($hash)"
  done
}

command_status() {
  [ -d "$i18n_dir" ] || { echo "No translations under $i18n_dir."; return 0; }
  total=$(find "$content_dir" -type f \( -name '*.md' -o -name '*.mdx' \) | wc -l | tr -d ' ')

  for language_dir in "$i18n_dir"/*/; do
    [ -d "$language_dir" ] || continue
    language=$(basename "$language_dir")
    translated=0
    outdated=0
    for translation in $(find "$language_dir" -type f \( -name '*.md' -o -name '*.mdx' \)); do
      if [ -z "$(check_translation "${translation%/}" worktree)" ]; then
        translated=$((translated + 1))
      else
        outdated=$((outdated + 1))
      fi
    done
    echo "$language: $translated up to date, $outdated need attention, $((total - translated - outdated)) untranslated (of $total)"
  done
}

command_update() {
  downloaded=$(mktemp)
  if ! curl -fsSL "$UPDATE_URL" -o "$downloaded"; then
    rm -f "$downloaded"
    echo "i18n: could not download $UPDATE_URL" >&2
    exit 1
  fi
  cat "$downloaded" > "$script_dir/check.sh"
  rm -f "$downloaded"
  echo "Updated docs/i18n/check.sh to $(sed -n 's/^# Scriptorium translation check, //p' "$script_dir/check.sh" | head -n 1)"
}

case "${1:-}" in
  "") command_check ;;
  --staged) command_check_staged ;;
  stamp) shift; command_stamp "$@" ;;
  status) command_status ;;
  update) command_update ;;
  -h | --help) sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//' ;;
  *) fail_usage "unknown command \"$1\"" ;;
esac
