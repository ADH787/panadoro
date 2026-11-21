// The web interface. Pure DOM + SVG, no framework: the data layer already has
// the same shape as the desktop app, so this file is mostly layout and events.

import {
  AUTH_SKIP_KEY,
  S,
  activeCategories,
  activeHabits,
  activeSessions,
  activeTags,
  addSubtask,
  addDays,
  bestFocusDay,
  breakRatio,
  categoryById,
  createCategory,
  createHabit,
  createTag,
  createTagTask,
  createTask,
  currentStreak,
  dailyFocusSeries,
  dailyTagFocusSeries,
  dateKey,
  dateFromKey,
  deleteCategory,
  deleteHabit,
  deleteSession,
  deleteTag,
  deleteTask,
  doneTaskCount,
  dueTodayTaskCount,
  effectiveDisplayName,
  emit,
  exportData,
  focusHeatmapWeeks,
  focusMinutesInRange,
  focusStreak,
  habitById,
  habitDone,
  habitHeatmap,
  habitProgress,
  habitStats,
  habitValue,
  importData,
  incrementHabit,
  lastNDays,
  openTaskCount,
  overdueTaskCount,
  recentSessions,
  relativeDay,
  removeSubtask,
  resetAllData,
  saveSettings,
  scheduledOn,
  selectTag,
  sessionsInRange,
  setDisplayName,
  setHabitEntry,
  setTagPublic,
  setTimerNote,
  skipTimer,
  startFocusNow,
  tagById,
  tagFocusSeries,
  tagGoalProgress,
  tagPercent,
  tagStats,
  tagStreak,
  tagTodayMinutes,
  taskById,
  taskFiltered,
  taskSubtaskProgress,
  tasksForTag,
  thisWeekDays,
  timeOfDayStats,
  todayFocusMinutes,
  todayHabits,
  todayKey,
  toggleHabitDone,
  toggleSubtask,
  toggleTask,
  toggleTimer,
  pauseTimer,
  resetTimer,
  updateCategory,
  updateHabit,
  updateTag,
  updateTask,
  weekdayName,
  weekdayOf,
  activeTag,
  activeTagId,
  computeSecondsLeft,
  timerCycle,
  timerDurationSec,
  timerPhase,
  timerProgress,
  timerRunning,
  timerSecondsLeft,
} from "./state.js?v=1.8.3";

import {
  clearAuthError,
  createAccount,
  ensureFreshToken,
  publishLeaderboard,
  refreshLeaderboard,
  signIn,
  signOut,
  syncNow,
} from "./cloud.js?v=1.8.3";
import { ICONS } from "./icons.js?v=1.8.3";
import morphdom from "./vendor/morphdom.js?v=1.8.3";
import { applyLanguage, currentLanguage, isRTL, setLanguage, translateDOM, translateText } from "./i18n.js?v=1.8.3";

const WEB_VERSION = document.querySelector('meta[name="app-version"]')?.content || "1.8.3";

const TABS = [
  { id: "today", label: "Today", icon: "sun" },
  { id: "tasks", label: "Tasks", icon: "checklist" },
  { id: "focus", label: "Focus", icon: "focus" },
  { id: "stats", label: "Stats", icon: "stats" },
  { id: "ranks", label: "Ranks", icon: "trophy" },
];

const ICON_ALIASES = {
  wb_sunny: "sun",
  checklist: "list-checks",
  psychiatry: "brain",
  monitoring: "chart-no-axes-column",
  settings: "settings",
  add: "plus",
  add_task: "plus",
  close: "x",
  arrow_back: "arrow-left",
  check: "check",
  check_circle: "circle-check",
  task_alt: "circle-check",
  radio_button_unchecked: "circle",
  edit: "pencil",
  edit_note: "pencil",
  delete: "trash-2",
  delete_forever: "trash-2",
  play_arrow: "play",
  pause: "pause",
  restart_alt: "rotate-ccw",
  skip_next: "skip-forward",
  coffee: "coffee",
  local_fire_department: "flame",
  favorite: "heart",
  neurology: "brain",
  self_improvement: "brain",
  fitness_center: "dumbbell",
  directions_run: "activity",
  menu_book: "book-open",
  school: "graduation-cap",
  work: "briefcase",
  palette: "palette",
  water_drop: "droplet",
  medication: "pill",
  restaurant: "utensils",
  music_note: "music",
  code: "code",
  brush: "palette",
  label: "tag",
  inbox: "inbox",
  apps: "layout-grid",
  person: "user",
  emoji_events: "trophy",
  insights: "chart-no-axes-column",
  timelapse: "clock",
  timer: "clock",
  schedule: "clock",
  today: "calendar",
  date_range: "calendar",
  event: "calendar",
  history: "clock",
  flag: "target",
  bolt: "flame",
  cloud: "cloud",
  cloud_off: "cloud-off",
  cloud_done: "cloud-check",
  sync: "refresh-cw",
  refresh: "refresh-cw",
  login: "log-in",
  logout: "log-out",
  key: "lock",
  lock: "lock",
  mail: "mail",
  visibility: "eye",
  warning: "triangle-alert",
  error: "triangle-alert",
  download: "download",
  upload: "upload",
  expand_more: "chevron-down",
  chevron_right: "chevron-right",
  note: "file-text",
  person_add: "user-plus",
  circle: "circle",
  pill: "pill",
  moon: "moon",
  more: "more-horizontal",
  loader: "loader-circle",
  celebration: "sparkles",
  done_all: "circle-check",
  focus: "timer",
  reset: "rotate-ccw",
  skip: "skip-forward",
  stats: "chart-no-axes-column",
  trash: "trash-2",
  book: "book-open",
  water: "droplet",
  run: "activity",
};

function iconName(name) {
  const clean = String(name ?? "").trim();
  const mapped = ICON_ALIASES[clean] || clean;
  return ICONS[mapped] ? mapped : "circle";
}

function icon(name, cls = "") {
  const key = iconName(name);
  return `<svg class="${cls}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[key]}</svg>`;
}

const ICON_CHOICES = [
  "focus", "flame", "heart", "book", "water", "run", "brain", "coffee",
  "school", "work", "palette", "note", "sun", "tag", "target", "inbox",
];

const SHAPES = [
  { id: "Cookie4Sided", radius: "38% 62% 58% 42% / 48% 42% 58% 52%" },
  { id: "Cookie7Sided", radius: "46% 54% 60% 40% / 52% 43% 57% 48%" },
  { id: "Cookie9Sided", radius: "52% 48% 44% 56% / 47% 58% 42% 53%" },
  { id: "Cookie12Sided", radius: "45% 55% 54% 46% / 55% 45% 55% 45%" },
  { id: "Flower", radius: "58% 42% 58% 42% / 45% 58% 42% 55%" },
  { id: "SoftBurst", radius: "34% 66% 52% 48% / 62% 38% 62% 38%" },
  { id: "Sunny", radius: "50% 50% 40% 60% / 42% 50% 50% 58%" },
  { id: "Heart", radius: "50% 50% 50% 50% / 60% 60% 40% 40%" },
  { id: "Gem", radius: "12% 88% 16% 84% / 82% 14% 86% 18%" },
];

const ACCENT_ROLES = ["primary", "secondary", "tertiary", "success", "error", "outline"];
const ACCENT_LABELS = {
  primary: "Green",
  secondary: "Blue",
  tertiary: "Amber",
  success: "Mint",
  error: "Red",
  outline: "Neutral",
};

let modalHandler = null;
let modalDelete = null;
let celebrationTimer = 0;
let confettiFrame = 0;
let lastRenderReason = "";

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

function esc(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

function attr(value) {
  return esc(value);
}

function validHex(value) {
  return /^#[0-9a-f]{6}([0-9a-f]{2})?$/i.test(String(value ?? "").trim());
}

function hexLuminance(hex) {
  const raw = String(hex).replace("#", "");
  const r = parseInt(raw.slice(0, 2), 16) / 255;
  const g = parseInt(raw.slice(2, 4), 16) / 255;
  const b = parseInt(raw.slice(4, 6), 16) / 255;
  const lin = (c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  return 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b);
}

function tagColor(tag) {
  if (tag && validHex(tag.color)) return String(tag.color).trim();
  return `var(--tag-${tag?.accent || "primary"})`;
}

function tagOn(tag) {
  if (tag && validHex(tag.color)) {
    return hexLuminance(tag.color) > 0.38 ? "#102013" : "#ffffff";
  }
  return `var(--on-${tag?.accent || "primary"})`;
}

function tagStyle(tag) {
  return `style="--accent:${tagColor(tag)};--on-accent:${tagOn(tag)};"`;
}

function shapeRadius(name) {
  return SHAPES.find((shape) => shape.id === name)?.radius || SHAPES[1].radius;
}

function shapeStyle(name, extra = "") {
  return `style="border-radius:${shapeRadius(name)};${extra}"`;
}

function fmtMin(minutes) {
  const value = Math.max(0, Math.round(Number(minutes ?? 0)));
  if (value < 60) return `${value}m`;
  const h = Math.floor(value / 60);
  const m = value % 60;
  return m ? `${h}h ${m}m` : `${h}h`;
}

function fmtSeconds(seconds) {
  return fmtMin(Math.round(Number(seconds ?? 0) / 60));
}

function fmtClock(seconds) {
  const total = Math.max(0, Math.round(Number(seconds ?? 0)));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
}

function initials(name) {
  const words = String(name ?? "").trim().split(/\s+/).filter(Boolean);
  if (!words.length) return "?";
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[words.length - 1][0]).toUpperCase();
}

function percent(value) {
  return `${Math.round(Number(value ?? 0) * 100)}%`;
}

function greeting() {
  const hour = new Date().getHours();
  if (hour < 5) return "Still up?";
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}

function selectedTabLabel() {
  return TABS.find((tab) => tab.id === S.ui.tab)?.label || "Today";
}

function pageSubtitle() {
  if (S.ui.settingsOpen) return "Tune Panadoro to your rhythm";
  switch (S.ui.tab) {
    case "today":
      return new Date().toLocaleDateString(currentLanguage() === "ar" ? "ar" : "en-US", {
        weekday: "long",
        day: "numeric",
        month: "long",
      });
    case "tasks":
      return `${openTaskCount()} open · ${dueTodayTaskCount()} due today`;
    case "focus": {
      const tag = activeTag();
      return tag
        ? `${tag.name} · ${tag.focusMinutes}m focus · ${tag.cycles} cycles`
        : "Pick a tag to begin";
    }
    case "stats":
      return "The shape of your time";
    case "ranks":
      return S.auth.signedIn
        ? "Combined focus across every public tag"
        : "Sign in to join the shared board";
    default:
      return "";
  }
}

function syncIcon() {
  if (!S.auth.signedIn) return "cloud-off";
  switch (S.sync.status) {
    case "synced": return "cloud-done";
    case "error": return "cloud-off";
    case "syncing": return "sync";
    default: return "cloud";
  }
}

function syncTitle() {
  if (!S.auth.signedIn) return "Sign in to sync between devices";
  return S.sync.message || "Sync now";
}

/* ── Layout ─────────────────────────────────────────────────────────── */

