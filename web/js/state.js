// Panadoro web data model.
//
// The on-disk shape is deliberately the same JSON document the Quickshell app
// writes to its local file and the Android app writes to Firestore. Every
// record carries updatedAt + device + a deletion tombstone so three clients can
// merge record by record instead of overwriting each other.

export const VERSION = 1;
export const STORE_KEY = "panadoro.store.v1";
export const SETTINGS_KEY = "panadoro.settings.v1";
export const DEVICE_KEY = "panadoro.device.v1";
export const AUTH_KEY = "panadoro.auth.v1";
export const AUTH_SKIP_KEY = "panadoro.authSkipped";

const ACCENT_NAMES = ["primary", "secondary", "tertiary", "success", "error", "outline"];
const listenerSet = new Set();
let saveHandle = 0;
let serial = 0;

/* ── Storage ─────────────────────────────────────────────────────────── */

function readJson(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw);
  } catch (_) {
    return fallback;
  }
}

function writeJson(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (_) {}
}

function loadDeviceId() {
  let id = "";
  try {
    id = localStorage.getItem(DEVICE_KEY) || "";
  } catch (_) {}
  if (!id) {
    id = "web-" + Math.random().toString(36).slice(2, 8) + Date.now().toString(36);
    try {
      localStorage.setItem(DEVICE_KEY, id);
    } catch (_) {}
  }
  return id;
}

export const deviceId = loadDeviceId();

const defaultSettings = {
  weekStartMonday: true,
  showCompleted: true,
  autoSync: true,
  theme: "system",
  language: "",
  displayName: "",
  displayNameUpdatedAt: 0,
  publicTagIds: [],
  publicTagsUpdatedAt: 0,
  lastSyncAt: "",
};

function loadSettings() {
  const stored = readJson(SETTINGS_KEY, {});
  const merged = { ...defaultSettings, ...(stored && typeof stored === "object" ? stored : {}) };
  merged.publicTagIds = Array.isArray(merged.publicTagIds)
    ? merged.publicTagIds.map(String)
    : [];
  merged.language = merged.language === "ar" || merged.language === "en" ? merged.language : "";
  merged.displayNameUpdatedAt = Number(merged.displayNameUpdatedAt ?? 0) || 0;
  merged.publicTagsUpdatedAt = Number(merged.publicTagsUpdatedAt ?? 0) || 0;
  return merged;
}

export const S = {
  data: normalizeStore(readJson(STORE_KEY, null)),
  settings: loadSettings(),
  auth: {
    email: "",
    uid: "",
    idToken: "",
    refreshToken: "",
    expiresAt: 0,
    signedIn: false,
    busy: false,
    error: "",
  },
  board: {
    entries: [],
    loading: false,
    error: "",
    updatedAt: 0,
  },
  sync: {
    status: "off",
    message: "Sign in to sync",
    inProgress: false,
    applying: false,
  },
  ui: {
    tab: "today",
    settingsOpen: false,
    authGate: false,
    selectedCategory: "all",
    taskFilter: "open",
    statsRange: 7,
    statsMode: "tags",
    rhythmTagId: "",
    rankPeriod: "week",
    rankTag: "",
    expandedTasks: new Set(),
    selectedTagId: "",
    modalOpen: false,
  },
  clock: 0,
  _celebratedDate: "",
};

/* ── Tiny event bus ──────────────────────────────────────────────────── */

export function subscribe(fn) {
  listenerSet.add(fn);
  return () => listenerSet.delete(fn);
}

export function emit(reason = "change") {
  for (const fn of listenerSet) {
    try {
      fn(reason);
    } catch (error) {
      console.error("[Panadoro] listener failed", error);
    }
  }
}

/* ── Settings / persistence ──────────────────────────────────────────── */

export function saveSettings() {
  writeJson(SETTINGS_KEY, S.settings);
  emit("settings");
}

export function setSetting(name, value) {
  S.settings[name] = value;
  saveSettings();
}

export function saveSoon() {
  clearTimeout(saveHandle);
  saveHandle = window.setTimeout(() => {
    S.data.updatedAt = S.data.updatedAt || Date.now();
    writeJson(STORE_KEY, S.data);
  }, 300);
}

export function saveNow() {
  clearTimeout(saveHandle);
  S.data.updatedAt = S.data.updatedAt || Date.now();
  S.data.device = deviceId;
  writeJson(STORE_KEY, S.data);
}

function changed(reason = "change") {
  S.data.updatedAt = Date.now();
  S.data.device = deviceId;
  saveSoon();
  emit(reason);
}

/* ── Dates ───────────────────────────────────────────────────────────── */

export function dateKey(date = new Date()) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

export function todayKey() {
  return dateKey(new Date());
}

export function dateFromKey(key) {
  const parts = String(key ?? "").split("-").map(Number);
  if (parts.length !== 3 || !parts[0] || !parts[1] || !parts[2]) return new Date();
  return new Date(parts[0], parts[1] - 1, parts[2], 12, 0, 0, 0);
}

export function addDays(key, amount) {
  const date = dateFromKey(key);
  date.setDate(date.getDate() + Number(amount || 0));
  return dateKey(date);
}

export function lastNDays(count) {
  const out = [];
  for (let i = Math.max(1, Number(count || 1)) - 1; i >= 0; i -= 1)
    out.push(addDays(todayKey(), -i));
  return out;
}

export function weekdayOf(key) {
  return dateFromKey(key).getDay();
}

function dateLocale() {
  const arabic =
    S.settings.language === "ar" ||
    (!S.settings.language && String(navigator.language ?? "").toLowerCase().startsWith("ar"));
  return arabic ? "ar" : undefined;
}

export function weekdayName(key, short = true) {
  return dateFromKey(key).toLocaleDateString(dateLocale(), { weekday: short ? "short" : "long" });
}

export function relativeDay(key) {
  const today = todayKey();
  if (key === today) return "Today";
  if (key === addDays(today, 1)) return "Tomorrow";
  if (key === addDays(today, -1)) return "Yesterday";
  return dateFromKey(key).toLocaleDateString(dateLocale(), {
    weekday: "short",
    day: "numeric",
    month: "short",
  });
}

export function weekStartOffset() {
  const day = new Date().getDay();
  return S.settings.weekStartMonday ? (day + 6) % 7 : day;
}

export function thisWeekDays() {
  const start = addDays(todayKey(), -weekStartOffset());
  const out = [];
  for (let i = 0; i < 7; i += 1) out.push(addDays(start, i));
  return out;
}

/* ── Normalisation ───────────────────────────────────────────────────── */

export function newId(prefix = "grit") {
  serial = (serial + 1) % 1000000;
  return `${prefix}-${deviceId.slice(0, 8)}-${Date.now().toString(36)}-${serial.toString(36)}`;
}

