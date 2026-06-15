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

        const modal = document.getElementById('prioritize-progress-modal');
        const progressText = document.getElementById('prioritize-progress-text');
        const progressTimer = document.getElementById('prioritize-progress-timer');
        let pollTimerId = null;
        let displayTimerId = null;
        const startedAt = Date.now();

        function formatElapsed(ms) {
            const totalSeconds = Math.floor(ms / 1000);
            const minutes = Math.floor(totalSeconds / 60);
            const seconds = totalSeconds % 60;
            return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
        }

        function updateTimerDisplay(elapsedMillis) {
            if (progressTimer) {
                progressTimer.textContent = t('testcases.progress.elapsed', {
                    time: formatElapsed(elapsedMillis ?? (Date.now() - startedAt))
                });
            }
        }

        function showProgressModal() {
            if (!modal) {
                return;
            }
            modal.hidden = false;
            updateTimerDisplay(0);
            if (progressText) {
                progressText.textContent = t('testcases.progress.tests', { completed: 0, total: testCaseKeys.length });
            }
            displayTimerId = window.setInterval(() => updateTimerDisplay(null), 1000);
        }

        function hideProgressModal() {
            if (modal) {
                modal.hidden = true;
            }
            if (pollTimerId != null) {
                window.clearInterval(pollTimerId);
                pollTimerId = null;
            }
            if (displayTimerId != null) {
                window.clearInterval(displayTimerId);
                displayTimerId = null;
            }
        }

        try {
            showProgressModal();

            const startResponse = await fetch('/api/prioritize', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ project, testCaseKeys })
            });

            const startText = await startResponse.text();
            let startResult;
            try {
                startResult = JSON.parse(startText);
            } catch (parseError) {
                throw new Error(t('testcases.prioritizeFailedInvalidResponse'));
            }

            if (!startResponse.ok) {
                throw new Error(startResult.message || t('testcases.prioritizeFailedGeneric'));
            }

            const jobId = startResult.jobId;
            if (!jobId) {
                throw new Error(t('testcases.prioritizeFailedInvalidResponse'));
            }
            const totalTests = startResult.totalTests ?? testCaseKeys.length;

            function resolveRedirectUrl(jobStatus) {
                if (jobStatus.redirectUrl) {
                    return jobStatus.redirectUrl;
                }
                if (jobStatus.cycleKey) {
                    return `/suggestedcycles/${encodeURIComponent(jobStatus.cycleKey)}?project=${encodeURIComponent(project)}`;
                }
                return null;
            }

            async function pollJobStatus() {
                const statusResponse = await fetch(`/api/prioritize/jobs/${encodeURIComponent(jobId)}`);
                const statusText = await statusResponse.text();
                const jobStatus = JSON.parse(statusText);

                if (!statusResponse.ok) {
                    throw new Error(jobStatus.message || t('testcases.prioritizeFailedGeneric'));
                }

                updateTimerDisplay(jobStatus.elapsedMillis);

                if (jobStatus.status === 'AI_SORT') {
                    if (progressText) {
                        progressText.textContent = t('testcases.progress.aiSort');
                    }
                } else if (progressText) {
                    progressText.textContent = t('testcases.progress.tests', {
                        completed: jobStatus.completedTests ?? 0,
                        total: jobStatus.totalTests ?? totalTests
                    });
                }

                if (jobStatus.status === 'COMPLETED') {
                    const redirectUrl = resolveRedirectUrl(jobStatus);
                    if (!redirectUrl) {
                        throw new Error(t('testcases.prioritizeFailedInvalidResponse'));
                    }
                    return { ...jobStatus, redirectUrl };
                }
                if (jobStatus.status === 'FAILED') {
                    throw new Error(jobStatus.errorMessage || t('testcases.prioritizeFailedGeneric'));
                }
                return null;
            }

            await new Promise((resolve, reject) => {
                const handlePollError = (pollError) => {
                    if (pollTimerId != null) {
                        window.clearInterval(pollTimerId);
                        pollTimerId = null;
                    }
                    reject(pollError);
                };

                const pollOnce = async () => {
                    try {
                        const completedStatus = await pollJobStatus();
                        if (completedStatus) {
                            if (pollTimerId != null) {
                                window.clearInterval(pollTimerId);
                                pollTimerId = null;
                            }
                            resolve(completedStatus);
                        }
                    } catch (pollError) {
                        handlePollError(pollError);
                    }
                };

                pollOnce();
                pollTimerId = window.setInterval(pollOnce, 500);
            }).then((jobStatus) => {
                hideProgressModal();
                clearStoredSelection();
                window.location.href = jobStatus.redirectUrl;
            });
        } catch (error) {
            console.error(error);
            hideProgressModal();
            alert(error.message || t('testcases.prioritizeFailedGeneric'));
            prioritizeBtn.textContent = t(originalKey);
            updateUi();
        }
    });
});