function railHTML() {
  const signed = S.auth.signedIn;
  const name = signed ? effectiveDisplayName() : "Local mode";
  const sub = signed ? S.auth.email : "Not signed in";
  return `
    <aside class="rail">
      <div class="brand">
        <div class="brand-mark">${icon("flame")}</div>
        <div>
          <div class="brand-name">Panadoro</div>
          <div class="brand-sub">habits · tasks · focus</div>
        </div>
      </div>
      <nav class="rail-nav">
        ${TABS.map((tab) => navItem(tab, false)).join("")}
      </nav>
      <div class="rail-bottom">
        <button class="profile-chip" data-action="settings">
          <span class="avatar" ${signed ? tagStyle({ accent: "primary" }) : ""}>${initials(name)}</span>
          <span class="profile-copy"><strong>${esc(name)}</strong><span>${esc(sub)}</span></span>
        </button>
        <button class="nav-item ${S.ui.settingsOpen ? "active" : ""}" data-action="settings">
          ${icon("settings")}<span class="nav-label">Settings</span>
        </button>
      </div>
    </aside>`;
}

function navItem(tab, bottom) {
  const active = !S.ui.settingsOpen && S.ui.tab === tab.id;
  let badge = "";
  if (tab.id === "tasks") {
    const count = openTaskCount();
    if (count > 0) badge = `<span class="nav-badge">${count}</span>`;
  } else if (tab.id === "focus" && timerRunning()) {
    badge = `<span class="nav-badge dot"></span>`;
  } else if (tab.id === "today") {
    const total = todayHabits().length;
    const done = todayHabits().filter((habit) => habitDone(habit)).length;
    if (total > 0) badge = `<span class="nav-badge">${done}/${total}</span>`;
  }
  return `<button class="nav-item ${active ? "active" : ""}" data-action="tab" data-tab="${tab.id}" ${bottom ? 'aria-label="' + tab.label + '"' : ""}>
    ${icon(tab.icon)}<span class="nav-label">${tab.label}</span>${badge}
  </button>`;
}

function topbarHTML() {
  const timer = S.timer || null;
  return `
    <header class="topbar">
      <div class="topbar-title">
        <h1>${esc(S.ui.settingsOpen ? "Settings" : selectedTabLabel())}</h1>
        <p>${esc(pageSubtitle())}</p>
      </div>
      <div class="topbar-actions">
        ${timerRunning() ? `
          <button class="timer-chip ${timerPhase() !== "focus" ? "break" : ""}" data-action="go-focus" title="Open the timer">
            ${icon(timerPhase() === "focus" ? "brain" : "coffee")}
            <span data-live-chip-time>${fmtClock(timerSecondsLeft())}</span>
          </button>` : ""}
        <button class="icon-btn" data-action="sync" title="${esc(syncTitle())}">${icon(syncIcon())}</button>
        <button class="icon-btn" data-action="settings" title="Settings">${icon("settings")}</button>
      </div>
    </header>`;
}

function bottomNavHTML() {
  return `<nav class="bottom-nav">${TABS.map((tab) => navItem(tab, true)).join("")}</nav>`;
}

function shellHTML() {
  return `
    <div class="shell">
      ${railHTML()}
      <div class="main">
        ${topbarHTML()}
        <main class="page" id="page">${pageHTML()}</main>
      </div>
    </div>
    ${bottomNavHTML()}`;
}

function pageHTML() {
  if (S.ui.settingsOpen) return settingsPage();
  switch (S.ui.tab) {
    case "tasks": return tasksPage();
    case "focus": return focusPage();
    case "stats": return statsPage();
    case "ranks": return ranksPage();
    default: return todayPage();
  }
}

/* ── Auth gate ──────────────────────────────────────────────────────── */

function authGateHTML() {
  const creating = S.ui.authCreate === true;
  return `
    <div class="auth">
      <div class="auth-blobs"><div class="auth-blob a"></div><div class="auth-blob b"></div><div class="auth-blob c"></div></div>
      <div class="auth-card">
        <div class="auth-brand">
          <div class="auth-mark">${icon("flame")}</div>
          <h1>Panadoro</h1>
          <p>Habits, tasks and tagged focus — in one quiet place.</p>
        </div>
        <div class="auth-mode">
          <h2>${creating ? "Make it yours" : "Welcome back"}</h2>
          <p>${creating
            ? "Create an account and your focus follows you to every device."
            : "Sign in to pick up your habits, tasks and focus where you left them."}</p>
        </div>
        <form class="auth-form" data-form="auth">
          <div class="field">
            <label for="auth-email">Email</label>
            <input id="auth-email" data-input="auth-email" type="email" autocomplete="email"
              value="${attr(S.ui.authEmail || "")}" placeholder="you@example.com" required>
          </div>
          <div class="field">
            <label for="auth-password">Password</label>
            <input id="auth-password" data-input="auth-password" type="password"
              autocomplete="${creating ? "new-password" : "current-password"}"
              value="${attr(S.ui.authPassword || "")}" placeholder="At least 6 characters" required>
          </div>
          ${S.auth.error ? `<div class="auth-error">${esc(S.auth.error)}</div>` : ""}
          <button class="btn filled" type="submit" ${S.auth.busy ? "disabled" : ""}>
            ${S.auth.busy ? `<span class="spinner"></span> Talking to Firebase…` : `${icon(creating ? "person" : "logout")} ${creating ? "Create account" : "Sign in"}`}
          </button>
        </form>
        <div class="auth-switch">
          ${creating ? "Already have an account?" : "New to Panadoro?"}
          <button data-action="auth-mode">${creating ? "Sign in" : "Create one"}</button>
        </div>
        <button class="auth-skip" data-action="skip-auth">Continue without an account</button>
        <div class="auth-note">Your habits, tasks and notes stay private. The leaderboard only sees the tags you choose to publish.</div>
      </div>
    </div>`;
}

/* ── Today ──────────────────────────────────────────────────────────── */

function todayPage() {
  const scheduled = todayHabits();
  const filter = S.ui.selectedCategory;
  const base = filter === "all" ? scheduled : scheduled.filter((habit) => String(habit.categoryId) === filter);
  const visible = S.settings.showCompleted ? base : base.filter((habit) => !habitDone(habit));
  const done = visible.filter((habit) => habitDone(habit)).length;
  const total = visible.length;
  const progress = total ? done / total : 0;
  const allDone = total > 0 && done === total;
  const totalHabits = scheduled.length;
  const doneHabits = scheduled.filter((habit) => habitDone(habit)).length;
  const subtitle = total === 0
    ? "Nothing scheduled. Add one small thing."
    : `${done} of ${total} habits · ${fmtMin(todayFocusMinutes())} focused today`;
  return `
    <div class="stack">
      <section class="hero">
        <div class="hero-icon">${icon(allDone ? "trophy" : "sun")}</div>
        <div class="hero-copy">
          <h2>${esc(greeting())}${allDone ? ", all done." : "."}</h2>
          <p>${esc(subtitle)}</p>
        </div>
        <div class="hero-side">
          ${progressRingHTML(progress, done, total)}
        </div>
      </section>

      <div class="stat-grid">
        ${statTile("check", `${doneHabits} / ${totalHabits}`, "Habits today", `${totalHabits ? Math.round((doneHabits * 100) / totalHabits) : 0}% complete`, "primary")}
        ${statTile("brain", fmtMin(todayFocusMinutes()), "Focused today", "Goals per tag", "tertiary")}
        ${statTile("flame", String(focusStreak()), "Focus streak", "Keep the thread", "secondary")}
      </div>

      <div class="section-title">
        <h2>Today</h2>
        ${filter !== "all" ? `
          <button class="icon-btn small" data-action="edit-category" data-id="${attr(filter)}" title="Edit category">${icon("edit")}</button>
          <button class="icon-btn small" data-action="delete-category" data-id="${attr(filter)}" title="Delete category">${icon("trash")}</button>` : ""}
        <button class="btn tonal" data-action="new-category">${icon("plus")} Category</button>
        <button class="btn filled" data-action="new-habit">${icon("plus")} Habit</button>
      </div>

      <div class="chip-row">
        <button class="chip ${filter === "all" ? "active" : ""}" data-action="select-category" data-id="all"
          style="--accent:var(--primary);--on-accent:var(--on-primary)">
          ${icon("apps")} All <small>${totalHabits}</small>
        </button>
        ${activeCategories().map((category) => `
          <button class="chip ${filter === String(category.id) ? "active" : ""}" data-action="select-category" data-id="${attr(category.id)}" ${tagStyle(category)}>
            ${icon(category.icon)} ${esc(category.name)}
          </button>`).join("")}
      </div>

      ${visible.length === 0 ? emptyHTML(
        filter === "all" ? "checklist" : "tag",
        filter === "all" && base.length === 0 ? "A clear day" : filter === "all" ? "Everything is done" : "Nothing here",
        filter === "all" && base.length === 0
          ? "Add a habit you can keep, and it will wait for you here."
          : filter === "all"
            ? "Completed habits stay tucked away while you enjoy the quiet."
            : "No habits in this category are scheduled today."
      ) : `<div class="habit-grid">${visible.map((habit, index) => habitCard(habit, index)).join("")}</div>`}

      <div style="height:40px"></div>
    </div>`;
}

function progressRingHTML(progress, done, total) {
  const circumference = 2 * Math.PI * 26;
  const offset = circumference * (1 - Math.max(0, Math.min(1, Number(progress ?? 0))));
  return `<div class="progress-ring" title="${done}/${total}">
    <svg viewBox="0 0 64 64" aria-hidden="true">
      <circle class="track" cx="32" cy="32" r="26"></circle>
      <circle class="value" cx="32" cy="32" r="26" stroke-dasharray="${circumference}" stroke-dashoffset="${offset}"></circle>
    </svg>
    <span>${done}/${total}</span>
  </div>`;
}

function statTile(symbol, value, label, hint, role = "primary", flame = false) {
  return `
    <div class="stat-tile" style="--tile-bg:var(--${role}-container);--tile-ink:var(--on-${role}-container)">
      <div class="stat-icon">${icon(symbol)}</div>
      <div>
        <div class="stat-value">${esc(value)}</div>
        <div class="stat-label">${esc(label)}</div>
        ${hint ? `<div class="stat-hint">${esc(hint)}</div>` : ""}
      </div>
    </div>`;
}

function habitCard(habit, index) {
  const category = categoryById(habit.categoryId);
  const done = habitDone(habit);
  const target = Math.max(1, Number(habit.target ?? 1));
  const value = habitValue(habit.id);
  const streak = currentStreak(habit);
  const week = thisWeekDays().map((key) => {
    const scheduled = scheduledOn(habit, key);
    const doneDay = habitDone(habit, key);
    const isToday = key === todayKey();
    const future = key > todayKey();
    return `<button class="day-cell ${scheduled ? "scheduled" : ""} ${doneDay ? "done" : ""} ${isToday ? "today" : ""}"
      data-action="habit-day" data-id="${attr(habit.id)}" data-date="${key}"
      ${!scheduled || future ? "disabled" : ""} title="${esc(relativeDay(key))}">
      <span class="d">${esc(weekdayName(key).slice(0, 1))}</span>
      <span class="v">${doneDay ? "✓" : target > 1 ? `${habitValue(habit.id, key)}` : "·"}</span>
    </button>`;
  }).join("");
  return `
    <article class="habit ${done ? "done" : ""}" data-action="edit-habit" data-id="${attr(habit.id)}" ${tagStyle(category)}>
      <div class="habit-icon" ${shapeStyle(habit.icon === "check_circle" ? "Cookie12Sided" : "Cookie7Sided")}>${icon(habit.icon)}</div>
      <div class="habit-copy">
        <div class="habit-name-row">
          <span class="habit-name">${esc(habit.name)}</span>
          ${streak > 0 ? `<span class="pill priority-1">${icon("flame")} ${streak}</span>` : ""}
        </div>
        <div class="habit-meta">
          <span class="tag" style="color:${tagColor(category)}">${esc(category.name)}</span>
          ${habit.time ? `<span>· ${esc(habit.time)}</span>` : ""}
          ${target > 1 ? `<span style="margin-left:auto">${value} of ${target} ${esc(habit.unit)}</span>` : ""}
        </div>
        ${target > 1 ? `<div class="habit-progress"><span style="width:${habitProgress(habit) * 100}%"></span></div>` : ""}
        <div class="week">${week}</div>
      </div>
      <button class="check-btn ${done ? "done" : ""}" data-action="toggle-habit" data-id="${attr(habit.id)}"
        title="${done ? "Mark not done" : "Mark done"}">${icon(done ? "check" : "plus")}</button>
    </article>`;
}

