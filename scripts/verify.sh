#!/usr/bin/env bash
set -euo pipefail

repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
classes_dir="$repo_dir/target/classes"
rm -rf "$classes_dir"
mkdir -p "$classes_dir"
find "$repo_dir/src/main/java" "$repo_dir/src/test/java" -name '*.java' -print0 \
  | xargs -0 javac --release 17 --add-modules jdk.httpserver -d "$classes_dir"
java --add-modules jdk.httpserver -cp "$classes_dir" education.media.snapshot.NightlySnapshotServiceTest
