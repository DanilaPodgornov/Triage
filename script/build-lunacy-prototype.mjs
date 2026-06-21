#!/usr/bin/env node
/**
 * Builds Triage UI prototype in Lunacy from ui-snapshot.json + reference captures.
 * Prerequisites: Lunacy open, MCP Server Enabled (localhost:31415).
 *
 * 1. In Lunacy: create a new page and make it active (API cannot create pages).
 * 2. node script/capture-ui-reference.mjs  (first, if snapshot is missing)
 * 3. node script/build-lunacy-prototype.mjs
 *
 * Optional:
 *   --clean    removes old 0x_ frames on the current page before building light theme
 *   --add-dark adds dark-theme copies below existing light frames on the current page
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const API = 'http://localhost:31415';
const SNAPSHOT_PATH = path.join(ROOT, 'docs', 'lunacy', 'reference', 'ui-snapshot.json');
const BRAND_ICON_PATH = path.join(ROOT, 'docs', 'lunacy', 'assets', 'triage-mark-28.png');

const W = 1440;
const FRAME_GAP = 100;
const VERTICAL_GAP = 100;
const PAD = 48;
const CONTENT_W = W - PAD * 2;
const CLEAN = process.argv.includes('--clean');
const ADD_DARK = process.argv.includes('--add-dark');

const SHARED = {
  fontSize: 14,
  fontFamily: 'Arial',
  rowHeight: 36,
  headerRowHeight: 37,
  radiusTile: 8,
  radiusBtn: 4,
  radiusModal: 12,
};

const THEMES = {
  light: {
    ...SHARED,
    text: '#222222',
    textStrong: '#111827',
    bg: '#f5f6f8',
    surface: '#ffffff',
    surfaceMuted: '#f9fafb',
    border: '#e5e7eb',
    borderInput: '#cbd5e1',
    muted: '#6b7280',
    headerBg: '#1f2937',
    headerText: '#ffffff',
    tabText: '#374151',
    tabActive: '#2563eb',
    primary: '#2563eb',
    primaryText: '#ffffff',
    primaryDisabled: '#93c5fd',
    secondaryBg: '#e5e7eb',
    secondaryText: '#111827',
    secondaryBorder: '#d1d5db',
    link: '#2563eb',
  },
  dark: {
    ...SHARED,
    text: '#e5e7eb',
    textStrong: '#f9fafb',
    bg: '#111827',
    surface: '#1f2937',
    surfaceMuted: '#111827',
    border: '#374151',
    borderInput: '#4b5563',
    muted: '#9ca3af',
    headerBg: '#030712',
    headerText: '#f9fafb',
    tabText: '#d1d5db',
    tabActive: '#60a5fa',
    primary: '#3b82f6',
    primaryText: '#ffffff',
    primaryDisabled: '#1e3a5f',
    secondaryBg: '#374151',
    secondaryText: '#f3f4f6',
    secondaryBorder: '#4b5563',
    link: '#60a5fa',
  },
};

const LIGHT_FRAME_NAMES = [
  '01_Chrome',
  '02_TestCases_LLM',
  '04_Modal_Prioritize',
  '05_SuggestedCycles',
  '06_CycleDetail_SR75',
  '07_Settings',
];

const SCREEN_HEIGHTS = {
  '01_Chrome': 180,
  '02_TestCases_LLM': 1400,
  '04_Modal_Prioritize': 1400,
  '05_SuggestedCycles': 750,
  '06_CycleDetail_SR75': 650,
  '07_Settings': 500,
};

function loadSnapshot() {
  if (!fs.existsSync(SNAPSHOT_PATH)) {
    throw new Error(`Missing ${SNAPSHOT_PATH}. Run: node script/capture-ui-reference.mjs`);
  }
  return JSON.parse(fs.readFileSync(SNAPSHOT_PATH, 'utf8'));
}

function lbl(snapshot, key, fallback = key) {
  return snapshot.labels?.[key] ?? fallback;
}

function hex(css) {
  if (!css) return 'FF000000';
  if (css.startsWith('#')) {
    const h = css.replace('#', '').toUpperCase();
    return h.length === 8 ? h : `FF${h}`;
  }
  const m = css.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/);
  if (m) {
    const r = Number(m[1]).toString(16).padStart(2, '0');
    const g = Number(m[2]).toString(16).padStart(2, '0');
    const b = Number(m[3]).toString(16).padStart(2, '0');
    return `FF${r}${g}${b}`.toUpperCase();
  }
  return 'FF222222';
}

function transform(x, y) {
  return [1, 0, x, 0, 1, y];
}

function fill(color) {
  return [{ color: hex(color), fillType: 0 }];
}

function border(color, thickness = 1) {
  return [{ color: hex(color), thickness, position: 0 }];
}

function textStyle(tk, color, size, bold) {
  return {
    font: tk.fontFamily,
    size,
    fills: fill(color),
    kerning: 0,
    align: 0,
    valign: 0,
    underline: false,
    strikethrough: false,
    casing: 0,
    ...(bold ? { weight: 600 } : {}),
  };
}

function rect(tk, name, x, y, w, h, opts = {}) {
  const layer = {
    _t: 'RECT',
    name,
    transform: transform(x, y),
    size: [w, h],
    fills: fill(opts.fill ?? tk.surface),
    locked: false,
    hidden: false,
    opacity: opts.opacity ?? 1,
  };
  if (opts.radius) layer.cornerRadius = [opts.radius, opts.radius, opts.radius, opts.radius];
  if (opts.border) layer.borders = border(opts.border);
  return layer;
}

function textLayer(tk, name, x, y, w, h, content, opts = {}) {
  return {
    _t: 'TEXT',
    name,
    transform: transform(x, y),
    size: [w, h],
    text: content,
    textStyle: textStyle(tk, opts.color ?? tk.text, opts.size ?? tk.fontSize, opts.bold),
    behavior: 1,
    clipContent: true,
    truncate: opts.truncate ?? false,
    locked: false,
    hidden: false,
    opacity: 1,
  };
}

function btn(tk, name, x, y, label, variant = 'secondary', disabled = false) {
  const w = Math.max(90, label.length * 7 + 32);
  const h = 34;
  const isPrimary = variant === 'primary';
  const bg = disabled
    ? isPrimary ? tk.primaryDisabled : tk.secondaryBg
    : isPrimary ? tk.primary : tk.secondaryBg;
  const color = isPrimary ? tk.primaryText : tk.secondaryText;
  const borderColor = isPrimary ? tk.primary : tk.secondaryBorder;
  return [
    rect(tk, `${name}-bg`, x, y, w, h, { fill: bg, radius: tk.radiusBtn, border: disabled && !isPrimary ? tk.secondaryBorder : borderColor }),
    textLayer(tk, `${name}-label`, x + 12, y + 8, w - 24, 18, label, { color, size: 14, bold: isPrimary }),
  ];
}

function selectBox(tk, x, y, w, label, value) {
  const layers = [];
  if (label) layers.push(textLayer(tk, `lbl-${label}`, x, y, w, 16, label, { bold: true, color: tk.textStrong }));
  const sy = label ? y + 22 : y;
  layers.push(
    rect(tk, `sel-${label || value}`, x, sy, w, 32, { fill: tk.surface, radius: tk.radiusBtn, border: tk.borderInput }),
    textLayer(tk, `sel-val-${label || value}`, x + 8, sy + 7, w - 16, 18, value, { color: tk.text })
  );
  return layers;
}

function checkbox(tk, x, y, checked = true) {
  const layers = [rect(tk, 'checkbox', x, y + 10, 16, 16, { fill: tk.surface, radius: 2, border: tk.borderInput })];
  if (checked) {
    layers.push(textLayer(tk, 'check-mark', x + 3, y + 9, 12, 14, '✓', { color: tk.text, size: 12 }));
  }
  return layers;
}

function radioOption(tk, x, y, checked) {
  const layers = [rect(tk, 'radio-outer', x, y, 14, 14, { fill: tk.surface, radius: 7, border: tk.borderInput })];
  if (checked) {
    layers.push(rect(tk, 'radio-inner', x + 4, y + 4, 6, 6, { fill: tk.primary, radius: 3 }));
  }
  return layers;
}

function priorityBadge(tk, x, y, text, bgColor, textColor) {
  const bg = bgColor || '#ffa900';
  let fg = textColor || '#111827';
  if (textColor && bgColor && textColor.toLowerCase() === bgColor.toLowerCase()) {
    fg = bg.toLowerCase() === '#ff0000' ? '#ffffff' : '#111827';
  }
  const w = Math.max(52, text.length * 7 + 20);
  return [
    rect(tk, `badge-${text}`, x, y + 8, w, 22, { fill: bg, radius: 4 }),
    textLayer(tk, `badge-txt-${text}`, x + 10, y + 11, w - 20, 16, text, { color: fg, size: 12, bold: true }),
  ];
}

function spinner(tk, cx, cy) {
  return [
    rect(tk, 'spinner-track', cx - 18, cy - 18, 36, 36, { fill: tk.surface, radius: 18, border: tk.border }),
    rect(tk, 'spinner-arc', cx - 14, cy - 18, 28, 14, { fill: tk.primary, radius: 4 }),
  ];
}

function formatNum(v) {
  if (v == null) return '-';
  return Number(v).toFixed(4);
}

function truncate(s, max = 42) {
  if (!s || s.length <= max) return s;
  return `${s.slice(0, max - 1)}…`;
}

async function api(method, endpoint, body) {
  const res = await fetch(`${API}${endpoint}`, {
    method,
    headers: body instanceof Buffer || body instanceof Uint8Array
      ? undefined
      : body
        ? { 'Content-Type': 'application/json' }
        : undefined,
    body: body instanceof Buffer || body instanceof Uint8Array ? body : body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(120000),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`${method} ${endpoint} failed (${res.status}): ${text}`);
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

async function findFrameAt(offsetX, offsetY, name) {
  const data = await api('POST', '/getlayersinarea', {
    x: offsetX,
    y: offsetY,
    width: W,
    height: 4000,
    maxDepth: 2,
  });
  const layers = Array.isArray(data) ? data : data?._t ? [data] : [];
  const match = layers.find((l) => l._t === 'FRAME' && l.name === name);
  if (!match?.id) throw new Error(`Frame "${name}" not found at x=${offsetX}, y=${offsetY}`);
  return match.id;
}

async function createLayers(parentId, layers) {
  return api('POST', '/createlayers', { parentId: parentId ?? null, layersJson: JSON.stringify(layers) });
}

async function createFrame(name, offsetX, offsetY, height, tk) {
  await createLayers(null, [{
    _t: 'FRAME',
    name,
    transform: transform(offsetX, offsetY),
    size: [W, height],
    fills: fill(tk.bg),
    clipContent: true,
    locked: false,
    hidden: false,
    opacity: 1,
  }]);
  return findFrameAt(offsetX, offsetY, name);
}

async function addToFrame(frameId, layers) {
  if (layers.length === 0) return;
  await createLayers(frameId, [...layers].reverse());
}

async function warnIfPrototypeExists() {
  try {
    const data = await api('POST', '/getlayersinarea', { x: 0, y: 0, width: W, height: 3000, maxDepth: 2 });
    const layers = Array.isArray(data) ? data : data?._t ? [data] : [];
    if (layers.some((l) => l._t === 'FRAME' && l.name === '01_Chrome')) {
      console.warn(
        'На текущей странице уже есть фрейм 01_Chrome. '
        + 'Переключитесь на пустую страницу в Lunacy, чтобы не дублировать прототип.'
      );
    }
  } catch {
    /* skip */
  }
}

