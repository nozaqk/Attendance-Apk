#!/usr/bin/env bash
# Exports mobile/ - with its history - as its own repository.
#
#   scripts/split-app-repo.sh                      # dry run: build in /tmp, do not push
#   scripts/split-app-repo.sh git@github.com:owner/app.git
#
# `git subtree split` rewrites the history so each commit contains only what was
# under mobile/, which becomes the new repository's root.
set -euo pipefail

ROOT=$(git rev-parse --show-toplevel)
REMOTE="${1:-}"

SHA=$(git -C "$ROOT" subtree split --prefix=mobile HEAD)
COUNT=$(git -C "$ROOT" rev-list --count "$SHA")
echo "mobile/ -> $SHA ($COUNT commits, $(git -C "$ROOT" ls-tree -r --name-only "$SHA" | wc -l) files)"

if [ -z "$REMOTE" ]; then
  echo "Dry run. Pushing would run:"
  echo "  git push <url> ${SHA}:refs/heads/main"
  echo "Re-run with the target URL to publish."
  exit 0
fi

git -C "$ROOT" push "$REMOTE" "$SHA:refs/heads/main"
echo "Pushed. The app repository now holds lib/, android/, test/, assets/,"
echo "pubspec.yaml, its own .github/workflows/flutter-apk.yml and .gitignore."
