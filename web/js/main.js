// Boot the web app.

import {
  AUTH_SKIP_KEY,
  S,
  emit,
  reconcileTimer,
  saveNow,
  subscribe,
  tickTimer,
  todayKey,
} from "./state.js?v=1.9.1";
import { initCloud, refreshLeaderboard } from "./cloud.js?v=1.9.1";
import { applyTheme, bindEvents, render } from "./ui.js?v=1.9.1";

const params = new URLSearchParams(location.search);
if (params.get("static") === "1") document.documentElement.dataset.static = "true";

S.ui.authCreate = false;
initCloud();
if (["today", "tasks", "focus", "stats", "ranks"].includes(params.get("tab") || "")) {
  S.ui.tab = params.get("tab");
  S.ui.settingsOpen = false;
}
if (["system", "light", "dark"].includes(params.get("theme") || "")) {
  S.settings.theme = params.get("theme");
}
S.ui.authGate = !S.auth.signedIn && sessionStorage.getItem(AUTH_SKIP_KEY) !== "1";
reconcileTimer();
bindEvents();
subscribe((reason) => render(reason));
render("auth");
applyTheme();

let lastDay = todayKey();
let lastBoardRefresh = 0;

window.setInterval(() => {
  const day = todayKey();
  if (day !== lastDay) {
    lastDay = day;
    emit("change");
  }
  tickTimer();
}, 250);

// The ranks page is a shared room: refresh while someone is looking at it.
window.setInterval(() => {
  if (!S.auth.signedIn) return;
  if (S.ui.settingsOpen || S.ui.tab !== "ranks") return;
  if (Date.now() - lastBoardRefresh < 60000) return;
  lastBoardRefresh = Date.now();
  refreshLeaderboard(true);
}, 5000);

window.addEventListener("visibilitychange", () => {
  if (document.visibilityState === "hidden") {
    saveNow();
  } else {
    // Catch up on time spent in another tab and refresh the board if visible.
    tickTimer();
    if (S.auth.signedIn && S.ui.tab === "ranks" && !S.ui.settingsOpen)
      refreshLeaderboard(true);
  }
});

window.addEventListener("beforeunload", saveNow);

if (S.auth.signedIn) {
  refreshLeaderboard();
}