/* ── Tasks ──────────────────────────────────────────────────────────── */

function tasksPage() {
  const filter = S.ui.taskFilter;
  const tasks = taskFiltered(filter);
  const showingDone = filter === "done";
  return `
    <div class="stack">
      <section class="hero">
        <div class="hero-icon">${icon(tasks.length ? "checklist" : "check")}</div>
        <div class="hero-copy">
          <h2>${tasks.length === 0 ? (showingDone ? "No history yet." : "Nothing left in this list.") : "One thing at a time."}</h2>
          <p>${openTaskCount()} open · ${dueTodayTaskCount()} due today · ${doneTaskCount()} done</p>
        </div>
        <div class="hero-side"><div class="stat-value" style="font-size:38px">${tasks.length}</div></div>
      </section>

      <div class="stat-grid">
        ${statTile("focus", String(openTaskCount()), "Open tasks", "", "primary")}
        ${statTile("calendar", String(dueTodayTaskCount()), "Due today", overdueTaskCount() ? `${overdueTaskCount()} overdue` : "", "tertiary")}
        ${statTile("check", String(doneTaskCount()), "Finished", "", "success")}
      </div>

      <div class="row">
        <h2 style="flex:1">Tasks</h2>
        <button class="btn tonal" data-action="new-task">${icon("plus")} Task</button>
      </div>

      <div class="segmented">
        ${[
          ["open", "Open", "focus"],
          ["today", "Today", "calendar"],
          ["upcoming", "Upcoming", "calendar"],
          ["done", "Done", "check"],
        ].map(([id, label, symbol]) => `<button class="${filter === id ? "active" : ""}" data-action="task-filter" data-filter="${id}">${icon(symbol)} ${label}</button>`).join("")}
      </div>

      ${tasks.length === 0 ? emptyHTML(
        showingDone ? "clock" : "checklist",
        showingDone ? "No history yet" : "The list is clear",
        showingDone ? "Finished tasks will collect here." : "Add a task, or enjoy the empty space."
      ) : `<div class="task-list">${tasks.map((task, index) => taskRow(task, index)).join("")}</div>`}

      <div style="height:30px"></div>
    </div>`;
}

function taskRow(task, index) {
  const category = categoryById(task.categoryId);
  const tag = tagById(task.taskTag);
  const expanded = S.ui.expandedTasks.has(String(task.id));
  const subs = Array.from(task.subtasks ?? []);
  const progress = taskSubtaskProgress(task);
  const overdue = !task.done && task.due && task.due < todayKey();
  return `
    <article class="task ${task.done ? "done" : ""} ${expanded ? "open" : ""}" style="--i:${index % 10}">
      <div class="task-main">
        <button class="task-check" data-action="toggle-task" data-id="${attr(task.id)}" title="${task.done ? "Mark open" : "Mark done"}">
          ${task.done ? icon("check") : ""}
        </button>
        <div class="task-copy" data-action="edit-task" data-id="${attr(task.id)}">
          <div class="task-title">${esc(task.title)}</div>
          <div class="task-meta">
            ${tag ? `<span class="pill tag" ${tagStyle(tag)}>${esc(tag.name)}</span>` : ""}
            <span>${esc(category.name)}</span>
            ${task.due ? `<span class="pill ${overdue ? "overdue" : ""}">${esc(relativeDay(task.due))}</span>` : ""}
            ${Number(task.priority) > 0 ? `<span class="pill priority-${task.priority}">${task.priority === 2 ? "High" : "Medium"}</span>` : ""}
            ${subs.length ? `<span class="pill">${subs.filter((sub) => sub.done).length}/${subs.length}</span>` : ""}
          </div>
        </div>
        ${progress >= 0 ? `<div class="habit-progress" style="width:44px"><span style="width:${progress * 100}%"></span></div>` : ""}
        ${subs.length ? `<button class="task-sub-toggle" data-action="expand-task" data-id="${attr(task.id)}" title="Subtasks">${icon("chevron-down")}</button>` : ""}
        <button class="icon-btn small" data-action="edit-task" data-id="${attr(task.id)}" title="Edit task">${icon("edit")}</button>
      </div>
      ${expanded ? `
        <div class="task-sub">
          ${subs.map((sub) => `
            <div class="sub-row ${sub.done ? "done" : ""}">
              <button class="sub-check ${sub.done ? "done" : ""}" data-action="toggle-subtask" data-id="${attr(task.id)}" data-sub="${attr(sub.id)}">${sub.done ? icon("check") : ""}</button>
              <span>${esc(sub.title)}</span>
              <button class="sub-remove" data-action="remove-subtask" data-id="${attr(task.id)}" data-sub="${attr(sub.id)}" title="Remove">${icon("close")}</button>
            </div>`).join("")}
          <form class="sub-add" data-form="subtask" data-id="${attr(task.id)}">
            <input name="title" placeholder="Add a subtask" autocomplete="off">
            <button class="btn small tonal" type="submit">${icon("plus")}</button>
          </form>
        </div>` : ""}
    </article>`;
}

/* ── Focus ──────────────────────────────────────────────────────────── */

function focusPage() {
  const tags = activeTags();
  if (!tags.length) {
    return `<div class="stack">${emptyHTML("focus", "No focus tags yet", "Create a tag with its own Pomodoro rhythm.")}<div class="row" style="justify-content:center"><button class="btn filled" data-action="new-tag">${icon("plus")} New tag</button></div></div>`;
  }
  const tag = activeTag();
  const colors = tagStyle(tag);
  const running = timerRunning();
  const onBreak = timerPhase() !== "focus";
  const progress = timerProgress();
  const phase = timerPhase();
  const phaseLabel = phase === "shortBreak" ? "Short break" : phase === "longBreak" ? "Long break" : "Focus";
  const goal = Math.max(0, Number(tag?.dailyGoalMinutes ?? 0));
  const goalProgress = Math.max(0, tagGoalProgress(tag));
  const cycles = Math.max(1, Number(tag?.cycles ?? 4));
  const cycle = timerCycle();
  const tasks = tasksForTag(tag?.id ?? "");
  const weekStats = tagStats(7);
  const weekMax = Math.max(1, ...weekStats.map((stat) => stat.seconds));
  const recent = recentSessions(5);
  const circumference = 2 * Math.PI * 45;

  return `
    <div class="stack" ${tagStyle(tag)}>
      <div class="row wrap">
        <div class="chip-row" style="flex:1">
          ${tags.map((item) => `
            <button class="chip ${item.id === tag?.id ? "active" : ""}" data-action="focus-tag" data-id="${attr(item.id)}" ${tagStyle(item)} ${running ? "disabled" : ""}>
              ${icon(item.icon)} ${esc(item.name)} ${tagTodayMinutes(item.id) ? `<small>${fmtMin(tagTodayMinutes(item.id))}</small>` : ""}
            </button>`).join("")}
        </div>
        <button class="icon-btn" data-action="new-tag" title="New tag">${icon("plus")}</button>
        <button class="icon-btn" data-action="edit-tag" data-id="${attr(tag.id)}" title="Edit tag">${icon("edit")}</button>
        ${tags.length > 1 ? `<button class="icon-btn" data-action="delete-tag" data-id="${attr(tag.id)}" title="Delete tag">${icon("trash")}</button>` : ""}
      </div>

      <section class="focus-stage ${onBreak ? "break" : ""}" ${tagStyle(tag)}>
        <div class="focus-copy">
          <span class="phase">${icon(onBreak ? "coffee" : "brain")} ${phaseLabel}</span>
          <h2 data-live-time>${fmtClock(timerSecondsLeft())}</h2>
          <div class="tag-line">${esc(tag.name)} · ${tag.focusMinutes}m focus · ${cycles} cycles</div>
          <div class="cycle-dots">${Array.from({ length: cycles }, (_, index) => `<span class="cycle-dot ${index < cycle ? "on" : ""}"></span>`).join("")}</div>
          <div class="timer-actions">
            <button class="btn filled" data-action="timer-toggle">${icon(running ? "pause" : "play")} ${running ? "Pause" : timerSecondsLeft() <= 0 ? "Again" : "Start"}</button>
            <button class="btn tonal" data-action="timer-reset" title="Reset this phase">${icon("reset")} Reset</button>
            <button class="btn ghost" data-action="timer-skip" title="Skip to the next phase">${icon("skip")} Skip</button>
          </div>
        </div>
        <div class="timer-ring" ${tagStyle(tag)}>
          <svg viewBox="0 0 100 100">
            <circle class="track" cx="50" cy="50" r="45"></circle>
            <circle class="value" data-live-ring cx="50" cy="50" r="45"
              stroke-dasharray="${circumference}" stroke-dashoffset="${circumference * (1 - progress)}"></circle>
          </svg>
          <div class="center"><strong data-live-time>${fmtClock(timerSecondsLeft())}</strong><span>${phaseLabel}</span></div>
        </div>
      </section>

      ${goal > 0 ? `
        <div class="goal-bar" ${tagStyle(tag)}>
          ${icon("target")} <span>Today's goal</span>
          <div class="track"><span style="width:${goalProgress * 100}%"></span></div>
          <b>${fmtMin(tagTodayMinutes(tag.id))} / ${fmtMin(goal)}</b>
        </div>` : ""}

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Session note</h3><p>A line for the session you are in right now.</p></div></div>
        <textarea class="note-field" id="focus-note" data-input="focus-note" placeholder="What are you working on?">${esc(S.data.timer?.note ?? "")}</textarea>
      </div>

      <div class="panel" ${tagStyle(tag)}>
        <div class="panel-head"><div class="copy"><h3>${esc(tag.name)} tasks</h3><p>Small steps that belong to this tag.</p></div></div>
        ${tasks.length === 0 ? `<p class="muted" style="font-size:11px">No tasks for this tag yet.</p>` : `
          <div class="task-list">
            ${tasks.map((task) => `
              <div class="task ${task.done ? "done" : ""}" style="padding:8px 10px">
                <div class="task-main">
                  <button class="task-check" data-action="toggle-task" data-id="${attr(task.id)}">${task.done ? icon("check") : ""}</button>
                  <div class="task-copy"><div class="task-title" style="font-size:12px">${esc(task.title)}</div></div>
                  <button class="icon-btn small" data-action="edit-task" data-id="${attr(task.id)}">${icon("edit")}</button>
                </div>
              </div>`).join("")}
          </div>`}
        <form class="tag-task-add" data-form="tag-task" data-id="${attr(tag.id)}">
          <input name="title" placeholder="Add a task for ${esc(tag.name)}" autocomplete="off">
          <button class="btn small tonal" type="submit">${icon("plus")}</button>
        </form>
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>This week, by tag</h3></div><span class="muted" style="font-size:11px">${fmtMin(focusMinutesInRange(7))}</span></div>
        ${weekStats.length === 0 ? `<p class="muted" style="font-size:11px">Finish a focus session and its tag will appear here.</p>` : `
          <div class="bar-list">
            ${weekStats.map((stat) => `
              <div class="bar-list-row" ${tagStyle(stat.tag)}>
                <span>${esc(stat.name)}</span><b style="font-size:10px;color:var(--accent)">${fmtMin(stat.seconds / 60)}</b>
                <div class="track"><span style="width:${(stat.seconds / weekMax) * 100}%"></span></div>
              </div>`).join("")}
          </div>`}
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Recent sessions</h3></div><span class="muted" style="font-size:11px">${fmtMin(todayFocusMinutes())} today</span></div>
        ${recent.length === 0 ? `<p class="muted" style="font-size:11px">No sessions yet. The first one is the hardest.</p>` : `
          <div>${recent.map((session) => {
            const tag = tagById(session.tagId);
            return `<div class="session-row" ${tagStyle(tag)}>
              <span class="dot"></span>
              <span>${esc(tag?.name || "Focus")}</span>
              <span class="meta">${fmtMin(session.durationSec / 60)} · ${esc(relativeDay(dateKey(new Date(session.endedAt))))}</span>
            </div>`;
          }).join("")}</div>`}
      </div>

      <div style="height:30px"></div>
    </div>`;
}

