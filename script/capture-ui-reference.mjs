#!/usr/bin/env node
/**
 * Captures reference screenshots and DOM snapshot from running Triage app.
 * Usage: node script/capture-ui-reference.mjs [--base-url http://localhost:8080]
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { chromium } from 'playwright';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const OUT_DIR = path.join(ROOT, 'docs', 'lunacy', 'reference');
const ASSETS_DIR = path.join(ROOT, 'docs', 'lunacy', 'assets');

const baseUrl = process.argv.find((a) => a.startsWith('--base-url='))?.split('=')[1]
  ?? process.env.TRIAGE_BASE_URL
  ?? 'http://localhost:8080';

async function ensureDirs() {
  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.mkdirSync(ASSETS_DIR, { recursive: true });
}

function extractSnapshotScript() {
  return () => {
    const style = (el) => {
      if (!el) return null;
      const cs = getComputedStyle(el);
      return {
        color: cs.color,
        backgroundColor: cs.backgroundColor,
        fontSize: cs.fontSize,
        fontWeight: cs.fontWeight,
        padding: cs.padding,
        borderRadius: cs.borderRadius,
      };
    };

    const i18nLabels = {};
    document.querySelectorAll('[data-i18n]').forEach((el) => {
      const key = el.getAttribute('data-i18n');
      if (key) i18nLabels[key] = el.textContent?.trim() ?? '';
    });

    const testRows = [];
    document.querySelectorAll('#testcases-table tbody tr').forEach((tr) => {
      if (tr.querySelector('.empty-row')) return;
      const cells = tr.querySelectorAll('td');
      if (cells.length < 6) return;
      const badge = cells[2].querySelector('.priority-badge');
      const badgeStyle = badge ? badge.getAttribute('style') || '' : '';
      const bgMatch = badgeStyle.match(/background-color:\s*([^;]+)/);
      const fgMatch = badgeStyle.match(/color:\s*([^;]+)/);
      testRows.push({
        key: cells[1].textContent?.trim() ?? '',
        priority: cells[2].textContent?.trim() ?? '',
        priorityColor: bgMatch ? bgMatch[1].trim() : null,
        priorityTextColor: fgMatch ? fgMatch[1].trim() : null,
        status: cells[3].textContent?.trim() ?? '',
        folder: cells[4].textContent?.trim() ?? '',
        name: cells[5].textContent?.trim() ?? '',
      });
    });

    const paginationInfo = document.querySelector('.pagination-info')?.textContent?.trim() ?? '';
    const hasPrevious = !!document.querySelector('.pagination-link')?.textContent?.includes('Назад')
      || !!Array.from(document.querySelectorAll('.pagination-link')).find((a) =>
        a.textContent?.includes('Previous') || a.textContent?.includes('Назад'));
    const hasNext = !!Array.from(document.querySelectorAll('.pagination-link')).find((a) =>
      a.textContent?.includes('Next') || a.textContent?.includes('Вперёд'));

    const cycleRows = [];
    document.querySelectorAll('#suggested-cycles-app table tbody tr, .tab-content table tbody tr').forEach((tr) => {
      if (tr.querySelector('.empty-row')) return;
      const cells = tr.querySelectorAll('td');
      if (cells.length < 3) return;
      const keyEl = cells[0].querySelector('a') || cells[0];
      cycleRows.push({
        key: keyEl.textContent?.trim() ?? '',
        name: cells[1].textContent?.trim() ?? '',
        status: cells[2].textContent?.trim() ?? '',
      });
    });

    let cycleDetail = null;
    const detailApp = document.getElementById('suggested-cycle-detail-app');
    if (detailApp) {
      const testsJsonEl = document.getElementById('suggested-cycle-tests-json');
      let tests = [];
      try {
        tests = JSON.parse(testsJsonEl?.textContent || '[]');
      } catch {
        tests = [];
      }
      cycleDetail = {
        key: detailApp.dataset.cycleKey ?? '',
        name: document.getElementById('cycle-name-input')?.value ?? '',
        status: document.getElementById('cycle-status')?.textContent?.trim() ?? '',
        artifactFolder: Array.from(document.querySelectorAll('.cycle-meta'))
          .map((p) => p.textContent?.trim())
          .find((t) => t?.includes('артефакт') || t?.includes('Artifact')) ?? '',
        tests,
      };
    }

    const settings = {
      theme: document.querySelector('#theme-settings input[name="theme"]:checked')?.value ?? null,
      locale: document.querySelector('#locale-settings input[name="locale"]:checked')?.value ?? null,
    };

    return {
      capturedAt: new Date().toISOString(),
      locale: document.documentElement.getAttribute('data-locale') || 'en',
      labels: i18nLabels,
      testCases: {
        rows: testRows,
        pagination: { text: paginationInfo, hasPrevious, hasNext },
        rowsPerPage: document.getElementById('rows-per-page')?.value ?? '15',
      },
      llm: {
        model: document.getElementById('llm-model-select')?.value ?? '',
        numCtx: document.getElementById('llm-num-ctx')?.value ?? '',
        weightsPrompt: (document.getElementById('weights-prompt')?.value ?? '').slice(0, 800),
        aisortPrompt: (document.getElementById('aisort-prompt')?.value ?? '').slice(0, 800),
        weightsVersion: document.getElementById('weights-version-select')?.value ?? '',
        aisortVersion: document.getElementById('aisort-version-select')?.value ?? '',
      },
      suggestedCycles: {
        rows: cycleRows.slice(0, 15),
        pagination: document.querySelector('#suggested-cycles-app .pagination-info, .tab-content .pagination-info')?.textContent?.trim() ?? '',
        rowsPerPage: document.querySelector('#suggested-cycles-app #rows-per-page, .tab-content #rows-per-page')?.value ?? '15',
      },
      cycleDetail,
      settings,
      styles: {
        header: style(document.querySelector('.app-header')),
        tabActive: style(document.querySelector('.tab-link.active')),
        btnPrimary: style(document.querySelector('.btn-primary')),
        priorityBadge: style(document.querySelector('.priority-badge')),
        dataTableTh: style(document.querySelector('.data-table th')),
      },
      modal: {
        progressText: document.getElementById('prioritize-progress-text')?.textContent?.trim() ?? '',
        progressTimer: document.getElementById('prioritize-progress-timer')?.textContent?.trim() ?? '',
      },
    };
  };
}

async function setupPage(context) {
  const page = await context.newPage();
  await page.addInitScript(() => {
    localStorage.setItem('triage-locale-preference', 'ru');
    localStorage.setItem('triage-theme-preference', 'light');
  });
  await page.setViewportSize({ width: 1440, height: 900 });
  return page;
}

async function waitForApp(page, url) {
  await page.goto(url, { waitUntil: 'networkidle', timeout: 120000 });
  await page.waitForSelector('.app-header', { timeout: 30000 });
  await page.waitForFunction(() => document.documentElement.getAttribute('data-locale') === 'ru', { timeout: 10000 });
}

async function main() {
  await ensureDirs();

  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const snapshot = {};

  try {
    const page = await setupPage(context);
    await waitForApp(page, `${baseUrl}/testcases?project=B2C`);
    await page.waitForSelector('#testcases-table tbody tr', { timeout: 60000 });

    await page.screenshot({ path: path.join(OUT_DIR, '01-chrome.png'), clip: { x: 0, y: 0, width: 1440, height: 180 } });
    await page.screenshot({ path: path.join(OUT_DIR, '02-testcases.png'), fullPage: true });

    const llmPanel = page.locator('.llm-panel');
    await llmPanel.scrollIntoViewIfNeeded();
    await page.screenshot({ path: path.join(OUT_DIR, '03-llm-panel.png'), fullPage: true });

    const brandIcon = page.locator('.app-brand__icon');
    await brandIcon.screenshot({ path: path.join(ASSETS_DIR, 'triage-mark-28.png') });

    await page.evaluate(() => {
      const modal = document.getElementById('prioritize-progress-modal');
      const text = document.getElementById('prioritize-progress-text');
      const timer = document.getElementById('prioritize-progress-timer');
      if (modal) modal.hidden = false;
      if (text && window.TriageI18n) {
        text.textContent = window.TriageI18n.translate('testcases.progress.processing', {
          current: 1,
          total: 3,
          key: 'SQM-T4',
        });
      }
      if (timer && window.TriageI18n) {
        timer.textContent = window.TriageI18n.translate('testcases.progress.elapsed', { time: '00:02' });
      }
    });
    await page.screenshot({ path: path.join(OUT_DIR, '04-modal-prioritize.png'), fullPage: false });

    await page.evaluate(() => {
      const modal = document.getElementById('prioritize-progress-modal');
      if (modal) modal.hidden = true;
    });

    Object.assign(snapshot, await page.evaluate(extractSnapshotScript()));

    await waitForApp(page, `${baseUrl}/suggestedcycles?project=B2C`);
    await page.waitForSelector('.data-table tbody tr', { timeout: 30000 });
    await page.screenshot({ path: path.join(OUT_DIR, '05-suggested-cycles.png'), fullPage: true });
    const cyclesData = await page.evaluate(extractSnapshotScript());
    snapshot.suggestedCycles = cyclesData.suggestedCycles;

    await waitForApp(page, `${baseUrl}/suggestedcycles/SQM-SR75?project=B2C`);
    await page.waitForSelector('#suggested-cycle-detail-app', { timeout: 30000 });
    await page.waitForFunction(() => {
      const body = document.getElementById('suggested-cycle-table-body');
      return body && body.children.length > 0;
    }, { timeout: 30000 });
    await page.screenshot({ path: path.join(OUT_DIR, '06-cycle-detail.png'), fullPage: true });
    const detailData = await page.evaluate(extractSnapshotScript());
    snapshot.cycleDetail = detailData.cycleDetail;

    await waitForApp(page, `${baseUrl}/settings?project=B2C`);
    await page.evaluate(() => {
      const themeForm = document.getElementById('theme-settings');
      const localeForm = document.getElementById('locale-settings');
      const systemTheme = themeForm?.querySelector('input[value="system"]');
      const ruLocale = localeForm?.querySelector('input[value="ru"]');
      if (systemTheme) systemTheme.checked = true;
      if (ruLocale) ruLocale.checked = true;
    });
    await page.screenshot({ path: path.join(OUT_DIR, '07-settings.png'), fullPage: true });
    const settingsData = await page.evaluate(extractSnapshotScript());
    snapshot.settings = settingsData.settings;

    fs.writeFileSync(path.join(OUT_DIR, 'ui-snapshot.json'), JSON.stringify(snapshot, null, 2), 'utf8');
    console.log(`Saved ${OUT_DIR}/ui-snapshot.json`);
    console.log(`Test cases rows: ${snapshot.testCases?.rows?.length ?? 0}`);
    console.log(`Suggested cycles rows: ${snapshot.suggestedCycles?.rows?.length ?? 0}`);
  } finally {
    await browser.close();
  }
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
