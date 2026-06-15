document.addEventListener('DOMContentLoaded', () => {
    const modelSelect = document.getElementById('llm-model-select');
    const pullInput = document.getElementById('llm-pull-input');
    const pullBtn = document.getElementById('llm-pull-btn');
    const numCtxInput = document.getElementById('llm-num-ctx');
    const saveModal = document.getElementById('prompt-save-modal');
    const saveCancelBtn = document.getElementById('prompt-save-cancel-btn');
    const saveConfirmBtn = document.getElementById('prompt-save-confirm-btn');
    const pullModal = document.getElementById('llm-pull-modal');
    const pullSpinner = document.getElementById('llm-pull-spinner');
    const pullTitle = document.getElementById('llm-pull-title');
    const pullText = document.getElementById('llm-pull-text');
    const pullError = document.getElementById('llm-pull-error');
    const pullCloseBtn = document.getElementById('llm-pull-close-btn');

    if (!modelSelect) {
        return;
    }

    const editors = {
        weights: createEditorState('weights', 'weights-prompt', 'weights-version-select', 'weights-save-btn'),
        aisort: createEditorState('aisort', 'aisort-prompt', 'aisort-version-select', 'aisort-save-btn')
    };

    let activeModel = null;
    let pendingSaveType = null;
    let configSaveTimer = null;

    function t(key, params) {
        return window.TriageI18n ? window.TriageI18n.translate(key, params) : key;
    }

    const DEFAULT_PROMPT_VERSION = '0.1.0';

    function createEditorState(type, textareaId, versionSelectId, saveBtnId) {
        const textarea = document.getElementById(textareaId);
        const versionSelect = document.getElementById(versionSelectId);
        const saveBtn = document.getElementById(saveBtnId);
        const downloadBtn = document.querySelector(`.prompt-download-btn[data-prompt-type="${type}"]`);
        return {
            type,
            textarea,
            versionSelect,
            saveBtn,
            downloadBtn,
            loadedContent: '',
            activeVersion: '0.1.0',
            promptAvailable: false
        };
    }

    function setBusy(button, busy, busyKey) {
        if (!button || button.tagName === 'SELECT') {
            return;
        }
        if (busy) {
            button.dataset.originalText = button.textContent;
            button.disabled = true;
            button.textContent = t(busyKey || 'testcases.pulling');
        } else {
            button.disabled = false;
            if (button.dataset.originalText) {
                button.textContent = button.dataset.originalText;
            }
        }
    }

    function setModelSelectBusy(busy) {
        modelSelect.disabled = busy;
    }

    async function fetchJson(url, options) {
        const response = await fetch(url, options);
        const text = await response.text();
        let body;
        try {
            body = text ? JSON.parse(text) : {};
        } catch {
            throw new Error(text || t('testcases.prioritizeFailedInvalidResponse'));
        }
        if (!response.ok) {
            throw new Error(body.message || body.error || text || t('testcases.prioritizeFailedGeneric'));
        }
        return body;
    }

    function populateModelSelect(models, selectedModel) {
        const previousValue = selectedModel || activeModel || modelSelect.value || '';
        modelSelect.innerHTML = '';
        const placeholder = document.createElement('option');
        placeholder.value = '';
        placeholder.textContent = t('testcases.chooseLlmPlaceholder');
        modelSelect.appendChild(placeholder);
        (models || []).forEach(model => {
            const option = document.createElement('option');
            option.value = model.name;
            option.textContent = model.name;
            modelSelect.appendChild(option);
        });
        activeModel = selectedModel || null;
        modelSelect.value = activeModel || '';
        if (modelSelect.value !== (activeModel || '') && previousValue) {
            const stillExists = (models || []).some(model => model.name === previousValue);
            if (stillExists) {
                modelSelect.value = previousValue;
                activeModel = previousValue;
            }
        }
    }

    function fillVersionSelect(editor, versions, activeVersion) {
        editor.versionSelect.innerHTML = '';
        const resolvedVersions = versions && versions.length > 0 ? versions : [DEFAULT_PROMPT_VERSION];
        resolvedVersions.forEach(version => {
            const option = document.createElement('option');
            option.value = version;
            option.textContent = version;
            editor.versionSelect.appendChild(option);
        });
        editor.activeVersion = activeVersion
            || (versions && versions.length ? versions[versions.length - 1] : DEFAULT_PROMPT_VERSION);
        editor.versionSelect.value = editor.activeVersion;
    }

    function setEditorEnabled(editor, enabled) {
        editor.textarea.disabled = !enabled;
        if (!enabled) {
            editor.saveBtn.disabled = true;
            editor.versionSelect.disabled = true;
            if (editor.downloadBtn) {
                editor.downloadBtn.disabled = true;
            }
            return;
        }
        editor.versionSelect.disabled = !editor.promptAvailable;
        if (editor.downloadBtn) {
            editor.downloadBtn.disabled = !editor.promptAvailable;
        }
    }

    function applyLoadedPrompt(editor, data) {
        const available = data.available === true && Array.isArray(data.versions) && data.versions.length > 0;
        editor.promptAvailable = available;
        fillVersionSelect(editor, available ? data.versions : [], data.activeVersion || DEFAULT_PROMPT_VERSION);
        editor.loadedContent = available ? (data.content || '') : '';
        editor.textarea.value = editor.loadedContent;
        editor.textarea.classList.toggle('is-missing-prompt', !available);
        editor.textarea.placeholder = available ? '' : t('testcases.promptMissing');
        setEditorEnabled(editor, true);
        updateSaveState(editor);
    }

    function clearEditor(editor) {
        editor.promptAvailable = false;
        editor.loadedContent = '';
        editor.textarea.value = '';
        editor.textarea.classList.remove('is-missing-prompt');
        editor.textarea.placeholder = t('testcases.chooseLlmPlaceholder');
        fillVersionSelect(editor, [], DEFAULT_PROMPT_VERSION);
        setEditorEnabled(editor, false);
        updateSaveState(editor);
    }

    function updateSaveState(editor) {
        if (!activeModel) {
            editor.saveBtn.disabled = true;
            return;
        }
        const hasInput = editor.textarea.value.length > 0;
        const dirty = editor.promptAvailable && editor.textarea.value !== editor.loadedContent;
        const canCreate = !editor.promptAvailable && hasInput;
        editor.saveBtn.disabled = !(dirty || canCreate);
    }

    function showPullModalLoading(model) {
        if (!pullModal) {
            return;
        }
        pullModal.hidden = false;
        pullModal.setAttribute('aria-busy', 'true');
        if (pullSpinner) {
            pullSpinner.hidden = false;
        }
        if (pullTitle) {
            pullTitle.textContent = t('testcases.pullModal.title');
        }
        if (pullText) {
            pullText.textContent = `${t('testcases.pullModal.progress', { model })}\n${t('testcases.pullModal.wait')}`;
        }
        if (pullError) {
            pullError.hidden = true;
            pullError.textContent = '';
        }
        if (pullCloseBtn) {
            pullCloseBtn.hidden = true;
        }
    }

    function showPullModalSuccess(model) {
        if (!pullModal) {
            return;
        }
        pullModal.setAttribute('aria-busy', 'false');
        if (pullSpinner) {
            pullSpinner.hidden = true;
        }
        if (pullText) {
            pullText.textContent = t('testcases.pullModal.success', { model });
        }
        window.setTimeout(() => {
            pullModal.hidden = true;
        }, 900);
    }

    function showPullModalError(errorMessage) {
        if (!pullModal) {
            return;
        }
        pullModal.hidden = false;
        pullModal.setAttribute('aria-busy', 'false');
        if (pullSpinner) {
            pullSpinner.hidden = true;
        }
        if (pullTitle) {
            pullTitle.textContent = t('testcases.pullModal.errorTitle');
        }
        if (pullText) {
            pullText.textContent = '';
        }
        if (pullError) {
            pullError.hidden = false;
            pullError.textContent = errorMessage || t('testcases.prioritizeFailedGeneric');
        }
        if (pullCloseBtn) {
            pullCloseBtn.hidden = false;
        }
    }

    function hidePullModal() {
        if (!pullModal) {
            return;
        }
        pullModal.hidden = true;
        pullModal.setAttribute('aria-busy', 'false');
    }

    async function loadModels() {
        const data = await fetchJson('/api/llm/models');
        populateModelSelect(data.models, data.activeModel);
        if (numCtxInput && data.numCtx) {
            numCtxInput.value = String(data.numCtx);
        }
        return data;
    }

    async function loadPrompt(type) {
        const editor = editors[type];
        if (!activeModel) {
            clearEditor(editor);
            return;
        }
        const data = await fetchJson(`/api/llm/prompts/${type}`);
        applyLoadedPrompt(editor, data);
    }

    async function reloadPrompts() {
        await Promise.all([loadPrompt('weights'), loadPrompt('aisort')]);
    }

    async function setActiveModel(model) {
        setModelSelectBusy(true);
        try {
            const data = await fetchJson('/api/llm/models/active', {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ model: model || '' })
            });
            populateModelSelect(data.models, data.activeModel);
            activeModel = data.activeModel || null;
            await reloadPrompts();
        } finally {
            setModelSelectBusy(false);
        }
    }

    async function pullModel() {
        const model = pullInput?.value?.trim();
        if (!model) {
            return;
        }
        setBusy(pullBtn, true, 'testcases.pulling');
        showPullModalLoading(model);
        try {
            const data = await fetchJson('/api/llm/models/pull', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ model })
            });
            populateModelSelect(data.models, data.activeModel);
            activeModel = data.activeModel || model;
            if (pullInput) {
                pullInput.value = '';
            }
            await reloadPrompts();
            showPullModalSuccess(model);
        } catch (error) {
            console.error(error);
            showPullModalError(error.message || t('testcases.prioritizeFailedGeneric'));
        } finally {
            setBusy(pullBtn, false);
        }
    }

    async function saveRuntimeConfig() {
        const numCtx = Number.parseInt(numCtxInput?.value, 10);
        await fetchJson('/api/llm/config', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                numCtx: Number.isFinite(numCtx) ? numCtx : null
            })
        });
    }

    function scheduleConfigSave() {
        if (configSaveTimer != null) {
            window.clearTimeout(configSaveTimer);
        }
        configSaveTimer = window.setTimeout(() => {
            saveRuntimeConfig().catch(error => console.error(error));
        }, 400);
    }

    function openSaveModal(type) {
        pendingSaveType = type;
        if (saveModal) {
            saveModal.hidden = false;
        }
    }

    function closeSaveModal() {
        pendingSaveType = null;
        if (saveModal) {
            saveModal.hidden = true;
        }
    }

    async function confirmSave() {
        const type = pendingSaveType;
        closeSaveModal();
        if (!type || !activeModel) {
            return;
        }
        const editor = editors[type];
        setBusy(editor.saveBtn, true, 'testcases.pulling');
        try {
            const result = await fetchJson(`/api/llm/prompts/${type}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: editor.textarea.value })
            });
            await loadPrompt(type);
            if (result.version) {
                editor.versionSelect.value = result.version;
            }
        } catch (error) {
            console.error(error);
            alert(error.message || t('testcases.prioritizeFailedGeneric'));
        } finally {
            setBusy(editor.saveBtn, false);
            updateSaveState(editor);
        }
    }

    async function loadPromptVersion(type, version) {
        await fetchJson(`/api/llm/prompts/${type}/active`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ version })
        });
        await loadPrompt(type);
    }

    function downloadPrompt(type) {
        const editor = editors[type];
        const modelPart = (activeModel || 'model').replace(/[:/]/g, '-');
        const blob = new Blob([editor.textarea.value], { type: 'text/markdown;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${modelPart}_${type}-${editor.versionSelect.value || editor.activeVersion}.md`;
        link.click();
        URL.revokeObjectURL(url);
    }

    modelSelect.addEventListener('change', () => {
        const model = modelSelect.value;
        setActiveModel(model || null).catch(error => {
            console.error(error);
            alert(error.message || t('testcases.prioritizeFailedGeneric'));
            modelSelect.value = activeModel || '';
        });
    });

    if (pullBtn) {
        pullBtn.addEventListener('click', () => {
            pullModel().catch(error => {
                console.error(error);
                showPullModalError(error.message || t('testcases.prioritizeFailedGeneric'));
            });
        });
    }

    if (pullCloseBtn) {
        pullCloseBtn.addEventListener('click', hidePullModal);
    }

    if (numCtxInput) {
        numCtxInput.addEventListener('change', scheduleConfigSave);
    }

    Object.values(editors).forEach(editor => {
        editor.textarea.addEventListener('input', () => updateSaveState(editor));
        editor.saveBtn.addEventListener('click', () => openSaveModal(editor.type));
        editor.versionSelect.addEventListener('change', () => {
            if (!editor.promptAvailable) {
                return;
            }
            loadPromptVersion(editor.type, editor.versionSelect.value).catch(error => {
                console.error(error);
                alert(error.message || t('testcases.prioritizeFailedGeneric'));
            });
        });
        if (editor.downloadBtn) {
            editor.downloadBtn.addEventListener('click', () => downloadPrompt(editor.type));
        }
    });

    if (saveCancelBtn) {
        saveCancelBtn.addEventListener('click', closeSaveModal);
    }
    if (saveConfirmBtn) {
        saveConfirmBtn.addEventListener('click', () => {
            confirmSave().catch(error => {
                console.error(error);
                alert(error.message || t('testcases.prioritizeFailedGeneric'));
            });
        });
    }

    loadModels()
        .then(data => {
            activeModel = data.activeModel || null;
            return reloadPrompts();
        })
        .catch(error => {
            console.error(error);
        });

    document.addEventListener('triage:locale-changed', () => {
        if (window.TriageI18n) {
            window.TriageI18n.applyTranslations(document);
        }
        Object.values(editors).forEach(editor => {
            if (!activeModel) {
                editor.textarea.placeholder = t('testcases.chooseLlmPlaceholder');
            } else if (!editor.promptAvailable) {
                editor.textarea.placeholder = t('testcases.promptMissing');
            }
        });
    });
});
