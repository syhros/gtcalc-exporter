#!/usr/bin/env bash
# Publishes an export test's results to the ci-results-<name> branch (replacing what was there), so they can be read
# without opening the Actions logs: check output, contact sheet of icons, manifest, errors, log tail, crash reports.
#
# Usage: publish_results.sh <name, e.g. 1.20.1-gregtech> <game log>
set -u
name="$1"
log="$2"
out=$(mktemp -d)

cp check.txt "$out/" 2>/dev/null || echo "no check output (the export did not finish?)" > "$out/check.txt"
cp contact-sheet.png contact-sheet.png.txt "$out/" 2>/dev/null || true
for f in export-out/*/manifest.json; do [ -f "$f" ] && cp "$f" "$out/manifest.json"; done
for f in export-out/*/errors.log; do [ -f "$f" ] && head -c 60000 "$f" > "$out/errors.log"; done
[ -f "$log" ] && tail -n 1500 "$log" > "$out/log-tail.txt"
[ -f client.log ] && grep -E "ERROR|Exception|Caused by|FATAL|Pack Extract|packextract" client.log | grep -v "^\s*at " | head -400 > "$out/client-problems.txt"
if ls run/crash-reports/* >/dev/null 2>&1; then
  mkdir -p "$out/crash-reports" && cp run/crash-reports/* "$out/crash-reports/"
fi
ls run/mods > "$out/mods.txt" 2>/dev/null || true
echo "${GITHUB_SHA:-local} ${GITHUB_RUN_ID:-} $(date -u +%FT%TZ)" > "$out/commit.txt"

cd "$out"
git init -q -b "ci-results-$name"
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add -A
git commit -q -m "Export test $name for ${GITHUB_SHA:-local}"
for i in 1 2 3; do
  git push -q -f "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}" "HEAD:ci-results-$name" && break
  sleep 3
done
