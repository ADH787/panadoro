// Firebase REST bridge for the web app.
//
// The private store and the public board use the same documents as the Android
// and Quickshell clients. Leaderboard writes are read-merge-write operations
// guarded by Firestore's updateTime: two devices publishing at the same moment
// retry instead of overwriting one another, equal periods keep the larger focus
// total, and the newest display-name / public-tag preference wins.

import {
  AUTH_KEY,
  S,
  adoptLeaderboardSettings,
  applyRemoteStore,
  effectiveDisplayName,
  emit,
  normalizeStore,
  persistSettings,
  publicLeaderboardTags,
  saveSettings,
  snapshot,
  subscribe,
  syncSignature,
  thisWeekDays,
  todayKey,
} from "./state.js?v=1.9.4";

const PROJECT = "habit-tracker-bd354";
const API_KEY = "AIzaSyAc97ILytNHNXRbS_EapcllNLd1CcaoLPo";
const DATABASE = "default";
const PRIVATE_COLLECTION = "panadora";
const BOARD_COLLECTION = "panadora_leaderboard";
const MAX_ATTEMPTS = 4;
const BASE = `https://firestore.googleapis.com/v1/projects/${PROJECT}/databases/${DATABASE}/documents`;

let syncPromise = null;
let publishPromise = null;
let tokenPromise = null;
let syncTimer = 0;
let publishTimer = 0;
let initialized = false;

/* ── Auth persistence ───────────────────────────────────────────────── */

function saveAuth() {
  try {
    localStorage.setItem(
      AUTH_KEY,
      JSON.stringify({
        email: S.auth.email,
        uid: S.auth.uid,
        idToken: S.auth.idToken,
        refreshToken: S.auth.refreshToken,
        expiresAt: S.auth.expiresAt,
      })
    );
  } catch (_) {}
}

function loadAuth() {
  try {
    const raw = localStorage.getItem(AUTH_KEY);
    if (!raw) return;
    const value = JSON.parse(raw);
    S.auth = {
      ...S.auth,
      email: String(value.email ?? ""),
      uid: String(value.uid ?? ""),
      idToken: String(value.idToken ?? ""),
      refreshToken: String(value.refreshToken ?? ""),
      expiresAt: Number(value.expiresAt ?? 0) || 0,
      signedIn: String(value.uid ?? "").length > 0 && String(value.refreshToken ?? "").length > 0,
      busy: false,
      error: "",
    };
  } catch (_) {}
}

function setCloudState() {
  emit("cloud");
}

/* ── HTTP ───────────────────────────────────────────────────────────── */

async function request(url, options = {}, timeout = 30000) {
  const controller = new AbortController();
  const timer = window.setTimeout(() => controller.abort(), timeout);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const text = await response.text().catch(() => "");
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch (_) {}
    return { ok: response.ok, status: response.status, data, text };
  } finally {
    window.clearTimeout(timer);
  }
}

function encoded(value) {
  return encodeURIComponent(String(value ?? ""));
}

function privateUrl(uid = S.auth.uid) {
  return `${BASE}/${PRIVATE_COLLECTION}/${encoded(uid)}?key=${encoded(API_KEY)}`;
}

function boardUrl(uid = "") {
  const suffix = uid ? `/${encoded(uid)}` : "";
  return `${BASE}/${BOARD_COLLECTION}${suffix}?key=${encoded(API_KEY)}`;
}

/* ── Auth ───────────────────────────────────────────────────────────── */

const AUTH_ERRORS = [
  [/EMAIL_EXISTS/, "An account with this email already exists"],
  [/EMAIL_NOT_FOUND|INVALID_LOGIN_CREDENTIALS|INVALID_PASSWORD/, "Wrong email or password"],
  [/INVALID_EMAIL/, "That email address is not valid"],
  [/WEAK_PASSWORD/, "Use at least 6 characters"],
  [/MISSING_PASSWORD/, "Enter a password"],
  [/TOO_MANY_ATTEMPTS/, "Too many attempts. Try again later"],
  [/USER_DISABLED/, "This account is disabled"],
  [/OPERATION_NOT_ALLOWED/, "Email sign-in is not enabled in Firebase"],
];

function authErrorMessage(data, status) {
  const code = String(data?.error?.message ?? "");
  for (const [pattern, message] of AUTH_ERRORS) if (pattern.test(code)) return message;
  if (code) return code;
  if (status === 401 || status === 403) return "Firebase rejected the sign-in";
  return `Sign-in failed (HTTP ${status})`;
}

