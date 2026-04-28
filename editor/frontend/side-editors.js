// Shared side-panel helpers for entity editors and nested modifier/reference editors.
const SideEditors = (() => {
  let cssInjected = false;

  function ensureCss() {
    if (cssInjected) return;
    cssInjected = true;
    const style = document.createElement('style');
    style.textContent = `
      .editor-split {
        display:flex;
        flex:1;
        min-height:0;
        overflow:hidden;
      }
      .editor-main {
        flex:1;
        min-width:0;
        overflow:auto;
        order:4;
      }
      .editor-side {
        width:360px;
        min-width:360px;
        background:var(--surface);
        border-right:1px solid var(--border);
        display:flex;
        flex-direction:column;
        min-height:0;
        order:1;
      }
      .editor-side.secondary {
        width:340px;
        min-width:340px;
        background:#13233d;
        order:2;
      }
      .editor-side.tertiary {
        width:320px;
        min-width:320px;
        background:#0f1c32;
        order:3;
      }
      .editor-side.hidden {
        display:none;
      }
      .editor-side-header {
        display:flex;
        align-items:center;
        gap:8px;
        padding:14px 16px;
        border-bottom:1px solid var(--border);
      }
      .editor-side-header h3 {
        flex:1;
        font-size:13px;
        color:var(--accent);
        text-transform:uppercase;
        letter-spacing:.5px;
      }
      .editor-side-header button {
        cursor:pointer;
        border:none;
        border-radius:5px;
        padding:5px 10px;
        font-size:11px;
        font-weight:600;
      }
      .editor-side-body {
        flex:1;
        overflow:auto;
        padding:16px;
      }
      .editor-empty {
        color:var(--text-dim);
        font-size:13px;
        line-height:1.6;
        padding:4px;
      }
      .editor-actions {
        display:flex;
        gap:8px;
        margin-top:16px;
      }
      .editor-actions button {
        cursor:pointer;
        border:none;
        border-radius:5px;
        padding:7px 14px;
        font-size:13px;
        font-weight:600;
      }
      .editor-list {
        display:flex;
        flex-direction:column;
        gap:6px;
      }
      .editor-list-item {
        display:flex;
        align-items:center;
        justify-content:space-between;
        gap:8px;
        background:#0f1e35;
        border:1px solid #1b3356;
        border-radius:6px;
        padding:7px 9px;
        font-size:12px;
      }
      .editor-list-item > span {
        flex:1;
        min-width:0;
      }
      .editor-list-item .item-actions {
        display:flex;
        gap:4px;
        flex-shrink:0;
      }
      .editor-mini-btn {
        cursor:pointer;
        border:none;
        border-radius:4px;
        padding:3px 7px;
        font-size:11px;
        font-weight:700;
      }
      .editor-mini-btn.edit {
        background:var(--border);
        color:var(--text-dim);
      }
      .editor-mini-btn.delete {
        background:#3a1020;
        color:#e94560;
      }
      .editor-add-btn {
        margin-top:8px;
        cursor:pointer;
        border:none;
        border-radius:5px;
        padding:6px 10px;
        font-size:12px;
        font-weight:600;
        background:var(--border);
        color:var(--text-dim);
      }
      .editor-ref-row {
        display:grid;
        grid-template-columns:1fr auto;
        gap:6px;
      }
      .editor-ref-row select,
      .editor-ref-row input,
      .editor-ref-row textarea {
        width:100%;
      }
      .editor-ref-row button {
        cursor:pointer;
        border:none;
        border-radius:5px;
        padding:0 10px;
        font-size:12px;
        font-weight:700;
        background:var(--border);
        color:var(--text-dim);
      }
      .editor-json {
        width:100%;
        min-height:110px;
        resize:vertical;
        background:var(--input-bg);
        border:1px solid var(--border);
        color:var(--text);
        padding:8px 10px;
        border-radius:5px;
        font-size:12px;
        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      }
      .editor-help {
        margin-top:4px;
        font-size:11px;
        color:var(--text-dim);
        line-height:1.4;
      }
      .data-table tbody tr.clickable-row {
        cursor:pointer;
      }
      .data-table tbody tr.clickable-row.active-row td {
        background:#203250;
      }
    `;
    document.head.appendChild(style);
  }

  function closePanel(panelEl, placeholder = '') {
    if (!panelEl) return;
    panelEl.classList.add('hidden');
    if (placeholder) panelEl.innerHTML = placeholder;
  }

  function lookupLabel(sourceKey, id) {
    if (!id) return '—';
    const item = (window.AppData[sourceKey] || []).find((entry) => entry.id === id);
    return item?.displayName || item?.effectId || id;
  }

  function stringifyJson(value, emptyValue = '') {
    if (value == null) return emptyValue;
    if (Array.isArray(value) && value.length === 0) return emptyValue;
    if (!Array.isArray(value) && typeof value === 'object' && Object.keys(value).length === 0) return emptyValue;
    return JSON.stringify(value, null, 2);
  }

  function parseJsonField(label, raw, fallback) {
    const text = (raw || '').trim();
    if (!text) return fallback;
    try {
      return JSON.parse(text);
    } catch (error) {
      alert(`${label} must be valid JSON.\n\n${error.message}`);
      throw error;
    }
  }

  function tagsToText(tags) {
    return Array.isArray(tags) ? tags.join(', ') : '';
  }

  function textToTags(text) {
    return String(text || '')
      .split(',')
      .map((entry) => entry.trim())
      .filter(Boolean);
  }

  function isPlainObject(value) {
    return value != null && typeof value === 'object' && !Array.isArray(value);
  }

  function cloneValue(value, fallback) {
    if (value == null) return fallback;
    return JSON.parse(JSON.stringify(value));
  }

  function extractRefList(entries, key) {
    return Array.isArray(entries)
      ? entries.map((entry) => entry?.[key]).filter(Boolean)
      : [];
  }

  function extractSingleRef(entry, key) {
    return isPlainObject(entry) ? String(entry[key] || '') : '';
  }

  function mergeRefObjects(ids, existingEntries, key) {
    const selectedIds = Array.isArray(ids)
      ? ids.map((id) => String(id || '').trim()).filter(Boolean)
      : [];
    const selectedSet = new Set(selectedIds);
    const emitted = new Set();
    const merged = [];

    (Array.isArray(existingEntries) ? existingEntries : []).forEach((entry) => {
      if (!isPlainObject(entry)) return;
      const refId = typeof entry[key] === 'string' ? entry[key].trim() : '';
      if (!refId) {
        merged.push(cloneValue(entry, {}));
        return;
      }
      if (!selectedSet.has(refId) || emitted.has(refId)) return;
      merged.push(cloneValue(entry, {}));
      emitted.add(refId);
    });

    selectedIds.forEach((id) => {
      if (emitted.has(id)) return;
      merged.push({ [key]: id });
      emitted.add(id);
    });

    return merged;
  }

  function mergeSingleRef(selectedId, existingEntry, key) {
    const nextId = String(selectedId || '').trim();
    if (nextId) {
      if (isPlainObject(existingEntry) && existingEntry[key] === nextId) {
        return cloneValue(existingEntry, {});
      }
      return { [key]: nextId };
    }
    if (isPlainObject(existingEntry) && !existingEntry[key]) {
      return cloneValue(existingEntry, {});
    }
    return {};
  }

  function renderSimpleRefEditor(panelEl, {
    title,
    sourceKey,
    onSave,
    initial = {},
    idLabel = 'ID',
    nameLabel = 'Display Name',
    descriptionLabel = 'Description',
  }) {
    ensureCss();
    panelEl.classList.remove('hidden');
    panelEl.innerHTML = `
      <div class="editor-side-header">
        <h3>${title}</h3>
        <button class="btn-secondary" type="button" data-action="close">Close</button>
      </div>
      <div class="editor-side-body">
        <div class="form-group"><label>${idLabel}</label><input id="se-ref-id" type="text" /></div>
        <div class="form-group"><label>${nameLabel}</label><input id="se-ref-name" type="text" /></div>
        <div class="form-group"><label>${descriptionLabel}</label><textarea id="se-ref-desc"></textarea></div>
        <div class="editor-actions">
          <button class="btn-secondary" type="button" data-action="cancel">Cancel</button>
          <button class="btn-primary" type="button" data-action="save">Save</button>
        </div>
      </div>
    `;

    panelEl.querySelector('#se-ref-id').value = initial.id || '';
    panelEl.querySelector('#se-ref-name').value = initial.displayName || '';
    panelEl.querySelector('#se-ref-desc').value = initial.description || '';

    const close = () => closePanel(panelEl);

    panelEl.querySelectorAll('[data-action="close"], [data-action="cancel"]').forEach((button) => {
      button.addEventListener('click', close);
    });

    panelEl.querySelector('[data-action="save"]').addEventListener('click', () => {
      const id = panelEl.querySelector('#se-ref-id').value.trim();
      if (!id) { alert('ID is required'); return; }
      const displayName = panelEl.querySelector('#se-ref-name').value.trim() || id;
      const description = panelEl.querySelector('#se-ref-desc').value.trim();
      const list = window.AppData[sourceKey];
      if (!Array.isArray(list)) { alert(`Unknown source: ${sourceKey}`); return; }
      if (list.some((entry) => entry.id === id)) { alert('ID already exists'); return; }
      const item = { id, displayName, description };
      list.push(item);
      App.markDirty();
      onSave?.(item);
      close();
    });
  }

  function fillSelect(select, sourceKey, currentValue, { nullable = false } = {}) {
    const previous = currentValue ?? select.value;
    select.innerHTML = '';
    if (nullable) {
      const option = document.createElement('option');
      option.value = '';
      option.textContent = '— none —';
      select.appendChild(option);
    }
    for (const item of (window.AppData[sourceKey] || [])) {
      const option = document.createElement('option');
      option.value = item.id;
      option.textContent = item.displayName || item.effectId || item.id;
      option.title = item.id;
      select.appendChild(option);
    }
    select.value = previous || '';
  }

  function makeModifierManager({ listEl, panelEl, nestedPanelEl, title = 'Modifier' }) {
    ensureCss();
    let rows = [];
    let editIndex = -1;

    function parseTargetSelection(target) {
      if (!target || typeof target !== 'object' || Array.isArray(target)) return { type: '', id: '', extraTarget: {} };
      const mappings = [
        ['ability', 'abilityId'],
        ['effect', 'effectId'],
        ['passive', 'passiveId'],
        ['skill', 'skillId'],
      ];
      for (const [type, idKey] of mappings) {
        if (target.type === type && target[idKey]) {
          return { type, id: String(target[idKey]), extraTarget: {} };
        }
      }
      return { type: '', id: '', extraTarget: { ...target } };
    }

    function formatTargetSummary(target) {
      const selection = parseTargetSelection(target);
      if (selection.type && selection.id) return ` · ${selection.type}:${selection.id}`;
      if (selection.type) return ` · ${selection.type}`;
      if (selection.extraTarget && Object.keys(selection.extraTarget).length) return ' · custom target';
      return '';
    }

    function buildTargetSelection(type, id) {
      if (!type || !id) return {};
      const idKey = `${type}Id`;
      return { type, [idKey]: id };
    }

    const emptyPlaceholder = `
      <div class="editor-side-header">
        <h3>${title}</h3>
        <button class="btn-secondary" type="button" data-action="close">Close</button>
      </div>
      <div class="editor-side-body">
        <div class="editor-empty">Select a modifier to edit or add a new one from the parent editor.</div>
      </div>
    `;

    function syncList() {
      listEl.innerHTML = '';
      const container = document.createElement('div');
      container.className = 'editor-list';
      rows.forEach((row, index) => {
        const item = document.createElement('div');
        item.className = 'editor-list-item';
        const conditionsSummary = Array.isArray(row.conditions) && row.conditions.length ? ` · ${row.conditions.length} cond` : '';
        const targetSummary = formatTargetSummary(row.target);
        item.innerHTML = `
          <span>${lookupLabel('modifiers', row.modifierId)} · ${lookupLabel('stats', row.statId)} ${row.operation || 'ADD'} ${row.value}${conditionsSummary}${targetSummary}</span>
          <span class="item-actions">
            <button class="editor-mini-btn edit" type="button">Edit</button>
            <button class="editor-mini-btn delete" type="button">Del</button>
          </span>
        `;
        item.querySelector('.edit').addEventListener('click', () => openEditor(index));
        item.querySelector('.delete').addEventListener('click', () => {
          rows.splice(index, 1);
          syncList();
        });
        container.appendChild(item);
      });
      listEl.appendChild(container);
      const addButton = document.createElement('button');
      addButton.type = 'button';
      addButton.className = 'editor-add-btn';
      addButton.textContent = '+ Add Modifier';
      addButton.addEventListener('click', () => openEditor(-1));
      listEl.appendChild(addButton);
    }

    function openRefCreator(sourceKey, targetSelect, titleText) {
      renderSimpleRefEditor(nestedPanelEl, {
        title: titleText,
        sourceKey,
        onSave: (item) => {
          fillSelect(targetSelect, sourceKey, item.id, { nullable: targetSelect.dataset.nullable === 'true' });
          targetSelect.value = item.id;
        },
      });
    }

    function openEditor(index) {
      editIndex = index;
      const row = index === -1 ? {
        modifierId: '',
        statId: '',
        operation: 'ADD',
        value: 0,
        priority: 100,
        conditions: [],
        target: {},
      } : {
        ...rows[index],
        conditions: Array.isArray(rows[index].conditions) ? rows[index].conditions : [],
        target: rows[index].target && typeof rows[index].target === 'object' ? rows[index].target : {},
      };

      panelEl.classList.remove('hidden');
      panelEl.innerHTML = `
        <div class="editor-side-header">
          <h3>${index === -1 ? `Add ${title}` : `Edit ${title}`}</h3>
          <button class="btn-secondary" type="button" data-action="close">Close</button>
        </div>
        <div class="editor-side-body">
          <div class="form-group">
            <label>Modifier Def</label>
            <div class="editor-ref-row">
              <select id="mod-id-select"></select>
              <button type="button" data-create="modifiers">+</button>
            </div>
          </div>
          <div class="form-group">
            <label>Stat</label>
            <div class="editor-ref-row">
              <select id="mod-stat-select"></select>
              <button type="button" data-create="stats">+</button>
            </div>
          </div>
          <div class="form-group">
            <label>Operation</label>
            <select id="mod-op-select">
              <option value="ADD">ADD</option>
              <option value="MULTIPLY">MULTIPLY</option>
              <option value="OVERRIDE">OVERRIDE</option>
            </select>
          </div>
          <div class="form-group"><label>Value</label><input id="mod-value-input" type="number" step="any" /></div>
          <div class="form-group"><label>Priority</label><input id="mod-priority-input" type="number" /></div>
          <div class="form-group">
            <label>Conditions</label>
            <div id="mod-conditions-picker"></div>
            <div class="editor-help">Selection-based refs. Any unsupported inline conditions already present are preserved automatically.</div>
          </div>
          <div class="form-group">
            <label>Target Scope</label>
            <select id="mod-target-type">
              <option value="">— none —</option>
              <option value="ability">Ability</option>
              <option value="effect">Effect</option>
              <option value="passive">Passive</option>
              <option value="skill">Skill</option>
            </select>
            <div class="editor-ref-row" style="margin-top:8px">
              <select id="mod-target-id"></select>
            </div>
            <div class="editor-help">Selection-based scope. Any unsupported advanced target object already present is preserved automatically.</div>
          </div>
          <div class="editor-actions">
            ${index === -1 ? '' : '<button class="btn-delete" type="button" data-action="delete">Delete</button>'}
            <button class="btn-secondary" type="button" data-action="cancel">Cancel</button>
            <button class="btn-primary" type="button" data-action="save">Save</button>
          </div>
        </div>
      `;

      const idSelect = panelEl.querySelector('#mod-id-select');
      const statSelect = panelEl.querySelector('#mod-stat-select');
      fillSelect(idSelect, 'modifiers', row.modifierId);
      fillSelect(statSelect, 'stats', row.statId);
      panelEl.querySelector('#mod-op-select').value = row.operation || 'ADD';
      panelEl.querySelector('#mod-value-input').value = row.value ?? 0;
      panelEl.querySelector('#mod-priority-input').value = row.priority ?? 100;
      const conditionRefs = extractRefList(row.conditions, 'conditionId');
      const extraConditions = Array.isArray(row.conditions) ? row.conditions.filter((entry) => !entry?.conditionId) : [];
      const targetSelection = parseTargetSelection(row.target);
      const targetTypeSelect = panelEl.querySelector('#mod-target-type');
      const targetIdSelect = panelEl.querySelector('#mod-target-id');
      const targetSourceByType = {
        ability: 'abilities',
        effect: 'effects',
        passive: 'passives',
        skill: 'tree',
      };
      const conditionsPicker = RefPicker.make(panelEl.querySelector('#mod-conditions-picker'), {
        sourceKey: 'conditions',
        multiple: true,
        placeholder: '— add condition —',
        disableCreate: true,
      });
      conditionsPicker.setValue(conditionRefs);
      const refreshTargetSelect = () => {
        const sourceKey = targetSourceByType[targetTypeSelect.value];
        if (!sourceKey) {
          targetIdSelect.innerHTML = '<option value="">— none —</option>';
          targetIdSelect.value = '';
          targetIdSelect.disabled = true;
          return;
        }
        targetIdSelect.disabled = false;
        fillSelect(targetIdSelect, sourceKey, targetIdSelect.dataset.currentValue || '', { nullable: true });
      };
      targetTypeSelect.value = targetSelection.type;
      targetIdSelect.dataset.currentValue = targetSelection.id;
      refreshTargetSelect();
      targetIdSelect.value = targetSelection.id || '';
      targetTypeSelect.addEventListener('change', () => {
        targetIdSelect.dataset.currentValue = '';
        refreshTargetSelect();
      });

      const close = () => closePanel(panelEl, emptyPlaceholder);
      panelEl.querySelector('[data-action="close"]').addEventListener('click', close);
      panelEl.querySelector('[data-action="cancel"]').addEventListener('click', close);

      panelEl.querySelector('[data-create="modifiers"]').addEventListener('click', () => openRefCreator('modifiers', idSelect, 'New Modifier Definition'));
      panelEl.querySelector('[data-create="stats"]').addEventListener('click', () => openRefCreator('stats', statSelect, 'New Stat'));

      panelEl.querySelector('[data-action="save"]').addEventListener('click', () => {
        if (!idSelect.value) { alert('Modifier definition is required'); return; }
        if (!statSelect.value) { alert('Stat is required'); return; }

        const next = {
          modifierId: idSelect.value,
          statId: statSelect.value,
          operation: panelEl.querySelector('#mod-op-select').value,
          value: parseFloat(panelEl.querySelector('#mod-value-input').value) || 0,
          priority: parseInt(panelEl.querySelector('#mod-priority-input').value, 10) || 100,
          conditions: [...mergeRefObjects(conditionsPicker.getValue(), row.conditions, 'conditionId').filter((entry) => entry?.conditionId), ...extraConditions],
        };
        const target = buildTargetSelection(targetTypeSelect.value, targetIdSelect.value);
        if (Object.keys(target).length) next.target = target;
        else if (Object.keys(targetSelection.extraTarget).length) next.target = targetSelection.extraTarget;

        if (editIndex === -1) rows.push(next);
        else rows[editIndex] = next;
        syncList();
        close();
      });

      const deleteBtn = panelEl.querySelector('[data-action="delete"]');
      if (deleteBtn) {
        deleteBtn.addEventListener('click', () => {
          rows.splice(editIndex, 1);
          syncList();
          close();
        });
      }
    }

    function setValue(value) {
      rows = (value || []).map((row) => ({
        ...row,
        conditions: Array.isArray(row.conditions) ? row.conditions : [],
        target: row.target && typeof row.target === 'object' && !Array.isArray(row.target) ? row.target : {},
      }));
      syncList();
      closePanel(panelEl, emptyPlaceholder);
      closePanel(nestedPanelEl);
    }

    function getValue() {
      return rows.map((row) => ({ ...row }));
    }

    function closeEditor() {
      closePanel(panelEl, emptyPlaceholder);
      closePanel(nestedPanelEl);
    }

    setValue([]);
    return { setValue, getValue, openEditor, closeEditor };
  }

  ensureCss();
  return {
    closePanel,
    renderSimpleRefEditor,
    fillSelect,
    lookupLabel,
    makeModifierManager,
    parseJsonField,
    stringifyJson,
    tagsToText,
    textToTags,
    extractRefList,
    extractSingleRef,
    mergeRefObjects,
    mergeSingleRef,
  };
})();