/* ── Stats ──────────────────────────────────────────────────────────── */

function statsPage() {
  const range = S.ui.statsRange;
  const tags = tagStats(range);
  const focusMinutes = focusMinutesInRange(range);
  const sessionCount = sessionsInRange(range, "focus").length;
  const average = Math.round(focusMinutes / range);
  const best = bestFocusDay(range);
  const breakInfo = breakRatio(range);
  const dayParts = timeOfDayStats(range);
  const series = S.ui.statsMode === "breaks"
    ? dailyFocusSeriesForChart(range)
    : dailyTagFocusSeriesForChart(range);
  const focusSlices = tags.filter((stat) => stat.seconds > 0).map((stat) => ({
    name: stat.name,
    value: stat.seconds,
    color: tagColor(stat.tag),
    tag: stat.tag,
  }));
  const labelEvery = range <= 7 ? 1 : range <= 30 ? 3 : 14;
  const chartDays = Math.min(range, 30);
  const chartLabelEvery = chartDays <= 7 ? 1 : chartDays <= 14 ? 2 : 5;
  const dayPartMax = Math.max(1, ...dayParts.map((part) => part.seconds));

  return `
    <div class="stack">
      <section class="hero">
        <div class="hero-icon">${icon("stats")}</div>
        <div class="hero-copy">
          <h2>${focusMinutes > 0 ? "You gave the time a shape." : "The first session is a blank page."}</h2>
          <p>${fmtMin(focusMinutes)} focused across ${range} ${range === 1 ? "day" : "days"}</p>
        </div>
        <div class="hero-side">
          <div class="segmented">
            ${[7, 30, 90].map((days) => `<button class="${range === days ? "active" : ""}" data-action="stats-range" data-value="${days}">${days}d</button>`).join("")}
          </div>
        </div>
      </section>

      <div class="stat-grid">
        ${statTile("brain", fmtMin(focusMinutes), "Focus time", `${fmtMin(average)} per day`, "primary")}
        ${statTile("clock", String(sessionCount), "Sessions", `${fmtMin(Math.round(breakInfo.breaks / 60))} break time`, "tertiary")}
        ${statTile("trophy", best.focus > 0 ? fmtMin(Math.round(best.focus / 60)) : "—", "Best day", best.date ? relativeDay(best.date) : "No sessions yet", "success")}
        ${statTile("flame", String(focusStreak()), "Day streak", "Days in a row with focus", "secondary")}
      </div>

      <div class="panel">
        <div class="panel-head">
          <div class="copy"><h3>Focus rhythm</h3><p>${S.ui.statsMode === "breaks" ? "Breaks between focus sessions" : "Focus time by tag, day by day"}</p></div>
          <div class="chip-row">
            <button class="chip ${S.ui.statsMode === "tags" ? "active" : ""}" data-action="stats-mode" data-value="tags" style="--accent:var(--primary);--on-accent:var(--on-primary)">By tag</button>
            <button class="chip ${S.ui.statsMode === "breaks" ? "active" : ""}" data-action="stats-mode" data-value="breaks" style="--accent:var(--tertiary);--on-accent:var(--on-tertiary)">Breaks</button>
          </div>
        </div>
        ${chartHTML(series, labelEvery)}
        ${S.ui.statsMode === "tags" && tags.length ? `<div class="legend">${tags.map((stat) => `<span class="legend-item" style="--accent:${tagColor(stat.tag)}"><i></i>${esc(stat.name)}</span>`).join("")}</div>` : ""}
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Focus split</h3><p>Focused time divided between tags</p></div></div>
        ${focusSlices.length === 0 ? `<p class="muted" style="font-size:11px">No focus time in this range yet.</p>` : donutHTML(focusSlices, donutCenter(focusMinutes))}
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>When you focus</h3></div></div>
        <div class="bar-list">
          ${dayParts.map((part) => `
            <div class="bar-list-row" style="--accent:var(--tertiary)">
              <span>${part.label} <small class="muted">${part.range}</small></span>
              <b style="font-size:10px">${fmtMin(Math.round(part.seconds / 60))}</b>
              <div class="track"><span style="width:${(part.seconds / dayPartMax) * 100}%"></span></div>
            </div>`).join("")}
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Twelve weeks of consistency</h3><p>Each column is a week · darker means more focus</p></div></div>
        ${heatmapHTML(focusHeatmapWeeks(12), "var(--primary)", true)}
        <div class="heat-legend">Quieter ${[0, 1, 2, 3, 4].map((i) => `<i style="background:${i === 0 ? "var(--field)" : `color-mix(in srgb, var(--primary) ${20 + i * 18}%, var(--field))`}"></i>`).join("")} More focus</div>
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Habit consistency</h3></div></div>
        ${habitStats(range).length === 0 ? `<p class="muted" style="font-size:11px">Add a habit to see its consistency here.</p>` : habitStats(range).map((stat) => `
          <div class="habit-stat-row" ${tagStyle({ accent: "primary", color: "" })}>
            <div class="habit-icon" style="width:30px;height:30px;border-radius:10px" ${shapeStyle("Cookie4Sided")}>${icon(stat.habit.icon)}</div>
            <div class="name">${esc(stat.name)}</div>
            <span class="muted" style="font-size:9px">${stat.due ? `${stat.done}/${stat.due}` : "not scheduled"}</span>
            <div class="track"><span style="width:${stat.rate * 100}%;background:${stat.rate >= 1 ? "var(--success)" : "var(--primary)"}"></span></div>
            ${stat.streak > 0 ? `<span class="pill priority-1">${icon("flame")} ${stat.streak}</span>` : ""}
          </div>`).join("")}
      </div>

      <div class="section-title" style="margin-top:4px"><h2>Consistency by tag</h2></div>
      ${activeTags().map((tag) => {
        const stat = tags.find((item) => item.tagId === tag.id);
        const seconds = stat?.seconds ?? 0;
        const percentValue = tagPercent(tag.id, range);
        const streak = tagStreak(tag.id);
        const goalMinutes = Math.max(0, Number(tag.dailyGoalMinutes ?? 0));
        const today = tagTodayMinutes(tag.id);
        const progress = Math.max(0, tagGoalProgress(tag));
        return `
          <div class="tag-card" ${tagStyle(tag)}>
            <div class="tag-card-head">
              <div class="habit-icon" style="width:38px;height:38px;border-radius:12px" ${shapeStyle(tag.shape)}>${icon(tag.icon)}</div>
              <div class="copy"><h4>${esc(tag.name)}</h4><p>${percent(percentValue)} of focus · ${fmtMin(seconds / 60)}</p></div>
              ${streak > 0 ? `<span class="pill priority-1">${icon("flame")} ${streak} day${streak === 1 ? "" : "s"}</span>` : ""}
            </div>
            ${chartHTML(tagFocusSeries(tag.id, chartDays), chartLabelEvery, goalMinutes * 60)}
            ${heatmapHTML(focusHeatmapWeeks(12, tag.id), tagColor(tag), true)}
            <div class="mini-pills">
              <span class="mini-pill">${icon("clock")} ${stat?.sessions ?? 0} sessions</span>
              <span class="mini-pill">${icon("target")} ${goalMinutes > 0 ? `${fmtMin(today)} / ${fmtMin(goalMinutes)} today` : "No daily goal"}</span>
            </div>
            ${goalMinutes > 0 ? `<div class="habit-progress"><span style="width:${progress * 100}%"></span></div>` : ""}
          </div>`;
      }).join("")}

      <div style="height:30px"></div>
    </div>`;
}

function dailyFocusSeriesForChart(days) {
  return dailyFocusSeries(days);
}
function dailyTagFocusSeriesForChart(days) {
  return dailyTagFocusSeries(days);
}

function chartHTML(series, labelEvery = 1, goalSeconds = 0) {
  if (!series.length) return `<div class="empty"><div class="empty-badge">${icon("stats")}</div><p>No chart data yet.</p></div>`;
  const max = Math.max(1, goalSeconds, ...series.map((day) => Number(day.total ?? 0)));
  const bars = series.map((day) => {
    const height = (Number(day.total ?? 0) / max) * 100;
    const segments = Array.from(day.segments ?? []).filter((segment) => Number(segment.seconds ?? 0) > 0);
    return `<div class="bar-slot" title="${esc(day.label)} · ${fmtSeconds(day.total)}">
      <div class="bar" style="height:${height}%">
        ${segments.map((segment) => {
          const share = Number(segment.seconds ?? 0) / Math.max(1, Number(day.total ?? 0));
          const color = segment.tag ? tagColor(segment.tag) : "var(--tertiary)";
          return `<span class="bar-seg" style="height:${share * 100}%;background:${color}"></span>`;
        }).join("")}
      </div>
    </div>`;
  }).join("");
  const labels = series.map((day, index) => `<span>${index % Math.max(1, labelEvery) === 0 ? esc(day.label) : ""}</span>`).join("");
  const goal = goalSeconds > 0 && goalSeconds <= max
    ? `<div class="goal-line" style="bottom:${(goalSeconds / max) * 100}%"></div>`
    : "";
  return `<div class="chart-wrap"><div class="chart" style="position:relative">${goal}${bars}</div><div class="chart-labels">${labels}</div></div>`;
}

function donutCenter(minutes) {
  const value = Math.max(0, Math.round(Number(minutes ?? 0)));
  if (value < 60) return `${value}m`;
  const hours = value / 60;
  return hours >= 10 ? `${Math.round(hours)}h` : `${hours.toFixed(1)}h`;
}

