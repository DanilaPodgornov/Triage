document.addEventListener('DOMContentLoaded', () => {
    const checkboxes = document.querySelectorAll('.testcase-checkbox');
    const prioritizeBtn = document.getElementById('prioritize-btn');

    if (!prioritizeBtn || checkboxes.length === 0) {
        return;
    }

    const updatePrioritizeButton = () => {
        const selectedCount = document.querySelectorAll('.testcase-checkbox:checked').length;
        prioritizeBtn.disabled = selectedCount < 2;
    };

    checkboxes.forEach(checkbox => checkbox.addEventListener('change', updatePrioritizeButton));
    updatePrioritizeButton();
});
