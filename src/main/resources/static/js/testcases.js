document.addEventListener('DOMContentLoaded', () => {
    const app = document.getElementById('testcases-app');
    const prioritizeBtn = document.getElementById('prioritize-btn');
    const selectAllBtn = document.getElementById('select-all-btn');
    const selectionCountEl = document.getElementById('selection-count');

    if (!prioritizeBtn) {
        return;
    }

    const allMatchingKeys = Array.isArray(window.__allMatchingTestCaseKeys)
        ? window.__allMatchingTestCaseKeys
        : [];
    const storageKey = buildSelectionStorageKey();
    const selectedKeys = loadSelection();

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
            selectionCountEl.textContent = count > 0 ? `${count} selected` : '';
        }
        prioritizeBtn.disabled = count < 2;
        if (selectAllBtn) {
            const allSelected = allMatchingKeys.length > 0
                && allMatchingKeys.every(key => selectedKeys.has(key));
            selectAllBtn.disabled = allMatchingKeys.length === 0;
            selectAllBtn.textContent = allSelected ? 'Deselect All' : 'Select All';
        }
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

    prioritizeBtn.addEventListener('click', async () => {
        const project = app?.dataset.project || 'B2C';
        const testCaseKeys = [...selectedKeys];

        if (testCaseKeys.length < 2) {
            return;
        }

        const originalText = prioritizeBtn.textContent;
        prioritizeBtn.disabled = true;
        prioritizeBtn.textContent = 'Prioritizing...';

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
            prioritizeBtn.textContent = originalText;
            updateUi();
        }
    });
});
