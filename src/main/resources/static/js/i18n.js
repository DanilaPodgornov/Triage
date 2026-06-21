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
        'testcases.progress.processing': 'Analyzing test {current} / {total} ({key})…',
        'testcases.progress.aiSort': 'Running AI sort across all tests…',
        'testcases.progress.elapsed': 'Elapsed: {time}',
        'testcases.progress.cancel': 'Cancel',
        'testcases.selected': '{count} selected',
        'setup.title': 'API credentials',
        'setup.intro': 'Enter Zephyr Scale and Jira credentials to connect Triage to your test management system.',
        'setup.zephyrToken': 'Zephyr Scale API for Jira',
        'setup.jiraUrl': 'Jira API URL',
        'setup.jiraEmail': 'Email',
        'setup.jiraToken': 'Jira API Token',
        'setup.remember': 'Remember credentials',
        'setup.rememberHint': 'Saved to secrets.yaml in the project directory.',
        'setup.submit': 'Continue',
        'setup.error.zephyrRequired': 'Zephyr Scale API token is required.',
        'setup.error.saveFailed': 'Failed to save credentials to secrets.yaml.',
        'testcases.applyFilters': 'Apply',
        'testcases.viewTests': 'View tests',
        'testcases.filters': 'Filters',
        'testcases.columns': 'Columns',
        'testcases.llm': 'Local LLM',
        'testcases.chooseLlm': 'LLM model',
        'testcases.chooseLlmPlaceholder': 'Choose LLM',
        'testcases.pullModel': 'Pull model',
        'testcases.browseOllama': 'Browse on Ollama',
        'testcases.weightsPrompt': 'Weights prompt',
        'testcases.aisortPrompt': 'AI sort prompt',
        'testcases.promptVersion': 'Version',
        'testcases.save': 'Save',
        'testcases.download': 'Download',
        'testcases.rowsPerPage': 'Rows per page',
        'testcases.numCtx': 'Context window size',
        'testcases.numCtxUnit': 'tokens',
        'testcases.saveWarning.title': 'Save prompt version?',
        'testcases.saveWarning.text': 'Changing the prompt may affect prioritization results. Save only if you are confident in your changes.',
        'testcases.cancel': 'Cancel',
        'testcases.ok': 'OK',
        'testcases.pulling': 'Working…',
        'testcases.pullModal.title': 'Downloading LLM model',
        'testcases.pullModal.progress': 'Pulling {model}…',
        'testcases.pullModal.wait': 'This may take several minutes depending on model size.',
        'testcases.pullModal.success': 'Model {model} is ready.',
        'testcases.pullModal.errorTitle': 'Failed to download model',
        'testcases.promptMissing': 'No prompts were found for this model. Please write one following the guide in the expandable section above.',
        'testcases.promptGuide.title': 'Prompt writing guide',
        'testcases.promptGuide.intro': 'Prompts are model-specific. Do not remove mandatory placeholders or change the JSON response schema.',
        'testcases.promptGuide.weightsTitle': 'Weights prompt',
        'testcases.promptGuide.weightsPlaceholder': 'Keep the {testCaseData} placeholder — the app injects test metadata there.',
        'testcases.promptGuide.weightsKeys': 'Response JSON must contain exactly eight keys: priority, status, labels, executionsCount, failuresCount, failedExecutionsRatio, lastExecutionStatus, executionTime.',
        'testcases.promptGuide.weightsRules': 'Each value is 0–1 and all eight must sum to exactly 1. Reply with JSON only.',
        'testcases.promptGuide.aisortTitle': 'AI sort prompt',
        'testcases.promptGuide.aisortPlaceholder': 'Keep the {testCasesJson} placeholder — the app injects the test list there.',
        'testcases.promptGuide.aisortSchema': 'Response JSON must include suggestedTestCycleName and a tests array with every input key exactly once.',
        'testcases.promptGuide.aisortRules': 'Each aiSort is 0–1; all values must sum to exactly 1. Tests with failed executions must rank higher.',
        'testcases.col.key': 'Key',
        'testcases.col.priority': 'Priority',
        'testcases.col.status': 'Status',
        'testcases.col.folder': 'Folder',
        'testcases.col.name': 'Name',
        'testcases.col.select': 'Select',
        'testcases.empty': 'No test cases found.',
        'testcases.emptyPrompt': 'Use filters above and click "View tests" to load test cases.',
        'testcases.pagination.previous': 'Previous',
        'testcases.pagination.next': 'Next',
        'testcases.pagination.info': 'Page {current} of {total} (items {from} - {to} of {items})',
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
        'suggested.detail.name': 'Name:',
        'suggested.detail.status': 'Status:',
        'suggested.detail.zephyrKey': 'Zephyr key:',
        'suggested.detail.artifactFolder': 'Artifact folder:',
        'suggested.save': 'Save',
        'suggested.downloadJson': 'Save JSON',
        'suggested.send': 'Send to Zephyr',
        'suggested.page.title': 'Suggested Test Cycles',
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
        'testcases.progress.processing': 'Анализ теста {current} / {total} ({key})…',
        'testcases.progress.aiSort': 'AI-сортировка по всем тестам…',
        'testcases.progress.elapsed': 'Прошло: {time}',
        'testcases.progress.cancel': 'Отмена',
        'testcases.selected': 'Выбрано: {count}',
        'setup.title': 'Учётные данные API',
        'setup.intro': 'Введите учётные данные Zephyr Scale и Jira для подключения Triage к системе управления тестами.',
        'setup.zephyrToken': 'Zephyr Scale API for Jira',
        'setup.jiraUrl': 'Jira API URL',
        'setup.jiraEmail': 'Email',
        'setup.jiraToken': 'Jira API Token',
        'setup.remember': 'Запомнить учётные данные',
        'setup.rememberHint': 'Сохраняется в secrets.yaml в каталоге проекта.',
        'setup.submit': 'Продолжить',
        'setup.error.zephyrRequired': 'Требуется токен Zephyr Scale API.',
        'setup.error.saveFailed': 'Не удалось сохранить учётные данные в secrets.yaml.',
        'testcases.applyFilters': 'Применить',
        'testcases.viewTests': 'Просмотреть тесты',
        'testcases.filters': 'Фильтры',
        'testcases.columns': 'Столбцы',
        'testcases.llm': 'Локальная LLM',
        'testcases.chooseLlm': 'Модель LLM',
        'testcases.chooseLlmPlaceholder': 'Выберите LLM',
        'testcases.pullModel': 'Скачать модель',
        'testcases.browseOllama': 'Каталог Ollama',
        'testcases.weightsPrompt': 'Промпт весов',
        'testcases.aisortPrompt': 'Промпт AI-сортировки',
        'testcases.promptVersion': 'Версия',
        'testcases.save': 'Сохранить',
        'testcases.download': 'Скачать',
        'testcases.rowsPerPage': 'Строк на странице',
        'testcases.numCtx': 'Размер контекстного окна',
        'testcases.numCtxUnit': 'токенов',
        'testcases.saveWarning.title': 'Сохранить версию промпта?',
        'testcases.saveWarning.text': 'Изменение промпта может повлиять на результаты приоритизации. Сохраняйте только если уверены в своих действиях.',
        'testcases.cancel': 'Отменить',
        'testcases.ok': 'ОК',
        'testcases.pulling': 'Выполняется…',
        'testcases.pullModal.title': 'Загрузка модели LLM',
        'testcases.pullModal.progress': 'Скачивание {model}…',
        'testcases.pullModal.wait': 'Это может занять несколько минут в зависимости от размера модели.',
        'testcases.pullModal.success': 'Модель {model} готова.',
        'testcases.pullModal.errorTitle': 'Не удалось скачать модель',
        'testcases.promptMissing': 'Не обнаружено ни одного промпта для данной модели, пожалуйста, напишите его, следуя рекомендациям в выпадающем меню выше.',
        'testcases.promptGuide.title': 'Рекомендации по промптам',
        'testcases.promptGuide.intro': 'Промпты привязаны к модели. Не удаляйте обязательные плейсхолдеры и не меняйте схему JSON-ответа.',
        'testcases.promptGuide.weightsTitle': 'Промпт весов',
        'testcases.promptGuide.weightsPlaceholder': 'Сохраните плейсхолдер {testCaseData} — приложение подставляет туда метаданные теста.',
        'testcases.promptGuide.weightsKeys': 'JSON-ответ должен содержать ровно восемь ключей: priority, status, labels, executionsCount, failuresCount, failedExecutionsRatio, lastExecutionStatus, executionTime.',
        'testcases.promptGuide.weightsRules': 'Каждое значение от 0 до 1, сумма всех восьми равна 1. Отвечайте только JSON.',
        'testcases.promptGuide.aisortTitle': 'Промпт AI-сортировки',
        'testcases.promptGuide.aisortPlaceholder': 'Сохраните плейсхолдер {testCasesJson} — приложение подставляет туда список тестов.',
        'testcases.promptGuide.aisortSchema': 'JSON-ответ должен содержать suggestedTestCycleName и массив tests с каждым входным ключом ровно один раз.',
        'testcases.promptGuide.aisortRules': 'Каждый aiSort от 0 до 1, сумма равна 1. Тесты с проваленными запусками должны быть выше.',
        'testcases.col.key': 'Ключ',
        'testcases.col.priority': 'Приоритет',
        'testcases.col.status': 'Статус',
        'testcases.col.folder': 'Папка',
        'testcases.col.name': 'Название',
        'testcases.col.select': 'Выбор',
        'testcases.empty': 'Тест-кейсы не найдены.',
        'testcases.emptyPrompt': 'Задайте фильтры выше и нажмите «Просмотреть тесты», чтобы загрузить тест-кейсы.',
        'testcases.pagination.previous': 'Назад',
        'testcases.pagination.next': 'Вперёд',
        'testcases.pagination.info': 'Страница {current} из {total} (элементы {from} - {to} из {items})',
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
        'suggested.detail.name': 'Название:',
        'suggested.detail.status': 'Статус:',
        'suggested.detail.zephyrKey': 'Ключ Zephyr:',
        'suggested.detail.artifactFolder': 'Папка артефактов:',
        'suggested.save': 'Сохранить',
        'suggested.downloadJson': 'Сохранить JSON',
        'suggested.send': 'Отправить в Zephyr',
        'suggested.page.title': 'Предложенные тест-циклы',
        'suggested.col.aiSort': 'AI-сорт.',
        'suggested.col.weightPriority': 'Приоритет',
        'suggested.col.weightStatus': 'Статус',
        'suggested.col.weightLabels': 'Метки',
        'suggested.col.weightExecutions': 'Запуски',
        'suggested.col.weightFailures': 'Дефекты',
        'suggested.col.weightFailedRatio': '% провалов',
        'suggested.col.weightLastStatus': 'Посл. статус',
        'suggested.col.weightExecutionTime': 't вып-я',
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
    const template = messages[key] || TRIAGE_MESSAGES.en[key];
    if (template == null) {
        return null;
    }
    return formatMessage(template, params);
}

