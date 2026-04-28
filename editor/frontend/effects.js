// effects.js — effects table with side editor + nested modifier panel.
(() => {
  const pane = document.getElementById('pane-effects');
  pane.insertAdjacentHTML('beforeend', `
    <div class="editor-split">
      <div class="editor-main">
        <div class="pane-inner">
          <div class="pane-toolbar">
            <h2>Effects</h2>
            <button class="btn-primary" onclick="EffectsTab.add()">+ Add</button>
          </div>
          <table class="data-table" id="effects-table">
            <thead><tr>
              <th>ID</th><th>Display Name</th><th>Effect Asset ID</th><th>Duration</th><th>Modifiers</th><th>Actions</th><th>Ops</th>
            </tr></thead>
            <tbody id="effects-tbody"></tbody>
          </table>
        </div>
      </div>
      <div class="editor-side" id="effects-editor">
        <div class="editor-side-header">
          <h3>Effect Editor</h3>
          <button class="btn-secondary" type="button" onclick="EffectsTab.close()">Close</button>
        </div>
        <div class="editor-side-body">
          <div class="editor-empty" id="effects-empty">Select an effect to edit or create a new one.</div>
            <div id="effects-form" style="display:none">
              <div class="form-group"><label>ID</label><input id="ef-id" type="text" /></div>
              <div class="form-group"><label>Display Name</label><input id="ef-name" type="text" /></div>
              <div class="form-group"><label>Description</label><textarea id="ef-desc"></textarea></div>
              <div class="form-group">
                <label>Effect Asset ID</label>
                <select id="ef-effect-id"></select>
              </div>
              <div class="form-group"><label>Duration (s)</label><input id="ef-duration" type="number" step="0.5" min="0" value="0" /></div>
              <div class="form-group"><label>Tags (comma-separated)</label><input id="ef-tags" type="text" /></div>
              <div class="form-group">
                <label>Requirements</label>
                <div id="ef-requirements-picker"></div>
              </div>
              <div class="form-group">
                <label>Actions</label>
                <div id="ef-actions-picker"></div>
              </div>
            <div class="form-group">
              <label>Modifiers</label>
              <div id="ef-mod-list"></div>
            </div>
            <div class="editor-actions">
              <button class="btn-delete" type="button" onclick="EffectsTab.delCurrent()">Delete</button>
              <button class="btn-primary" type="button" onclick="EffectsTab.save()">Save</button>
            </div>
          </div>
        </div>
      </div>
      <div class="editor-side secondary hidden" id="effects-mod-panel"></div>
      <div class="editor-side tertiary hidden" id="effects-ref-panel"></div>
    </div>
  `);

  let editIndex = null;
  let effectAssetLoadSeq = 0;
  let effectAssetLoading = false;
  const modManager = SideEditors.makeModifierManager({
    listEl: document.getElementById('ef-mod-list'),
    panelEl: document.getElementById('effects-mod-panel'),
    nestedPanelEl: document.getElementById('effects-ref-panel'),
    title: 'Effect Modifier',
  });

  function list() {
    return window.AppData.effects;
  }

  const requirementsPicker = RefPicker.make(document.getElementById('ef-requirements-picker'), {
    sourceKey: 'requirements',
    multiple: true,
    placeholder: '— add requirement —',
    disableCreate: true,
  });

  const actionsPicker = RefPicker.make(document.getElementById('ef-actions-picker'), {
    sourceKey: 'actions',
    multiple: true,
    placeholder: '— add action —',
    disableCreate: true,
  });

  function renderTable() {
    const tbody = document.getElementById('effects-tbody');
    tbody.innerHTML = '';
    list().forEach((effect, index) => {
      const tr = document.createElement('tr');
      tr.className = 'clickable-row' + (index === editIndex ? ' active-row' : '');
      const modifiers = (effect.modifiers || []).map((modifier) => {
        const stat = SideEditors.lookupLabel('stats', modifier.statId);
        const mod = SideEditors.lookupLabel('modifiers', modifier.modifierId);
        return `<span class="tag">${mod}: ${stat} ${modifier.operation || 'ADD'} ${modifier.value}</span>`;
      }).join('') || '—';
      tr.innerHTML = `
        <td><code>${effect.id}</code></td>
        <td>${effect.displayName || ''}</td>
        <td><code>${effect.effectId || ''}</code></td>
        <td>${effect.duration ?? 0}s</td>
        <td>${modifiers}</td>
        <td>${(effect.actions || []).length || '—'}</td>
        <td class="actions">
          <button class="btn-edit" type="button" onclick="EffectsTab.edit(${index}); event.stopPropagation();">Edit</button>
          <button class="btn-delete" type="button" onclick="EffectsTab.del(${index}); event.stopPropagation();">Del</button>
        </td>
      `;
      tr.addEventListener('click', () => openEditor(index));
      tbody.appendChild(tr);
    });
  }

  function setEditorBusy(isBusy) {
    effectAssetLoading = isBusy;
    const saveBtn = document.querySelector('#effects-form .btn-primary');
    if (saveBtn) saveBtn.disabled = isBusy;
  }

  async function openEditor(index) {
    editIndex = index;
    const isNew = index === -1;
    const effect = isNew ? {} : list()[index];
    document.getElementById('effects-empty').style.display = 'none';
    document.getElementById('effects-form').style.display = '';
    document.getElementById('ef-id').value = effect.id || '';
    document.getElementById('ef-name').value = effect.displayName || '';
    document.getElementById('ef-desc').value = effect.description || '';
    const effectIdSelect = document.getElementById('ef-effect-id');
    const effectAssetId = effect.effectId || '';
    const loadSeq = ++effectAssetLoadSeq;
    setEditorBusy(true);
    await HytaleAssetStore.fillSelect(effectIdSelect, 'effects', effectAssetId, { nullable: true });
    if (loadSeq === effectAssetLoadSeq) {
      effectIdSelect.value = effectAssetId;
      setEditorBusy(false);
    }
    document.getElementById('ef-duration').value = effect.duration ?? 0;
    document.getElementById('ef-tags').value = SideEditors.tagsToText(effect.tags);
    requirementsPicker.setValue(SideEditors.extractRefList(effect.requirements, 'requirementId'));
    actionsPicker.setValue(SideEditors.extractRefList(effect.actions, 'actionId'));
    document.querySelector('#effects-form .btn-delete').style.display = isNew ? 'none' : '';
    modManager.setValue(effect.modifiers || []);
    renderTable();
  }

  function closeEditor() {
    editIndex = null;
    effectAssetLoadSeq += 1;
    setEditorBusy(false);
    document.getElementById('effects-empty').style.display = '';
    document.getElementById('effects-form').style.display = 'none';
    modManager.closeEditor();
    renderTable();
  }

  function save() {
    if (effectAssetLoading) {
      alert('Wait for Effect Asset IDs to finish loading.');
      return;
    }
    const id = document.getElementById('ef-id').value.trim();
    if (!id) { alert('ID is required'); return; }
    if (editIndex == null) return;
    if (editIndex === -1 && list().some((effect) => effect.id === id)) { alert('ID already exists'); return; }

    const selectedEffectId = document.getElementById('ef-effect-id').value.trim();
    const previousEffectId = editIndex >= 0 ? (list()[editIndex]?.effectId || '') : '';
    if (!selectedEffectId && !previousEffectId) { alert('Effect Asset ID is required'); return; }
    const previous = editIndex >= 0 ? list()[editIndex] : {};
    const entry = {
      id,
      displayName: document.getElementById('ef-name').value.trim() || id,
      description: document.getElementById('ef-desc').value.trim(),
      effectId: selectedEffectId || previousEffectId,
      duration: parseFloat(document.getElementById('ef-duration').value) || 0,
      tags: SideEditors.textToTags(document.getElementById('ef-tags').value),
      requirements: SideEditors.mergeRefObjects(requirementsPicker.getValue(), previous?.requirements, 'requirementId'),
      modifiers: modManager.getValue(),
      actions: SideEditors.mergeRefObjects(actionsPicker.getValue(), previous?.actions, 'actionId'),
    };
    if (editIndex === -1) {
      list().push(entry);
      editIndex = list().length - 1;
    } else {
      const previousId = list()[editIndex].id;
      list()[editIndex] = entry;
      if (previousId !== id) {
        window.AppData.abilities.forEach((ability) => {
          ability.effects = (ability.effects || []).map((effectId) => effectId === previousId ? id : effectId);
        });
      }
    }
    App.markDirty();
    renderTable();
  }

  function del(index) {
    const effect = list()[index];
    if (!confirm(`Delete effect "${effect.id}"?`)) return;
    list().splice(index, 1);
    window.AppData.abilities.forEach((ability) => {
      ability.effects = (ability.effects || []).filter((effectId) => effectId !== effect.id);
    });
    App.markDirty();
    if (editIndex === index) closeEditor();
    else renderTable();
  }

  function delCurrent() {
    if (editIndex == null || editIndex < 0) return;
    del(editIndex);
  }

  App.onTabActivated('effects', renderTable);
  window.EffectsTab = {
    add: () => openEditor(-1),
    edit: openEditor,
    save,
    del,
    delCurrent,
    close: closeEditor,
  };
})();
