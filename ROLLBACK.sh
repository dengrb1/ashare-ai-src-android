#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  printf 'usage: %s <source> <target> <expected-sha256>\n' "$0" >&2
  exit 2
fi

source_path=$1
target_path=$2
expected_hash=${3^^}

if [[ ! -f "$source_path" ]]; then
  printf 'ROLLBACK FAIL source_missing=%s\n' "$source_path" >&2
  exit 1
fi

cp -- "$source_path" "$target_path"
actual_hash=$(sha256sum -- "$target_path" | awk '{print toupper($1)}')
if [[ "$actual_hash" != "$expected_hash" ]]; then
  printf 'ROLLBACK FAIL target=%s hash=%s expected=%s\n' "$target_path" "$actual_hash" "$expected_hash" >&2
  exit 1
fi

printf 'ROLLBACK PASS target=%s hash=%s restored=true\n' "$target_path" "$actual_hash"
