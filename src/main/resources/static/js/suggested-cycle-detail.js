document.addEventListener('DOMContentLoaded', () => {
    const app = document.getElementById('suggested-cycle-detail-app');
    const tableBody = document.getElementById('suggested-cycle-table-body');
    const saveBtn = document.getElementById('save-sort-btn');
    const sendBtn = document.getElementById('send-zephyr-btn');
    const statusEl = document.getElementById('cycle-status');
    const testsJsonEl = document.getElementById('suggested-cycle-tests-json');

    if (!app || !tableBody || !saveBtn || !sendBtn || !testsJsonEl) {
        return;
    }

    const cycleKey = app.dataset.cycleKey;
    const project = app.dataset.project || 'B2C';
    let sortField = app.dataset.sortField || 'aiSort';
    let sortDirection = app.dataset.sortDirection || 'DESC';
    const initialSortField = sortField;
    const initialSortDirection = sortDirection;

    let tests = [];
    try {
        tests = JSON.parse(testsJsonEl.textContent || '[]');
    } catch (error) {
        console.error('Failed to parse suggested cycle tests JSON.', error);
        return;
    }

    document.querySelectorAll('#suggested-cycle-table .sortable-col').forEach(header => {
        header.addEventListener('click', () => {
            const field = header.dataset.sortField;
            if (!field) {
                return;
            }
            if (sortField === field) {
                sortDirection = sortDirection === 'ASC' ? 'DESC' : 'ASC';
            } else {
                sortField = field;
                sortDirection = field === 'key' || field === 'name' || field === 'lastExecutionStatus' ? 'ASC' : 'DESC';
            }
            renderTable();
            updateButtonsState();
        });
    });

    saveBtn.addEventListener('click', async () => {
        const response = await fetch(`/api/suggestedcycles/${encodeURIComponent(cycleKey)}/save`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sortField, sortDirection })
        });
        if (!response.ok) {
            alert('Failed to save sort state.');
            return;
        }
        app.dataset.sortField = sortField;
        app.dataset.sortDirection = sortDirection;
        updateButtonsState();
    });

    sendBtn.addEventListener('click', async () => {
        const response = await fetch(`/api/suggestedcycles/${encodeURIComponent(cycleKey)}/send`, {
            method: 'POST'
        });
        if (!response.ok) {
            alert('Failed to send test cycle.');
            return;
        }
        if (statusEl) {
            statusEl.textContent = 'Sent';
        }
        app.dataset.status = 'Sent';
        sendBtn.disabled = true;
        saveBtn.disabled = true;
    });

    function renderTable() {
        const sorted = [...tests].sort((left, right) => compareEntries(left, right, sortField, sortDirection));
        tableBody.innerHTML = sorted.map(entry => `
            <tr>
                <td>${escapeHtml(entry.key)}</td>
                <td>${escapeHtml(entry.name || '-')}</td>
                <td>${formatWeight(entry.weights, 'priority')}</td>
                <td>${formatWeight(entry.weights, 'status')}</td>
                <td>${formatWeight(entry.weights, 'labels')}</td>
                <td>${formatWeight(entry.weights, 'executionsCount')}</td>
                <td>${formatWeight(entry.weights, 'failuresCount')}</td>
                <td>${formatWeight(entry.weights, 'lastExecutionStatus')}</td>
                <td>${formatWeight(entry.weights, 'executionTime')}</td>
                <td>${formatNumber(entry.aiSort)}</td>
            </tr>
        `).join('');
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
        if (field === 'lastExecutionStatus') {
            return entry.metrics?.lastExecutionStatus || '';
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

    function isDirty() {
        return sortField !== initialSortField || sortDirection !== initialSortDirection;
    }

    function updateButtonsState() {
        const sent = app.dataset.status === 'Sent';
        saveBtn.disabled = !isDirty() || sent;
        sendBtn.disabled = sent;
    }

    renderTable();
    updateButtonsState();
});
