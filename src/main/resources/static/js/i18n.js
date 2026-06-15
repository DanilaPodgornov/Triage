const TRIAGE_MESSAGES = {
    en: {
        'nav.testCases': 'Test Cases',
        'nav.testCycles': 'Test Cycles',
        'nav.suggestedCycles': 'Suggested Test Cycles',
        'nav.settings': 'Settings',
        'filter.project': 'Project',
        'testcases.selectAll': 'Select All',
        'testcases.deselectAll': 'Deselect All',
        'testcases.prioritize': 'Prioritize',
        'testcases.prioritizing': 'Prioritizing...',
        'testcases.prioritizeFailedGeneric': 'Prioritization failed. Ensure Zephyr API token and Ollama are available.',
        'testcases.prioritizeFailedInvalidResponse': 'Prioritization failed: invalid server response. Check application logs.',
        'testcases.progress.title': 'Prioritizing test cases',
        'testcases.progress.tests': '{completed} / {total} tests analyzed by LLM',
        'testcases.progress.aiSort': 'Running AI sort across all tests…',
        'testcases.progress.elapsed': 'Elapsed: {time}',
        'testcases.selected': '{count} selected',
        'testcases.filters': 'Filters',
        'testcases.columns': 'Columns',
        'testcases.llm': 'Local LLM',
        'testcases.llmPrompt': 'Prompt',
        'testcases.llmReset': 'Reset default prompt',
        'testcases.llmDownload': 'Download prompt',
        'testcases.llmHint': 'Edits are stored in this browser only. Prioritize still uses the server default prompt.',
        'testcases.col.key': 'Key',
        'testcases.col.priority': 'Priority',
        'testcases.col.status': 'Status',
        'testcases.col.folder': 'Folder',
        'testcases.col.name': 'Name',
        'testcases.col.select': 'Select',
        'testcases.empty': 'No test cases found.',
        'testcases.pagination.previous': 'Previous',
        'testcases.pagination.next': 'Next',
        'testcases.pagination.info': 'Page {current} of {total} ({items} items)',
        'testcases.filter.allPriorities': 'All priorities',
        'testcases.filter.allFolders': 'All folders',
        'testcases.filter.allStatuses': 'All statuses',
        'testcases.filter.priority': 'Priority',
        'testcases.filter.folder': 'Folder',
        'testcases.filter.status': 'Status',
        'testcases.filter.sortBy': 'Sort by',
        'testcases.filter.order': 'Order',
        'testcases.sort.key': 'Key',
        'testcases.sort.priority': 'Priority',
        'testcases.sort.folder': 'Folder',
        'testcases.sort.status': 'Status',
        'testcases.sort.asc': 'Ascending',
        'testcases.sort.desc': 'Descending',
        'settings.appearance': 'Appearance',
        'settings.language': 'Language',
        'settings.theme.dark': 'Dark',
        'settings.theme.light': 'Light',
        'settings.theme.system': 'Match system',
        'settings.locale.en': 'English',
        'settings.locale.ru': 'Russian',
        'settings.locale.system': 'Match system',
        'settings.themeHint': 'Theme preference is stored in this browser only.',
        'settings.localeHint': 'Language preference is stored in this browser only.',
        'stub.message': 'This section is under development.',
        'suggested.col.key': 'Key',
        'suggested.col.name': 'Name',
        'suggested.col.status': 'Status',
        'suggested.empty': 'No suggested test cycles yet.',
        'suggested.detail.key': 'Key:',
        'suggested.detail.status': 'Status:',
        'suggested.save': 'Save',
        'suggested.send': 'Send to Zephyr',
        'suggested.col.aiSort': 'AI sort',
        'suggested.col.weightPriority': 'Priority',
        'suggested.col.weightStatus': 'Status',
        'suggested.col.weightLabels': 'Labels',
        'suggested.col.weightExecutions': 'Executions',
        'suggested.col.weightFailures': 'Failures',
        'suggested.col.weightFailedRatio': 'Fail rate',
        'suggested.col.weightLastStatus': 'Last status',
        'suggested.col.weightExecutionTime': 'Exec time',
        'suggested.back': 'Back to list'
    },
    ru: {
        'nav.testCases': 'Тест-кейсы',
        'nav.testCycles': 'Тест-циклы',
        'nav.suggestedCycles': 'Предложенные тест-циклы',
        'nav.settings': 'Настройки',
        'filter.project': 'Проект',
        'testcases.selectAll': 'Выбрать все',
        'testcases.deselectAll': 'Снять выбор',
        'testcases.prioritize': 'Приоритизировать',
        'testcases.prioritizing': 'Приоритизация…',
        'testcases.prioritizeFailedGeneric': 'Приоритизация не удалась. Проверьте токен Zephyr и доступность Ollama.',
        'testcases.prioritizeFailedInvalidResponse': 'Приоритизация не удалась: некорректный ответ сервера. Смотрите логи приложения.',
        'testcases.progress.title': 'Приоритизация тест-кейсов',
        'testcases.progress.tests': 'Проанализировано LLM: {completed} / {total}',
        'testcases.progress.aiSort': 'AI-сортировка по всем тестам…',
        'testcases.progress.elapsed': 'Прошло: {time}',
        'testcases.selected': 'Выбрано: {count}',
        'testcases.filters': 'Фильтры',
        'testcases.columns': 'Столбцы',
        'testcases.llm': 'Локальная LLM',
        'testcases.llmPrompt': 'Промпт',
        'testcases.llmReset': 'Сбросить промпт по умолчанию',
        'testcases.llmDownload': 'Скачать промпт',
        'testcases.llmHint': 'Изменения сохраняются только в этом браузере. Prioritize использует серверный промпт по умолчанию.',
        'testcases.col.key': 'Ключ',
        'testcases.col.priority': 'Приоритет',
        'testcases.col.status': 'Статус',
        'testcases.col.folder': 'Папка',
        'testcases.col.name': 'Название',
        'testcases.col.select': 'Выбор',
        'testcases.empty': 'Тест-кейсы не найдены.',
        'testcases.pagination.previous': 'Назад',
        'testcases.pagination.next': 'Вперёд',
        'testcases.pagination.info': 'Страница {current} из {total} ({items} элементов)',
        'testcases.filter.allPriorities': 'Все приоритеты',
        'testcases.filter.allFolders': 'Все папки',
        'testcases.filter.allStatuses': 'Все статусы',
        'testcases.filter.priority': 'Приоритет',
        'testcases.filter.folder': 'Папка',
        'testcases.filter.status': 'Статус',
        'testcases.filter.sortBy': 'Сортировка',
        'testcases.filter.order': 'Порядок',
        'testcases.sort.key': 'Ключ',
        'testcases.sort.priority': 'Приоритет',
        'testcases.sort.folder': 'Папка',
        'testcases.sort.status': 'Статус',
        'testcases.sort.asc': 'По возрастанию',
        'testcases.sort.desc': 'По убыванию',
        'settings.appearance': 'Оформление',
        'settings.language': 'Язык',
        'settings.theme.dark': 'Тёмный',
        'settings.theme.light': 'Светлый',
        'settings.theme.system': 'Как в системе',
        'settings.locale.en': 'Английский',
        'settings.locale.ru': 'Русский',
        'settings.locale.system': 'Как в системе',
        'settings.themeHint': 'Тема сохраняется только в этом браузере.',
        'settings.localeHint': 'Язык сохраняется только в этом браузере.',
        'stub.message': 'Раздел в разработке.',
        'suggested.col.key': 'Ключ',
        'suggested.col.name': 'Название',
        'suggested.col.status': 'Статус',
        'suggested.empty': 'Предложенных тест-циклов пока нет.',
        'suggested.detail.key': 'Ключ:',
        'suggested.detail.status': 'Статус:',
        'suggested.save': 'Сохранить',
        'suggested.send': 'Отправить в Zephyr',
        'suggested.col.aiSort': 'AI sort',
        'suggested.col.weightPriority': 'Priority',
        'suggested.col.weightStatus': 'Status',
        'suggested.col.weightLabels': 'Labels',
        'suggested.col.weightExecutions': 'Executions',
        'suggested.col.weightFailures': 'Failures',
        'suggested.col.weightFailedRatio': 'Fail rate',
        'suggested.col.weightLastStatus': 'Last status',
        'suggested.col.weightExecutionTime': 'Exec time',
        'suggested.back': 'К списку'
    }
};