async function insertBrandIcon(parentId, x, y) {
  if (!fs.existsSync(BRAND_ICON_PATH)) {
    console.warn('Brand icon PNG missing, using placeholder');
    return;
  }
  const buffer = fs.readFileSync(BRAND_ICON_PATH);
  const res = await fetch(`${API}/insertimage`, {
    method: 'POST',
    headers: {
      'Content-Type': 'image/png',
      'X-Image-Position-X': String(x),
      'X-Image-Position-Y': String(y),
      'X-Image-File-Name': 'triage-mark-28.png',
      'X-Image-Parent-Id': parentId,
    },
    body: buffer,
    signal: AbortSignal.timeout(30000),
  });
  if (!res.ok) console.warn('insertimage failed:', await res.text());
}

async function cleanOldFrames() {
  const ids = new Set();
  for (let x = 0; x < 20000; x += 400) {
    try {
      const data = await api('POST', '/getlayersinarea', { x, y: 0, width: 1600, height: 8000, maxDepth: 1 });
      const layers = Array.isArray(data) ? data : data?._t ? [data] : [];
      for (const l of layers) {
        if (l._t === 'FRAME' && (/^0\d_/.test(l.name) || l.name === 'API_TEST_FRAME')) {
          ids.add(l.id);
        }
      }
    } catch {
      /* skip */
    }
  }
  if (ids.size) {
    await api('POST', '/deletelayers', [...ids]);
    console.log(`Cleaned ${ids.size} old frame(s)`);
  }
}

