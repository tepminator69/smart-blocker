'use strict';
/* =========================================================
   Smart Blocker — the main screen.

   Inside the app, Kotlin gives this page an object called "Android"
   (see MainActivity.kt → class Bridge). In a normal browser it doesn't
   exist, so makeMock() gives pretend data instead. That means you can
   open index.html on your PC and work on the design.
   ========================================================= */

const Native = window.Android || makeMock();

let S = null;               // settings: the rules you made
let ST = null;              // status: today's minutes and which permissions are on
let allApps = null;         // every app on the phone (loaded when the picker opens)
let tab = 'today';
let sheet = null;           // open panel: 'setup' | 'picker' | 'editor' | 'strict' | null
let pickerFromEditor = false;
let draft = null;           // the block schedule being edited
let search = '';
let animate = true;         // play the entry animations on the next render

const $ = (sel) => document.querySelector(sel);
const DAYS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];
const DAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const SHORT = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];
const STRICT_MSG = 'Strict mode is on until midnight. You can only make rules stricter.';

/* ---------- Icons (drawn with lines, so they take the text colour) ---------- */
function svg(inner, width) {
  return '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="' +
    (width || 1.9) + '" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + inner + '</svg>';
}
const ICON = {
  shield: svg('<path d="M12 3.5l7 3v5.2c0 4.3-3 7.6-7 8.8-4-1.2-7-4.5-7-8.8V6.5z"/><path d="M9 12l2.2 2.2L15.5 10"/>'),
  plus: svg('<path d="M12 5v14M5 12h14"/>', 2),
  minus: svg('<path d="M5 12h14"/>', 2),
  close: svg('<path d="M6 6l12 12M18 6L6 18"/>', 2),
  chev: svg('<path d="M9 6l6 6-6 6"/>', 2),
  lock: svg('<rect x="5" y="10.5" width="14" height="10" rx="2.5"/><path d="M8 10.5V8a4 4 0 0 1 8 0v2.5"/>'),
  check: svg('<path d="M5 12.5l4.5 4.5L19 7.5"/>', 2.6),
  today: svg('<circle cx="12" cy="12" r="8" opacity=".4"/><path d="M12 4a8 8 0 1 1-7.6 10.5"/>'),
  cal: svg('<rect x="3.5" y="5" width="17" height="15.5" rx="3.5"/><path d="M3.5 10h17M8.5 3v4M15.5 3v4"/>'),
  hourglass: svg('<path d="M6.5 3.5h11M6.5 20.5h11"/><path d="M8 3.5v2.2a4 4 0 0 0 1.6 3.2L12 10.8l2.4-1.9A4 4 0 0 0 16 5.7V3.5"/><path d="M8 20.5v-2.2a4 4 0 0 1 1.6-3.2L12 13.2l2.4 1.9a4 4 0 0 1 1.6 3.2v2.2"/>'),
  search: svg('<circle cx="11" cy="11" r="6.5"/><path d="M20 20l-4.2-4.2"/>')
};