function resolveTranslation(element, key, params = {}) {
    const translated = translate(key, params);
    if (translated != null) {
        return translated;
    }
    const fallback = element.dataset.i18nFallback;
    if (fallback) {
        return fallback;
    }
    const existingText = element.textContent?.trim();
    if (existingText && !existingText.includes('.')) {
        return existingText;
    }
    return TRIAGE_MESSAGES.en[key] || existingText || '';
}

function applyTranslations(root = document) {
    root.querySelectorAll('[data-i18n]').forEach(element => {
        const key = element.getAttribute('data-i18n');
        if (!key) {
            return;
        }
        if (!element.dataset.i18nFallback && element.textContent?.trim()) {
            element.dataset.i18nFallback = element.textContent.trim();
        }
        const params = {};
        Array.from(element.attributes).forEach(attr => {
            if (attr.name.startsWith('data-i18n-') && attr.name !== 'data-i18n' && attr.name !== 'data-i18n-fallback') {
                params[attr.name.slice('data-i18n-'.length)] = attr.value;
            }
        });
        element.textContent = resolveTranslation(element, key, params);
    });

    root.querySelectorAll('[data-i18n-placeholder]').forEach(element => {
        const key = element.getAttribute('data-i18n-placeholder');
        if (!key) {
            return;
        }
        const translated = translate(key);
        if (translated != null) {
            element.placeholder = translated;
        }
    });

    root.querySelectorAll('[data-i18n-title]').forEach(element => {
        const key = element.getAttribute('data-i18n-title');
        if (!key) {
            return;
        }
        const translated = resolveTranslation(element, key);
        if (translated) {
            element.title = translated;
            if (element.hasAttribute('aria-label')) {
                element.setAttribute('aria-label', translated);
            }
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