function adoptAuth(data, fallbackEmail) {
  const uid = String(data.localId ?? "");
  const refresh = String(data.refreshToken ?? "");
  const token = String(data.idToken ?? "");
  if (!uid || !refresh || !token) {
    S.auth.error = "Firebase returned an incomplete session";
    S.auth.busy = false;
    setCloudState();
    return false;
  }
  S.auth = {
    email: String(data.email ?? fallbackEmail ?? ""),
    uid,
    idToken: token,
    refreshToken: refresh,
    expiresAt: Date.now() + Number(data.expiresIn ?? 3600) * 1000,
    signedIn: true,
    busy: false,
    error: "",
  };
  saveAuth();
  setCloudState();
  syncNow();
  publishLeaderboard();
  refreshLeaderboard();
  return true;
}

async function authenticate(path, email, password) {
  const clean = String(email ?? "").trim();
  const pass = String(password ?? "");
  if (!clean || !pass) {
    S.auth.error = "Enter an email and password";
    setCloudState();
    return false;
  }
  S.auth.busy = true;
  S.auth.error = "";
  setCloudState();
  try {
    const { ok, status, data } = await request(
      `https://identitytoolkit.googleapis.com/v1/${path}?key=${encoded(API_KEY)}`,
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: clean, password: pass, returnSecureToken: true }),
      }
    );
    if (!ok) {
      S.auth.busy = false;
      S.auth.error = authErrorMessage(data, status);
      setCloudState();
      return false;
    }
    return adoptAuth(data, clean);
  } catch (error) {
    S.auth.busy = false;
    S.auth.error = "Could not reach Firebase";
    setCloudState();
    return false;
  }
}

export function signIn(email, password) {
  return authenticate("accounts:signInWithPassword", email, password);
}

export function createAccount(email, password) {
  return authenticate("accounts:signUp", email, password);
}

export function clearAuthError() {
  if (!S.auth.error) return;
  S.auth.error = "";
  setCloudState();
}