function donutHTML(slices, center) {
  const total = slices.reduce((sum, slice) => sum + Number(slice.value ?? 0), 0);
  if (total <= 0) return `<p class="muted" style="font-size:11px">No focus time yet.</p>`;
  const circumference = 2 * Math.PI * 54;
  let offset = 0;
  const rings = slices.map((slice) => {
    const length = (Number(slice.value) / total) * circumference;
    const ring = `<circle cx="84" cy="84" r="54" fill="none" stroke="${slice.color}" stroke-width="18"
      stroke-dasharray="${length} ${circumference - length}" stroke-dashoffset="${-offset}"></circle>`;
    offset += length;
    return ring;
  }).join("");
  return `<div class="donut-wrap">
    <div class="donut">
      <svg viewBox="0 0 168 168">
        <circle cx="84" cy="84" r="54" fill="none" stroke="var(--field)" stroke-width="18"></circle>
        ${rings}
      </svg>
      <div class="donut-center"><strong>${esc(center)}</strong><span>focused</span></div>
    </div>
    <div class="donut-legend">
      ${slices.map((slice) => `<div class="donut-row" style="--accent:${slice.color}">
        <i style="background:${slice.color}"></i>
        <span class="name">${esc(slice.name)}</span>
        <b style="color:${slice.color}">${Math.round((slice.value / total) * 100)}%</b>
        <em>${fmtSeconds(slice.value)}</em>
      </div>`).join("")}
    </div>
  </div>`;
}

function heatmapHTML(values, accent, labels = false) {
  const max = Math.max(1, ...values.map((cell) => Number(cell.value ?? 0)));
  return `<div class="heatmap">${values.map((cell) => {
    const value = Number(cell.value ?? 0);
    const intensity = value <= 0 ? 0 : 22 + 78 * Math.sqrt(value / max);
    const background = value <= 0
      ? "var(--field)"
      : `color-mix(in srgb, ${accent} ${intensity}%, var(--field))`;
    const today = cell.date === todayKey();
    return `<div class="heat-cell ${cell.future ? "future" : ""} ${today ? "today" : ""}"
      style="background:${background}" title="${esc(cell.date)} · ${fmtSeconds(value)}"></div>`;
  }).join("")}</div>`;
}

/* ── Ranks ──────────────────────────────────────────────────────────── */

function ranksPage() {
  const period = S.ui.rankPeriod;
  const entries = Array.isArray(S.board.entries) ? S.board.entries : [];
  const ranked = entries.map((entry) => ({
    ...entry,
    seconds: (entry.tags ?? [])
      .reduce((sum, tag) => sum + Number(tag[period] ?? 0), 0),
  })).sort((a, b) => b.seconds - a.seconds || a.displayName.localeCompare(b.displayName));
  const max = Math.max(1, ...ranked.map((entry) => entry.seconds));
  const top = ranked.slice(0, 3);
  const podiumOrder = top.length === 0 ? [] : top.length >= 3 ? [1, 0, 2] : top.length === 2 ? [1, 0] : [0];
  const podium = podiumOrder.map((index) => ({ ...top[index], rank: index + 1 }));
  const updated = S.board.updatedAt
    ? new Date(S.board.updatedAt).toLocaleTimeString(currentLanguage() === "ar" ? "ar" : [], {
        hour: "2-digit",
        minute: "2-digit",
      })
    : "";
  const boardPending = S.board.loading && ranked.length === 0;

  return `
    <div class="stack">
      <section class="hero">
        <div class="hero-icon">${icon("trophy")}</div>
        <div class="hero-copy">
          <h2>Focus ranks</h2>
          <p>${S.auth.signedIn
            ? "Combined focus across every public tag. Never habits, tasks or notes."
            : "Sign in to add your focus to the community board."}</p>
        </div>
        <div class="hero-side">
          <div class="segmented">
            ${[["day", "Today"], ["week", "Week"], ["month", "Month"]].map(([id, label]) => `<button class="${period === id ? "active" : ""}" data-action="rank-period" data-value="${id}">${label}</button>`).join("")}
          </div>
          <button class="icon-btn" data-action="refresh-board" title="Refresh the leaderboard" ${!S.auth.signedIn ? "disabled" : ""}>${icon(S.board.loading ? "sync" : "refresh")}</button>
        </div>
      </section>

      ${!S.auth.signedIn ? `
        <div class="panel">
          <div class="panel-head"><div class="copy"><h3>The ranks are a shared room</h3><p>Sign in, choose a display name and flip the tags you want to share. Private tags stay on this device.</p></div></div>
          <button class="btn filled" style="width:100%" data-action="open-auth">${icon("logout")} Sign in or create an account</button>
        </div>` : ""}

      ${S.board.loading ? `<div class="loading-line"><span class="spinner"></span> Listening to the other devices…</div>` : ""}
      ${S.board.error ? `<div class="error-line">${esc(S.board.error)}</div>` : ""}

      ${boardPending ? ranksSkeletonHTML() : `
        ${podium.length > 0 ? `<div class="podium">${podium.map((entry) => podiumCard(entry, period)).join("")}</div>` : ""}

        ${ranked.length > 0 ? `
          <div class="row" style="margin-top:4px"><h2 style="flex:1">Everyone</h2><span class="muted" style="font-size:10.5px">${ranked.length} account${ranked.length === 1 ? "" : "s"}</span></div>
          <div class="rank-list">
            ${ranked.map((entry, index) => rankRow(entry, index, max, period)).join("")}
          </div>` : `
          <div class="panel">
            <div class="empty">
              <div class="empty-badge">${icon("trophy")}</div>
              <h3>${S.auth.signedIn ? "The podium is waiting." : "No shared focus yet."}</h3>
              <p>${S.auth.signedIn ? "Publish a tag below and the first focus session puts you on the board." : "Sign in and make one tag public to start the board."}</p>
            </div>
          </div>`}
      `}

      ${S.auth.signedIn ? `
        <div class="panel">
          <div class="panel-head"><div class="copy"><h3>How you appear</h3><p>A display name for the ranks. Your email is never published.</p></div></div>
          <form class="row" data-form="rank-name">
            <div class="field" style="flex:1"><input name="displayName" value="${attr(S.settings.displayName || "")}" placeholder="${attr(effectiveDisplayName())}" maxlength="40"></div>
            <button class="btn filled" type="submit">${icon("check")} Save</button>
          </form>
        </div>

        <div class="panel">
          <div class="panel-head"><div class="copy"><h3>Public tags</h3><p>Flip a tag on to share its focus time for today, this week and this month.</p></div></div>
          <div class="public-list">
            ${activeTags().map((tag) => {
              const on = S.settings.publicTagIds.includes(String(tag.id));
              return `<div class="public-row" ${tagStyle(tag)}>
                <div class="habit-icon" style="width:32px;height:32px;border-radius:11px" ${shapeStyle(tag.shape)}>${icon(tag.icon)}</div>
                <div class="copy"><strong>${esc(tag.name)}</strong><span class="${on ? "on" : ""}">${on ? "Visible on the ranks" : "Private · stays here"}</span></div>
                <button class="switch ${on ? "on" : ""}" data-action="public-toggle" data-id="${attr(tag.id)}" aria-label="Toggle ${attr(tag.name)} public"></button>
              </div>`;
            }).join("")}
          </div>
          <p class="muted" style="font-size:9.5px;margin-top:9px">${S.settings.publicTagIds.length} of ${activeTags().length} tags public · habits, tasks and notes are never uploaded to the board.</p>
        </div>` : ""}

      <p class="muted" style="text-align:center;font-size:9.5px;margin-top:4px">${updated ? `Updated ${updated} · the board refreshes every minute` : "Waiting for the first sync."}</p>
      <div style="height:30px"></div>
    </div>`;
}

function ranksSkeletonHTML() {
  return `<div class="skeleton-page">
    <div class="podium">
      <div class="skeleton" style="height:164px;flex:1"></div>
      <div class="skeleton" style="height:184px;flex:1"></div>
      <div class="skeleton" style="height:154px;flex:1"></div>
    </div>
    <div class="skeleton skeleton-line" style="margin-top:8px"></div>
    <div class="skeleton skeleton-row"></div>
    <div class="skeleton skeleton-row"></div>
    <div class="skeleton skeleton-row"></div>
  </div>`;
}

function podiumCard(entry, period) {
  const topTag = Array.from(entry.tags ?? []).sort((a, b) => Number(b[period]) - Number(a[period]))[0];
  const accent = topTag ? tagColor(topTag) : "var(--primary)";
  const medal = entry.rank === 1 ? "#E7B84B" : entry.rank === 2 ? "#B9C4CE" : "#C98B5B";
  const you = String(entry.uid) === String(S.auth.uid);
  return `<div class="podium-card ${entry.rank === 1 ? "first" : entry.rank === 2 ? "second" : "third"} ${you ? "you" : ""}"
    style="--accent:${accent};--on-accent:${tagOn(topTag)}">
    <div class="podium-rank" style="color:${medal}">${entry.rank}</div>
    <div class="podium-avatar" ${shapeStyle(topTag?.shape || "Cookie12Sided")}>${initials(entry.displayName)}</div>
    <div class="podium-name">${esc(entry.displayName)}</div>
    <div class="podium-seconds">${fmtSeconds(entry.seconds)}</div>
    <div class="podium-label">${period === "day" ? "today" : period === "month" ? "this month" : "this week"}</div>
    ${you ? `<div class="podium-label" style="color:var(--primary);font-weight:800">YOU</div>` : ""}
  </div>`;
}

function rankRow(entry, index, max, period) {
  const topTag = Array.from(entry.tags ?? []).sort((a, b) => Number(b[period]) - Number(a[period]))[0];
  const accent = topTag ? tagColor(topTag) : "var(--primary)";
  const you = String(entry.uid) === String(S.auth.uid);
  return `<div class="rank-row ${you ? "you" : ""}" style="--accent:${accent};--on-accent:${tagOn(topTag)}">
    <div class="rank-num" style="color:${index < 3 ? ["#E7B84B", "#B9C4CE", "#C98B5B"][index] : "var(--muted)"}">${index + 1}</div>
    <div class="rank-avatar" ${shapeStyle(topTag?.shape || "Cookie12Sided")}>${initials(entry.displayName)}</div>
    <div class="rank-copy">
      <div class="rank-name">${esc(entry.displayName)} ${you ? `<span class="you-badge">YOU</span>` : ""}</div>
      <div class="rank-track"><span style="width:${Math.max(2, (entry.seconds / max) * 100)}%"></span></div>
      <div class="rank-meta">${Array.from(entry.tags ?? []).length} public tag${Array.from(entry.tags ?? []).length === 1 ? "" : "s"} · ${period === "day" ? "today" : period === "month" ? "this month" : "this week"}</div>
    </div>
    <div class="rank-seconds">${fmtSeconds(entry.seconds)}</div>
  </div>`;
}

/* ── Settings ───────────────────────────────────────────────────────── */

