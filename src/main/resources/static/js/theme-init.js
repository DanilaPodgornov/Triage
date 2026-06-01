(function () {
    const STORAGE_KEY = 'triage-theme-preference';
    const root = document.documentElement;
    const serverDefault = root.getAttribute('data-default-theme') || 'system';

    function resolveTheme(preference) {
        if (preference === 'dark' || preference === 'light') {
            return preference;
        }
        return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    }

    function applyTheme(preference) {
        root.setAttribute('data-theme-preference', preference);
        root.setAttribute('data-theme', resolveTheme(preference));
    }

    function getPreference() {
        const stored = localStorage.getItem(STORAGE_KEY);
        return stored || serverDefault;
    }

    applyTheme(getPreference());

    window.TriageTheme = {
        STORAGE_KEY,
        applyTheme,
        getPreference,
        getServerDefault: () => serverDefault
    };
})();
