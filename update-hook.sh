#!/usr/bin/env bash
# Check whether an update replaced the Panadoro files and put them back.
# Runs from a user timer; manual use: ./update-hook.sh [--no-restart]
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
QS="${XDG_CONFIG_HOME:-$HOME/.config}/quickshell/ii"
STATE="$HERE/.last-commit"
RESTART=(--restart)
[[ "${1:-}" == "--no-restart" ]] && RESTART=()

# Never race the updater itself; the timer will try again later.
if pgrep -f 'setup-ii-dusk[.]sh' >/dev/null 2>&1; then
  exit 0
fi

CURRENT="$(cat "$QS/.active-commit" 2>/dev/null || true)"
LAST="$(cat "$STATE" 2>/dev/null || true)"
# Only react to a deployment, not to ordinary edits in between updates.
[[ -n "$CURRENT" && "$CURRENT" != "$LAST" ]] || exit 0

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

changed=false
for rel in "${FILES[@]}"; do
  if ! cmp -s "$HERE/quickshell/$rel" "$QS/$rel" 2>/dev/null; then
    changed=true
    break
  fi
done

if [[ "$changed" == false ]]; then
  # The updater already carried the files; nothing to restore.
  printf '%s\n' "$CURRENT" > "$STATE"
  exit 0
fi

"$HERE/reapply-updater.sh" "${RESTART[@]}"