export function stamp(record) {
  return { ...(record || {}), updatedAt: Date.now(), device: deviceId };
}

export function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function normalizeList(array) {
  if (!Array.isArray(array)) return [];
  return array
    .filter((item) => item && typeof item === "object" && String(item.id ?? "").length > 0)
    .map((item) => ({
      ...item,
      updatedAt: Number(item.updatedAt ?? 0) || 0,
      deleted: item.deleted === true,
    }));
}

function dedupeList(list) {
  const byId = {};
  for (const item of normalizeList(list)) {
    const previous = byId[item.id];
    if (
      !previous ||
      (previous.deleted && !item.deleted) ||
      (previous.deleted === item.deleted && item.updatedAt >= previous.updatedAt)
    )
      byId[item.id] = item;
  }
  return Object.keys(byId).map((id) => byId[id]);
}

export function defaultCategories() {
  return [
    { id: "cat-personal", name: "Personal", accent: "primary", icon: "person", updatedAt: 0, device: "seed", deleted: false },
    { id: "cat-work", name: "Work", accent: "tertiary", icon: "work", updatedAt: 0, device: "seed", deleted: false },
    { id: "cat-study", name: "Study", accent: "secondary", icon: "school", updatedAt: 0, device: "seed", deleted: false },
    { id: "cat-health", name: "Health", accent: "success", icon: "favorite", updatedAt: 0, device: "seed", deleted: false },
  ];
}

export function defaultTags() {
  return [
    {
      id: "tag-deep-work", name: "Deep work", accent: "primary", color: "",
      shape: "Cookie7Sided", icon: "neurology",
      focusMinutes: 25, breakMinutes: 5, longBreakMinutes: 15, cycles: 4,
      dailyGoalMinutes: 120, autoStartBreak: true, autoStartFocus: false,
      updatedAt: 0, device: "seed", deleted: false,
    },
    {
      id: "tag-study", name: "Study", accent: "tertiary", color: "",
      shape: "Cookie9Sided", icon: "school",
      focusMinutes: 45, breakMinutes: 10, longBreakMinutes: 20, cycles: 3,
      dailyGoalMinutes: 90, autoStartBreak: true, autoStartFocus: false,
      updatedAt: 0, device: "seed", deleted: false,
    },
    {
      id: "tag-creative", name: "Creative", accent: "secondary", color: "",
      shape: "Flower", icon: "palette",
      focusMinutes: 30, breakMinutes: 8, longBreakMinutes: 15, cycles: 4,
      dailyGoalMinutes: 60, autoStartBreak: true, autoStartFocus: false,
      updatedAt: 0, device: "seed", deleted: false,
    },
    {
      id: "tag-admin", name: "Admin", accent: "success", color: "",
      shape: "Cookie4Sided", icon: "inbox",
      focusMinutes: 20, breakMinutes: 5, longBreakMinutes: 10, cycles: 3,
      dailyGoalMinutes: 45, autoStartBreak: true, autoStartFocus: false,
      updatedAt: 0, device: "seed", deleted: false,
    },
  ];
}

function defaultGoalForTagId(id) {
  switch (String(id ?? "")) {
    case "tag-deep-work": return 120;
    case "tag-study": return 90;
    case "tag-creative": return 60;
    case "tag-admin": return 45;
    default: return 0;
  }
}

function normalizeTag(item) {
  const tag = { ...item };
  if (!Object.prototype.hasOwnProperty.call(tag, "dailyGoalMinutes"))
    tag.dailyGoalMinutes = defaultGoalForTagId(tag.id);
  tag.dailyGoalMinutes = Math.max(0, Math.min(720, Number(tag.dailyGoalMinutes ?? 0)));
  return tag;
}

export function normalizeStore(raw) {
  const data = raw && typeof raw === "object" ? raw : {};
  return {
    version: Number(data.version ?? VERSION) || VERSION,
    habits: dedupeList(data.habits),
    entries: dedupeList(data.entries),
    tasks: dedupeList(data.tasks),
    categories: Array.isArray(data.categories)
      ? dedupeList(data.categories)
      : defaultCategories(),
    tags: Array.isArray(data.tags)
      ? dedupeList(data.tags).map(normalizeTag)
      : defaultTags(),
    focusSessions: sortSessions(dedupeList(data.focusSessions)),
    timer: data.timer && typeof data.timer === "object" ? data.timer : null,
    updatedAt: Number(data.updatedAt ?? 0) || 0,
    device: String(data.device ?? ""),
  };
}

export function emptyStore() {
  return {
    version: VERSION,
    habits: [],
    entries: [],
    tasks: [],
    categories: defaultCategories(),
    tags: defaultTags(),
    focusSessions: [],
    timer: null,
    updatedAt: Date.now(),
    device: deviceId,
  };
}

export function snapshot() {
  return {
    version: VERSION,
    habits: S.data.habits,
    entries: S.data.entries,
    tasks: S.data.tasks,
    categories: S.data.categories,
    tags: S.data.tags,
    focusSessions: S.data.focusSessions,
    timer: S.data.timer,
    updatedAt: Date.now(),
    device: deviceId,
  };
}

if (!S.data) S.data = emptyStore();

export function ensureDefaults() {
  if (!S.data.timer) ensureTimer();
}

/* ── Record merging ──────────────────────────────────────────────────── */

function sortSessions(list) {
  return Array.from(list || []).sort((a, b) => Number(b.endedAt ?? 0) - Number(a.endedAt ?? 0));
}

export function mergeRecordLists(local, remote) {
  const map = {};
  for (const item of Array.from(local || [])) {
    if (item && item.id) map[String(item.id)] = item;
  }
  for (const item of Array.from(remote || [])) {
    if (!item || !item.id) continue;
    const id = String(item.id);
    const current = map[id];
    if (!current) {
      map[id] = item;
      continue;
    }
    const currentTime = Number(current.updatedAt ?? 0);
    const remoteTime = Number(item.updatedAt ?? 0);
    if (
      remoteTime > currentTime ||
      (remoteTime === currentTime && item.deleted === true && current.deleted !== true)
    )
      map[id] = item;
  }
  return Object.keys(map).map((id) => map[id]);
}

export function mergeStores(local, remote) {
  const a = local || {};
  const b = remote || {};
  const merged = {
    version: VERSION,
    habits: mergeRecordLists(a.habits, b.habits),
    entries: mergeRecordLists(a.entries, b.entries),
    tasks: mergeRecordLists(a.tasks, b.tasks),
    categories: mergeRecordLists(a.categories, b.categories),
    tags: mergeRecordLists(a.tags, b.tags),
    focusSessions: sortSessions(mergeRecordLists(a.focusSessions, b.focusSessions)),
    timer: a.timer,
    updatedAt: Date.now(),
    device: deviceId,
  };
  if (b.timer && typeof b.timer === "object") {
    if (!a.timer || Number(b.timer.updatedAt ?? 0) > Number(a.timer.updatedAt ?? 0))
      merged.timer = b.timer;
  }
  return merged;
}