async function findLightFrames() {
  const data = await api('POST', '/getlayersinarea', {
    x: -5000,
    y: -5000,
    width: 25000,
    height: 25000,
    maxDepth: 1,
  });
  const layers = Array.isArray(data) ? data : [];
  const frames = [];
  for (const name of LIGHT_FRAME_NAMES) {
    const frame = layers.find((l) => l._t === 'FRAME' && l.name === name);
    if (!frame) throw new Error(`Светлый фрейм "${name}" не найден на текущей странице`);
    frames.push(frame);
  }
  return frames;
}

function framePos(frame) {
  if (Array.isArray(frame.pos)) return frame.pos;
  if (Array.isArray(frame.transform)) return [frame.transform[2], frame.transform[5]];
  return [0, 0];
}

function frameSize(frame) {
  if (Array.isArray(frame.size)) return frame.size;
  return [W, SCREEN_HEIGHTS[frame.name] ?? 500];
}

function renderChrome(snapshot, activeTab, tk) {
  const layers = [];
  const headerH = 60;
  const navH = 44;
  const filterH = 52;

  layers.push(rect(tk, 'header-bg', 0, 0, W, headerH, { fill: tk.headerBg }));
  layers.push(textLayer(tk, 'brand-name', PAD + 38, 18, 200, 24, 'Triage', { color: tk.headerText, size: 20, bold: true }));

  layers.push(rect(tk, 'nav-bg', 0, headerH, W, navH, { fill: tk.surface, border: tk.border }));
  const tabs = [
    { id: 'testcases', label: lbl(snapshot, 'nav.testCases') },
    { id: 'suggestedcycles', label: lbl(snapshot, 'nav.suggestedCycles') },
    { id: 'settings', label: lbl(snapshot, 'nav.settings') },
  ];
  let tx = PAD;
  for (const tab of tabs) {
    const tw = Math.max(tab.label.length * 8 + 32, 100);
    const active = tab.id === activeTab;
    if (active) layers.push(rect(tk, `tab-line-${tab.id}`, tx, headerH + navH - 2, tw, 2, { fill: tk.tabActive }));
    layers.push(textLayer(tk, `tab-${tab.id}`, tx + 16, headerH + 12, tw - 32, 20, tab.label, {
      color: active ? tk.tabActive : tk.tabText,
      bold: active,
    }));
    tx += tw;
  }

  layers.push(rect(tk, 'filters-bg', 0, headerH + navH, W, filterH, { fill: tk.surface, border: tk.border }));
  layers.push(textLayer(tk, 'filter-lbl', PAD, headerH + navH + 16, 60, 18, lbl(snapshot, 'filter.project'), { bold: true, color: tk.textStrong }));
  layers.push(...selectBox(tk, PAD + 70, headerH + navH + 8, 120, null, 'B2C'));

  return { layers, contentY: headerH + navH + filterH + 20 };
}

