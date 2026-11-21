#!/usr/bin/env bash
# Put the Panadoro changes back into the Quickshell config after an update.
# Usage: ./restore.sh [--restart]
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QS="${XDG_CONFIG_HOME:-$HOME/.config}/quickshell/ii"
RESTART=false
[[ "${1:-}" == "--restart" ]] && RESTART=true

FILES=(
  "services/GritService.qml"
  "modules/ii/grit/GritApp.qml"
  "modules/ii/grit/GritAppContent.qml"
  "modules/ii/grit/GritAppWindow.qml"
  "modules/ii/grit/tabs/GritSettingsPage.qml"
  "modules/ii/grit/tabs/GritRanksTab.qml"
  "modules/ii/grit/tabs/qmldir"
  "modules/common/Persistent.qml"
  "modules/common/Directories.qml"
  "modules/common/ShellActionRegistry.qml"
  "translations/ar_SY.json"
)

if [[ ! -d "$QS" ]]; then
  echo "Quickshell config not found at $QS" >&2
  exit 1
fi

for rel in "${FILES[@]}"; do
  src="$HERE/quickshell/$rel"
  [[ -f "$src" ]] || { echo "Missing backup file: $src" >&2; exit 1; }
  install -Dm644 "$src" "$QS/$rel"
done

# The local HTTP sync server is no longer used; drop it if an update restored it.
rm -f "$QS/scripts/grit/sync_server.py"
rmdir "$QS/scripts/grit" 2>/dev/null || true

# Brand the launcher keybind if the upstream file returned it to "Panadora".
KB="${XDG_CONFIG_HOME:-$HOME/.config}/hypr/hyprland/keybinds.lua"
if [[ -f "$KB" ]]; then
  sed -i 's/Panadora: Toggle habits, tasks and focus/Panadoro: Toggle habits, tasks and focus/' "$KB"
fi

echo "Panadoro files restored into $QS"

if [[ "$RESTART" == true ]]; then
  pkill -f '^bash [^ ]*/qs-guard[.]sh' 2>/dev/null || true
  qs kill -c ii 2>/dev/null || true
  killall qs quickshell 2>/dev/null || true
  for _ in $(seq 50); do pgrep -x qs >/dev/null || break; sleep 0.1; done
  MALLOC_CONF=narenas:1 setsid -f "$QS/scripts/quickshell-guard/qs-guard.sh" -c ii 9>&- || true
  echo "Quickshell restarted."
else
  echo "Restart Quickshell (Ctrl+Super+R) to load them."
fi
