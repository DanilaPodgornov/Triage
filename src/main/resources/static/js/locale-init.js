(function () {
    const STORAGE_KEY = 'triage-locale-preference';
    const root = document.documentElement;
    const serverDefault = root.getAttribute('data-default-locale') || 'en';

    function resolveLocale(preference) {
        if (preference === 'ru' || preference === 'en') {
            return preference;
        }
        const browserLang = (navigator.language || navigator.userLanguage || serverDefault).toLowerCase();
        return browserLang.startsWith('ru') ? 'ru' : 'en';
    }

    function getPreference() {
        const stored = localStorage.getItem(STORAGE_KEY);
        return stored || 'system';
    }

    function applyLocale(preference) {
        const locale = resolveLocale(preference);
        root.setAttribute('data-locale-preference', preference);
        root.setAttribute('data-locale', locale);
        root.lang = locale;
        return locale;
    }

    applyLocale(getPreference());

    window.TriageLocale = {
        STORAGE_KEY,
        serverDefault,
        getPreference,
        resolveLocale,
        applyLocale
    };
})();
