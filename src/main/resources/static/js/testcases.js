document.addEventListener('DOMContentLoaded', () => {
    const app = document.getElementById('testcases-app');
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

    prioritizeBtn.addEventListener('click', async () => {
        const project = app?.dataset.project || 'B2C';
        const testCaseKeys = [...document.querySelectorAll('.testcase-checkbox:checked')]
            .map(checkbox => checkbox.value);

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
            window.location.href = result.redirectUrl;
        } catch (error) {
            console.error(error);
            alert('Prioritization failed. Ensure Zephyr API token and Ollama are available.');
            prioritizeBtn.disabled = false;
            prioritizeBtn.textContent = originalText;
            updatePrioritizeButton();
        }
    });
});
