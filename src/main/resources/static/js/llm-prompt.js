document.addEventListener('DOMContentLoaded', () => {
    const promptField = document.getElementById('llm-prompt');
    const resetBtn = document.getElementById('llm-prompt-reset-btn');
    const downloadBtn = document.getElementById('llm-prompt-download-btn');

    if (!promptField) {
        return;
    }

    const STORAGE_KEY = 'triage-llm-prompt';
    const serverDefault = typeof window.__defaultLlmPrompt === 'string'
        ? window.__defaultLlmPrompt
        : promptField.value;

    function loadPrompt() {
        const stored = localStorage.getItem(STORAGE_KEY);
        return stored != null ? stored : serverDefault;
    }

    function savePrompt(value) {
        localStorage.setItem(STORAGE_KEY, value);
    }

    function applyPrompt(value) {
        promptField.value = value;
    }

    function resetToDefault() {
        localStorage.removeItem(STORAGE_KEY);
        applyPrompt(serverDefault);
    }

    function downloadPrompt() {
        const blob = new Blob([promptField.value], { type: 'text/plain;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'prioritization-prompt.txt';
        link.click();
        URL.revokeObjectURL(url);
    }

    applyPrompt(loadPrompt());

    promptField.addEventListener('input', () => {
        savePrompt(promptField.value);
    });

    if (resetBtn) {
        resetBtn.addEventListener('click', resetToDefault);
    }

    if (downloadBtn) {
        downloadBtn.addEventListener('click', downloadPrompt);
    }
});
