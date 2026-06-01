document.addEventListener('DOMContentLoaded', () => {
    initThemeSettings();
    initSystemThemeListener();
});

function initThemeSettings() {
    const form = document.getElementById('theme-settings');
    if (!form || !window.TriageTheme) {
        return;
    }

    const preference = window.TriageTheme.getPreference();
    const selected = form.querySelector(`input[name="theme"][value="${preference}"]`);
    if (selected) {
        selected.checked = true;
    }

    form.addEventListener('change', event => {
        const target = event.target;
        if (target.name !== 'theme') {
            return;
        }

        localStorage.setItem(window.TriageTheme.STORAGE_KEY, target.value);
        window.TriageTheme.applyTheme(target.value);
    });
}

function initSystemThemeListener() {
    if (!window.TriageTheme) {
        return;
    }

    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
        if (window.TriageTheme.getPreference() === 'system') {
            window.TriageTheme.applyTheme('system');
        }
    });
}