function renderTable(tk, x, y, width, colWidths, headers, rows, opts = {}) {
  const layers = [];
  const rh = tk.rowHeight;
  const hh = tk.headerRowHeight;
  let cy = y;

  if (opts.tile) {
    const pagH = opts.pagination ? 48 : 0;
    layers.push(rect(tk, 'table-tile', x - 16, y - 16, width + 32, hh + rows.length * rh + 32 + pagH, {
      fill: tk.surface,
      radius: opts.tileRadius ?? tk.radiusTile,
      border: tk.border,
    }));
  }

  let cx = x;
  for (let i = 0; i < headers.length; i++) {
    const hdr = headers[i];
    layers.push(
      rect(tk, `th-${i}`, cx, cy, colWidths[i], hh, { fill: tk.surfaceMuted, border: tk.border }),
      textLayer(tk, `th-txt-${i}`, cx + 8, cy + 10, colWidths[i] - 16, 18, hdr, { bold: true, color: tk.textStrong, size: 12 })
    );
    cx += colWidths[i];
  }
  cy += hh;

  for (let r = 0; r < rows.length; r++) {
    cx = x;
    for (let c = 0; c < rows[r].length; c++) {
      const cell = rows[r][c];
      if (cell === '__checkbox__') {
        layers.push(...checkbox(tk, cx + 8, cy, false));
      } else if (cell?.type === 'priority') {
        layers.push(...priorityBadge(tk, cx + 8, cy, cell.text, cell.bg, cell.fg));
      } else {
        const isLink = opts.linkCol === c;
        const mono = opts.monoFrom != null && c >= opts.monoFrom;
        layers.push(textLayer(tk, `c-${r}-${c}`, cx + 8, cy + 10, colWidths[c] - 16, 18, String(cell), {
          color: isLink ? tk.link : tk.text,
          size: mono ? 11 : 14,
          truncate: c === 1 && opts.truncateName,
        }));
      }
      cx += colWidths[c];
    }
    layers.push(rect(tk, `row-${r}`, x, cy + rh - 1, width, 1, { fill: tk.border }));
    cy += rh;
  }
  return { layers, bottomY: cy };
}

function renderPagination(tk, x, y, snapshot, section, listW) {
  const layers = [];
  const pag = snapshot[section]?.pagination;
  const pagText = typeof pag === 'string' ? pag : pag?.text ?? '';
  const hasPrev = typeof pag === 'object' && pag?.hasPrevious;
  const hasNext = typeof pag === 'object' ? pag?.hasNext : true;
  let px = x;
  if (hasPrev) {
    layers.push(textLayer(tk, 'pag-prev', px, y, 60, 18, lbl(snapshot, 'testcases.pagination.previous'), { color: tk.link }));
    px += 70;
  }
  layers.push(textLayer(tk, 'pag-info', px, y, 420, 18, pagText, { color: tk.text, size: 13 }));
  const rp = snapshot[section]?.rowsPerPage ?? '15';
  layers.push(
    textLayer(tk, 'rows-lbl', x + listW - 200, y, 110, 18, lbl(snapshot, 'testcases.rowsPerPage'), { color: tk.text, size: 13 }),
    ...selectBox(tk, x + listW - 80, y - 6, 60, null, rp)
  );
  if (hasNext) {
    layers.push(textLayer(tk, 'pag-next', px + pagText.length * 5 + 20, y, 60, 18, lbl(snapshot, 'testcases.pagination.next'), { color: tk.link }));
  }
  return layers;
}

