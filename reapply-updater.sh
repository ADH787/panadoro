#!/usr/bin/env bash
# Re-apply the Panadoro files after an ii-dusk update and re-arm the updater
# patch for the next one. Usage: ./reapply-updater.sh [--restart]
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
UPDATER="${XDG_DATA_HOME:-$HOME/.local/share}/ii-dusk/setup-ii-dusk.sh"
RESTART_ARGS=()
[[ "${1:-}" == "--restart" ]] && RESTART_ARGS=(--restart)

mkdir -p "$(dirname "$UPDATER")"
if [[ ! -f "$UPDATER" ]]; then
  cp "$HERE/updater/setup-ii-dusk.patched.sh" "$UPDATER"
  chmod +x "$UPDATER"
  echo "Installed patched updater at $UPDATER"
else
  python3 - "$UPDATER" <<'PY'
import sys
from pathlib import Path

path = Path(sys.argv[1])
text = path.read_text()
marker = "Panadoro local changes"
if marker in text:
    print("Updater already carries the Panadoro files.")
    raise SystemExit(0)

names = [
    "services/GritService.qml",
    "modules/ii/grit/GritApp.qml",
    "modules/ii/grit/GritAppContent.qml",
    "modules/ii/grit/GritAppWindow.qml",
    "modules/ii/grit/tabs/GritSettingsPage.qml",
    "modules/ii/grit/tabs/GritRanksTab.qml"
    "modules/ii/grit/tabs/qmldir"
    "modules/common/Persistent.qml",
    "modules/common/Directories.qml",
    "modules/common/ShellActionRegistry.qml",
    "translations/ar_SY.json",
]
block = "    # Panadoro local changes - carried across updates.\n" + "".join(
    '    "%s"\n' % name for name in names
)
anchor = "PROTECTED_PATTERNS=(\n"
if anchor not in text:
    sys.exit("Could not find PROTECTED_PATTERNS in the updater; restore the files manually instead.")
text = text.replace(anchor, anchor + block, 1)
path.write_text(text)
print("Patched the updater to carry the Panadoro files.")
PY
fi

bash -n "$UPDATER"
"$HERE/restore.sh" "${RESTART_ARGS[@]}"

QS="${XDG_CONFIG_HOME:-$HOME/.config}/quickshell/ii"
CURRENT="$(cat "$QS/.active-commit" 2>/dev/null || true)"
if [[ -n "$CURRENT" ]]; then
  printf '%s\n' "$CURRENT" > "$HERE/.last-commit"
fi