export function stableStringify(value) {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(",")}]`;
  if (value && typeof value === "object") {
    const keys = Object.keys(value).sort();
    return `{${keys.map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(",")}}`;
  }
  return JSON.stringify(value);
}

export function syncSignature(store) {
  if (!store || typeof store !== "object") return "";
  const copy = { ...store };
  delete copy.updatedAt;
  delete copy.device;
  return stableStringify(copy);
}

export function applyRemoteStore(payload) {
  if (!payload || typeof payload !== "object") return;
  S.sync.applying = true;
  const merged = mergeStores(snapshot(), payload);
  S.data = normalizeStore(merged);
  ensureTimer();
  S.sync.applying = false;
  saveSoon();
  emit("sync");
}

/* ── Lookups ─────────────────────────────────────────────────────────── */

export const activeHabits = () => S.data.habits.filter((item) => !item.deleted);
export const activeTasks = () => S.data.tasks.filter((item) => !item.deleted);
export const activeCategories = () => S.data.categories.filter((item) => !item.deleted);
export const activeTags = () => S.data.tags.filter((item) => !item.deleted);
export const activeSessions = () => S.data.focusSessions.filter((item) => !item.deleted);
export const activeEntries = () => S.data.entries.filter((item) => !item.deleted);

export function habitById(id) {
  return S.data.habits.find((item) => String(item.id) === String(id)) || null;
}
export function taskById(id) {
  return S.data.tasks.find((item) => String(item.id) === String(id)) || null;
}
export function tagById(id) {
  return S.data.tags.find((item) => String(item.id) === String(id)) || null;
}
export function activeTagById(id) {
  return activeTags().find((item) => String(item.id) === String(id)) || null;
}
export function categoryById(id) {
  return (
    S.data.categories.find((item) => String(item.id) === String(id)) || {
      id: "",
      name: "Uncategorised",
      accent: "outline",
      icon: "circle",
      deleted: true,
    }
  );
}

/* ── Habits ──────────────────────────────────────────────────────────── */

export function entryFor(habitId, date = todayKey()) {
  const key = String(date ?? todayKey());
  return (
    activeEntries().find(
      (item) => String(item.habitId) === String(habitId) && String(item.date) === key
    ) || null
  );
}

export function habitValue(habitId, date = todayKey()) {
  return Number(entryFor(habitId, date)?.value ?? 0);
}

export function habitDone(habit, date = todayKey()) {
  if (!habit) return false;
  return habitValue(habit.id, date) >= Math.max(1, Number(habit.target ?? 1));
}

export function habitProgress(habit, date = todayKey()) {
  if (!habit) return 0;
  return Math.max(0, Math.min(1, habitValue(habit.id, date) / Math.max(1, Number(habit.target ?? 1))));
}

export function scheduledOn(habit, date = todayKey()) {
  if (!habit) return false;
  const days = Array.isArray(habit.days) ? habit.days : [0, 1, 2, 3, 4, 5, 6];
  return days.map(Number).includes(weekdayOf(date));
}

export function setHabitEntry(habitId, date, value, quiet = false) {
  const key = String(date ?? todayKey());
  const habit = habitById(habitId);
  if (!habit) return false;
  const max = Math.max(1, Number(habit.target ?? 1));
  const nextValue = Math.max(0, Math.min(max, Number(value ?? 0)));
  const existing = entryFor(habitId, key);
  let next;
  if (existing) {
    next = S.data.entries.map((item) =>
      String(item.id) === String(existing.id)
        ? stamp({ ...item, value: nextValue })
        : item
    );
  } else {
    next = S.data.entries.concat([
      stamp({
        id: newId("entry"),
        habitId: String(habitId),
        date: key,
        value: nextValue,
        note: "",
        deleted: false,
      }),
    ]);
  }
  S.data.entries = next;
  if (!quiet) {
    changed();
    maybeCelebrateHabits(habitId);
  } else {
    saveSoon();
  }
  return true;
}

export function incrementHabit(habitId, delta = 1) {
  setHabitEntry(habitId, todayKey(), habitValue(habitId, todayKey()) + Number(delta || 1));
}

export function toggleHabitDone(habitId) {
  const habit = habitById(habitId);
  if (!habit) return;
  setHabitEntry(habitId, todayKey(), habitDone(habit) ? 0 : Math.max(1, Number(habit.target ?? 1)));
}

function maybeCelebrateHabits(habitId) {
  const due = todayHabits();
  if (due.length === 0) return;
  const done = due.filter((habit) => habitDone(habit)).length;
  if (done >= due.length && S._celebratedDate !== todayKey()) {
    S._celebratedDate = todayKey();
    const habit = habitById(habitId);
    dispatchCelebration("habits", "All habits done", `${done} of ${due.length} cleared today`, habit?.icon || "check");
  }
}

export function createHabit(draft = {}) {
  const habit = stamp({
    id: newId("habit"),
    name: String(draft.name ?? "").trim() || "New habit",
    icon: String(draft.icon ?? "check_circle"),
    categoryId: String(draft.categoryId ?? activeCategories()[0]?.id ?? ""),
    days: Array.isArray(draft.days) ? draft.days.map(Number) : [0, 1, 2, 3, 4, 5, 6],
    time: String(draft.time ?? ""),
    target: Math.max(1, Number(draft.target ?? 1)),
    unit: String(draft.unit ?? "times"),
    note: String(draft.note ?? ""),
    createdAt: Date.now(),
    deleted: false,
  });
  S.data.habits = S.data.habits.concat([habit]);
  changed();
  return habit.id;
}

export function updateHabit(id, changes) {
  let found = false;
  S.data.habits = S.data.habits.map((item) => {
    if (String(item.id) !== String(id)) return item;
    found = true;
    return stamp({ ...item, ...(changes || {}) });
  });
  if (found) changed();
  return found;
}

export function deleteHabit(id) {
  S.data.habits = S.data.habits.map((item) =>
    String(item.id) === String(id) ? stamp({ ...item, deleted: true }) : item
  );
  changed();
}

export function currentStreak(habit) {
  if (!habit) return 0;
  let cursor = todayKey();
  if (!scheduledOn(habit, cursor)) cursor = previousScheduled(habit, cursor);
  let streak = 0;
  let guard = 0;
  while (guard++ < 400) {
    if (!scheduledOn(habit, cursor)) {
      cursor = addDays(cursor, -1);
      continue;
    }
    if (habitDone(habit, cursor)) {
      streak += 1;
      cursor = previousScheduled(habit, cursor);
    } else {
      if (cursor !== todayKey()) break;
      cursor = previousScheduled(habit, cursor);
    }
  }
  return streak;
}

function previousScheduled(habit, fromKey) {
  let cursor = addDays(fromKey, -1);
  for (let i = 0; i < 14; i += 1) {
    if (scheduledOn(habit, cursor)) return cursor;
    cursor = addDays(cursor, -1);
  }
  return cursor;
}

export function bestStreak(habit) {
  if (!habit) return 0;
  let cursor = dateKey(new Date(Math.max(Number(habit.createdAt ?? Date.now()) - 86400000 * 365, Date.now() - 86400000 * 365)));
  let best = 0;
  let run = 0;
  let guard = 0;
  while (cursor <= todayKey() && guard++ < 400) {
    if (scheduledOn(habit, cursor)) {
      if (habitDone(habit, cursor)) {
        run += 1;
        best = Math.max(best, run);
      } else if (cursor !== todayKey()) {
        run = 0;
      }
    }
    cursor = addDays(cursor, 1);
  }
  return Math.max(best, currentStreak(habit));
}

export function todayHabits() {
  return activeHabits()
    .filter((habit) => scheduledOn(habit, todayKey()))
    .sort((a, b) => String(a.time ?? "99:99").localeCompare(String(b.time ?? "99:99")));
}

/* ── Categories ──────────────────────────────────────────────────────── */

export function createCategory(draft = {}) {
  const category = stamp({
    id: newId("cat"),
    name: String(draft.name ?? "").trim() || "New category",
    accent: String(draft.accent ?? "primary"),
    icon: String(draft.icon ?? "label"),
    deleted: false,
  });
  S.data.categories = S.data.categories.concat([category]);
  changed();
  return category.id;
}

export function updateCategory(id, changes) {
  S.data.categories = S.data.categories.map((item) =>
    String(item.id) === String(id) ? stamp({ ...item, ...(changes || {}) }) : item
  );
  changed();
}

export function deleteCategory(id) {
  const fallback = activeCategories().find((item) => String(item.id) !== String(id))?.id ?? "";
  S.data.categories = S.data.categories.map((item) =>
    String(item.id) === String(id) ? stamp({ ...item, deleted: true }) : item
  );
  S.data.habits = S.data.habits.map((item) =>
    String(item.categoryId ?? "") === String(id) ? stamp({ ...item, categoryId: fallback }) : item
  );
  S.data.tasks = S.data.tasks.map((item) =>
    String(item.categoryId ?? "") === String(id) ? stamp({ ...item, categoryId: fallback }) : item
  );
  changed();
}

/* ── Tags ────────────────────────────────────────────────────────────── */

export function createTag(draft = {}) {
  const tag = stamp({
    id: newId("tag"),
    name: String(draft.name ?? "").trim() || "New tag",
    accent: String(draft.accent ?? "primary"),
    color: String(draft.color ?? ""),
    shape: String(draft.shape ?? "Cookie7Sided"),
    icon: String(draft.icon ?? "local_fire_department"),
    focusMinutes: Math.max(1, Number(draft.focusMinutes ?? 25)),
    breakMinutes: Math.max(1, Number(draft.breakMinutes ?? 5)),
    longBreakMinutes: Math.max(1, Number(draft.longBreakMinutes ?? 15)),
    cycles: Math.max(1, Number(draft.cycles ?? 4)),
    dailyGoalMinutes: Math.max(0, Math.min(720, Number(draft.dailyGoalMinutes ?? 0))),
    autoStartBreak: draft.autoStartBreak !== false,
    autoStartFocus: draft.autoStartFocus === true,
    deleted: false,
  });
  S.data.tags = S.data.tags.concat([tag]);
  changed();
  return tag.id;
}

export function updateTag(id, changes) {
  let found = false;
  S.data.tags = S.data.tags.map((item) => {
    if (String(item.id) !== String(id)) return item;
    found = true;
    const merged = { ...item, ...(changes || {}) };
    merged.focusMinutes = Math.max(1, Number(merged.focusMinutes ?? 25));
    merged.breakMinutes = Math.max(1, Number(merged.breakMinutes ?? 5));
    merged.longBreakMinutes = Math.max(1, Number(merged.longBreakMinutes ?? 15));
    merged.cycles = Math.max(1, Number(merged.cycles ?? 4));
    merged.dailyGoalMinutes = Math.max(0, Math.min(720, Number(merged.dailyGoalMinutes ?? 0)));
    return stamp(merged);
  });
  if (found) {
    if (S.data.timer && String(S.data.timer.tagId) === String(id) && !S.data.timer.running) {
      const tag = tagById(id);
      const minutes = minutesForPhase(tag, S.data.timer.phase);
      S.data.timer = {
        ...S.data.timer,
        durationSec: minutes * 60,
        accumulatedSec: Math.min(Number(S.data.timer.accumulatedSec ?? 0), minutes * 60),
        updatedAt: Date.now(),
      };
    }
    changed();
  }
  return found;
}

export function deleteTag(id) {
  if (activeTags().length <= 1) return false;
  S.data.tags = S.data.tags.map((item) =>
    String(item.id) === String(id) ? stamp({ ...item, deleted: true }) : item
  );
  if (S.data.timer && String(S.data.timer.tagId) === String(id)) {
    const fallback = activeTags()[0];
    S.data.timer = { ...S.data.timer, tagId: fallback?.id ?? "", updatedAt: Date.now() };
  }
  changed();
  return true;
}

export function minutesForPhase(tag, phase) {
  if (!tag) return 25;
  if (phase === "shortBreak") return Math.max(1, Number(tag.breakMinutes ?? 5));
  if (phase === "longBreak") return Math.max(1, Number(tag.longBreakMinutes ?? 15));
  return Math.max(1, Number(tag.focusMinutes ?? 25));
}

/* ── Tasks ───────────────────────────────────────────────────────────── */

function normalizeSubtasks(list) {
  return Array.from(list || [])
    .map((task) => ({
      id: String(task.id ?? newId("sub")),
      title: String(task.title ?? "").trim(),
      done: task.done === true,
      updatedAt: Number(task.updatedAt ?? Date.now()),
    }))
    .filter((task) => task.title.length > 0);
}

export function createTask(draft = {}) {
  const task = stamp({
    id: newId("task"),
    title: String(draft.title ?? "").trim() || "New task",
    notes: String(draft.notes ?? ""),
    categoryId: String(draft.categoryId ?? activeCategories()[0]?.id ?? ""),
    due: String(draft.due ?? ""),
    done: draft.done === true,
    priority: Math.max(0, Math.min(2, Number(draft.priority ?? 0))),
    subtasks: normalizeSubtasks(draft.subtasks),
    taskTag: String(draft.taskTag ?? ""),
    deleted: false,
    createdAt: Date.now(),
  });
  S.data.tasks = S.data.tasks.concat([task]);
  changed();
  return task.id;
}

export function updateTask(id, changes) {
  let found = false;
  S.data.tasks = S.data.tasks.map((item) => {
    if (String(item.id) !== String(id)) return item;
    found = true;
    const merged = { ...item, ...(changes || {}) };
    if (changes && Object.prototype.hasOwnProperty.call(changes, "subtasks"))
      merged.subtasks = normalizeSubtasks(changes.subtasks);
    merged.priority = Math.max(0, Math.min(2, Number(merged.priority ?? 0)));
    return stamp(merged);
  });
  if (found) changed();
  return found;
}

export function deleteTask(id) {
  S.data.tasks = S.data.tasks.map((item) =>
    String(item.id) === String(id) ? stamp({ ...item, deleted: true }) : item
  );
  changed();
}

export function toggleTask(id) {
  const task = taskById(id);
  if (task) updateTask(id, { done: !task.done });
}

export function addSubtask(taskId, title) {
  const task = taskById(taskId);
  const text = String(title ?? "").trim();
  if (!task || !text) return;
  updateTask(taskId, {
    subtasks: Array.from(task.subtasks ?? []).concat([
      { id: newId("sub"), title: text, done: false, updatedAt: Date.now() },
    ]),
  });
}

export function toggleSubtask(taskId, subtaskId) {
  const task = taskById(taskId);
  if (!task) return;
  updateTask(taskId, {
    subtasks: Array.from(task.subtasks ?? []).map((sub) =>
      String(sub.id) === String(subtaskId)
        ? { ...sub, done: !sub.done, updatedAt: Date.now() }
        : sub
    ),
  });
}

export function removeSubtask(taskId, subtaskId) {
  const task = taskById(taskId);
  if (!task) return;
  updateTask(taskId, {
    subtasks: Array.from(task.subtasks ?? []).filter(
      (sub) => String(sub.id) !== String(subtaskId)
    ),
  });
}

export function taskFiltered(filter = S.ui.taskFilter) {
  const now = todayKey();
  let list = activeTasks();
  if (filter === "today")
    list = list.filter((task) => !task.done && (task.due === now || (task.due && task.due < now)));
  else if (filter === "upcoming") list = list.filter((task) => !task.done && task.due > now);
  else if (filter === "done") list = list.filter((task) => task.done);
  else if (filter === "open") list = list.filter((task) => !task.done);
  return list.sort((a, b) => {
    if (a.done !== b.done) return a.done ? 1 : -1;
    if (a.due !== b.due) return String(a.due ?? "9999").localeCompare(String(b.due ?? "9999"));
    if (a.priority !== b.priority) return Number(b.priority ?? 0) - Number(a.priority ?? 0);
    return Number(b.createdAt ?? 0) - Number(a.createdAt ?? 0);
  });
}

export function tasksForTag(tagId) {
  return activeTasks()
    .filter((task) => String(task.taskTag ?? "") === String(tagId ?? ""))
    .sort((a, b) => {
      if (a.done !== b.done) return a.done ? 1 : -1;
      if (a.due !== b.due) return String(a.due ?? "9999").localeCompare(String(b.due ?? "9999"));
      return Number(b.createdAt ?? 0) - Number(a.createdAt ?? 0);
    });
}

export function createTagTask(tagId, title) {
  return createTask({
    title,
    taskTag: String(tagId ?? ""),
    due: todayKey(),
    categoryId: activeCategories()[0]?.id ?? "",
  });
}

export const openTaskCount = () => activeTasks().filter((task) => !task.done).length;
export const doneTaskCount = () => activeTasks().filter((task) => task.done).length;
export const dueTodayTaskCount = () =>
  activeTasks().filter((task) => !task.done && task.due && task.due <= todayKey()).length;
export const overdueTaskCount = () =>
  activeTasks().filter((task) => !task.done && task.due && task.due < todayKey()).length;

export function taskSubtaskProgress(task) {
  const subtasks = Array.from(task?.subtasks ?? []);
  if (!subtasks.length) return -1;
  return subtasks.filter((sub) => sub.done).length / subtasks.length;
}

/* ── Timer ───────────────────────────────────────────────────────────── */

export function timerPhase() {
  return String(S.data.timer?.phase ?? "focus");
}
export function timerRunning() {
  return S.data.timer?.running === true;
}
export function activeTagId() {
  return String(S.data.timer?.tagId ?? activeTags()[0]?.id ?? "");
}
export function activeTag() {
  return tagById(activeTagId()) ?? activeTags()[0] ?? null;
}
export function timerCycle() {
  return Math.max(0, Number(S.data.timer?.cycle ?? 0));
}
export function timerDurationSec() {
  return Math.max(
    1,
    Number(S.data.timer?.durationSec ?? minutesForPhase(activeTag(), timerPhase()) * 60)
  );
}
export function computeSecondsLeft(value = S.data.timer) {
  if (!value) return 0;
  const duration = Math.max(1, Number(value.durationSec ?? 0));
  const accumulated = Math.max(0, Number(value.accumulatedSec ?? 0));
  const runningPart = value.running
    ? (Date.now() - Number(value.runStartedAt ?? Date.now())) / 1000
    : 0;
  return Math.max(0, Math.ceil(duration - accumulated - runningPart));
}
export function elapsedSeconds(value = S.data.timer) {
  if (!value) return 0;
  const accumulated = Math.max(0, Number(value.accumulatedSec ?? 0));
  const runningPart = value.running
    ? (Date.now() - Number(value.runStartedAt ?? Date.now())) / 1000
    : 0;
  return Math.max(0, Math.min(Number(value.durationSec ?? 0), accumulated + runningPart));
}
export function timerSecondsLeft() {
  return computeSecondsLeft(S.data.timer);
}
export function timerProgress() {
  return Math.max(0, Math.min(1, 1 - timerSecondsLeft() / timerDurationSec()));
}

export function makeTimer(tagIdValue, phase = "focus", cycle = 0, running = false) {
  const tag =
    activeTagById(tagIdValue) ?? activeTagById(activeTagId()) ?? activeTags()[0] ?? null;
  const minutes = minutesForPhase(tag, phase);
  const now = Date.now();
  return {
    phase: String(phase ?? "focus"),
    tagId: String(tag?.id ?? ""),
    taskId: "",
    note: "",
    running: running === true,
    startedAt: now,
    runStartedAt: running === true ? now : 0,
    accumulatedSec: 0,
    durationSec: Math.round(minutes * 60),
    sessionId: newId("session"),
    cycle: Math.max(0, Number(cycle ?? 0)),
    updatedAt: now,
    device: deviceId,
  };
}

export function ensureTimer() {
  if (!S.data.timer || typeof S.data.timer !== "object" || !String(S.data.timer.phase ?? "").length) {
    S.data.timer = makeTimer(activeTagId(), "focus", 0, false);
    return;
  }
  if (!tagById(S.data.timer.tagId)) {
    S.data.timer = { ...S.data.timer, tagId: activeTags()[0]?.id ?? "", updatedAt: Date.now() };
  }
}

export function selectTag(tagIdValue) {
  if (timerRunning()) return false;
  const tag = activeTagById(tagIdValue);
  if (!tag) return false;
  S.data.timer = makeTimer(tag.id, timerPhase(), timerCycle(), false);
  changed();
  return true;
}

export function setTimerNote(text) {
  if (!S.data.timer) ensureTimer();
  S.data.timer = { ...S.data.timer, note: String(text ?? ""), updatedAt: Date.now() };
  saveSoon();
  emit("note");
}

export function startPhase(tagIdValue, phase, cycle = 0, running = false) {
  S.data.timer = makeTimer(tagIdValue, phase, cycle, running);
  changed("timer");
}

export function startFocusNow(tagIdValue = activeTagId()) {
  const tag = activeTagById(tagIdValue) ?? activeTags()[0] ?? null;
  startPhase(tag?.id ?? "", "focus", timerPhase() === "focus" ? timerCycle() : 0, true);
}

export function toggleTimer() {
  if (!S.data.timer) {
    startFocusNow(activeTagId());
    return;
  }
  if (timerRunning()) pauseTimer();
  else if (timerSecondsLeft() <= 0) startFocusNow(activeTagId());
  else resumeTimer();
}

export function pauseTimer() {
  if (!S.data.timer || !S.data.timer.running) return;
  const elapsed = elapsedSeconds(S.data.timer);
  S.data.timer = {
    ...S.data.timer,
    running: false,
    runStartedAt: 0,
    accumulatedSec: elapsed,
    updatedAt: Date.now(),
  };
  changed("timer");
}

export function resumeTimer() {
  if (!S.data.timer || S.data.timer.running) return;
  if (computeSecondsLeft(S.data.timer) <= 0) {
    startFocusNow(activeTagId());
    return;
  }
  S.data.timer = {
    ...S.data.timer,
    running: true,
    runStartedAt: Date.now(),
    updatedAt: Date.now(),
  };
  changed("timer");
}

export function resetTimer() {
  if (!S.data.timer) return;
  const current = S.data.timer;
  finalizeSession(current, false);
  S.data.timer = makeTimer(current.tagId, current.phase, current.cycle, false);
  changed("timer");
}

export function skipTimer() {
  if (!S.data.timer) return;
  const current = S.data.timer;
  finalizeSession(current, false);
  advanceAfter(current, false);
}

function finalizeSession(value, completed) {
  if (!value) return null;
  const elapsed = Math.round(elapsedSeconds(value));
  if (elapsed < 15) return null;
  const session = stamp({
    id: String(value.sessionId ?? newId("session")),
    tagId: String(value.tagId ?? ""),
    taskId: String(value.taskId ?? ""),
    phase: String(value.phase ?? "focus"),
    startedAt: Number(value.startedAt ?? Date.now() - elapsed * 1000),
    endedAt: Date.now(),
    durationSec: elapsed,
    plannedSec: Math.max(1, Number(value.durationSec ?? elapsed)),
    completed: completed === true,
    note: String(value.note ?? ""),
    deleted: false,
  });
  if (!S.data.focusSessions.some((item) => String(item.id) === String(session.id))) {
    S.data.focusSessions = sortSessions(S.data.focusSessions.concat([session]));
  }
  return session;
}

export function completeCurrentPhase() {
  if (!S.data.timer) return;
  const current = S.data.timer;
  S.data.timer = {
    ...current,
    running: false,
    runStartedAt: 0,
    accumulatedSec: Math.max(Number(current.durationSec ?? 0), elapsedSeconds(current)),
    updatedAt: Date.now(),
  };
  const session = finalizeSession(S.data.timer, true);
  afterPhaseDone(S.data.timer, session);
}

function advanceAfter(current, completed) {
  const tag = tagById(current.tagId) ?? activeTag() ?? {};
  const phase = String(current.phase ?? "focus");
  const cycles = Math.max(1, Number(tag.cycles ?? 4));
  if (phase === "focus") {
    const count = Math.max(0, Number(current.cycle ?? 0)) + 1;
    const long = count >= cycles;
    startPhase(tag.id, long ? "longBreak" : "shortBreak", count, tag.autoStartBreak === true);
  } else {
    const cycle = phase === "longBreak" ? 0 : Math.max(0, Number(current.cycle ?? 0));
    startPhase(tag.id, "focus", cycle, tag.autoStartFocus === true);
  }
}

function afterPhaseDone(value, session) {
  const tag = tagById(value.tagId) ?? activeTag();
  const phase = String(value.phase ?? "focus");
  if (phase === "focus") {
    const minutes = Math.round(Number(session?.durationSec ?? value.durationSec ?? 0) / 60);
    dispatchCelebration(
      "focus",
      `Focus complete · ${tag?.name ?? "Focus"}`,
      `${minutes} ${minutes === 1 ? "minute" : "minutes"} banked. Time to breathe.`,
      "celebration"
    );
    notify("Focus complete", `${tag?.name ?? "Focus"} · time for a break`);
  } else {
    notify("Break over", "Ready for another focus session?");
  }
  advanceAfter(value, true);
  changed("timer");
}

export function tickTimer() {
  S.clock += 1;
  if (S.data.timer?.running && computeSecondsLeft(S.data.timer) <= 0) {
    completeCurrentPhase();
    return;
  }
  emit("tick");
}

export function reconcileTimer() {
  ensureTimer();
  if (S.data.timer?.running && computeSecondsLeft(S.data.timer) <= 0) completeCurrentPhase();
}

function dispatchCelebration(kind, title, text, symbol) {
  window.dispatchEvent(
    new CustomEvent("panadoro:celebrate", { detail: { kind, title, text, symbol } })
  );
}

function notify(title, body) {
  try {
    if ("Notification" in window && Notification.permission === "granted")
      new Notification(title, { body, icon: "./assets/icon.svg", tag: "panadoro-timer" });
  } catch (_) {}
}

/* ── Focus statistics ────────────────────────────────────────────────── */

export function sessionsInRange(days, phase) {
  const start = dateFromKey(addDays(todayKey(), -(Math.max(1, Number(days ?? 7)) - 1)));
  const startMs = new Date(start.getFullYear(), start.getMonth(), start.getDate()).getTime();
  return activeSessions().filter((session) => {
    if (phase && String(session.phase) !== String(phase)) return false;
    return Number(session.endedAt ?? 0) >= startMs;
  });
}

export function secondsInRange(days, phase) {
  return sessionsInRange(days, phase).reduce(
    (total, session) => total + Math.max(0, Number(session.durationSec ?? 0)),
    0
  );
}

export function focusMinutesInRange(days) {
  return Math.round(secondsInRange(days, "focus") / 60);
}

export const todayFocusSeconds = () =>
  activeSessions()
    .filter(
      (session) =>
        String(session.phase) === "focus" && dateKey(new Date(Number(session.endedAt ?? 0))) === todayKey()
    )
    .reduce((total, session) => total + Math.max(0, Number(session.durationSec ?? 0)), 0);

export const todayFocusMinutes = () => Math.round(todayFocusSeconds() / 60);

export function focusStreak() {
  const days = {};
  for (const session of activeSessions()) {
    if (String(session.phase) !== "focus" || Number(session.durationSec ?? 0) < 30) continue;
    days[dateKey(new Date(Number(session.endedAt ?? 0)))] = true;
  }
  let cursor = todayKey();
  if (!days[cursor]) cursor = addDays(cursor, -1);
  let streak = 0;
  while (days[cursor] && streak < 3650) {
    streak += 1;
    cursor = addDays(cursor, -1);
  }
  return streak;
}

export function tagSecondsInRange(tagId, days) {
  return sessionsInRange(days, "focus")
    .filter((session) => String(session.tagId ?? "") === String(tagId ?? ""))
    .reduce((total, session) => total + Math.max(0, Number(session.durationSec ?? 0)), 0);
}

export function tagTodayMinutes(tagId) {
  return Math.round(tagSecondsInRange(tagId, 1) / 60);
}

export function tagPercent(tagId, days) {
  const total = secondsInRange(days, "focus");
  return total > 0 ? tagSecondsInRange(tagId, days) / total : 0;
}

export function tagStreak(tagId) {
  const dates = {};
  for (const session of activeSessions()) {
    if (String(session.phase) !== "focus" || String(session.tagId ?? "") !== String(tagId ?? ""))
      continue;
    dates[dateKey(new Date(Number(session.endedAt ?? 0)))] = true;
  }
  let key = todayKey();
  if (!dates[key]) key = addDays(key, -1);
  let streak = 0;
  while (dates[key]) {
    streak += 1;
    key = addDays(key, -1);
  }
  return streak;
}

export function tagGoalProgress(tag) {
  const goal = Math.max(0, Number(tag?.dailyGoalMinutes ?? 0));
  if (goal <= 0) return -1;
  return Math.max(0, Math.min(1, tagTodayMinutes(tag?.id ?? "") / goal));
}

export function tagStats(days) {
  const totals = {};
  for (const session of sessionsInRange(days, "focus")) {
    const id = String(session.tagId ?? "");
    if (!totals[id]) totals[id] = { tagId: id, seconds: 0, sessions: 0 };
    totals[id].seconds += Math.max(0, Number(session.durationSec ?? 0));
    totals[id].sessions += 1;
  }
  const allSeconds = Math.max(1, secondsInRange(days, "focus"));
  return Object.keys(totals)
    .map((id) => {
      const tag = tagById(id);
      return {
        ...totals[id],
        tag,
        name: tag?.name ?? "Untagged",
        accent: tag?.accent ?? "outline",
        color: tag?.color ?? "",
        shape: tag?.shape ?? "Circle",
        percent: totals[id].seconds / allSeconds,
        streak: tagStreak(id),
        goalMinutes: Math.max(0, Number(tag?.dailyGoalMinutes ?? 0)),
        todayMinutes: tagTodayMinutes(id),
      };
    })
    .sort((a, b) => b.seconds - a.seconds);
}

export function dailyFocusSeries(days) {
  const keys = lastNDays(Math.max(1, Number(days ?? 7)));
  const index = {};
  for (const key of keys)
    index[key] = { date: key, label: weekdayName(key).slice(0, 2), focus: 0, breaks: 0 };
  for (const session of activeSessions()) {
    const key = dateKey(new Date(Number(session.endedAt ?? 0)));
    if (!index[key]) continue;
    if (String(session.phase) === "focus")
      index[key].focus += Math.max(0, Number(session.durationSec ?? 0));
    else index[key].breaks += Math.max(0, Number(session.durationSec ?? 0));
  }
  return keys.map((key) => index[key]);
}

export function dailyTagFocusSeries(days) {
  const keys = lastNDays(Math.max(1, Number(days ?? 7)));
  const index = {};
  for (const key of keys)
    index[key] = { date: key, label: weekdayName(key).slice(0, 2), total: 0, segments: [] };
  for (const session of activeSessions()) {
    if (String(session.phase) !== "focus") continue;
    const key = dateKey(new Date(Number(session.endedAt ?? 0)));
    const day = index[key];
    if (!day) continue;
    const tagId = String(session.tagId ?? "");
    let segment = day.segments.find((item) => item.tagId === tagId);
    if (!segment) {
      segment = { tagId, tag: tagById(tagId), seconds: 0 };
      day.segments.push(segment);
    }
    segment.seconds += Math.max(0, Number(session.durationSec ?? 0));
    day.total += Math.max(0, Number(session.durationSec ?? 0));
  }
  for (const key of keys) index[key].segments.sort((a, b) => b.seconds - a.seconds);
  return keys.map((key) => index[key]);
}

export function tagFocusSeries(tagId, days) {
  return dailyTagFocusSeries(days).map((day) => {
    const segment = day.segments.find((item) => String(item.tagId) === String(tagId ?? ""));
    const seconds = Math.max(0, Number(segment?.seconds ?? 0));
    return {
      date: day.date,
      label: day.label,
      total: seconds,
      segments: seconds > 0 ? [{ tagId: String(tagId ?? ""), tag: tagById(tagId), seconds }] : [],
    };
  });
}

export function focusHeatmapWeeks(weeks, tagId = "") {
  const count = Math.max(1, Number(weeks ?? 12));
  const offset = weekStartOffset();
  const start = addDays(todayKey(), -offset - (count - 1) * 7);
  const wantedTag = String(tagId ?? "");
  const out = [];
  for (let i = 0; i < count * 7; i += 1) {
    const key = addDays(start, i);
    let value = 0;
    for (const session of activeSessions()) {
      if (String(session.phase) !== "focus") continue;
      if (wantedTag.length > 0 && String(session.tagId ?? "") !== wantedTag) continue;
      if (dateKey(new Date(Number(session.endedAt ?? 0))) === key)
        value += Math.max(0, Number(session.durationSec ?? 0));
    }
    out.push({ date: key, value, future: key > todayKey() });
  }
  return out;
}

export function timeOfDayStats(days) {
  const buckets = [
    { id: "night", label: "Night", range: "00–06", seconds: 0 },
    { id: "morning", label: "Morning", range: "06–12", seconds: 0 },
    { id: "afternoon", label: "Afternoon", range: "12–18", seconds: 0 },
    { id: "evening", label: "Evening", range: "18–24", seconds: 0 },
  ];
  for (const session of sessionsInRange(days, "focus")) {
    const hour = new Date(Number(session.startedAt ?? session.endedAt ?? 0)).getHours();
    const index = Math.max(0, Math.min(3, Math.floor(hour / 6)));
    buckets[index].seconds += Math.max(0, Number(session.durationSec ?? 0));
  }
  return buckets;
}

export function breakRatio(days) {
  const focus = secondsInRange(days, "focus");
  const breaks = secondsInRange(days, "shortBreak") + secondsInRange(days, "longBreak");
  return { focus, breaks, ratio: breaks > 0 ? focus / breaks : 0 };
}

export function bestFocusDay(days) {
  const series = dailyFocusSeries(days);
  let best = { date: "", focus: 0 };
  for (const day of series) if (day.focus > best.focus) best = day;
  return best;
}

export function recentSessions(limit = 8) {
  return activeSessions().slice(0, Math.max(1, Number(limit ?? 8)));
}

export function deleteSession(id) {
  S.data.focusSessions = S.data.focusSessions.map((session) =>
    String(session.id) === String(id) ? stamp({ ...session, deleted: true }) : session
  );
  changed();
}

export function habitStats(days) {
  const list = lastNDays(Math.max(1, Number(days ?? 30)));
  return activeHabits()
    .map((habit) => {
      let due = 0;
      let done = 0;
      for (const key of list) {
        if (!scheduledOn(habit, key)) continue;
        due += 1;
        if (habitDone(habit, key)) done += 1;
      }
      return {
        habit,
        name: habit.name,
        due,
        done,
        rate: due > 0 ? done / due : 0,
        streak: currentStreak(habit),
        best: bestStreak(habit),
      };
    })
    .sort((a, b) => b.rate - a.rate);
}

export function habitHeatmap(habit, weeks = 16) {
  return lastNDays(Math.max(7, Number(weeks ?? 16) * 7)).map((key) => ({
    date: key,
    value: habitDone(habit, key) ? 1 : 0,
    scheduled: scheduledOn(habit, key),
  }));
}

/* ── Public leaderboard payload ──────────────────────────────────────── */

export function publicLeaderboardTags() {
  const weekDays = thisWeekDays();
  const monthStart = `${todayKey().slice(0, 7)}-01`;
  const wanted = S.settings.publicTagIds.map(String);
  return activeTags()
    .filter((tag) => wanted.includes(String(tag.id)))
    .map((tag) => {
      let day = 0;
      let week = 0;
      let month = 0;
      for (const session of activeSessions()) {
        if (String(session.phase) !== "focus" || String(session.tagId ?? "") !== String(tag.id))
          continue;
        const key = dateKey(new Date(Number(session.endedAt ?? 0)));
        const seconds = Math.max(0, Number(session.durationSec ?? 0));
        if (key === todayKey()) day += seconds;
        if (weekDays.includes(key)) week += seconds;
        if (key >= monthStart && key <= todayKey()) month += seconds;
      }
      return {
        id: String(tag.id),
        name: String(tag.name ?? ""),
        accent: String(tag.accent ?? "primary"),
        color: String(tag.color ?? ""),
        shape: String(tag.shape ?? "Cookie7Sided"),
        icon: String(tag.icon ?? "local_fire_department"),
        updatedAt: Number(tag.updatedAt ?? 0),
        day,
        week,
        month,
      };
    });
}

export function effectiveDisplayName() {
  const custom = String(S.settings.displayName ?? "").trim();
  if (custom) return custom.slice(0, 40);
  const local = String(S.auth.email ?? "").split("@")[0].trim();
  if (!local) return "Panadoro user";
  return local
    .replace(/[._-]+/g, " ")
    .split(" ")
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ")
    .slice(0, 40);
}

export function setTagPublic(tagId, isPublic) {
  const id = String(tagId ?? "");
  if (!id) return;
  const current = S.settings.publicTagIds;
  if (current.includes(id) === Boolean(isPublic)) return;
  const next = current.filter((item) => item !== id);
  if (isPublic) next.push(id);
  S.settings.publicTagIds = next;
  S.settings.publicTagsUpdatedAt = Date.now();
  saveSettings();
}

export function setDisplayName(value) {
  const clean = String(value ?? "").trim().slice(0, 40);
  if (clean === S.settings.displayName) return;
  S.settings.displayName = clean;
  S.settings.displayNameUpdatedAt = Date.now();
  saveSettings();
}

/**
 * Adopt a newer preference published by another device. Timestamps are per
 * field, so a stale phone cannot undo a name or public-tag change made later
 * somewhere else.
 */
export function adoptLeaderboardSettings({
  displayName,
  nameUpdatedAt,
  publicTagIds,
  tagsUpdatedAt,
}) {
  let changed = false;
  if (Number(nameUpdatedAt ?? 0) > Number(S.settings.displayNameUpdatedAt ?? 0)) {
    const clean = String(displayName ?? "").trim().slice(0, 40);
    if (clean !== S.settings.displayName) changed = true;
    S.settings.displayName = clean;
    S.settings.displayNameUpdatedAt = Number(nameUpdatedAt);
  }
  if (Number(tagsUpdatedAt ?? 0) > Number(S.settings.publicTagsUpdatedAt ?? 0)) {
    const next = Array.isArray(publicTagIds) ? publicTagIds.map(String) : [];
    if (next.join("|") !== S.settings.publicTagIds.join("|")) changed = true;
    S.settings.publicTagIds = next;
    S.settings.publicTagsUpdatedAt = Number(tagsUpdatedAt);
  }
  if (changed) saveSettings();
}

/* ── Reset / export ──────────────────────────────────────────────────── */

export function resetAllData() {
  const tombstone = (item) => stamp({ ...item, deleted: true });
  S.data.habits = S.data.habits.map(tombstone);
  S.data.entries = S.data.entries.map(tombstone);
  S.data.tasks = S.data.tasks.map(tombstone);
  S.data.focusSessions = S.data.focusSessions.map(tombstone);
  S.data.tags = S.data.tags.map(tombstone).concat(defaultTags().map(stamp));
  S.data.categories = S.data.categories.map(tombstone).concat(defaultCategories().map(stamp));
  S.data.timer = makeTimer(activeTags()[0]?.id ?? "", "focus", 0, false);
  changed("reset");
  window.dispatchEvent(new CustomEvent("panadoro:sync-now"));
}

export function exportData() {
  const blob = new Blob([JSON.stringify(snapshot(), null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `panadoro-${todayKey()}.json`;
  link.click();
  URL.revokeObjectURL(url);
}

export async function importData(file) {
  const text = await file.text();
  const parsed = JSON.parse(text);
  applyRemoteStore(parsed);
  saveNow();
  window.dispatchEvent(new CustomEvent("panadoro:sync-now"));
  emit("import");
}

export function accentNames() {
  return ACCENT_NAMES;
}