function buildChromeOnly(snapshot, tk) {
  return renderChrome(snapshot, 'testcases', tk).layers;
}

function buildTestCasesAndLlm(snapshot, tk) {
  const layers = [];
  const { layers: chrome, contentY } = renderChrome(snapshot, 'testcases', tk);
  layers.push(...chrome);

  const tileX = PAD;
  const tileY = contentY;
  const listW = Math.floor(CONTENT_W * 0.66);
  const sideW = CONTENT_W - listW;
  const rows = snapshot.testCases.rows;

  layers.push(rect(tk, 'tests-tile', tileX, tileY, CONTENT_W, 37 + 15 * 36 + 100, { fill: tk.surface, radius: tk.radiusTile, border: tk.border }));
  layers.push(...btn(tk, 'select-all', tileX + 20, tileY + 16, lbl(snapshot, 'testcases.selectAll'), 'secondary'));
  layers.push(...btn(tk, 'prioritize', tileX + 140, tileY + 16, lbl(snapshot, 'testcases.prioritize'), 'primary', true));

  const colW = [36, 88, 88, 80, 100, listW - 36 - 88 - 88 - 80 - 100 - 40];
  const headers = ['', lbl(snapshot, 'testcases.col.key'), lbl(snapshot, 'testcases.col.priority'),
    lbl(snapshot, 'testcases.col.status'), lbl(snapshot, 'testcases.col.folder'), lbl(snapshot, 'testcases.col.name')];
  const tableRows = rows.map((r) => [
    '__checkbox__',
    r.key,
    { type: 'priority', text: r.priority, bg: r.priorityColor, fg: r.priorityTextColor },
    r.status,
    r.folder,
    truncate(r.name, 55),
  ]);
  const table = renderTable(tk, tileX + 20, tileY + 56, listW - 40, colW, headers, tableRows);
  layers.push(...table.layers);
  layers.push(...renderPagination(tk, tileX + 20, table.bottomY + 8, snapshot, 'testCases', listW - 40));

  const sideX = tileX + listW;
  layers.push(rect(tk, 'sidebar-div', sideX, tileY + 52, 1, 37 + 15 * 36 + 20, { fill: tk.border }));
  layers.push(textLayer(tk, 'cols-h', sideX + 20, tileY + 68, sideW - 40, 18, lbl(snapshot, 'testcases.columns'), { bold: true, color: tk.textStrong }));
  [lbl(snapshot, 'testcases.col.key'), lbl(snapshot, 'testcases.col.priority'), lbl(snapshot, 'testcases.col.status'),
    lbl(snapshot, 'testcases.col.folder'), lbl(snapshot, 'testcases.col.name')].forEach((l, i) => {
    layers.push(...checkbox(tk, sideX + 20, tileY + 96 + i * 28));
    layers.push(textLayer(tk, `col-${i}`, sideX + 44, tileY + 100 + i * 28, sideW - 60, 18, l, { color: tk.text }));
  });
  layers.push(textLayer(tk, 'filt-h', sideX + 20, tileY + 240, sideW - 40, 18, lbl(snapshot, 'testcases.filters'), { bold: true, color: tk.textStrong }));
  const filters = [
    [lbl(snapshot, 'testcases.filter.priority'), lbl(snapshot, 'testcases.filter.allPriorities')],
    [lbl(snapshot, 'testcases.filter.folder'), lbl(snapshot, 'testcases.filter.allFolders')],
    [lbl(snapshot, 'testcases.filter.status'), lbl(snapshot, 'testcases.filter.allStatuses')],
    [lbl(snapshot, 'testcases.filter.sortBy'), lbl(snapshot, 'testcases.sort.key')],
    [lbl(snapshot, 'testcases.filter.order'), lbl(snapshot, 'testcases.sort.asc')],
  ];
  filters.forEach(([l, v], i) => layers.push(...selectBox(tk, sideX + 20, tileY + 268 + i * 58, sideW - 40, l, v)));

  const llmY = tileY + 37 + 15 * 36 + 70;
  const llm = snapshot.llm;
  layers.push(rect(tk, 'llm-tile', tileX, llmY, CONTENT_W, 520, { fill: tk.surface, radius: tk.radiusTile, border: tk.border }));
  layers.push(textLayer(tk, 'llm-h', tileX + 20, llmY + 16, 200, 18, lbl(snapshot, 'testcases.llm'), { bold: true, color: tk.textStrong }));
  layers.push(...selectBox(tk, tileX + 20, llmY + 44, 200, lbl(snapshot, 'testcases.chooseLlm'), llm.model || 'qwen2.5:7b'));
  layers.push(...selectBox(tk, tileX + 240, llmY + 44, 180, lbl(snapshot, 'testcases.pullModel'), 'qwen2.5:3b'));
  layers.push(...btn(tk, 'pull', tileX + 440, llmY + 66, lbl(snapshot, 'testcases.pullModel'), 'secondary'));
  layers.push(textLayer(tk, 'ollama', tileX + 560, llmY + 74, 120, 18, lbl(snapshot, 'testcases.browseOllama'), { color: tk.link }));
  layers.push(...selectBox(tk, tileX + 700, llmY + 44, 120, lbl(snapshot, 'testcases.numCtx'), llm.numCtx || '8192'));
  layers.push(textLayer(tk, 'guide', tileX + 20, llmY + 110, 320, 18, `▸ ${lbl(snapshot, 'testcases.promptGuide.title')}`, { bold: true, color: tk.textStrong }));

  const editorW = (CONTENT_W - 52) / 2;
  const prompts = [
    [lbl(snapshot, 'testcases.weightsPrompt'), llm.weightsPrompt, llm.weightsVersion],
    [lbl(snapshot, 'testcases.aisortPrompt'), llm.aisortPrompt, llm.aisortVersion],
  ];
  prompts.forEach(([title, body, ver], idx) => {
    const ex = tileX + 20 + idx * (editorW + 12);
    const ey = llmY + 140;
    layers.push(rect(tk, `ed-${idx}`, ex, ey, editorW, 280, { fill: tk.surfaceMuted, radius: tk.radiusBtn, border: tk.border }));
    layers.push(textLayer(tk, `ed-t-${idx}`, ex + 12, ey + 10, editorW - 100, 18, title, { bold: true, color: tk.textStrong }));
    layers.push(...selectBox(tk, ex + editorW - 90, ey + 4, 78, null, ver || '0.1.0'));
    const preview = (body || '').split('\n').slice(0, 8).join('\n');
    layers.push(textLayer(tk, `ed-b-${idx}`, ex + 12, ey + 36, editorW - 24, 200, preview, { color: tk.text, size: 11 }));
    layers.push(...btn(tk, `save-${idx}`, ex + 12, ey + 244, lbl(snapshot, 'testcases.save'), 'primary', true));
  });

  return layers;
}

