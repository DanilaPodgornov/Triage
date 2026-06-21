document.addEventListener('DOMContentLoaded', () => {
    const app = document.getElementById('suggested-cycle-detail-app');
    const tableBody = document.getElementById('suggested-cycle-table-body');
    const saveBtn = document.getElementById('save-sort-btn');
    const sendBtn = document.getElementById('send-zephyr-btn');
    const statusEl = document.getElementById('cycle-status');
    const nameInput = document.getElementById('cycle-name-input');
    const zephyrKeyRow = document.getElementById('zephyr-key-row');
    const zephyrKeyEl = document.getElementById('zephyr-cycle-key');
    const testsJsonEl = document.getElementById('suggested-cycle-tests-json');

    if (!app || !tableBody || !saveBtn || !sendBtn || !testsJsonEl) {
        return;
    }

    const cycleKey = app.dataset.cycleKey;
    const project = app.dataset.project || 'B2C';
    let sortField = app.dataset.sortField || 'aiSort';
    let sortDirection = normalizeDirection(app.dataset.sortDirection || 'DESC');
    let savedSortField = sortField;
    let savedSortDirection = sortDirection;
    let savedName = nameInput ? nameInput.value : '';
    let manualOrder = sortField === 'manual';
    let savedOrderKeys = [];
    let dragSourceKey = null;

    let tests = [];
    try {
        tests = JSON.parse(testsJsonEl.textContent || '[]');
        savedOrderKeys = tests.map(entry => entry.key);
    } catch (error) {
        console.error('Failed to parse suggested cycle tests JSON.', error);
        return;
    }

    document.querySelectorAll('#suggested-cycle-table .sortable-col').forEach(header => {
        header.addEventListener('click', () => {
            if (manualOrder) {
                return;
            }
            const field = header.dataset.sortField;
            if (!field) {
                return;
            }
            if (sortField === field) {
                sortDirection = sortDirection === 'DESC' ? 'ASC' : 'DESC';
            } else {
                sortField = field;
                sortDirection = 'DESC';
            }
            renderTable();
            updateButtonsState();
        });
    });

    if (nameInput) {
        nameInput.addEventListener('input', updateButtonsState);
    }

    saveBtn.addEventListener('click', async () => {
        const payload = {
            sortField,
            sortDirection,
            suggestedTestCycleName: nameInput ? nameInput.value : null
        };
        if (manualOrder) {
            payload.sortField = 'manual';
            payload.sortDirection = 'ASC';
            payload.orderedKeys = tests.map(entry => entry.key);
        }

        const response = await fetch(`/api/suggestedcycles/${encodeURIComponent(cycleKey)}/save`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (!response.ok) {
            alert('Failed to save sort state.');
            return;
        }
        app.dataset.sortField = manualOrder ? 'manual' : sortField;
        app.dataset.sortDirection = manualOrder ? 'ASC' : sortDirection;
        savedSortField = app.dataset.sortField;
        savedSortDirection = app.dataset.sortDirection;
        savedOrderKeys = tests.map(entry => entry.key);
        if (nameInput) {
            savedName = nameInput.value;
            document.title = `${nameInput.value} — Triage`;
        }
        updateButtonsState();
    });

    sendBtn.addEventListener('click', async () => {
        sendBtn.disabled = true;
        const response = await fetch(`/api/suggestedcycles/${encodeURIComponent(cycleKey)}/send`, {
            method: 'POST'
        });
        if (!response.ok) {
            sendBtn.disabled = app.dataset.status === 'Sent';
            let message = 'Failed to send test cycle.';
            try {
                const errorBody = await response.json();
                if (errorBody?.message) {
                    message = errorBody.message;
                }
            } catch (error) {
                // keep default message
            }
            alert(message);
            return;
        }
        let zephyrTestCycleKey = '';
        try {
            const body = await response.json();
            zephyrTestCycleKey = body?.zephyrTestCycleKey || '';
        } catch (error) {
            console.error('Failed to parse send-to-Zephyr response.', error);
        }
        if (statusEl) {
            statusEl.textContent = 'Sent';
        }
        if (zephyrKeyEl && zephyrTestCycleKey) {
            zephyrKeyEl.textContent = zephyrTestCycleKey;
        }
        if (zephyrKeyRow && zephyrTestCycleKey) {
            zephyrKeyRow.classList.remove('cycle-meta--hidden');
        }
        app.dataset.status = 'Sent';
        sendBtn.disabled = true;
        saveBtn.disabled = true;
        if (nameInput) {
            nameInput.disabled = true;
        }
    });

    function renderTable() {
        const rows = manualOrder
            ? [...tests]
            : [...tests].sort((left, right) => compareEntries(left, right, sortField, sortDirection));
        tableBody.innerHTML = rows.map(entry => `
            <tr draggable="true" data-test-key="${escapeAttr(entry.key)}">
                <td class="col-drag">
                    <span class="drag-handle material-symbols-outlined" aria-hidden="true" title="Drag to reorder">menu</span>
                </td>
                <td class="col-key">${escapeHtml(entry.key)}</td>
                <td class="col-name" title="${escapeAttr(entry.name || '-')}">${escapeHtml(entry.name || '-')}</td>
                <td class="col-mono">${formatNumber(entry.aiSort)}</td>
                <td class="col-mono">${formatWeight(entry.weights, 'priority')}</td>
                <td class="col-mono">${formatWeight(entry.weights, 'status')}</td>
                <td class="col-mono">${formatWeight(entry.weights, 'labels')}</td>
                <td class="col-mono">${formatWeight(entry.weights, 'executionsCount')}</td>
                <td class="col-mono">${formatWeight(entry.weights, 'failuresCount')}</td>
                <td class="col-mono">${formatWeight(entry.weights, 'failedExecutionsRatio')}</td>
                <td class="col-mono">${formatWeight(entry.weights, 'lastExecutionStatus')}</td>
                <td class="col-mono">${formatWeight(entry.weights, 'executionTime')}</td>
            </tr>
        `).join('');
        bindDragAndDrop();
        updateSortHeaders();
    }

    function bindDragAndDrop() {
        tableBody.querySelectorAll('tr[data-test-key]').forEach(row => {
            row.addEventListener('dragstart', event => {
                if (app.dataset.status === 'Sent') {
                    event.preventDefault();
                    return;
                }
                dragSourceKey = row.dataset.testKey;
                row.classList.add('dragging');
                if (event.dataTransfer) {
                    event.dataTransfer.effectAllowed = 'move';
                    event.dataTransfer.setData('text/plain', dragSourceKey);
                }
            });

            row.addEventListener('dragend', () => {
                row.classList.remove('dragging');
                tableBody.querySelectorAll('tr.drag-over').forEach(target => target.classList.remove('drag-over'));
                dragSourceKey = null;
            });

            row.addEventListener('dragover', event => {
                if (app.dataset.status === 'Sent' || !dragSourceKey) {
                    return;
                }
                event.preventDefault();
                if (event.dataTransfer) {
                    event.dataTransfer.dropEffect = 'move';
                }
                row.classList.add('drag-over');
            });

            row.addEventListener('dragleave', () => {
                row.classList.remove('drag-over');
            });

            row.addEventListener('drop', event => {
                event.preventDefault();
                row.classList.remove('drag-over');
                const sourceKey = dragSourceKey || event.dataTransfer?.getData('text/plain');
                const targetKey = row.dataset.testKey;
                if (!sourceKey || !targetKey || sourceKey === targetKey) {
                    return;
                }
                reorderTests(sourceKey, targetKey);
            });
        });
    }

    function reorderTests(sourceKey, targetKey) {
        const sourceIndex = tests.findIndex(entry => entry.key === sourceKey);
        const targetIndex = tests.findIndex(entry => entry.key === targetKey);
        if (sourceIndex < 0 || targetIndex < 0) {
            return;
        }
        const [moved] = tests.splice(sourceIndex, 1);
        tests.splice(targetIndex, 0, moved);
        manualOrder = true;
        sortField = 'manual';
        sortDirection = 'ASC';
        renderTable();
        updateButtonsState();
    }

    function updateSortHeaders() {
        document.querySelectorAll('#suggested-cycle-table .sortable-col').forEach(header => {
            header.classList.remove('sort-active', 'sort-asc', 'sort-desc');
            const badge = header.querySelector('.sort-header__badge');
            if (badge) {
                badge.textContent = '';
            }

            if (manualOrder || header.dataset.sortField !== sortField) {
                return;
            }

            header.classList.add('sort-active', sortDirection === 'ASC' ? 'sort-asc' : 'sort-desc');
            const sortIcon = sortDirection === 'ASC' ? 'arrow_drop_up' : 'arrow_drop_down';
            if (badge) {
                badge.textContent = sortIcon;
            }
        });
    }

    function compareEntries(left, right, field, direction) {
        const leftValue = resolveSortValue(left, field);
        const rightValue = resolveSortValue(right, field);
        let result;
        if (typeof leftValue === 'number' && typeof rightValue === 'number') {
            result = leftValue - rightValue;
        } else {
            result = String(leftValue).localeCompare(String(rightValue));
        }
        return direction === 'ASC' ? result : -result;
    }

    function resolveSortValue(entry, field) {
        if (field === 'key') {
            return entry.key;
        }
        if (field === 'name') {
            return entry.name || '';
        }
        if (field === 'aiSort') {
            return entry.aiSort ?? 0;
        }
        return entry.weights?.[field] ?? 0;
    }

    function formatWeight(weights, key) {
        if (!weights || weights[key] == null) {
            return '-';
        }
        return formatNumber(weights[key]);
    }

    function formatNumber(value) {
        return Number(value).toFixed(4);
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;');
    }

    function escapeAttr(value) {
        return String(value)
            .replaceAll('&', '&amp;')
            .replaceAll('"', '&quot;')
            .replaceAll('<', '&lt;');
    }

    function normalizeDirection(direction) {
        return String(direction).toUpperCase() === 'ASC' ? 'ASC' : 'DESC';
    }

    function orderChanged() {
        const currentKeys = tests.map(entry => entry.key);
        if (currentKeys.length !== savedOrderKeys.length) {
            return true;
        }
        return currentKeys.some((key, index) => key !== savedOrderKeys[index]);
    }

    function isDirty() {
        const nameChanged = nameInput ? nameInput.value !== savedName : false;
        if (manualOrder) {
            return orderChanged() || nameChanged;
        }
        return sortField !== savedSortField || sortDirection !== savedSortDirection || nameChanged;
    }

    function updateButtonsState() {
        const sent = app.dataset.status === 'Sent';
        saveBtn.disabled = !isDirty() || sent;
        sendBtn.disabled = sent;
    }

    renderTable();
    updateButtonsState();

    document.addEventListener('triage:locale-changed', () => {
        if (window.TriageI18n) {
            window.TriageI18n.applyTranslations(app);
        }
        updateSortHeaders();
    });
});
