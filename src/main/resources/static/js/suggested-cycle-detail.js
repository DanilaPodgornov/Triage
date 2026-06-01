document.addEventListener('DOMContentLoaded', () => {
    const app = document.getElementById('suggested-cycle-detail-app');
    const list = document.getElementById('test-case-list');
    const saveBtn = document.getElementById('save-order-btn');
    const sendBtn = document.getElementById('send-zephyr-btn');
    const statusEl = document.getElementById('cycle-status');

    if (!app || !list || !saveBtn || !sendBtn) {
        return;
    }

    const cycleKey = app.dataset.cycleKey;
    const project = app.dataset.project || 'B2C';
    let initialOrder = getCurrentOrder();
    let draggedItem = null;

    list.querySelectorAll('.sortable-item').forEach(item => {
        item.addEventListener('dragstart', () => {
            draggedItem = item;
            item.classList.add('dragging');
        });
        item.addEventListener('dragend', () => {
            item.classList.remove('dragging');
            draggedItem = null;
            updateButtonsState();
        });
    });

    list.addEventListener('dragover', event => {
        event.preventDefault();
        const afterElement = getDragAfterElement(list, event.clientY);
        if (!draggedItem) {
            return;
        }
        if (afterElement == null) {
            list.appendChild(draggedItem);
        } else {
            list.insertBefore(draggedItem, afterElement);
        }
    });

    saveBtn.addEventListener('click', async () => {
        const testCases = getCurrentOrder();
        const response = await fetch(`/api/suggestedcycles/${encodeURIComponent(cycleKey)}/save`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ testCases })
        });
        if (!response.ok) {
            alert('Failed to save order.');
            return;
        }
        window.location.href = `/suggestedcycles?project=${encodeURIComponent(project)}`;
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
        initialOrder = getCurrentOrder();
        updateButtonsState();
    });

    function getCurrentOrder() {
        return [...list.querySelectorAll('.sortable-item')].map(item => item.dataset.key);
    }

    function isDirty() {
        const current = getCurrentOrder();
        return current.length !== initialOrder.length
            || current.some((key, index) => key !== initialOrder[index]);
    }

    function updateButtonsState() {
        const dirty = isDirty();
        const sent = app.dataset.status === 'Sent';
        saveBtn.disabled = !dirty || sent;
        sendBtn.disabled = dirty || sent;
    }

    function getDragAfterElement(container, y) {
        const draggableElements = [...container.querySelectorAll('.sortable-item:not(.dragging)')];
        return draggableElements.reduce((closest, child) => {
            const box = child.getBoundingClientRect();
            const offset = y - box.top - box.height / 2;
            if (offset < 0 && offset > closest.offset) {
                return { offset, element: child };
            }
            return closest;
        }, { offset: Number.NEGATIVE_INFINITY, element: null }).element;
    }

    updateButtonsState();
});