function buildModalPrioritize(snapshot, baseLayers, tk) {
  const layers = [...baseLayers];
  layers.push(rect(tk, 'overlay', 0, 0, W, 1400, { fill: '#8c0f172a' }));
  const panelW = 420;
  const py = 320;
  const px = (W - panelW) / 2;
  layers.push(rect(tk, 'modal', px, py, panelW, 220, { fill: tk.surface, radius: tk.radiusModal, border: tk.border }));
  layers.push(...spinner(tk, px + panelW / 2, py + 40));
  layers.push(textLayer(tk, 'modal-title', px + 32, py + 72, panelW - 64, 24, lbl(snapshot, 'testcases.progress.title'), {
    bold: true, color: tk.textStrong, size: 18,
  }));
  layers.push(textLayer(tk, 'modal-body', px + 32, py + 108, panelW - 64, 24, snapshot.modal?.progressText || '', { color: tk.text }));
  layers.push(textLayer(tk, 'modal-timer', px + 32, py + 136, panelW - 64, 20, snapshot.modal?.progressTimer || '', { color: tk.muted, size: 13 }));
  layers.push(...btn(tk, 'cancel', px + panelW - 110, py + 168, lbl(snapshot, 'testcases.progress.cancel'), 'secondary'));
  return layers;
}

function buildSuggestedCycles(snapshot, tk) {
  const layers = [];
  const { layers: chrome, contentY } = renderChrome(snapshot, 'suggestedcycles', tk);
  layers.push(...chrome);
  const rows = snapshot.suggestedCycles.rows;
  const colW = [160, CONTENT_W - 160 - 120 - 32, 120];
  const headers = [lbl(snapshot, 'suggested.col.key'), lbl(snapshot, 'suggested.col.name'), lbl(snapshot, 'suggested.col.status')];
  const tableRows = rows.map((r) => [r.key, r.name, r.status]);
  const table = renderTable(tk, PAD, contentY, CONTENT_W, colW, headers, tableRows, { tile: true, linkCol: 0, pagination: true });
  layers.push(...table.layers);
  layers.push(...renderPagination(tk, PAD, table.bottomY + 12, snapshot, 'suggestedCycles', CONTENT_W));
  return layers;
}