function settingsPage() {
  const signed = S.auth.signedIn;
  const resetArmed = S.ui.resetArmed === true;
  return `
    <div class="stack">
      ${authSettingsCard(signed)}
      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Week</h3><p>Used by streaks and the weekly view</p></div></div>
        <div class="row wrap">
          <span style="flex:1;font-size:12px">Week starts on</span>
          <div class="segmented small" style="min-width:190px">
            <button class="${S.settings.weekStartMonday ? "active" : ""}" data-action="week-start" data-value="monday">Monday</button>
            <button class="${!S.settings.weekStartMonday ? "active" : ""}" data-action="week-start" data-value="sunday">Sunday</button>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Today</h3></div></div>
        <div class="row" style="margin-bottom:12px">
          <span style="flex:1;font-size:12px">Show completed habits</span>
          <button class="switch ${S.settings.showCompleted ? "on" : ""}" data-action="toggle-show-completed" aria-label="Show completed habits"></button>
        </div>
        <div class="row">
          <span style="flex:1;font-size:12px">Live sync between devices</span>
          <button class="switch ${S.settings.autoSync ? "on" : ""}" data-action="toggle-auto-sync" aria-label="Live sync"></button>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Appearance</h3><p>Dynamic Material colour in the desktop shell; a calm forest palette here.</p></div></div>
        <div class="row wrap">
          <span style="flex:1;font-size:12px">Theme</span>
          <div class="segmented small" style="min-width:230px">
            ${[["system", "System"], ["light", "Light"], ["dark", "Dark"]].map(([id, label]) => `<button class="${S.settings.theme === id ? "active" : ""}" data-action="set-theme" data-value="${id}">${label}</button>`).join("")}
          </div>
        </div>
        <div class="row wrap" style="margin-top:12px">
          <span style="flex:1;font-size:12px">Language</span>
          <div class="segmented small" style="min-width:180px">
            <button class="${currentLanguage() === "en" ? "active" : ""}" data-action="set-language" data-value="en">English</button>
            <button class="${currentLanguage() === "ar" ? "active" : ""}" data-action="set-language" data-value="ar">العربية</button>
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Leaderboard</h3><p>Your public tags and display name live in the Ranks tab.</p></div></div>
        <button class="btn tonal" data-action="go-ranks">${icon("trophy")} Open Ranks</button>
      </div>

      <div class="panel">
        <div class="panel-head"><div class="copy"><h3>Data</h3><p>Local export is a complete JSON snapshot, including tombstones.</p></div></div>
        <div class="inline-actions">
          <button class="btn tonal" data-action="export-data">${icon("download")} Export JSON</button>
          <button class="btn ghost" data-action="import-data">${icon("upload")} Import JSON</button>
          <button class="btn ghost" data-action="hard-reload">${icon("refresh")} Reload fresh</button>
          <button class="btn ${resetArmed ? "danger" : "ghost"}" data-action="reset-data">${icon(resetArmed ? "warning" : "reset")} ${resetArmed ? "Press again to erase" : "Reset…"}</button>
          <input id="import-file" type="file" accept="application/json" hidden>
        </div>
        ${resetArmed ? `<p class="error-line" style="margin-top:7px">This tombstones every habit, task, tag and session and syncs the deletion to your other devices.</p>` : ""}
      </div>

      <div class="panel">
        <div class="row" style="gap:12px">
          <div class="habit-icon" style="width:48px;height:48px;border-radius:17px" ${shapeStyle("Cookie12Sided")}>${icon("flame")}</div>
          <div><h3>Panadoro × Tomato</h3><p class="muted" style="font-size:10.5px">Habits, tasks and tagged focus · web ${WEB_VERSION}</p></div>
        </div>
        <p class="muted" style="font-size:10.5px;margin-top:10px;line-height:1.5">
          The web app reads the same private Firestore document as the Android and Quickshell apps, merging record by record. The public board is a separate, opt-in tag mirror.
        </p>
      </div>
      <div style="height:30px"></div>
    </div>`;
}

function authSettingsCard(signed) {
  if (signed) {
    return `<div class="panel">
      <div class="panel-head"><div class="copy"><h3>Account & sync</h3><p>${esc(S.auth.email)}</p></div>
        <span class="pill ${S.sync.status === "error" ? "overdue" : "priority-1"}">${esc(S.sync.message || (S.auth.signedIn ? "Signed in" : "Local"))}</span>
      </div>
      <div class="row wrap">
        <button class="btn tonal" data-action="sync">${icon("sync")} Sync now</button>
        <button class="btn ghost" data-action="sign-out">${icon("logout")} Sign out</button>
      </div>
      ${S.settings.lastSyncAt ? `<p class="muted" style="font-size:9.5px;margin-top:8px">Last sync ${esc(S.settings.lastSyncAt)}</p>` : ""}
    </div>`;
  }
  return `<div class="panel">
    <div class="panel-head"><div class="copy"><h3>Account & sync</h3><p>Create an account here, then sign in with the same email on your other devices.</p></div></div>
    <form class="stack" data-form="settings-auth">
      <div class="field-row">
        <div class="field"><label>Email</label><input data-input="auth-email" type="email" autocomplete="email" value="${attr(S.ui.authEmail || "")}" placeholder="you@example.com"></div>
        <div class="field"><label>Password</label><input data-input="auth-password" type="password" autocomplete="current-password" value="${attr(S.ui.authPassword || "")}" placeholder="At least 6 characters"></div>
      </div>
      ${S.auth.error ? `<div class="auth-error">${esc(S.auth.error)}</div>` : ""}
      <div class="inline-actions">
        <button class="btn filled" type="submit" name="intent" value="signin" ${S.auth.busy ? "disabled" : ""}>${icon("logout")} Sign in</button>
        <button class="btn tonal" type="submit" name="intent" value="create" ${S.auth.busy ? "disabled" : ""}>${icon("person")} Create account</button>
      </div>
    </form>
    <p class="muted" style="font-size:9.5px;margin-top:9px">Your habits, tasks and focus history stay private to your account.</p>
  </div>`;
}

/* ── Empty state ────────────────────────────────────────────────────── */

function emptyHTML(symbol, title, text) {
  return `<div class="empty">
    <div class="empty-badge">${icon(symbol)}</div>
    <h3>${esc(title)}</h3>
    <p>${esc(text)}</p>
  </div>`;
}

/* ── Modal / editors ────────────────────────────────────────────────── */

function showModal({ title, subtitle, body, submitLabel = "Save", dangerLabel = "", onSubmit, onDelete }) {
  const modal = $("#modal");
  modalHandler = onSubmit || null;
  modalDelete = onDelete || null;
  modal.innerHTML = `
    <form class="modal-form" data-form="modal">
      <div class="modal-head">
        <div class="copy"><h2>${esc(title)}</h2>${subtitle ? `<p>${esc(subtitle)}</p>` : ""}</div>
        <button class="icon-btn small" type="button" data-action="close-modal" aria-label="Close">${icon("close")}</button>
      </div>
      <div class="modal-body">${body}</div>
      <div class="modal-foot">
        ${dangerLabel ? `<button class="btn danger" type="button" data-action="modal-delete">${icon("trash")} ${esc(dangerLabel)}</button>` : ""}
        <button class="btn ghost" type="button" data-action="close-modal">Cancel</button>
        <button class="btn filled" type="submit">${icon("check")} ${esc(submitLabel)}</button>
      </div>
    </form>`;
  translateDOM(modal);
  modal.showModal();
  window.setTimeout(() => {
    const first = $(".modal-body input:not([type=hidden]), .modal-body select, .modal-body textarea", modal);
    first?.focus({ preventScroll: true });
  }, 60);
}

function closeModal() {
  const modal = $("#modal");
  if (modal?.open) modal.close();
  modalHandler = null;
  modalDelete = null;
}

function iconPickerHTML(selected) {
  return `<div class="field"><label>Icon</label>
    <input type="hidden" id="f-icon" name="icon" value="${attr(selected)}">
    <div class="icon-picker">
      ${ICON_CHOICES.map((name) => `<button type="button" class="icon-choice ${iconName(selected) === name ? "active" : ""}" data-action="pick-icon" data-value="${name}">${icon(name)}</button>`).join("")}
    </div>
  </div>`;
}

function accentPickerHTML(selected) {
  return `<div class="field"><label>Colour</label>
    <input type="hidden" id="f-accent" name="accent" value="${attr(selected)}">
    <div class="accent-picker">
      ${ACCENT_ROLES.map((role) => `<button type="button" class="accent-choice ${selected === role ? "active" : ""}" data-action="pick-accent" data-value="${role}" style="--accent:var(--tag-${role})" title="${ACCENT_LABELS[role]}"></button>`).join("")}
    </div>
  </div>`;
}

function shapePickerHTML(selected) {
  return `<div class="field"><label>Shape</label>
    <input type="hidden" id="f-shape" name="shape" value="${attr(selected)}">
    <div class="shape-picker">
      ${SHAPES.map((shape) => `<button type="button" class="shape-choice ${selected === shape.id ? "active" : ""}" data-action="pick-shape" data-value="${attr(shape.id)}" style="border-radius:${shape.radius}" title="${attr(shape.id)}">${icon("focus")}</button>`).join("")}
    </div>
  </div>`;
}

function categoryOptions(selected) {
  return `<select name="categoryId">${activeCategories().map((category) => `<option value="${attr(category.id)}" ${String(category.id) === String(selected) ? "selected" : ""}>${esc(category.name)}</option>`).join("")}</select>`;
}

function tagOptions(selected) {
  return `<select name="taskTag"><option value="">No focus tag</option>${activeTags().map((tag) => `<option value="${attr(tag.id)}" ${String(tag.id) === String(selected) ? "selected" : ""}>${esc(tag.name)}</option>`).join("")}</select>`;
}

function dayPickerHTML(days) {
  const active = new Set((Array.isArray(days) ? days : [0, 1, 2, 3, 4, 5, 6]).map(Number));
  return `<div class="field"><label>Days</label>
    <input type="hidden" id="f-days" name="days" value="${Array.from(active).sort().join(",")}">
    <div class="chip-row">
      ${[1, 2, 3, 4, 5, 6, 0].map((day) => `<button type="button" class="chip day-toggle ${active.has(day) ? "active" : ""}" data-action="toggle-day" data-day="${day}" style="--accent:var(--primary);--on-accent:var(--on-primary)">${esc(weekdayName(addDays(todayKey(), day - weekdayOf(todayKey())), true).slice(0, 2))}</button>`).join("")}
    </div>
  </div>`;
}

export function editHabit(id = "") {
  const habit = id ? habitById(id) : null;
  const initial = habit || {
    name: "", icon: "check_circle", categoryId: activeCategories()[0]?.id ?? "",
    days: [0, 1, 2, 3, 4, 5, 6], time: "", target: 1, unit: "times", note: "",
  };
  showModal({
    title: habit ? "Edit habit" : "New habit",
    subtitle: "Small enough to keep, clear enough to finish.",
    submitLabel: habit ? "Save habit" : "Add habit",
    dangerLabel: habit ? "Delete habit" : "",
    body: `
      <div class="field"><label>Name</label><input name="name" value="${attr(initial.name)}" placeholder="Read a chapter" required></div>
      <div class="field-row">
        <div class="field"><label>Category</label>${categoryOptions(initial.categoryId)}</div>
        <div class="field"><label>Time</label><input name="time" type="time" value="${attr(initial.time)}"></div>
      </div>
      ${dayPickerHTML(initial.days)}
      <div class="field-row">
        <div class="field"><label>Target</label><input name="target" type="number" min="1" max="99" value="${Number(initial.target ?? 1)}"></div>
        <div class="field"><label>Unit</label><input name="unit" value="${attr(initial.unit)}" placeholder="times"></div>
      </div>
      <div class="field"><label>Note</label><textarea name="note" placeholder="Why does this habit matter?">${esc(initial.note)}</textarea></div>
      ${iconPickerHTML(initial.icon)}
    `,
    onSubmit(form) {
      const data = formValues(form);
      data.icon = $("#f-icon", form).value || "check_circle";
      data.days = $("#f-days", form).value.split(",").filter(Boolean).map(Number);
      data.target = Math.max(1, Number(data.target || 1));
      if (habit) {
        updateHabit(habit.id, data);
        toast("Habit updated");
      } else {
        createHabit(data);
        toast("Habit added");
      }
      closeModal();
    },
    onDelete: habit ? () => {
      deleteHabit(habit.id);
      closeModal();
      toast("Habit deleted");
    } : null,
  });
}

