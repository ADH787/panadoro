// Small, text-node translation layer. The markup stays readable English and a
// post-render pass applies the selected language, so no component has to carry
// two copies of every sentence.

import { AR } from "./i18n-ar.js?v=1.9.1";
import { S, saveSettings, emit } from "./state.js?v=1.9.1";

export const LANGUAGES = [
  { id: "en", label: "English" },
  { id: "ar", label: "العربية" },
];

export function currentLanguage() {
  const chosen = String(S.settings.language ?? "");
  if (chosen === "ar" || chosen === "en") return chosen;
  return String(navigator.language ?? "").toLowerCase().startsWith("ar") ? "ar" : "en";
}

export function isRTL() {
  return currentLanguage() === "ar";
}

export function setLanguage(language) {
  S.settings.language = language === "ar" ? "ar" : "en";
  saveSettings();
  applyLanguage();
  emit("tab");
}

export function applyLanguage() {
  document.documentElement.lang = currentLanguage();
  document.documentElement.dir = isRTL() ? "rtl" : "ltr";
}

const PATTERNS = [
  [
    /^(\d+) of (\d+) habits · (.+) focused today$/,
    (m) => `${m[1]} من ${m[2]} عادات · ${m[3]} تركيز اليوم`,
  ],
  [
    /^(\d+) open · (\d+) due today · (\d+) done$/,
    (m) => `${m[1]} مفتوحة · ${m[2]} مستحقة اليوم · ${m[3]} منتهية`,
  ],
  [/^(\d+) open · (\d+) due today$/, (m) => `${m[1]} مفتوحة · ${m[2]} مستحقة اليوم`],
  [/^(\d+) accounts?$/, (m) => `${m[1]} حساب`],
  [
    /^(\d+) public tags? · (today|this week|this month)$/,
    (m) =>
      `${m[1]} وسم عام · ${
        m[2] === "today" ? "اليوم" : m[2] === "this month" ? "هذا الشهر" : "هذا الأسبوع"
      }`,
  ],
  [
    /^Updated (.+) · the board refreshes every minute$/,
    (m) => `آخر تحديث ${m[1]} · تتحدث اللوحة كل دقيقة`,
  ],
  [
    /^(\d+) of (\d+) tags public · habits, tasks and notes are never uploaded to the board\.$/,
    (m) => `${m[1]} من ${m[2]} وسوم عامة · لا تُرفع العادات أو المهام أو الملاحظات إلى اللوحة.`,
  ],
  [/^(.+) focused across (\d+) days$/, (m) => `${m[1]} تركيز خلال ${m[2]} يوم`],
  [
    /^(.+) · (\d+)m focus · (\d+) cycles$/,
    (m) => `${m[1]} · ${m[2]} د تركيز · ${m[3]} دورات`,
  ],
  [/^(\d+) day streak$/, (m) => `${m[1]} يوم متتالي`],
  [/^(\d+) sessions$/, (m) => `${m[1]} جلسات`],
  [/^(.+) \/ (.+) today$/, (m) => `${m[1]} / ${m[2]} اليوم`],
  [/^(\d+)m$/, (m) => `${m[1]} د`],
  [/^(\d+)h$/, (m) => `${m[1]} س`],
  [/^(\d+)h (\d+)m$/, (m) => `${m[1]} س ${m[2]} د`],
  [/^(\d+)% complete$/, (m) => `${m[1]}% مكتمل`],
  [/^(\d+) of (\d+) (times|glasses|blocks|pages|minutes)$/, (m) => {
    const units = { times: "مرات", glasses: "أكواب", blocks: "فترات", pages: "صفحات", minutes: "دقائق" };
    return `${m[1]} من ${m[2]} ${units[m[3]] || m[3]}`;
  }],
  [/^Good (morning|afternoon|evening), all done\.$/, (m) =>
    m[1] === "morning" ? "صباح الخير، أنجزت كل شيء." : "مساء الخير، أنجزت كل شيء."],
  [/^(\d+)m break time$/, (m) => `${m[1]} د استراحة`],
  [/^(\d+) of (\d+) habits$/, (m) => `${m[1]} من ${m[2]} عادات`],
];