function buildCycleDetail(snapshot, tk) {
  const layers = [];
  const { layers: chrome, contentY } = renderChrome(snapshot, 'suggestedcycles', tk);
  layers.push(...chrome);
  const c = snapshot.cycleDetail;
  let y = contentY;
  layers.push(textLayer(tk, 'k', PAD, y, CONTENT_W, 20, `${lbl(snapshot, 'suggested.detail.key')} ${c.key}`, { color: tk.text }));
  y += 24;
  layers.push(textLayer(tk, 'n-lbl', PAD, y, 80, 20, lbl(snapshot, 'suggested.detail.name'), { color: tk.text, bold: true }));
  layers.push(rect(tk, 'name-in', PAD + 90, y - 2, 420, 28, { fill: tk.surface, radius: tk.radiusBtn, border: tk.borderInput }));
  layers.push(textLayer(tk, 'n-val', PAD + 98, y + 2, 400, 18, c.name, { color: tk.text }));
  y += 32;
  layers.push(textLayer(tk, 'st', PAD, y, CONTENT_W, 20, `${lbl(snapshot, 'suggested.detail.status')} ${c.status}`, { color: tk.text }));
  y += 24;
  const folder = c.artifactFolder.includes(':') ? c.artifactFolder : `${lbl(snapshot, 'suggested.detail.artifactFolder')} ${c.artifactFolder}`;
  layers.push(textLayer(tk, 'af', PAD, y, CONTENT_W, 20, folder, { color: tk.text }));
  y += 36;
  layers.push(...btn(tk, 'save', PAD, y, lbl(snapshot, 'suggested.save'), 'secondary', true));
  layers.push(...btn(tk, 'json', PAD + 100, y, lbl(snapshot, 'suggested.downloadJson'), 'secondary'));
  layers.push(...btn(tk, 'zephyr', PAD + 240, y, lbl(snapshot, 'suggested.send'), 'primary'));
  layers.push(textLayer(tk, 'back', PAD + 400, y + 8, 100, 18, lbl(snapshot, 'suggested.back'), { color: tk.link }));
  y += 48;

  const headers = [
    lbl(snapshot, 'suggested.col.key'),
    lbl(snapshot, 'suggested.col.name'),
    `${lbl(snapshot, 'suggested.col.aiSort')} ↓`,
    lbl(snapshot, 'suggested.col.weightPriority'),
    lbl(snapshot, 'suggested.col.weightStatus'),
    lbl(snapshot, 'suggested.col.weightLabels'),
    lbl(snapshot, 'suggested.col.weightExecutions'),
    lbl(snapshot, 'suggested.col.weightFailures'),
    lbl(snapshot, 'suggested.col.weightFailedRatio'),
    lbl(snapshot, 'suggested.col.weightLastStatus'),
    lbl(snapshot, 'suggested.col.weightExecutionTime'),
  ];
  const colW = [72, 150, 58, 58, 58, 58, 58, 58, 58, 58, 58];
  const sorted = [...c.tests].sort((a, b) => (b.aiSort ?? 0) - (a.aiSort ?? 0));
  const tableRows = sorted.map((t) => [
    t.key,
    truncate(t.name, 38),
    formatNum(t.aiSort),
    formatNum(t.weights?.priority),
    formatNum(t.weights?.status),
    formatNum(t.weights?.labels),
    formatNum(t.weights?.executionsCount),
    formatNum(t.weights?.failuresCount),
    formatNum(t.weights?.failedExecutionsRatio),
    formatNum(t.weights?.lastExecutionStatus),
    formatNum(t.weights?.executionTime),
  ]);
  const table = renderTable(tk, PAD, y, colW.reduce((a, b) => a + b, 0), colW, headers, tableRows, { tile: true, linkCol: 0, monoFrom: 2 });
  layers.push(...table.layers);
  return layers;
}

function buildSettings(snapshot, tk) {
  const layers = [];
  const { layers: chrome, contentY } = renderChrome(snapshot, 'settings', tk);
  layers.push(...chrome);
  layers.push(rect(tk, 'tile', PAD, contentY, CONTENT_W, 240, { fill: tk.surface, radius: 6, border: tk.border }));
  let y = contentY + 20;
  layers.push(textLayer(tk, 'app-h', PAD + 16, y, 200, 18, lbl(snapshot, 'settings.appearance', 'Оформление'), { bold: true, color: tk.textStrong }));
  y += 32;
  const themes = [
    ['dark', lbl(snapshot, 'settings.theme.dark', 'Тёмный')],
    ['light', lbl(snapshot, 'settings.theme.light', 'Светлый')],
    ['system', lbl(snapshot, 'settings.theme.system', 'Как в системе')],
  ];
  for (const [val, label] of themes) {
    layers.push(...radioOption(tk, PAD + 20, y, snapshot.settings?.theme === val));
    layers.push(textLayer(tk, `th-${val}`, PAD + 42, y - 2, 160, 18, label, { color: tk.text }));
    y += 28;
  }
  y += 12;
  layers.push(textLayer(tk, 'lang-h', PAD + 16, y, 200, 18, lbl(snapshot, 'settings.language', 'Язык'), { bold: true, color: tk.textStrong }));
  y += 32;
  const locales = [
    ['ru', lbl(snapshot, 'settings.locale.ru', 'Русский')],
    ['en', lbl(snapshot, 'settings.locale.en', 'Английский')],
    ['system', lbl(snapshot, 'settings.locale.system', 'Как в системе')],
  ];
  for (const [val, label] of locales) {
    layers.push(...radioOption(tk, PAD + 20, y, snapshot.settings?.locale === val));
    layers.push(textLayer(tk, `loc-${val}`, PAD + 42, y - 2, 160, 18, label, { color: tk.text }));
    y += 28;
  }
  return layers;
}

