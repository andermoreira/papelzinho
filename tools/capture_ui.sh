#!/usr/bin/env bash
set -euo pipefail
name="${1:?Usage: bash tools/capture_ui.sh whatsapp/pt-BR/preview_default}"
case "$name" in
  *..*|/*|*[!a-zA-Z0-9_/-]*) printf '%s\n' 'Invalid fixture name' >&2; exit 2 ;;
esac
root="$(cd "$(dirname "$0")/.." && pwd)"
adb_bin="${ADB:-adb}"
if ! command -v "$adb_bin" >/dev/null 2>&1; then
  adb_bin="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
fi
package=com.whatsapp
case "$name" in business/*) package=com.whatsapp.w4b ;; esac
"$adb_bin" get-state >/dev/null
mkdir -p "$root/fixtures/$(dirname "$name")"
"$adb_bin" shell uiautomator dump /sdcard/papelzinho-ui.xml
"$adb_bin" pull /sdcard/papelzinho-ui.xml "$root/fixtures/$name.xml"
"$adb_bin" shell rm /sdcard/papelzinho-ui.xml
python3 "$root/tools/sanitize_fixture.py" "$root/fixtures/$name.xml"
version="$("$adb_bin" shell dumpsys package "$package" | sed -n 's/^[[:space:]]*versionName=//p' | head -1)"
locale="$("$adb_bin" shell getprop persist.sys.locale | tr -d '\r')"
printf '\n- `%s.xml`: package `%s`, version `%s`, locale `%s`, captured %s\n' "$name" "$package" "$version" "$locale" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" >> "$root/fixtures/README.md"
printf '%s\n' 'Review XML for personal information before committing.'