export function editCategory(id = "") {
  const category = id ? activeCategories().find((item) => String(item.id) === String(id)) : null;
  const initial = category || { name: "", accent: "primary", icon: "label" };
  showModal({
    title: category ? "Edit category" : "New category",
    subtitle: "A quiet group for habits and tasks.",
    submitLabel: category ? "Save" : "Create",
    dangerLabel: category ? "Delete category" : "",
    body: `
      <div class="field"><label>Name</label><input name="name" value="${attr(initial.name)}" placeholder="Personal" required></div>
      ${accentPickerHTML(initial.accent)}
      ${iconPickerHTML(initial.icon)}
    `,
    onSubmit(form) {
      const data = formValues(form);
      data.icon = $("#f-icon", form).value || "label";
      data.accent = $("#f-accent", form).value || "primary";
      if (category) updateCategory(category.id, data);
      else createCategory(data);
      closeModal();
      toast(category ? "Category updated" : "Category created");
    },
    onDelete: category ? () => {
      deleteCategory(category.id);
      closeModal();
      toast("Category deleted; its items moved to the next category");
    } : null,
  });
}

export function editTag(id = "") {
  const tag = id ? tagById(id) : null;
  const initial = tag || {
    name: "", accent: "primary", color: "", shape: "Cookie7Sided", icon: "focus",
    focusMinutes: 25, breakMinutes: 5, longBreakMinutes: 15, cycles: 4,
    dailyGoalMinutes: 60, autoStartBreak: true, autoStartFocus: false,
  };
  showModal({
    title: tag ? "Edit focus tag" : "New focus tag",
    subtitle: "Its own timer rhythm, colour and daily goal.",
    submitLabel: tag ? "Save tag" : "Create tag",
    dangerLabel: tag && activeTags().length > 1 ? "Delete tag" : "",
    body: `
      <div class="field"><label>Name</label><input name="name" value="${attr(initial.name)}" placeholder="Deep work" required></div>
      ${accentPickerHTML(initial.accent)}
      <div class="field"><label>Custom hex (optional)</label><input name="color" value="${attr(initial.color)}" placeholder="#7fae72"></div>
      ${shapePickerHTML(initial.shape)}
      ${iconPickerHTML(initial.icon)}
      <div class="field-row">
        <div class="field"><label>Focus min</label><input name="focusMinutes" type="number" min="1" value="${Number(initial.focusMinutes)}"></div>
        <div class="field"><label>Break min</label><input name="breakMinutes" type="number" min="1" value="${Number(initial.breakMinutes)}"></div>
        <div class="field"><label>Long break</label><input name="longBreakMinutes" type="number" min="1" value="${Number(initial.longBreakMinutes)}"></div>
        <div class="field"><label>Cycles</label><input name="cycles" type="number" min="1" value="${Number(initial.cycles)}"></div>
      </div>
      <div class="field"><label>Daily goal (minutes, 0 = none)</label><input name="dailyGoalMinutes" type="number" min="0" max="720" value="${Number(initial.dailyGoalMinutes)}"></div>
      <div class="row"><span style="flex:1;font-size:11.5px">Auto-start breaks</span><input type="checkbox" name="autoStartBreak" ${initial.autoStartBreak !== false ? "checked" : ""}></div>
      <div class="row"><span style="flex:1;font-size:11.5px">Auto-start next focus</span><input type="checkbox" name="autoStartFocus" ${initial.autoStartFocus === true ? "checked" : ""}></div>
    `,
    onSubmit(form) {
      const data = formValues(form);
      data.icon = $("#f-icon", form).value || "focus";
      data.accent = $("#f-accent", form).value || "primary";
      data.shape = $("#f-shape", form).value || "Cookie7Sided";
      data.autoStartBreak = form.elements.autoStartBreak.checked;
      data.autoStartFocus = form.elements.autoStartFocus.checked;
      ["focusMinutes", "breakMinutes", "longBreakMinutes", "cycles", "dailyGoalMinutes"].forEach((key) => {
        data[key] = Math.max(0, Number(data[key] || 0));
      });
      if (tag) updateTag(tag.id, data);
      else createTag(data);
      closeModal();
      toast(tag ? "Tag updated" : "Tag created");
    },
    onDelete: tag && activeTags().length > 1 ? () => {
      deleteTag(tag.id);
      closeModal();
      toast("Tag deleted");
    } : null,
  });
}

export function editTask(id = "") {
  const task = id ? taskById(id) : null;
  const initial = task || {
    title: "", notes: "", categoryId: activeCategories()[0]?.id ?? "",
    due: "", priority: 0, taskTag: "",
  };
  showModal({
    title: task ? "Edit task" : "New task",
    subtitle: task ? "Subtasks are managed from the task row." : "A small next step with a date if it needs one.",
    submitLabel: task ? "Save task" : "Add task",
    dangerLabel: task ? "Delete task" : "",
    body: `
      <div class="field"><label>Title</label><input name="title" value="${attr(initial.title)}" placeholder="Send the invoice" required></div>
      <div class="field-row">
        <div class="field"><label>Category</label>${categoryOptions(initial.categoryId)}</div>
        <div class="field"><label>Focus tag</label>${tagOptions(initial.taskTag)}</div>
        <div class="field"><label>Due</label><input name="due" type="date" value="${attr(initial.due)}"></div>
      </div>
      <div class="field"><label>Priority</label>
        <input type="hidden" id="f-priority" name="priority" value="${Number(initial.priority ?? 0)}">
        <div class="segmented small">
          ${[0, 1, 2].map((priority) => `<button type="button" class="${Number(initial.priority ?? 0) === priority ? "active" : ""}" data-action="pick-priority" data-value="${priority}">${["Low", "Medium", "High"][priority]}</button>`).join("")}
        </div>
      </div>
      <div class="field"><label>Notes</label><textarea name="notes" placeholder="Anything useful for later.">${esc(initial.notes)}</textarea></div>
      ${task ? `<p class="muted" style="font-size:10px">Subtasks: ${Array.from(task.subtasks ?? []).length}. Expand the task row to add, tick or remove them.</p>` : ""}
    `,
    onSubmit(form) {
      const data = formValues(form);
      data.priority = Number($("#f-priority", form).value || 0);
      data.done = task?.done === true;
      if (task) updateTask(task.id, data);
      else createTask(data);
      closeModal();
      toast(task ? "Task updated" : "Task added");
    },
    onDelete: task ? () => {
      deleteTask(task.id);
      closeModal();
      toast("Task deleted");
    } : null,
  });
}

function formValues(form) {
  const out = {};
  for (const [key, value] of new FormData(form).entries()) out[key] = String(value).trim();
  return out;
}

/* ── Render ─────────────────────────────────────────────────────────── */

export function render(reason = "change") {
  if (reason === "tick") {
    updateLive();
    return;
  }
  if (reason === "note") return;
  if (document.activeElement?.id === "focus-note" && reason !== "timer" && reason !== "auth") {
    updateLive();
    return;
  }
  lastRenderReason = reason;
  const app = document.getElementById("app");
  const html = S.ui.authGate ? authGateHTML() : shellHTML();
  morphdom(app, `<div id="app">${html}</div>`, { childrenOnly: true });
  applyLanguage();
  translateDOM(app);
  applyTheme();
  updateLive();
  if (reason === "tab" || reason === "auth" || reason === "import") {
    window.scrollTo({ top: 0, behavior: "auto" });
  }
}

export function applyTheme() {
  const theme = S.settings.theme || "system";
  const dark = theme === "dark" || (theme === "system" && window.matchMedia("(prefers-color-scheme: dark)").matches);
  document.documentElement.dataset.theme = dark ? "dark" : "light";
  const color = dark ? "#0b100c" : "#f2efe7";
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute("content", color);
}

export function updateLive() {
  const left = timerSecondsLeft();
  $$("[data-live-time]").forEach((el) => {
    el.textContent = fmtClock(left);
  });
  $$("[data-live-chip-time]").forEach((el) => {
    el.textContent = fmtClock(left);
  });
  $$("[data-live-ring]").forEach((ring) => {
    const circumference = 2 * Math.PI * 45;
    ring.setAttribute("stroke-dashoffset", String(circumference * (1 - timerProgress())));
  });
}

/* ── Celebration / toast ────────────────────────────────────────────── */

export function showCelebration(detail = {}) {
  const overlay = $("#celebration");
  const title = $("#celebration-title");
  const text = $("#celebration-text");
  const iconBox = $("#celebration-icon");
  const accent = detail.kind === "habits" ? "var(--success)" : "var(--primary)";
  overlay.style.setProperty("--accent", accent);
  title.textContent = translateText(detail.title || "Done");
  text.textContent = translateText(detail.text || "");
  iconBox.innerHTML = icon(detail.symbol || "check");
  overlay.classList.add("show");
  confetti(detail.kind === "habits");
  window.clearTimeout(celebrationTimer);
  celebrationTimer = window.setTimeout(hideCelebration, 4600);
}

export function hideCelebration() {
  window.clearTimeout(celebrationTimer);
  $("#celebration")?.classList.remove("show");
  stopConfetti();
}

export function toast(message) {
  const node = $("#toast");
  node.textContent = translateText(message);
  node.classList.add("show");
  window.clearTimeout(toast.timer);
  toast.timer = window.setTimeout(() => node.classList.remove("show"), 2400);
}

function confetti(strong = false) {
  const canvas = $("#confetti");
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
  stopConfetti();
  const dpr = window.devicePixelRatio || 1;
  const width = window.innerWidth;
  const height = window.innerHeight;
  canvas.width = width * dpr;
  canvas.height = height * dpr;
  canvas.style.width = `${width}px`;
  canvas.style.height = `${height}px`;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  const palette = ["#9bdc9f", "#e9b85e", "#a7c8f2", "#ef9a94", "#82d49e", "#c9f3c9"];
  const count = strong ? 120 : 80;
  const particles = Array.from({ length: count }, () => ({
    x: width / 2 + (Math.random() - 0.5) * 60,
    y: height * 0.38,
    vx: (Math.random() - 0.5) * 15,
    vy: -5 - Math.random() * 11,
    size: 4 + Math.random() * 8,
    color: palette[Math.floor(Math.random() * palette.length)],
    spin: (Math.random() - 0.5) * 0.3,
    angle: Math.random() * Math.PI,
    life: 1,
  }));
  const start = performance.now();
  const step = (now) => {
    const elapsed = now - start;
    if (elapsed > 2800) {
      stopConfetti();
      return;
    }
    ctx.clearRect(0, 0, width, height);
    for (const particle of particles) {
      particle.vy += 0.32;
      particle.vx *= 0.992;
      particle.x += particle.vx;
      particle.y += particle.vy;
      particle.angle += particle.spin;
      particle.life = Math.max(0, 1 - elapsed / 2800);
      ctx.save();
      ctx.translate(particle.x, particle.y);
      ctx.rotate(particle.angle);
      ctx.globalAlpha = particle.life;
      ctx.fillStyle = particle.color;
      ctx.fillRect(-particle.size / 2, -particle.size / 4, particle.size, particle.size / 2);
      ctx.restore();
    }
    confettiFrame = requestAnimationFrame(step);
  };
  confettiFrame = requestAnimationFrame(step);
}

function stopConfetti() {
  if (confettiFrame) cancelAnimationFrame(confettiFrame);
  confettiFrame = 0;
  const canvas = $("#confetti");
  const ctx = canvas?.getContext("2d");
  if (ctx) ctx.clearRect(0, 0, canvas.width, canvas.height);
}

/* ── Events ─────────────────────────────────────────────────────────── */