const EXTRA_AR = {
  Language: "اللغة",
  English: "الإنجليزية",
  العربية: "العربية",
  "Focus rhythm": "إيقاع التركيز",
  "By tag": "حسب الوسم",
  Breaks: "الاستراحات",
  "Night 00–06": "الليل 00–06",
  "Morning 06–12": "الصباح 06–12",
  "Afternoon 12–18": "بعد الظهر 12–18",
  "Evening 18–24": "المساء 18–24",
  "Quieter": "أهدأ",
  "More focus": "تركيز أكثر",
  "No daily goal": "لا هدف يومي",
  "Publish a tag below and the first focus session puts you on the board.":
    "اجعل وسمًا عامًا أدناه، وأول جلسة تركيز ستضعك على اللوحة.",
  "Tune Panadoro to your rhythm": "اضبط Panadoro على إيقاعك",
  "Your public tags and display name live in the Ranks tab.":
    "وسومك العامة واسمك المعروض في تبويب الترتيب.",
  "Dynamic Material colour in the desktop shell; a calm forest palette here.":
    "ألوان ماتيريال ديناميكية في سطح المكتب؛ وهنا لوحة غابات هادئة.",
  "The web app reads the same private Firestore document as the Android and Quickshell apps, merging record by record. The public board is a separate, opt-in tag mirror.":
    "يقرأ تطبيق الويب نفس مستند Firestore الخاص لتطبيقي أندرويد وكويك شل، ويدمج السجلات سجلًا بسجل. لوحة الترتيب العامة مرآة منفصلة واختيارية للوسوم.",
  "Firebase rejected the sync request": "رفض Firebase طلب المزامنة",
  "Offline — your session is still signed in": "أنت غير متصل — جلستك ما زالت مسجلة",
  "Last sync": "آخر مزامنة",
  "Signed in": "تم تسجيل الدخول",
  "Ready to sync": "جاهز للمزامنة",
  "Waiting for the first sync.": "بانتظار أول مزامنة.",
  "No public tags yet.": "لا توجد وسوم عامة بعد.",
  "Reload fresh": "إعادة تحميل حديثة",
  "Good morning.": "صباح الخير.",
  "Good afternoon.": "مساء الخير.",
  "Good evening.": "مساء الخير.",
  "Still up?.": "ما زلت مستيقظًا؟",
  "Nothing scheduled. Add one small thing.": "لا شيء مجدول. أضف شيئًا صغيرًا.",
  "habits · tasks · focus": "عادات · مهام · تركيز",
};

function translateExact(text) {
  if (EXTRA_AR[text]) return EXTRA_AR[text];
  if (AR[text]) return AR[text];
  for (const [pattern, render] of PATTERNS) {
    const match = text.match(pattern);
    if (match) return render(match);
  }
  return text;
}

export function translateText(text) {
  if (currentLanguage() !== "ar") return String(text ?? "");
  const value = String(text ?? "");
  const lead = value.match(/^\s*/)?.[0] ?? "";
  const trail = value.match(/\s*$/)?.[0] ?? "";
  const core = value.trim();
  return core ? `${lead}${translateExact(core)}${trail}` : value;
}

export function translateDOM(root = document) {
  if (!root) return;
  if (currentLanguage() !== "ar") {
    applyLanguage();
    return;
  }
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes = [];
  while (walker.nextNode()) {
    const node = walker.currentNode;
    if (node.nodeValue && node.nodeValue.trim()) nodes.push(node);
  }
  for (const node of nodes) {
    const next = translateText(node.nodeValue);
    if (next !== node.nodeValue) node.nodeValue = next;
  }
  for (const el of root.querySelectorAll("[title], [aria-label], [placeholder]")) {
    for (const attr of ["title", "aria-label", "placeholder"]) {
      if (!el.hasAttribute(attr)) continue;
      const value = el.getAttribute(attr);
      if (!value) continue;
      const next = translateExact(value.trim());
      if (next !== value.trim()) el.setAttribute(attr, next);
    }
  }
  applyLanguage();
}