function makeScreenBuilders(snapshot, tk) {
  const testCasesBase = buildTestCasesAndLlm(snapshot, tk);
  return [
    { name: '01_Chrome', height: SCREEN_HEIGHTS['01_Chrome'], build: () => buildChromeOnly(snapshot, tk), icon: true },
    { name: '02_TestCases_LLM', height: SCREEN_HEIGHTS['02_TestCases_LLM'], build: () => testCasesBase, icon: true },
    { name: '04_Modal_Prioritize', height: SCREEN_HEIGHTS['04_Modal_Prioritize'], build: () => buildModalPrioritize(snapshot, testCasesBase, tk), icon: true },
    { name: '05_SuggestedCycles', height: SCREEN_HEIGHTS['05_SuggestedCycles'], build: () => buildSuggestedCycles(snapshot, tk), icon: true },
    { name: '06_CycleDetail_SR75', height: SCREEN_HEIGHTS['06_CycleDetail_SR75'], build: () => buildCycleDetail(snapshot, tk), icon: true },
    { name: '07_Settings', height: SCREEN_HEIGHTS['07_Settings'], build: () => buildSettings(snapshot, tk), icon: true },
  ];
}

function snapshotForDarkBuild(snapshot, screenName) {
  if (screenName === '07_Settings') {
    return { ...snapshot, settings: { ...snapshot.settings, theme: 'dark' } };
  }
  return snapshot;
}

async function buildLightTheme(snapshot) {
  const tk = THEMES.light;
  const screens = makeScreenBuilders(snapshot, tk);
  let offsetX = 0;
  for (const screen of screens) {
    console.log(`Frame ${screen.name} @ x=${offsetX}, y=0`);
    const frameId = await createFrame(screen.name, offsetX, 0, screen.height, tk);
    const children = screen.build();
    console.log(`  ${children.length} layers`);
    await addToFrame(frameId, children);
    if (screen.icon) await insertBrandIcon(frameId, PAD, 16);
    offsetX += W + FRAME_GAP;
  }
}

async function buildDarkTheme(snapshot) {
  const tk = THEMES.dark;
  const lightFrames = await findLightFrames();
  const allLayers = await api('POST', '/getlayersinarea', {
    x: -5000,
    y: -5000,
    width: 25000,
    height: 25000,
    maxDepth: 1,
  });
  const existing = Array.isArray(allLayers) ? allLayers : [];

  for (const light of lightFrames) {
    const name = light.name;
    const darkName = `${name}_Dark`;
    const [lx, ly] = framePos(light);
    const [, lh] = frameSize(light);
    const darkY = ly + lh + VERTICAL_GAP;
    const height = SCREEN_HEIGHTS[name] ?? lh;

    if (existing.some((l) => l._t === 'FRAME' && l.name === darkName)) {
      console.warn(`Фрейм ${darkName} уже существует — пропуск`);
      continue;
    }

    const snap = snapshotForDarkBuild(snapshot, name);
    const screens = makeScreenBuilders(snap, tk);
    const screen = screens.find((s) => s.name === name);
    if (!screen) continue;

    console.log(`Frame ${darkName} @ x=${lx}, y=${darkY}`);
    const frameId = await createFrame(darkName, lx, darkY, height, tk);
    const children = screen.build();
    console.log(`  ${children.length} layers`);
    await addToFrame(frameId, children);
    if (screen.icon) await insertBrandIcon(frameId, PAD, 16);
  }
}

async function main() {
  if (ADD_DARK && CLEAN) {
    throw new Error('Флаги --add-dark и --clean несовместимы');
  }

  const snapshot = loadSnapshot();
  console.log('Lunacy API check...');
  console.log((await api('GET', '/getviewportinfo')).message ?? 'ok');

  if (ADD_DARK) {
    await buildDarkTheme(snapshot);
    await api('POST', '/showtoast', {
      text: 'Тёмные копии прототипа добавлены под светлыми фреймами. Сохраните документ в Lunacy.',
      iconType: 2,
      priority: 5,
      durationSeconds: 8,
    });
  } else {
    if (!CLEAN) await warnIfPrototypeExists();
    if (CLEAN) await cleanOldFrames();
    await buildLightTheme(snapshot);
    await api('POST', '/showtoast', {
      text: 'Прототип Triage обновлён по эталону приложения. Сохраните документ в Lunacy.',
      iconType: 2,
      priority: 5,
      durationSeconds: 8,
    });
  }

  console.log('Done.');
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