export function bindEvents() {
  document.addEventListener("click", async (event) => {
    const target = event.target.closest("[data-action]");
    if (!target) {
      if (event.target === $("#modal")) closeModal();
      return;
    }
    const action = target.dataset.action;
    const id = target.dataset.id ?? "";
    event.preventDefault();
    event.stopPropagation();

    switch (action) {
      case "tab":
        S.ui.tab = target.dataset.tab || "today";
        S.ui.settingsOpen = false;
        emit("tab");
        break;
      case "settings":
        S.ui.settingsOpen = !S.ui.settingsOpen;
        emit("tab");
        break;
      case "go-focus":
        S.ui.tab = "focus";
        S.ui.settingsOpen = false;
        emit("tab");
        break;
      case "go-ranks":
        S.ui.tab = "ranks";
        S.ui.settingsOpen = false;
        emit("tab");
        break;
      case "sync":
        if (!S.auth.signedIn) {
          S.ui.authGate = true;
          emit("auth");
        } else {
          await syncNow();
        }
        break;
      case "toggle-habit":
        toggleHabitDone(id);
        break;
      case "habit-day": {
        const habit = habitById(id);
        if (!habit) break;
        const date = target.dataset.date;
        const next = habitDone(habit, date) ? 0 : Math.max(1, Number(habit.target ?? 1));
        setHabitEntry(id, date, next);
        break;
      }
      case "edit-habit": editHabit(id); break;
      case "new-habit": editHabit(""); break;
      case "select-category":
        S.ui.selectedCategory = id;
        emit("tab");
        break;
      case "new-category": editCategory(""); break;
      case "edit-category": editCategory(id); break;
      case "delete-category": confirmDelete("Delete category?", "Habits and tasks in this category move to the next category. The deletion syncs to your other devices.", () => {
        deleteCategory(id);
        toast("Category deleted");
      }); break;
      case "task-filter":
        S.ui.taskFilter = target.dataset.filter || "open";
        emit("tab");
        break;
      case "toggle-task": toggleTask(id); break;
      case "expand-task": {
        const key = String(id);
        if (S.ui.expandedTasks.has(key)) S.ui.expandedTasks.delete(key);
        else S.ui.expandedTasks.add(key);
        render("ui");
        break;
      }
      case "edit-task": editTask(id); break;
      case "new-task": editTask(""); break;
      case "toggle-subtask": toggleSubtask(id, target.dataset.sub); break;
      case "remove-subtask": removeSubtask(id, target.dataset.sub); break;
      case "focus-tag":
        if (!selectTag(id)) toast("Pause the timer before changing tag");
        break;
      case "new-tag": editTag(""); break;
      case "edit-tag": editTag(id); break;
      case "delete-tag":
        if (activeTags().length <= 1) toast("Keep at least one focus tag");
        else confirmDelete("Delete focus tag?", "Its sessions stay in the history, but the tag disappears from today's choices.", () => {
          deleteTag(id);
          toast("Tag deleted");
        });
        break;
      case "timer-toggle":
        toggleTimer();
        break;
      case "timer-reset": resetTimer(); break;
      case "timer-skip": skipTimer(); break;
      case "stats-range":
        S.ui.statsRange = Number(target.dataset.value || 7);
        emit("tab");
        break;
      case "stats-mode":
        S.ui.statsMode = target.dataset.value || "tags";
        emit("tab");
        break;
      case "rank-period":
        S.ui.rankPeriod = target.dataset.value || "week";
        emit("tab");
        break;
      case "refresh-board":
        await refreshLeaderboard();
        break;
      case "public-toggle":
        setTagPublic(id, !S.settings.publicTagIds.includes(String(id)));
        refreshLeaderboard();
        publishLeaderboard();
        toast(S.settings.publicTagIds.includes(String(id)) ? "Tag is public" : "Tag is private");
        break;
      case "open-auth":
        S.ui.authGate = true;
        clearAuthError();
        emit("auth");
        break;
      case "skip-auth":
        try { sessionStorage.setItem(AUTH_SKIP_KEY, "1"); } catch (_) {}
        S.ui.authGate = false;
        emit("auth");
        break;
      case "auth-mode":
        S.ui.authCreate = !S.ui.authCreate;
        clearAuthError();
        emit("auth");
        break;
      case "sign-out":
        signOut();
        S.ui.authGate = true;
        try { sessionStorage.removeItem(AUTH_SKIP_KEY); } catch (_) {}
        emit("auth");
        break;
      case "toggle-show-completed":
        S.settings.showCompleted = !S.settings.showCompleted;
        saveSettings();
        break;
      case "toggle-auto-sync":
        S.settings.autoSync = !S.settings.autoSync;
        saveSettings();
        break;
      case "week-start":
        S.settings.weekStartMonday = target.dataset.value === "monday";
        saveSettings();
        break;
      case "set-theme":
        S.settings.theme = target.dataset.value || "system";
        saveSettings();
        break;
      case "set-language":
        setLanguage(target.dataset.value || "en");
        break;
      case "export-data": exportData(); toast("Snapshot exported"); break;
      case "import-data": $("#import-file")?.click(); break;
      case "hard-reload":
        try {
          const registrations = await navigator.serviceWorker.getRegistrations();
          await Promise.all(registrations.map((registration) => registration.unregister()));
        } catch (_) {}
        try {
          const keys = await caches.keys();
          await Promise.all(keys.map((key) => caches.delete(key)));
        } catch (_) {}
        location.reload();
        break;
      case "reset-data":
        if (S.ui.resetArmed) {
          S.ui.resetArmed = false;
          resetAllData();
          toast("Everything erased and synced");
        } else {
          S.ui.resetArmed = true;
          emit("tab");
          window.setTimeout(() => {
            if (S.ui.resetArmed) {
              S.ui.resetArmed = false;
              emit("tab");
            }
          }, 5000);
        }
        break;
      case "close-modal": closeModal(); break;
      case "modal-delete":
        if (modalDelete) {
          const handler = modalDelete;
          modalDelete = null;
          handler();
        }
        break;
      case "close-celebration": hideCelebration(); break;
      case "pick-icon": {
        const picker = target.closest(".icon-picker");
        if (!picker) break;
        picker.querySelectorAll(".icon-choice").forEach((node) => node.classList.remove("active"));
        target.classList.add("active");
        const hidden = picker.parentElement.querySelector("#f-icon");
        if (hidden) hidden.value = target.dataset.value || "focus";
        break;
      }
      case "pick-accent": {
        const picker = target.closest(".accent-picker");
        if (!picker) break;
        picker.querySelectorAll(".accent-choice").forEach((node) => node.classList.remove("active"));
        target.classList.add("active");
        const hidden = picker.parentElement.querySelector("#f-accent");
        if (hidden) hidden.value = target.dataset.value || "primary";
        break;
      }
      case "pick-shape": {
        const picker = target.closest(".shape-picker");
        if (!picker) break;
        picker.querySelectorAll(".shape-choice").forEach((node) => node.classList.remove("active"));
        target.classList.add("active");
        const hidden = picker.parentElement.querySelector("#f-shape");
        if (hidden) hidden.value = target.dataset.value || "Cookie7Sided";
        break;
      }
      case "pick-priority": {
        const wrapper = target.parentElement;
        wrapper.querySelectorAll("button").forEach((node) => node.classList.remove("active"));
        target.classList.add("active");
        const hidden = wrapper.parentElement.querySelector("#f-priority");
        if (hidden) hidden.value = target.dataset.value || "0";
        break;
      }
      case "toggle-day": {
        target.classList.toggle("active");
        const field = target.closest(".field");
        const hidden = field?.querySelector("#f-days");
        if (hidden) {
          hidden.value = Array.from(field.querySelectorAll(".day-toggle.active"))
            .map((node) => node.dataset.day)
            .join(",");
        }
        break;
      }
      default:
        break;
    }
  });

  document.addEventListener("submit", async (event) => {
    const form = event.target;
    if (!(form instanceof HTMLFormElement)) return;
    const type = form.dataset.form;
    if (!type) return;
    event.preventDefault();

    if (type === "auth") {
      const creating = S.ui.authCreate === true;
      const ok = creating
        ? await createAccount(S.ui.authEmail, S.ui.authPassword)
        : await signIn(S.ui.authEmail, S.ui.authPassword);
      if (ok) {
        S.ui.authGate = false;
        S.ui.authPassword = "";
        try { sessionStorage.setItem(AUTH_SKIP_KEY, "1"); } catch (_) {}
        toast(creating ? "Account created" : "Signed in");
        emit("auth");
      }
      return;
    }

    if (type === "settings-auth") {
      const intent = event.submitter?.value || "signin";
      const ok = intent === "create"
        ? await createAccount(S.ui.authEmail, S.ui.authPassword)
        : await signIn(S.ui.authEmail, S.ui.authPassword);
      if (ok) {
        S.ui.authPassword = "";
        toast(intent === "create" ? "Account created" : "Signed in");
        emit("auth");
      }
      return;
    }

    if (type === "subtask") {
      const title = form.elements.title?.value ?? "";
      if (String(title).trim()) addSubtask(form.dataset.id, title);
      return;
    }

    if (type === "tag-task") {
      const title = form.elements.title?.value ?? "";
      if (String(title).trim()) createTagTask(form.dataset.id, title);
      return;
    }

    if (type === "rank-name") {
      const value = form.elements.displayName?.value ?? "";
      setDisplayName(value);
      publishLeaderboard();
      refreshLeaderboard();
      toast("Display name saved");
      return;
    }

    if (type === "modal") {
      if (modalHandler) {
        const handler = modalHandler;
        modalHandler = null;
        handler(form);
        emit("ui");
      }
    }
  });

  document.addEventListener("input", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement || target instanceof HTMLTextAreaElement)) return;
    if (target.dataset.input === "auth-email") {
      S.ui.authEmail = target.value;
      clearAuthError();
      return;
    }
    if (target.dataset.input === "auth-password") {
      S.ui.authPassword = target.value;
      clearAuthError();
      return;
    }
    if (target.dataset.input === "focus-note") {
      setTimerNote(target.value);
    }
  });

  document.addEventListener("change", async (event) => {
    const target = event.target;
    if (target?.id === "import-file") {
      const file = target.files?.[0];
      if (!file) return;
      try {
        await importData(file);
        toast("Snapshot imported");
      } catch (_) {
        toast("That file is not a valid Panadoro snapshot");
      }
    }
  });

  $("#celebration")?.addEventListener("click", (event) => {
    if (event.target.closest("[data-action]")) return;
    hideCelebration();
  });

  window.addEventListener("panadoro:celebrate", (event) => showCelebration(event.detail || {}));

  window.matchMedia("(prefers-color-scheme: dark)").addEventListener?.("change", () => applyTheme());

  window.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      if ($("#modal")?.open) closeModal();
      else if ($("#celebration")?.classList.contains("show")) hideCelebration();
    }
    if (event.ctrlKey && event.key >= "1" && event.key <= "5") {
      const index = Number(event.key) - 1;
      if (TABS[index]) {
        S.ui.tab = TABS[index].id;
        S.ui.settingsOpen = false;
        emit("tab");
      }
    }
    if (event.ctrlKey && event.key.toLowerCase() === "k") {
      event.preventDefault();
      if (S.ui.tab === "today") editHabit("");
      else if (S.ui.tab === "tasks") editTask("");
    }
  });
}

function confirmDelete(title, text, onConfirm) {
  showModal({
    title,
    subtitle: text,
    submitLabel: "Cancel",
    dangerLabel: "Delete",
    body: "",
    onSubmit: closeModal,
    onDelete: () => {
      onConfirm();
      closeModal();
    },
  });
}