function formatMessage(template, params) {
    return template.replace(/\{(\w+)\}/g, (_, key) => (params[key] != null ? String(params[key]) : ''));
}

function getLocale() {
    return document.documentElement.getAttribute('data-locale') || 'en';
}

function translate(key, params = {}) {
    const locale = getLocale();
    const messages = TRIAGE_MESSAGES[locale] || TRIAGE_MESSAGES.en;
    const template = messages[key] || TRIAGE_MESSAGES.en[key] || key;
    return formatMessage(template, params);
}

function applyTranslations(root = document) {
    root.querySelectorAll('[data-i18n]').forEach(element => {
        const key = element.getAttribute('data-i18n');
        if (!key) {
            return;
        }
        const params = {};
        Array.from(element.attributes).forEach(attr => {
            if (attr.name.startsWith('data-i18n-') && attr.name !== 'data-i18n') {
                params[attr.name.slice('data-i18n-'.length)] = attr.value;
            }
        });
        element.textContent = translate(key, params);
    });

    root.querySelectorAll('[data-i18n-placeholder]').forEach(element => {
        const key = element.getAttribute('data-i18n-placeholder');
        if (key) {
            element.placeholder = translate(key);
        }
    });

    document.dispatchEvent(new CustomEvent('triage:i18n-applied'));
}

document.addEventListener('DOMContentLoaded', () => {
    applyTranslations();
    initLocaleSettings();
});

function initLocaleSettings() {
    const form = document.getElementById('locale-settings');
    if (!form || !window.TriageLocale) {
        return;
    }

    const preference = window.TriageLocale.getPreference();
    const selected = form.querySelector(`input[name="locale"][value="${preference}"]`);
    if (selected) {
        selected.checked = true;
    }

    form.addEventListener('change', event => {
        const target = event.target;
        if (target.name !== 'locale') {
            return;
        }
        localStorage.setItem(window.TriageLocale.STORAGE_KEY, target.value);
        window.TriageLocale.applyLocale(target.value);
        applyTranslations();
        document.dispatchEvent(new CustomEvent('triage:locale-changed'));
    });
}

window.TriageI18n = { translate, applyTranslations, getLocale };