export async function ensureFreshToken(force = false) {
  if (!S.auth.signedIn) return false;
  if (!force && Number(S.auth.expiresAt ?? 0) > Date.now() + 90000) return true;
  if (tokenPromise) return tokenPromise;
  tokenPromise = (async () => {
    const refresh = S.auth.refreshToken;
    if (!refresh) return false;
    try {
      const response = await fetch(
        `https://securetoken.googleapis.com/v1/token?key=${encoded(API_KEY)}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/x-www-form-urlencoded" },
          body: `grant_type=refresh_token&refresh_token=${encodeURIComponent(refresh)}`,
        }
      );
      const data = await response.json().catch(() => ({}));
      if (!response.ok) {
        const reason = String(data?.error?.message ?? "");
        if (
          /INVALID_REFRESH_TOKEN|TOKEN_EXPIRED|INVALID_GRANT|USER_DISABLED|USER_NOT_FOUND/.test(
            reason
          )
        ) {
          signOut();
          S.auth.error = "Your session ended. Sign in again.";
          setCloudState();
        }
        return false;
      }
      S.auth.idToken = String(data.id_token ?? "");
      S.auth.refreshToken = String(data.refresh_token ?? refresh);
      S.auth.expiresAt = Date.now() + Number(data.expires_in ?? 3600) * 1000;
      if (data.user_id) S.auth.uid = String(data.user_id);
      saveAuth();
      setCloudState();
      return Boolean(S.auth.idToken);
    } catch (_) {
      // Offline is not a sign-out. The refresh token is still in storage.
      return false;
    } finally {
      tokenPromise = null;
    }
  })();
  return tokenPromise;
}

export function signOut() {
  const uid = S.auth.uid;
  const token = S.auth.idToken;
  if (uid && token) deleteBoard(uid, token, "");
  S.auth = {
    ...S.auth,
    email: "",
    uid: "",
    idToken: "",
    refreshToken: "",
    expiresAt: 0,
    signedIn: false,
    busy: false,
    error: "",
  };
  try {
    localStorage.removeItem(AUTH_KEY);
  } catch (_) {}
  S.board = { entries: [], loading: false, error: "", updatedAt: 0 };
  S.sync = { status: "off", message: "Sign in to sync", inProgress: false, applying: false };
  saveAuth();
  setCloudState();
}

/* ── Private store sync ─────────────────────────────────────────────── */

function firestoreString(fields, name) {
  return String(fields?.[name]?.stringValue ?? "");
}
function firestoreInt(fields, name) {
  return Number(fields?.[name]?.integerValue ?? 0) || 0;
}

function parsePrivateDocument(document) {
  const fields = document?.fields ?? {};
  let payload = null;
  try {
    const raw = fields?.payload?.stringValue;
    if (raw) payload = JSON.parse(raw);
  } catch (_) {}
  return {
    payload,
    revision: firestoreInt(fields, "revision"),
    updateTime: String(document?.updateTime ?? ""),
  };
}

export function syncSoon() {
  window.clearTimeout(syncTimer);
  syncTimer = window.setTimeout(() => syncNow(true), 700);
}

export async function syncNow(quiet = false) {
  if (!S.auth.signedIn) {
    S.sync.status = "off";
    S.sync.message = "Sign in to sync";
    setCloudState();
    return false;
  }
  if (syncPromise) return syncPromise;
  const before = S.sync.status;
  syncPromise = (async () => {
    S.sync.inProgress = true;
    if (!quiet) {
      S.sync.status = "syncing";
      S.sync.message = "Contacting Firebase…";
      setCloudState();
    }
    try {
      if (!(await ensureFreshToken())) {
        S.sync.status = "error";
        S.sync.message = "Offline — your session is still signed in";
        return false;
      }
      for (let attempt = 0; attempt < 3; attempt += 1) {
        const pulled = await request(privateUrl(), {
          headers: {
            Accept: "application/json",
            Authorization: `Bearer ${S.auth.idToken}`,
          },
        });
        if (pulled.status === 404) {
          const pushed = await pushPrivate("", 0, quiet);
          if (pushed === "conflict") continue;
          return pushed === true;
        }
        if (!pulled.ok) {
          S.sync.status = "error";
          S.sync.message =
            pulled.status === 401 || pulled.status === 403
              ? "Firebase rejected the sync request"
              : `Firebase sync failed (HTTP ${pulled.status})`;
          return false;
        }
        const remote = parsePrivateDocument(pulled.data);
        if (remote.payload) {
          const remoteSignature = syncSignature(remote.payload);
          applyRemoteStore(remote.payload);
          if (remoteSignature === syncSignature(snapshot())) {
            finishSync(remote.revision, quiet);
            return true;
          }
        }
        const pushed = await pushPrivate(remote.updateTime, remote.revision, quiet);
        if (pushed === "conflict") continue;
        return pushed === true;
      }
      S.sync.status = "error";
      S.sync.message = "The other device kept changing the data. Try again.";
      return false;
    } catch (error) {
      S.sync.status = "error";
      S.sync.message = "Could not reach Firebase";
      return false;
    } finally {
      S.sync.inProgress = false;
      syncPromise = null;
      if (!quiet || before !== S.sync.status) setCloudState();
    }
  })();
  return syncPromise;
}

async function pushPrivate(updateTime, baseRevision, quiet = false) {
  const revision = Number(baseRevision ?? 0) + 1;
  const body = {
    fields: {
      payload: { stringValue: JSON.stringify(snapshot()) },
      revision: { integerValue: String(revision) },
      updatedAt: { integerValue: String(Date.now()) },
    },
  };
  let url =
    `${privateUrl()}&updateMask.fieldPaths=payload` +
    `&updateMask.fieldPaths=revision&updateMask.fieldPaths=updatedAt`;
  url += updateTime
    ? `&currentDocument.updateTime=${encoded(updateTime)}`
    : "&currentDocument.exists=false";
  const pushed = await request(url, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
      Authorization: `Bearer ${S.auth.idToken}`,
    },
    body: JSON.stringify(body),
  });
  if (pushed.ok) {
    const result = pushed.data ?? {};
    finishSync(firestoreInt(result.fields ?? {}, "revision") || revision, quiet);
    return true;
  }
  if (isConflict(pushed.status, pushed.text)) return "conflict";
  S.sync.status = "error";
  S.sync.message =
    pushed.status === 401 || pushed.status === 403
      ? "Firebase rejected the sync request"
      : `Firebase sync failed (HTTP ${pushed.status})`;
  return false;
}

function finishSync(revision, quiet = false) {
  S.sync.status = "synced";
  S.sync.message = `Synced ${new Date().toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  })}`;
  S.settings.lastSyncAt = new Date().toLocaleString();
  if (quiet) persistSettings();
  else saveSettings();
}

/* ── Leaderboard ────────────────────────────────────────────────────── */

function isConflict(status, text) {
  if (status === 409 || status === 412) return true;
  if (status !== 400) return false;
  try {
    return JSON.parse(text ?? "{}")?.error?.status === "FAILED_PRECONDITION";
  } catch (_) {
    return false;
  }
}

function parseBoardTag(id, fields) {
  return {
    id: String(id),
    name: firestoreString(fields, "name") || "Focus",
    accent: firestoreString(fields, "accent") || "primary",
    color: firestoreString(fields, "color"),
    shape: firestoreString(fields, "shape") || "Cookie7Sided",
    icon: firestoreString(fields, "icon") || "local_fire_department",
    updatedAt: firestoreInt(fields, "updatedAt"),
    day: firestoreInt(fields, "day"),
    week: firestoreInt(fields, "week"),
    month: firestoreInt(fields, "month"),
  };
}

function parseBoardDocument(document, uidFallback = "") {
  if (!document) return null;
  const uid = String(document.name ?? "").split("/").pop() || String(uidFallback);
  const fields = document.fields ?? {};
  const tagFields = fields?.tags?.mapValue?.fields ?? {};
  const tags = Object.keys(tagFields).map((id) =>
    parseBoardTag(id, tagFields[id]?.mapValue?.fields ?? {})
  );
  if (!uid || tags.length === 0) return null;
  return {
    uid,
    displayName: firestoreString(fields, "displayName") || "Panadoro user",
    nameUpdatedAt: firestoreInt(fields, "nameUpdatedAt"),
    tagsUpdatedAt: firestoreInt(fields, "tagsUpdatedAt"),
    dayKey: firestoreString(fields, "dayKey"),
    weekKey: firestoreString(fields, "weekKey"),
    monthKey: firestoreString(fields, "monthKey"),
    updatedAt: firestoreInt(fields, "updatedAt"),
    updateTime: String(document.updateTime ?? ""),
    tags,
  };
}

function adoptFromBoard(board) {
  if (!board) return;
  const active = new Set(
    S.data.tags.filter((tag) => !tag.deleted).map((tag) => String(tag.id))
  );
  adoptLeaderboardSettings({
    displayName: board.displayName,
    nameUpdatedAt: board.nameUpdatedAt,
    publicTagIds: board.tags.map((tag) => String(tag.id)).filter((id) => active.has(id)),
    tagsUpdatedAt: board.tagsUpdatedAt,
  });
}

export async function refreshLeaderboard(quiet = false) {
  if (!S.auth.signedIn) {
    S.board = {
      entries: [],
      loading: false,
      error: "Sign in to join the leaderboard",
      updatedAt: 0,
    };
    setCloudState();
    return false;
  }
  if (!(await ensureFreshToken())) {
    S.board.loading = false;
    S.board.error = "Could not refresh the leaderboard";
    setCloudState();
    return false;
  }
  if (!quiet) {
    S.board.loading = true;
    S.board.error = "";
    setCloudState();
  }
  try {
    const response = await request(`${boardUrl()}&pageSize=300`, {
      headers: {
        Accept: "application/json",
        Authorization: `Bearer ${S.auth.idToken}`,
      },
    });
    if (!response.ok) {
      S.board.loading = false;
      S.board.error =
        response.status === 403
          ? "The leaderboard rules are not deployed yet"
          : `Could not load the leaderboard (HTTP ${response.status})`;
      setCloudState();
      return false;
    }
    const entries = [];
    for (const document of Array.from(response.data?.documents ?? [])) {
      const entry = parseBoardDocument(document);
      if (entry) entries.push(entry);
    }
    const signature = JSON.stringify(entries);
    const changedBoard = signature !== JSON.stringify(S.board.entries);
    S.board = {
      entries,
      loading: false,
      error: "",
      updatedAt: Date.now(),
    };
    const own = entries.find((entry) => entry.uid === S.auth.uid);
    if (own) adoptFromBoard(own);
    if (!quiet || changedBoard || S.board.error) setCloudState();
    return true;
  } catch (_) {
    S.board.loading = false;
    S.board.error = "Could not reach Firebase";
    setCloudState();
    return false;
  }
}

export async function publishLeaderboard() {
  if (!S.auth.signedIn) return false;
  if (publishPromise) return publishPromise;
  publishPromise = (async () => {
    if (!(await ensureFreshToken())) return false;
    for (let attempt = 0; attempt < MAX_ATTEMPTS; attempt += 1) {
      let remote = null;
      const fetched = await request(boardUrl(S.auth.uid), {
        headers: {
          Accept: "application/json",
          Authorization: `Bearer ${S.auth.idToken}`,
        },
      });
      if (fetched.status !== 404) {
        if (!fetched.ok) {
          S.board.error = `Could not settle the leaderboard (HTTP ${fetched.status})`;
          setCloudState();
          return false;
        }
        remote = parseBoardDocument(fetched.data, S.auth.uid);
        if (remote) adoptFromBoard(remote);
      }

      const settings = S.settings;
      const localTags = publicLeaderboardTags();
      const today = todayKey();
      const weekKey = thisWeekDays()[0] ?? today;
      const monthKey = today.slice(0, 7);
      // If the board holds a newer name choice than this client knows about,
      // publish the board's name instead of our fallback, so a stale client
      // cannot quietly revert the row while carrying its timestamp forward.
      const localNameAt = Number(settings.displayNameUpdatedAt ?? 0);
      const name =
        remote && Number(remote.nameUpdatedAt ?? 0) > localNameAt
          ? String(remote.displayName ?? "").trim() || effectiveDisplayName()
          : effectiveDisplayName();

      if (localTags.length === 0) {
        if (!remote) return true;
        const deleted = await deleteBoard(S.auth.uid, S.auth.idToken, remote.updateTime);
        if (deleted === "conflict") continue;
        if (deleted === true) return true;
        S.board.error = "Could not remove the public leaderboard entry";
        setCloudState();
        return false;
      }

      const mergedTags = mergeBoardTags(localTags, remote, today, weekKey, monthKey);
      if (mergedTags.length === 0) {
        if (!remote) return true;
        const deleted = await deleteBoard(S.auth.uid, S.auth.idToken, remote.updateTime);
        if (deleted === "conflict") continue;
        if (deleted === true) return true;
        continue;
      }

      const body = {
        fields: {
          displayName: { stringValue: name },
          nameUpdatedAt: {
            integerValue: String(
              Math.max(Number(settings.displayNameUpdatedAt ?? 0), remote?.nameUpdatedAt ?? 0)
            ),
          },
          tagsUpdatedAt: {
            integerValue: String(
              Math.max(Number(settings.publicTagsUpdatedAt ?? 0), remote?.tagsUpdatedAt ?? 0)
            ),
          },
          dayKey: { stringValue: today },
          weekKey: { stringValue: weekKey },
          monthKey: { stringValue: monthKey },
          updatedAt: { integerValue: String(Date.now()) },
          device: { stringValue: S.data.device ?? "web" },
          tags: {
            mapValue: {
              fields: Object.fromEntries(mergedTags.map((tag) => [tag.id, boardTagValue(tag)])),
            },
          },
        },
      };
      let url =
        `${boardUrl(S.auth.uid)}` +
        "&updateMask.fieldPaths=displayName" +
        "&updateMask.fieldPaths=nameUpdatedAt" +
        "&updateMask.fieldPaths=tagsUpdatedAt" +
        "&updateMask.fieldPaths=dayKey" +
        "&updateMask.fieldPaths=weekKey" +
        "&updateMask.fieldPaths=monthKey" +
        "&updateMask.fieldPaths=updatedAt" +
        "&updateMask.fieldPaths=device" +
        "&updateMask.fieldPaths=tags";
      url += remote?.updateTime
        ? `&currentDocument.updateTime=${encoded(remote.updateTime)}`
        : "&currentDocument.exists=false";
      const written = await request(url, {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Accept: "application/json",
          Authorization: `Bearer ${S.auth.idToken}`,
        },
        body: JSON.stringify(body),
      });
      if (written.ok) {
        S.board.error = "";
        setCloudState();
        return true;
      }
      if (isConflict(written.status, written.text)) continue;
      S.board.error = `Could not publish the leaderboard (HTTP ${written.status})`;
      setCloudState();
      return false;
    }
    S.board.error = "The leaderboard kept changing on another device. Try again.";
    setCloudState();
    return false;
  })();
  try {
    return await publishPromise;
  } finally {
    publishPromise = null;
  }
}

function boardTagValue(tag) {
  return {
    mapValue: {
      fields: {
        name: { stringValue: tag.name },
        accent: { stringValue: tag.accent },
        color: { stringValue: tag.color },
        shape: { stringValue: tag.shape },
        icon: { stringValue: tag.icon },
        updatedAt: { integerValue: String(tag.updatedAt ?? 0) },
        day: { integerValue: String(tag.day) },
        week: { integerValue: String(tag.week) },
        month: { integerValue: String(tag.month) },
      },
    },
  };
}

function mergeBoardTags(localTags, remote, dayKey, weekKey, monthKey) {
  if (!remote) return localTags;
  const localById = Object.fromEntries(localTags.map((tag) => [tag.id, tag]));
  const remoteById = Object.fromEntries(remote.tags.map((tag) => [tag.id, tag]));
  const localAt = Number(S.settings.publicTagsUpdatedAt ?? 0);
  const ids =
    localAt > remote.tagsUpdatedAt
      ? Object.keys(localById)
      : localAt < remote.tagsUpdatedAt
        ? Object.keys(remoteById)
        : Array.from(new Set([...Object.keys(localById), ...Object.keys(remoteById)]));
  const sameDay = remote.dayKey === dayKey;
  const sameWeek = remote.weekKey === weekKey;
  const sameMonth = remote.monthKey === monthKey;
  const out = [];
  for (const id of ids) {
    const local = localById[id];
    const remoteTag = remoteById[id];
    if (!local && !remoteTag) continue;
    const meta =
      !local || (remoteTag && Number(remoteTag.updatedAt ?? 0) > Number(local.updatedAt ?? 0))
        ? remoteTag
        : local;
    out.push({
      ...meta,
      id,
      day: local
        ? sameDay
          ? Math.max(Number(local.day ?? 0), Number(remoteTag?.day ?? 0))
          : Number(local.day ?? 0)
        : sameDay
          ? Number(remoteTag?.day ?? 0)
          : 0,
      week: local
        ? sameWeek
          ? Math.max(Number(local.week ?? 0), Number(remoteTag?.week ?? 0))
          : Number(local.week ?? 0)
        : sameWeek
          ? Number(remoteTag?.week ?? 0)
          : 0,
      month: local
        ? sameMonth
          ? Math.max(Number(local.month ?? 0), Number(remoteTag?.month ?? 0))
          : Number(local.month ?? 0)
        : sameMonth
          ? Number(remoteTag?.month ?? 0)
          : 0,
    });
  }
  return out;
}

async function deleteBoard(uid, token, updateTime) {
  if (!uid || !token) return false;
  let url = `${boardUrl(uid)}`;
  if (updateTime) url += `&currentDocument.updateTime=${encoded(updateTime)}`;
  try {
    const response = await request(url, {
      method: "DELETE",
      headers: { Accept: "application/json", Authorization: `Bearer ${token}` },
    });
    if (response.ok || response.status === 404) return true;
    if (isConflict(response.status, response.text)) return "conflict";
    return false;
  } catch (_) {
    return false;
  }
}

export function scheduleBoardRefresh() {
  if (!S.auth.signedIn) return;
  refreshLeaderboard();
}

/* ── Boot ───────────────────────────────────────────────────────────── */

export function initCloud() {
  if (initialized) return;
  initialized = true;
  loadAuth();
  S.sync.status = S.auth.signedIn ? "idle" : "off";
  S.sync.message = S.auth.signedIn ? "" : "Sign in to sync";

  subscribe((reason) => {
    if (reason === "tick" || reason === "note" || reason === "cloud" || reason === "auth") return;
    if (reason === "sync") return;
    if (S.auth.signedIn && S.settings.autoSync && reason !== "settings") syncSoon();
    if (S.auth.signedIn) {
      window.clearTimeout(publishTimer);
      publishTimer = window.setTimeout(() => publishLeaderboard(), 8000);
    }
  });

  window.setInterval(() => {
    if (S.auth.signedIn && S.settings.autoSync) syncNow(true);
  }, 5000);

  window.setInterval(() => {
    if (S.auth.signedIn && S.settings.publicTagIds.length > 0) publishLeaderboard();
  }, 600000);

  window.addEventListener("panadoro:sync-now", () => {
    syncNow();
    publishLeaderboard();
  });

  if (S.auth.signedIn) {
    ensureFreshToken().then(() => {
      syncNow();
      if (S.settings.publicTagIds.length > 0) publishLeaderboard();
    });
  }
}
