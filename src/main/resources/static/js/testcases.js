document.addEventListener('DOMContentLoaded', () => {
    const app = document.getElementById('testcases-app');
    const prioritizeBtn = document.getElementById('prioritize-btn');
    const selectAllBtn = document.getElementById('select-all-btn');
    const selectionCountEl = document.getElementById('selection-count');
    const tableScroll = document.getElementById('testcases-table-scroll');
    const table = document.getElementById('testcases-table');

    if (!prioritizeBtn) {
        return;
    }

    const allMatchingKeys = Array.isArray(window.__allMatchingTestCaseKeys)
        ? window.__allMatchingTestCaseKeys
        : [];
    const storageKey = buildSelectionStorageKey();
    const columnsStorageKey = 'triage-testcase-columns';
    const selectedKeys = loadSelection();

    function t(key, params) {
        return window.TriageI18n ? window.TriageI18n.translate(key, params) : key;
    }

    function buildSelectionStorageKey() {
        const params = new URLSearchParams(window.location.search);
        params.delete('page');
        const pairs = [];
        params.forEach((value, key) => {
            pairs.push([key, value]);
        });
        pairs.sort((a, b) => a[0].localeCompare(b[0]) || a[1].localeCompare(b[1]));
        const query = pairs.map(([key, value]) => `${key}=${value}`).join('&');
        return `triage-testcase-selection:${query}`;
    }

    function isPageReload() {
        const navigationEntry = performance.getEntriesByType('navigation')[0];
        return navigationEntry?.type === 'reload';
    }

    function clearStoredSelection() {
        sessionStorage.removeItem(storageKey);
        sessionStorage.removeItem(`triage-testcase-selection:${window.location.search}`);
    }

    function readStoredSelection() {
        try {
            let stored = sessionStorage.getItem(storageKey);
            if (!stored) {
                const legacyKey = `triage-testcase-selection:${window.location.search}`;
                stored = sessionStorage.getItem(legacyKey);
                if (stored) {
                    sessionStorage.setItem(storageKey, stored);
                    sessionStorage.removeItem(legacyKey);
                }
            }
            if (!stored) {
                return [];
            }
            const parsed = JSON.parse(stored);
            return Array.isArray(parsed) ? parsed : [];
        } catch {
            return [];
        }
    }

    function loadSelection() {
        if (isPageReload()) {
            clearStoredSelection();
            return new Set();
        }
        return new Set(readStoredSelection());
    }

    function saveSelection() {
        sessionStorage.setItem(storageKey, JSON.stringify([...selectedKeys]));
    }

    function syncCheckboxesFromSelection() {
        document.querySelectorAll('.testcase-checkbox').forEach(checkbox => {
            checkbox.checked = selectedKeys.has(checkbox.value);
        });
    }

    function updateUi() {
        const count = selectedKeys.size;
        if (selectionCountEl) {
            selectionCountEl.textContent = count > 0
                ? t('testcases.selected', { count })
                : '';
        }
        prioritizeBtn.disabled = count < 2;
        if (selectAllBtn) {
            const allSelected = allMatchingKeys.length > 0
                && allMatchingKeys.every(key => selectedKeys.has(key));
            selectAllBtn.disabled = allMatchingKeys.length === 0;
            selectAllBtn.textContent = allSelected
                ? t('testcases.deselectAll')
                : t('testcases.selectAll');
        }
    }

    function loadColumnVisibility() {
        try {
            const stored = localStorage.getItem(columnsStorageKey);
            if (!stored) {
                return null;
            }
            const parsed = JSON.parse(stored);
            return typeof parsed === 'object' && parsed !== null ? parsed : null;
        } catch {
            return null;
        }
    }

    function saveColumnVisibility(visibility) {
        localStorage.setItem(columnsStorageKey, JSON.stringify(visibility));
    }

    function applyColumnVisibility(visibility) {
        if (!table) {
            return;
        }
        const columns = ['key', 'priority', 'status', 'folder', 'name'];
        columns.forEach(column => {
            const visible = visibility[column] !== false;
            table.querySelectorAll(`[data-column="${column}"]`).forEach(cell => {
                cell.classList.toggle('column-hidden', !visible);
            });
        });
        updateEmptyRowColspan();
        updateTableScrollState();
    }

    function updateEmptyRowColspan() {
        if (!table) {
            return;
        }
        const emptyRow = table.querySelector('.empty-row');
        if (!emptyRow) {
            return;
        }
        const visibleCount = 1 + ['key', 'priority', 'status', 'folder', 'name']
            .filter(column => {
                const toggle = document.querySelector(`[data-column-toggle="${column}"]`);
                return !toggle || toggle.checked;
            }).length;
        emptyRow.colSpan = visibleCount;
    }

    function initColumnToggles() {
        const stored = loadColumnVisibility();
        document.querySelectorAll('[data-column-toggle]').forEach(toggle => {
            const column = toggle.getAttribute('data-column-toggle');
            if (stored && Object.prototype.hasOwnProperty.call(stored, column)) {
                toggle.checked = stored[column] !== false;
            }
            toggle.addEventListener('change', () => {
                const visibility = {};
                document.querySelectorAll('[data-column-toggle]').forEach(input => {
                    visibility[input.getAttribute('data-column-toggle')] = input.checked;
                });
                saveColumnVisibility(visibility);
                applyColumnVisibility(visibility);
            });
        });

        const visibility = {};
        document.querySelectorAll('[data-column-toggle]').forEach(input => {
            visibility[input.getAttribute('data-column-toggle')] = input.checked;
        });
        applyColumnVisibility(visibility);
    }

    function updateTableScrollState() {
        if (!tableScroll || !table) {
            return;
        }
        const needsScroll = table.scrollWidth > tableScroll.clientWidth + 1;
        tableScroll.classList.toggle('is-scrollable', needsScroll);
        tableScroll.style.overflowX = needsScroll ? 'auto' : 'hidden';
    }

    document.querySelectorAll('.testcase-checkbox').forEach(checkbox => {
        checkbox.addEventListener('change', () => {
            if (checkbox.checked) {
                selectedKeys.add(checkbox.value);
            } else {
                selectedKeys.delete(checkbox.value);
            }
            saveSelection();
            updateUi();
        });
    });

    if (selectAllBtn) {
        selectAllBtn.addEventListener('click', () => {
            const allSelected = allMatchingKeys.length > 0
                && allMatchingKeys.every(key => selectedKeys.has(key));

            if (allSelected) {
                allMatchingKeys.forEach(key => selectedKeys.delete(key));
            } else {
                allMatchingKeys.forEach(key => selectedKeys.add(key));
            }

            syncCheckboxesFromSelection();
            saveSelection();
            updateUi();
        });
    }

    syncCheckboxesFromSelection();
    updateUi();
    initColumnToggles();

    window.addEventListener('resize', updateTableScrollState);
    document.addEventListener('triage:locale-changed', () => {
        updateUi();
        if (window.TriageI18n) {
            window.TriageI18n.applyTranslations(app || document);
        }
    });
    document.addEventListener('triage:i18n-applied', updateTableScrollState);

    prioritizeBtn.addEventListener('click', async () => {
        const project = app?.dataset.project || 'B2C';
        const testCaseKeys = [...selectedKeys];

        if (testCaseKeys.length < 2) {
            return;
        }

        const originalKey = prioritizeBtn.getAttribute('data-i18n') || 'testcases.prioritize';
        prioritizeBtn.disabled = true;
        prioritizeBtn.textContent = t('testcases.prioritizing');

        try {
            const response = await fetch('/api/prioritize', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ project, testCaseKeys })
            });

            if (!response.ok) {
                throw new Error('Prioritize request failed');
            }

            const result = await response.json();
            clearStoredSelection();
            window.location.href = result.redirectUrl;
        } catch (error) {
            console.error(error);
            alert('Prioritization failed. Ensure Zephyr API token and Ollama are available.');
            prioritizeBtn.textContent = t(originalKey);
            updateUi();
        }
    });
});