/* ---------- Small helpers ---------- */
function esc(t) {
  return String(t == null ? '' : t).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
/** 90 -> "1h 30m" */
function fmt(min) {
  min = Math.max(0, Math.round(min));
  if (min < 60) return min + 'm';
  const h = Math.floor(min / 60);
  const m = min % 60;
  return m ? h + 'h ' + String(m).padStart(2, '0') + 'm' : h + 'h';
}
/** 570 -> "09:30" (minutes after midnight) */
function hhmm(m) {
  m = ((m % 1440) + 1440) % 1440;
  return String(Math.floor(m / 60)).padStart(2, '0') + ':' + String(m % 60).padStart(2, '0');
}
function nowMin() { const d = new Date(); return d.getHours() * 60 + d.getMinutes(); }
function todayIndex() { return (new Date().getDay() + 6) % 7; } // Monday = 0 … Sunday = 6
function anim(cls) { return animate ? cls : ''; }

function isActive(s) {
  if (!s.on) return false;
  const n = nowMin();
  const t = todayIndex();
  const y = (t + 6) % 7;
  if (s.start < s.end) return !!s.days[t] && n >= s.start && n < s.end;
  if (s.start > s.end) return (!!s.days[t] && n >= s.start) || (!!s.days[y] && n < s.end);
  return !!s.days[t];
}
function blockLength(s) { return ((s.end - s.start) + 1440) % 1440 || 1440; }
function minutesLeft(s) { return ((s.end - nowMin()) + 1440) % 1440 || 1440; }

/** The next block that will start (within 7 days). */
function nextBlock() {
  const n = nowMin();
  const t = todayIndex();
  let best = null;
  S.schedules.forEach((s) => {
    if (!s.on || isActive(s)) return;
    for (let k = 0; k < 7; k++) {
      const d = (t + k) % 7;
      if (!s.days[d] || (k === 0 && s.start <= n)) continue;
      const wait = k * 1440 + s.start - n;
      if (!best || wait < best.wait) best = { s: s, wait: wait, k: k, d: d };
      break;
    }
  });
  return best;
}

function usedMin(pkg) { return (ST.usage && ST.usage[pkg]) || 0; }
function appName(pkg) {
  const a = S.apps.find((x) => x.pkg === pkg);
  if (a) return a.name;
  const b = (allApps || []).find((x) => x.pkg === pkg) || (ST.top || []).find((x) => x.pkg === pkg);
  return b ? b.name : String(pkg).split('.').pop();
}
function appList(pkgs) {
  const names = pkgs.map(appName);
  if (!names.length) return 'No apps chosen';
  if (names.length <= 3) return names.join(', ');
  return names.slice(0, 2).join(', ') + ' and ' + (names.length - 2) + ' more';
}
function tone(pkg) {
  let h = 0;
  for (let i = 0; i < pkg.length; i++) h = (h * 31 + pkg.charCodeAt(i)) >>> 0;
  return [0.06, 0.1, 0.14, 0.18, 0.22][h % 5];
}
function avatar(pkg, size) {
  const name = appName(pkg);
  return '<span class="avatar ' + (size || '') + '" aria-hidden="true" style="background:rgba(255,255,255,' + tone(pkg) + ')">' +
    esc(name.charAt(0).toUpperCase()) + '</span>';
}
function daysText(d) {
  const n = d.filter(Boolean).length;
  if (n === 7) return 'Every day';
  if (n === 5 && d[0] && d[1] && d[2] && d[3] && d[4]) return 'Weekdays';
  if (n === 2 && d[5] && d[6]) return 'Weekends';
  if (n === 0) return 'No days chosen';
  return SHORT.filter((x, i) => d[i]).join(', ');
}
function strictOn() { return (S.strictUntil || 0) > Date.now(); }
function dailyReached() { return S.dailyLimit > 0 && (ST.total || 0) >= S.dailyLimit; }
function missingSteps() {
  const p = ST.perms || {};
  return ['accessibility', 'usage', 'notifications', 'battery'].filter((k) => !p[k]).length;
}
function switchBtn(action, on, label, extra) {
  return '<button class="switch-btn" role="switch" aria-checked="' + on + '" aria-label="' + esc(label) + '" data-a="' + action + '" ' +
    (extra || '') + '><span class="sw ' + (on ? 'on' : '') + '"><span class="knob"></span></span></button>';
}
function flipSwitch(el, on) {
  el.setAttribute('aria-checked', String(on));
  el.querySelector('.sw').classList.toggle('on', on);
}

/* ---------- Loading and saving ---------- */
function loadAll() {
  let s = null;
  let st = null;
  try { s = JSON.parse(Native.getSettings()); } catch (e) { s = null; }
  try { st = JSON.parse(Native.getStatus()); } catch (e) { st = null; }
  S = Object.assign({ dailyLimit: 240, apps: [], schedules: [], reminders: {}, strictUntil: 0 }, s || {});
  ST = Object.assign({ perms: {}, usage: {}, total: 0, top: [], snoozesLeft: 3 }, st || {});
}

/** Sends the rules to Kotlin. Returns false if they were refused (Strict mode). */
function persist() {
  const result = Native.saveSettings(JSON.stringify(S));
  if (result === 'strict') {
    toast(STRICT_MSG);
    S = JSON.parse(Native.getSettings());
    return false;
  }
  if (result !== 'ok') {
    toast('Could not save. Please try again.');
    return false;
  }
  return true;
}
function save() { persist(); render(); }

/* ---------- Drawing the page ---------- */
function render() {
  renderHead();
  renderView();
  renderNav();
  renderSheet();
  animate = false;
}

function renderHead() {
  const date = new Date().toLocaleDateString('en-GB', { weekday: 'long', day: 'numeric', month: 'long' });
  const on = S.schedules.filter((s) => s.on).length;
  const titles = { today: 'Today', schedules: 'Schedules', limits: 'Limits' };
  const eyebrow = tab === 'today' ? date
    : tab === 'schedules' ? on + ' of ' + S.schedules.length + ' blocks switched on'
      : 'Your daily time budget';
  const dot = missingSteps() ? '<span class="dot ping"></span>' : '';
  $('#head').innerHTML =
    '<div class="head-text"><span class="cap">' + esc(eyebrow) + '</span><h1 class="h1">' + titles[tab] + '</h1></div>' +
    '<button class="glass icon-btn setup-btn" data-a="openSetup" aria-label="Setup and permissions">' + ICON.shield + dot + '</button>';
}

function renderView() {
  $('#view').innerHTML = tab === 'today' ? viewToday() : tab === 'schedules' ? viewSchedules() : viewLimits();
}

function renderNav() {
  const nav = $('#nav');
  const tabs = [['today', 'Today', ICON.today], ['schedules', 'Schedules', ICON.cal], ['limits', 'Limits', ICON.hourglass]];
  if (!nav.dataset.built) {
    nav.innerHTML = '<span class="nav-pill"></span>' + tabs.map((t) =>
      '<button class="tab" data-a="tab" data-tab="' + t[0] + '">' + t[2] + '<span>' + t[1] + '</span></button>').join('');
    nav.dataset.built = '1';
  }
  const i = tabs.findIndex((t) => t[0] === tab);
  nav.querySelector('.nav-pill').style.transform = 'translateX(' + (i * 100) + '%)';
  nav.querySelectorAll('.tab').forEach((b) => {
    const on = b.dataset.tab === tab;
    b.classList.toggle('on', on);
    b.setAttribute('aria-pressed', String(on));
  });
}

/* ---------- Today ---------- */
function viewToday() {
  let html = '';
  const missing = missingSteps();
  if (missing) {
    html += '<button class="glass card ' + anim('rise') + '" data-a="openSetup">' +
      '<span class="row-between"><span class="col"><span class="t">Finish setup</span>' +
      '<span class="muted">' + (4 - missing) + ' of 4 steps done. The blocker needs them to work.</span></span>' + ICON.chev + '</span>' +
      '<span class="bar thin"><span class="bar-fill" style="width:' + ((4 - missing) * 25) + '%"></span></span></button>';
  }

  const used = ST.total || 0;
  const lim = S.dailyLimit || 0;
  const pct = lim > 0 ? Math.min(1, used / lim) : 0;
  const C = 691.15; // the ring's length: 2 × π × 110
  const chips = [];
  if (!ST.perms.usage) chips.push('Usage access needed');
  else if (lim > 0) chips.push(used < lim ? fmt(lim - used) + ' left today' : 'Whole-day limit reached');
  const nb = nextBlock();
  if (nb) {
    const when = nb.k === 0 ? '' : nb.k === 1 ? 'tomorrow ' : SHORT[nb.d] + ' ';
    chips.push('Next · ' + nb.s.name + ' ' + when + hhmm(nb.s.start));
  }

  html += '<section class="glass card ring-card ' + anim('rise') + '">' +
    '<span class="ring-wrap"><svg class="ring" viewBox="0 0 260 260" role="img" aria-label="Used ' + fmt(used) + ' today' + (lim ? ' of ' + fmt(lim) : '') + '">' +
    '<circle class="spin" cx="130" cy="130" r="126" fill="none" stroke="rgba(255,255,255,.22)" stroke-width="1" stroke-dasharray="2 8"/>' +
    '<circle cx="130" cy="130" r="110" fill="none" stroke="rgba(255,255,255,.08)" stroke-width="14"/>' +
    '<circle class="ring-fill ' + anim('ring-in') + '" cx="130" cy="130" r="110" fill="none" stroke="#f4f4f5" stroke-width="14" stroke-linecap="round" stroke-dasharray="691.15" transform="rotate(-90 130 130)" style="stroke-dashoffset:' + (C * (1 - pct)).toFixed(2) + 'px;opacity:' + (pct > 0 ? 1 : 0) + '"/>' +
    '</svg><span class="ring-center"><span class="cap">Used today</span><span class="big">' + fmt(used) + '</span>' +
    '<span class="muted">' + (lim ? 'of ' + fmt(lim) + ' limit' : 'no whole-day limit') + '</span></span></span>' +
    '<span class="chips">' + chips.map((c) => '<span class="chip">' + esc(c) + '</span>').join('') + '</span></section>';

  S.schedules.filter(isActive).forEach((s, i) => {
    const len = blockLength(s);
    const left = minutesLeft(s);
    html += '<button class="glass card ' + anim('rise') + '" style="animation-delay:' + (120 + i * 60) + 'ms" data-a="editSchedule" data-id="' + esc(s.id) + '">' +
      '<span class="row-between"><span class="row"><span class="live-dot ping"></span><span class="col">' +
      '<span class="t">' + esc(s.name) + ' block is on</span><span class="muted">Until ' + hhmm(s.end) + ' · ' + fmt(left) + ' left</span></span></span>' +
      '<span class="avatars">' + s.apps.slice(0, 3).map((p) => avatar(p, 'sm')).join('') + '</span></span>' +
      '<span class="bar thin"><span class="bar-fill ' + anim('bar-in') + '" style="width:' + Math.min(100, ((len - left) / len) * 100).toFixed(1) + '%"></span></span></button>';
  });

  if (!S.apps.length) {
    html += '<section class="glass card ' + anim('rise') + '" style="animation-delay:200ms">' +
      '<h2 class="h2">Choose the apps that take your time</h2>' +
      '<p class="muted">Smart Blocker only limits and blocks the apps you pick.</p>' +
      '<button class="btn-primary" data-a="openPicker">Choose apps</button></section>';
    const top = (ST.top || []).slice(0, 5);
    if (top.length) {
      html += '<section class="col gap ' + anim('rise') + '" style="animation-delay:280ms"><h2 class="h2 pad-x">Most used today</h2>' +
        top.map((a) => '<div class="glass card"><span class="row">' + avatar(a.pkg) +
          '<span class="col grow"><span class="t ellipsis">' + esc(a.name) + '</span><span class="muted">' + fmt(a.min) + ' today</span></span>' +
          '<button class="glass icon-btn" data-a="quickAdd" data-pkg="' + esc(a.pkg) + '" data-name="' + esc(a.name) + '" aria-label="Add ' + esc(a.name) + ' to your apps">' + ICON.plus + '</button></span></div>').join('') +
        '</section>';
    }
  } else {
    html += '<section class="col gap"><span class="row-between pad-x ' + anim('rise') + '" style="animation-delay:200ms">' +
      '<h2 class="h2">Your apps</h2><button class="link" data-a="goLimits">Edit limits</button></span>' +
      S.apps.map(appCard).join('') + '</section>';
  }
  return html;
}

function appCard(a, i) {
  const used = usedMin(a.pkg);
  const block = S.schedules.find((s) => isActive(s) && s.apps.indexOf(a.pkg) >= 0);
  const overLimit = a.limit > 0 && used >= a.limit;
  const paused = !!block || overLimit || dailyReached();
  let status;
  if (block) status = 'Blocked by ' + block.name + ' until ' + hhmm(block.end);
  else if (overLimit) status = 'Limit reached · back tomorrow';
  else if (dailyReached()) status = 'Whole-day limit reached';
  else status = a.limit > 0 ? fmt(used) + ' of ' + fmt(a.limit) : fmt(used) + ' · no limit';
  const w = a.limit > 0 ? Math.min(100, (used / a.limit) * 100) : 0;
  return '<div class="glass card ' + anim('rise') + '" style="animation-delay:' + (260 + i * 60) + 'ms">' +
    '<span class="row">' + avatar(a.pkg) +
    '<span class="col grow"><span class="t ellipsis">' + esc(a.name) + '</span><span class="muted">' + esc(status) + '</span></span>' +
    (paused ? '<span class="pill-solid">' + ICON.lock + 'Paused</span>' : '') + '</span>' +
    '<span class="bar"><span class="bar-fill ' + anim('bar-in') + '" style="width:' + w.toFixed(1) + '%"></span></span></div>';
}

/* ---------- Schedules ---------- */
function viewSchedules() {
  let html = '';
  if (!S.schedules.length) {
    html += '<section class="glass card ' + anim('rise') + '"><h2 class="h2">No blocks yet</h2>' +
      '<p class="muted">A block pauses chosen apps at set times. For example: Study, 09:00 – 13:00 on weekdays.</p></section>';
  }
  S.schedules.forEach((s, i) => {
    const dim = s.on ? '' : 'dim';
    html += '<article class="glass card ' + anim('rise') + '" style="animation-delay:' + (i * 80) + 'ms" data-sched="' + esc(s.id) + '">' +
      '<span class="row-between top">' +
      '<button class="sched-main" data-a="editSchedule" data-id="' + esc(s.id) + '" aria-label="Edit ' + esc(s.name) + '">' +
      '<span class="row wrap"><span class="t">' + esc(s.name) + '</span>' + (isActive(s) ? '<span class="pill-solid">Active now</span>' : '') + '</span>' +
      '<span class="time ' + dim + '">' + hhmm(s.start) + ' – ' + hhmm(s.end) + '</span></button>' +
      switchBtn('toggleSchedule', s.on, s.name + ' schedule', 'data-id="' + esc(s.id) + '"') + '</span>' +
      '<span class="sr">Repeats: ' + esc(daysText(s.days)) + '</span>' +
      '<span class="days ' + dim + '" aria-hidden="true">' + s.days.map((on, j) => '<span class="day ' + (on ? 'on' : '') + '">' + DAYS[j] + '</span>').join('') + '</span>' +
      '<span class="muted ' + dim + '">' + esc(appList(s.apps)) + ' · ' + (s.mode === 'strict' ? 'Strict' : 'Gentle') + '</span></article>';
  });
  html += '<button class="new-block ' + anim('rise') + '" data-a="newSchedule">' + ICON.plus + 'New block schedule</button>';
  return html;
}

/* ---------- Limits ---------- */
function viewLimits() {
  const strict = strictOn();
  const dailyText = S.dailyLimit ? fmt(S.dailyLimit) : 'Off';
  let html = '<section class="glass card ' + anim('rise') + '">' +
    '<label for="daily" class="cap">Whole-day limit</label>' +
    '<span class="row-between baseline"><span class="big" id="dailyLabel">' + dailyText + '</span>' +
    '<span class="muted">' + fmt(ST.total || 0) + ' used today</span></span>' +
    '<input id="daily" type="range" min="0" max="720" step="15" value="' + S.dailyLimit + '" data-in="daily" aria-valuetext="' + dailyText + '">' +
    '<p class="muted small">All apps together. When you reach it, your apps pause until midnight. Slide all the way left to switch it off.</p></section>';

  html += '<section class="glass card list ' + anim('rise') + '" style="animation-delay:80ms">' +
    '<span class="row-between"><h2 class="h2">Per-app limits</h2><button class="link" data-a="openPicker">' + ICON.plus + 'Add apps</button></span>' +
    (S.apps.length ? '' : '<p class="muted" style="padding-bottom:12px">No apps yet. Add the ones you want to limit.</p>') +
    S.apps.map((a) => '<div class="li">' + avatar(a.pkg, 'md') +
      '<span class="col grow"><span class="t ellipsis">' + esc(a.name) + '</span><span class="muted small">' + fmt(usedMin(a.pkg)) + ' today</span></span>' +
      '<button class="glass icon-btn" data-a="limitDown" data-pkg="' + esc(a.pkg) + '" aria-label="Less time for ' + esc(a.name) + '">' + ICON.minus + '</button>' +
      '<span class="limit-val">' + (a.limit ? fmt(a.limit) : 'None') + '</span>' +
      '<button class="glass icon-btn" data-a="limitUp" data-pkg="' + esc(a.pkg) + '" aria-label="More time for ' + esc(a.name) + '">' + ICON.plus + '</button></div>').join('') +
    '</section>';

  const R = S.reminders || {};
  const rem = [
    ['half', 'Halfway there', 'When you have used 50% of a limit'],
    ['eighty', 'Almost done', 'At 80% of a limit'],
    ['five', '5 minutes left', 'Right before an app pauses'],
    ['block', 'Block is coming', '10 minutes before a block starts'],
    ['summary', 'Evening summary', 'Your day in one notification at 21:00']
  ];
  html += '<section class="glass card list ' + anim('rise') + '" style="animation-delay:160ms"><h2 class="h2">Reminders</h2>' +
    rem.map((r) => '<div class="li"><span class="col grow"><span class="t">' + r[1] + '</span><span class="muted small">' + r[2] + '</span></span>' +
      switchBtn('toggleRem', !!R[r[0]], r[1], 'data-k="' + r[0] + '"') + '</div>').join('') + '</section>';

  html += '<section class="glass card ' + anim('rise') + '" style="animation-delay:240ms"><span class="row">' +
    '<span class="avatar md" aria-hidden="true">' + ICON.lock + '</span>' +
    '<span class="col grow"><span class="t">Strict mode</span><span class="muted small">' +
    (strict ? 'On until midnight. You can only make rules stricter.' : 'No snoozes, and rules can only get stricter, until midnight.') +
    '</span></span>' + (strict ? '<span class="pill-solid">On</span>' : switchBtn('askStrict', false, 'Strict mode')) + '</span></section>';
  return html;
}

/* ---------- Panels (bottom sheets) ---------- */
function renderSheet() {
  const layer = $('#sheet');
  if (!sheet) {
    layer.classList.remove('open');
    layer.setAttribute('aria-hidden', 'true');
    return;
  }
  const old = layer.querySelector('.panel');
  const scroll = old ? old.scrollTop : 0;
  let body = '';
  if (sheet === 'setup') body = sheetSetup();
  else if (sheet === 'picker') body = sheetPicker();
  else if (sheet === 'editor') body = sheetEditor();
  else if (sheet === 'strict') body = sheetStrict();
  layer.innerHTML = '<div class="scrim" data-a="closeSheet"></div><div class="panel" role="dialog" aria-modal="true">' + body + '</div>';
  layer.setAttribute('aria-hidden', 'false');
  const panel = layer.querySelector('.panel');
  if (layer.classList.contains('open')) {
    panel.scrollTop = scroll;
  } else {
    requestAnimationFrame(() => requestAnimationFrame(() => layer.classList.add('open')));
  }
}

function sheetSetup() {
  const p = ST.perms || {};
  const steps = [
    ['accessibility', 'Accessibility', 'Lets Smart Blocker see which app opens, so it can pause it. It never reads your screen.', 'Open Accessibility'],
    ['usage', 'Usage access', 'Counts your minutes in each app. The same data Digital Wellbeing uses.', 'Open Usage access'],
    ['notifications', 'Notifications', 'For reminders before limits run out and before blocks start.', 'Allow notifications'],
    ['battery', 'Battery', 'Stops Android from closing Smart Blocker to save power.', 'Allow']
  ];
  return '<div class="sheet-head"><h2 class="h2">Set up Smart Blocker</h2>' +
    '<button class="glass icon-btn" data-a="closeSheet" aria-label="Close">' + ICON.close + '</button></div>' +
    '<p class="muted small">Each button opens the right Android page. Switch Smart Blocker on there, then come back.</p>' +
    '<ol class="steps">' + steps.map((s, i) => {
      const done = !!p[s[0]];
      let extra = '';
      if (s[0] === 'accessibility' && !done) {
        extra = '<span class="hint">Find <b>Smart Blocker</b> (sometimes under “Downloaded apps” or “Installed services”) and switch it on. ' +
          '<b>Switch greyed out?</b> Open App info → ⋮ (top right) → <b>Allow restricted settings</b>, then try again. ' +
          '<button class="link" data-a="perm" data-k="appinfo">Open App info</button></span>';
      }
      return '<li class="step ' + (done ? 'done' : '') + '"><span class="step-num" aria-hidden="true">' + (done ? ICON.check : i + 1) + '</span>' +
        '<span class="col grow"><span class="t">' + s[1] + (done ? '<span class="sr"> (done)</span>' : '') + '</span>' +
        '<span class="muted small">' + s[2] + '</span>' +
        (done ? '<span class="ok">Done</span>' : '<button class="btn-small" data-a="perm" data-k="' + s[0] + '">' + s[3] + '</button>') +
        extra + '</span></li>';
    }).join('') + '</ol>' +
    '<details class="tecno"><summary>Tecno phones (HiOS)</summary>' +
    '<p class="muted small">Tecno closes background apps on its own. Also do this (menu names can differ a little by model):</p>' +
    '<ul class="muted small"><li><b>Phone Master</b> → Toolbox → <b>Auto-start management</b> → allow Smart Blocker.</li>' +
    '<li><b>Settings</b> → Battery Lab → Battery saving settings → switch off power saving management for Smart Blocker.</li></ul></details>';
}

function sheetPicker() {
  if (!allApps) {
    try { allApps = JSON.parse(Native.getApps()); } catch (e) { allApps = []; }
  }
  return '<div class="sheet-head"><h2 class="h2">Choose apps</h2><button class="btn-small solid" data-a="pickerDone">Done</button></div>' +
    '<label class="search glass">' + ICON.search + '<span class="sr">Search apps</span>' +
    '<input type="search" data-in="search" placeholder="Search apps" value="' + esc(search) + '"></label>' +
    '<div id="pickList" class="pick-list">' + pickRows() + '</div>';
}

function pickRows() {
  const q = search.trim().toLowerCase();
  const list = allApps.filter((a) => !q || a.name.toLowerCase().indexOf(q) >= 0);
  if (!list.length) return '<p class="muted small pad-x">No apps found.</p>';
  return list.map((a) => {
    const on = S.apps.some((x) => x.pkg === a.pkg);
    return '<button class="pick-row" data-a="pickToggle" data-pkg="' + esc(a.pkg) + '" data-name="' + esc(a.name) + '" aria-pressed="' + on + '">' +
      avatar(a.pkg, 'md') + '<span class="t grow ellipsis">' + esc(a.name) + '</span>' +
      '<span class="chk ' + (on ? 'on' : '') + '">' + ICON.check + '</span></button>';
  }).join('');
}

function newDraft() {
  return {
    id: 's' + Date.now().toString(36), name: '', start: 540, end: 780,
    days: [true, true, true, true, true, false, false], apps: [],
    mode: 'gentle', remind: true, on: true, isNew: true
  };
}

function timeBox(label, key, val) {
  return '<span class="col time-box"><span class="cap">' + label + '</span><span class="big">' + hhmm(val) + '</span>' +
    '<span class="row"><button class="glass icon-btn" data-a="draftTime" data-k="' + key + '" data-d="-15" aria-label="' + label + ' 15 minutes earlier">' + ICON.minus + '</button>' +
    '<button class="glass icon-btn" data-a="draftTime" data-k="' + key + '" data-d="15" aria-label="' + label + ' 15 minutes later">' + ICON.plus + '</button></span></span>';
}

function sheetEditor() {
  const d = draft;
  const len = blockLength(d);
  const pc = (v) => (v / 1440 * 100).toFixed(2) + '%';
  const seg = (left, width) => '<span class="seg-fill" style="left:' + pc(left) + ';width:' + pc(width) + '"></span>';
  const segs = d.end > d.start ? seg(d.start, d.end - d.start) : seg(d.start, 1440 - d.start) + (d.end > 0 ? seg(0, d.end) : '');
  const pkgs = S.apps.map((a) => a.pkg).concat(d.apps.filter((p) => !S.apps.some((a) => a.pkg === p)));
  const gentle = d.mode !== 'strict';

  return '<div class="sheet-head"><h2 class="h2">' + (d.isNew ? 'New block' : 'Edit block') + '</h2>' +
    '<button class="glass icon-btn" data-a="closeSheet" aria-label="Close">' + ICON.close + '</button></div>' +

    '<label class="cap" for="bname">Name</label>' +
    '<input id="bname" class="glass field" data-in="bname" maxlength="30" placeholder="Study, Sleep, Gym…" value="' + esc(d.name) + '">' +

    '<section class="glass card inner"><span class="grid2">' + timeBox('Starts', 'start', d.start) + timeBox('Ends', 'end', d.end) + '</span>' +
    '<span class="track" aria-hidden="true">' + segs + '</span>' +
    '<span class="ticks" aria-hidden="true"><span>00</span><span>06</span><span>12</span><span>18</span><span>24</span></span>' +
    '<span class="muted">' + fmt(len) + ' blocked' + (d.end <= d.start ? ' · runs overnight' : '') + '</span></section>' +

    '<section class="glass card inner"><span class="row-between"><span class="cap">Repeat on</span><span class="muted small" id="daysText">' + daysText(d.days) + '</span></span>' +
    '<span class="day-row">' + d.days.map((on, i) => '<button class="dayb ' + (on ? 'on' : '') + '" data-a="draftDay" data-i="' + i + '" aria-pressed="' + on + '" aria-label="' + DAY_NAMES[i] + '">' + DAYS[i] + '</button>').join('') + '</span></section>' +

    '<section class="glass card inner list"><span class="row-between"><span class="cap">Apps to block</span><span class="muted small" id="appsCount">' + d.apps.length + ' chosen</span></span>' +
    (pkgs.length ? '' : '<p class="muted small" style="padding:4px 0 8px">Choose the apps this block should pause.</p>') +
    pkgs.map((p) => {
      const on = d.apps.indexOf(p) >= 0;
      return '<button class="pick-row" data-a="draftApp" data-pkg="' + esc(p) + '" aria-pressed="' + on + '">' + avatar(p, 'md') +
        '<span class="t grow ellipsis">' + esc(appName(p)) + '</span><span class="chk ' + (on ? 'on' : '') + '">' + ICON.check + '</span></button>';
    }).join('') +
    '<button class="link" data-a="pickerFromEditor">' + ICON.plus + 'Choose more apps</button></section>' +

    '<section class="glass card inner"><span class="cap">When I open a blocked app</span><span class="seg-wrap">' +
    '<button class="seg ' + (gentle ? 'on' : '') + '" data-a="draftMode" data-v="gentle" aria-pressed="' + gentle + '"><span class="t">Gentle</span><span class="small">10-second pause, then 5 min. 3 times a day.</span></button>' +
    '<button class="seg ' + (gentle ? '' : 'on') + '" data-a="draftMode" data-v="strict" aria-pressed="' + !gentle + '"><span class="t">Strict</span><span class="small">No way to open it until the block ends.</span></button>' +
    '</span></section>' +

    '<section class="glass card inner"><span class="row"><span class="col grow"><span class="t">Remind me before it starts</span>' +
    '<span class="muted small">A notification 10 minutes earlier</span></span>' + switchBtn('draftRemind', d.remind, 'Remind me before it starts') + '</span></section>' +

    '<button class="btn-primary sheen" data-a="saveDraft">' + ICON.check + 'Save block</button>' +
    (d.isNew ? '' : '<button class="btn-text danger" data-a="deleteDraft">Delete this block</button>');
}

function sheetStrict() {
  return '<div class="sheet-head"><h2 class="h2">Turn on Strict mode?</h2>' +
    '<button class="glass icon-btn" data-a="closeSheet" aria-label="Close">' + ICON.close + '</button></div>' +
    '<p class="muted">Until midnight:</p>' +
    '<ul class="muted small bullets"><li>No snoozes on the block screen.</li>' +
    '<li>You can’t raise limits, remove apps or switch blocks off.</li>' +
    '<li>You can still make rules stricter.</li></ul>' +
    '<p class="muted small">You can’t turn it off before midnight. Uninstalling the app still works: it helps your self-control, it isn’t a lock.</p>' +
    '<button class="btn-primary" data-a="strictYes">' + ICON.lock + 'Turn on until midnight</button>' +
    '<button class="btn-text" data-a="closeSheet">Not now</button>';
}

/* ---------- Toast ---------- */
let toastTimer = null;
function toast(msg) {
  const t = $('#toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
}

/* ---------- What each button does (data-a="name") ---------- */
function stepUp(v) { return v < 60 ? v + 5 : v + 15; }
function stepDown(v) { return v <= 0 ? 0 : v <= 60 ? v - 5 : v - 15; }

const actions = {
  tab(d) {
    if (tab === d.tab) return;
    tab = d.tab;
    animate = true;
    window.scrollTo(0, 0);
    render();
  },
  goLimits() { actions.tab({ tab: 'limits' }); },

  openSetup() { sheet = 'setup'; renderSheet(); },
  perm(d) { Native.openSetting(d.k); },

  openPicker() { pickerFromEditor = false; search = ''; sheet = 'picker'; renderSheet(); },
  pickerFromEditor() { pickerFromEditor = true; search = ''; sheet = 'picker'; renderSheet(); },
  pickToggle(d, el) {
    const i = S.apps.findIndex((x) => x.pkg === d.pkg);
    if (i >= 0) {
      S.apps.splice(i, 1);
      if (draft) draft.apps = draft.apps.filter((p) => p !== d.pkg);
    } else {
      S.apps.push({ pkg: d.pkg, name: d.name, limit: 0 });
      if (pickerFromEditor && draft && draft.apps.indexOf(d.pkg) < 0) draft.apps.push(d.pkg);
    }
    persist();
    $('#pickList').innerHTML = pickRows();
  },
  pickerDone() {
    search = '';
    if (pickerFromEditor) {
      pickerFromEditor = false;
      sheet = 'editor';
      renderSheet();
    } else {
      sheet = null;
      render();
    }
  },
  quickAdd(d) {
    if (!S.apps.some((x) => x.pkg === d.pkg)) S.apps.push({ pkg: d.pkg, name: d.name, limit: 0 });
    animate = true;
    save();
    toast(d.name + ' added. Set its limit in Limits.');
  },

  limitUp(d) {
    const a = S.apps.find((x) => x.pkg === d.pkg);
    if (!a) return;
    a.limit = Math.min(600, stepUp(a.limit || 0));
    save();
  },
  limitDown(d) {
    const a = S.apps.find((x) => x.pkg === d.pkg);
    if (!a) return;
    a.limit = Math.max(0, stepDown(a.limit || 0));
    save();
  },
  toggleRem(d, el) {
    S.reminders = S.reminders || {};
    S.reminders[d.k] = !S.reminders[d.k];
    flipSwitch(el, S.reminders[d.k]);
    if (!persist()) render();
  },
  askStrict() { sheet = 'strict'; renderSheet(); },
  strictYes() {
    const midnight = new Date();
    midnight.setHours(24, 0, 0, 0);
    S.strictUntil = midnight.getTime();
    sheet = null;
    save();
    toast('Strict mode is on until midnight.');
  },

  toggleSchedule(d, el) {
    const s = S.schedules.find((x) => x.id === d.id);
    if (!s) return;
    s.on = !s.on;
    flipSwitch(el, s.on);
    if (!persist()) { render(); return; }
    setTimeout(render, 380);
  },
  newSchedule() { draft = newDraft(); sheet = 'editor'; renderSheet(); },
  editSchedule(d) {
    const s = S.schedules.find((x) => x.id === d.id);
    if (!s) return;
    draft = JSON.parse(JSON.stringify(s));
    draft.isNew = false;
    sheet = 'editor';
    renderSheet();
  },
  draftTime(d) {
    draft[d.k] = ((draft[d.k] + Number(d.d)) % 1440 + 1440) % 1440;
    renderSheet();
  },
  draftDay(d, el) {
    const i = Number(d.i);
    draft.days[i] = !draft.days[i];
    el.classList.toggle('on', draft.days[i]);
    el.setAttribute('aria-pressed', String(draft.days[i]));
    $('#daysText').textContent = daysText(draft.days);
  },
  draftApp(d, el) {
    const i = draft.apps.indexOf(d.pkg);
    if (i >= 0) draft.apps.splice(i, 1); else draft.apps.push(d.pkg);
    const on = i < 0;
    el.querySelector('.chk').classList.toggle('on', on);
    el.setAttribute('aria-pressed', String(on));
    $('#appsCount').textContent = draft.apps.length + ' chosen';
  },
  draftMode(d) { draft.mode = d.v; renderSheet(); },
  draftRemind(d, el) { draft.remind = !draft.remind; flipSwitch(el, draft.remind); },
  saveDraft() {
    const d = draft;
    d.name = (d.name || '').trim() || 'Block';
    if (!d.days.some(Boolean)) { toast('Pick at least one day.'); return; }
    if (!d.apps.length) { toast('Choose at least one app to block.'); return; }
    const clean = {
      id: d.id, name: d.name, start: d.start, end: d.end, days: d.days, apps: d.apps,
      mode: d.mode, remind: d.remind, on: d.isNew ? true : d.on
    };
    const i = S.schedules.findIndex((x) => x.id === d.id);
    if (i >= 0) S.schedules[i] = clean; else S.schedules.push(clean);
    if (persist()) {
      sheet = null;
      draft = null;
      tab = 'schedules';
      animate = true;
      toast('Block saved.');
    }
    render();
  },
  deleteDraft() {
    S.schedules = S.schedules.filter((x) => x.id !== draft.id);
    if (persist()) {
      sheet = null;
      draft = null;
      toast('Block deleted.');
    }
    render();
  },
  closeSheet() {
    if (sheet === 'picker' && pickerFromEditor) {
      pickerFromEditor = false;
      sheet = 'editor';
      renderSheet();
      return;
    }
    sheet = null;
    draft = null;
    search = '';
    render();
  }
};

/* ---------- Listening for taps and typing ---------- */
document.addEventListener('click', (e) => {
  const el = e.target.closest('[data-a]');
  if (!el) return;
  const fn = actions[el.dataset.a];
  if (fn) {
    e.preventDefault();
    fn(el.dataset, el);
  }
});

document.addEventListener('input', (e) => {
  const k = e.target.dataset ? e.target.dataset.in : null;
  if (k === 'daily') {
    const v = Number(e.target.value);
    $('#dailyLabel').textContent = v ? fmt(v) : 'Off';
  } else if (k === 'search') {
    search = e.target.value;
    $('#pickList').innerHTML = pickRows();
  } else if (k === 'bname') {
    if (draft) draft.name = e.target.value;
  }
});

document.addEventListener('change', (e) => {
  if (e.target.dataset && e.target.dataset.in === 'daily') {
    S.dailyLimit = Number(e.target.value);
    save();
  }
});

/* Called by Kotlin: when you come back to the app, and when Back is pressed. */
window.refresh = function () {
  try { ST = Object.assign(ST || {}, JSON.parse(Native.getStatus())); } catch (e) { /* keep old */ }
  if (!draft) {
    try { S = Object.assign(S || {}, JSON.parse(Native.getSettings())); } catch (e) { /* keep old */ }
  }
  renderHead();
  renderView();
  renderNav();
  if (sheet === 'setup') renderSheet();
};
window.goBack = function () {
  if (sheet) { actions.closeSheet(); return true; }
  if (tab !== 'today') { actions.tab({ tab: 'today' }); return true; }
  return false;
};
setInterval(() => { if (!document.hidden) window.refresh(); }, 30000);

/* ---------- Start ---------- */
loadAll();
render();

/* ---------- Pretend data for a normal browser ---------- */
function makeMock() {
  const yt = 'com.google.android.youtube';
  const ig = 'com.instagram.android';
  const tt = 'com.zhiliaoapp.musically';
  const ch = 'com.android.chrome';
  let settings = {
    dailyLimit: 240,
    apps: [{ pkg: yt, name: 'YouTube', limit: 60 }, { pkg: ig, name: 'Instagram', limit: 45 }, { pkg: tt, name: 'TikTok', limit: 30 }],
    schedules: [
      { id: 'study', name: 'Study', start: 540, end: 780, days: [true, true, true, true, true, false, false], apps: [ig, tt, yt], mode: 'gentle', remind: true, on: true },
      { id: 'wind', name: 'Wind-down', start: 1350, end: 420, days: [true, true, true, true, true, true, true], apps: [ig, tt, yt, ch], mode: 'strict', remind: true, on: true }
    ],
    reminders: { half: false, eighty: true, five: true, block: true, summary: true },
    strictUntil: 0
  };
  const usage = {};
  usage[yt] = 45; usage[ig] = 38; usage[tt] = 30; usage[ch] = 21;
  return {
    getSettings: () => JSON.stringify(settings),
    saveSettings: (json) => { settings = JSON.parse(json); return 'ok'; },
    getStatus: () => JSON.stringify({
      perms: { accessibility: false, usage: true, notifications: true, battery: false },
      usage: usage,
      total: 134,
      top: [{ pkg: yt, name: 'YouTube', min: 45 }, { pkg: ig, name: 'Instagram', min: 38 }, { pkg: tt, name: 'TikTok', min: 30 }, { pkg: ch, name: 'Chrome', min: 21 }],
      snoozesLeft: 3,
      android: 34
    }),
    getApps: () => JSON.stringify([
      { pkg: ch, name: 'Chrome' }, { pkg: ig, name: 'Instagram' }, { pkg: 'com.reddit.frontpage', name: 'Reddit' },
      { pkg: 'com.snapchat.android', name: 'Snapchat' }, { pkg: 'org.telegram.messenger', name: 'Telegram' },
      { pkg: tt, name: 'TikTok' }, { pkg: yt, name: 'YouTube' }
    ]),
    openSetting: (which) => { console.log('In the app this opens Android settings:', which); }
  };
}
