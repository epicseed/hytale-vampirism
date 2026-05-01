// abilities.js — abilities table with side editor + nested effect panel.
(() => {
  const pane = document.getElementById('pane-abilities');
  pane.insertAdjacentHTML('beforeend', `
    <div class="editor-split">
      <div class="editor-main">
        <div class="pane-inner">
          <div class="pane-toolbar">
            <h2>Abilities</h2>
            <button class="btn-primary" onclick="AbilitiesTab.add()">+ Add</button>
          </div>
          <table class="data-table" id="abilities-table">
            <thead><tr>
              <th>ID</th><th>Display Name</th><th>Cooldown</th><th>Blood</th>
              <th>Effects</th><th>Actions</th><th>Requirements</th><th>Ops</th>
            </tr></thead>
            <tbody id="abilities-tbody"></tbody>
          </table>
        </div>
      </div>
      <div class="editor-side" id="abilities-editor">
        <div class="editor-side-header">
          <h3>Ability Editor</h3>
          <button class="btn-secondary" type="button" onclick="AbilitiesTab.close()">Close</button>
        </div>
        <div class="editor-side-body">
          <div class="editor-empty" id="abilities-empty">Select an ability to edit or create a new one.</div>
          <div id="abilities-form" style="display:none">
            <div class="form-group"><label>ID</label><input id="ab-id" type="text" /></div>
            <div class="form-group"><label>Display Name</label><input id="ab-name" type="text" /></div>
            <div class="form-group"><label>Description</label><textarea id="ab-desc"></textarea></div>
            <div class="form-group"><label>Icon Path</label><input id="ab-icon" type="text" /></div>
            <div class="form-group"><label>Tags (comma-separated)</label><input id="ab-tags" type="text" /></div>
            <div class="form-group"><label>Cooldown (s)</label><input id="ab-cooldown" type="number" step="0.5" value="0" /></div>
            <div class="form-group"><label>Duration (s)</label><input id="ab-duration" type="number" step="0.5" value="0" /></div>
            <div class="form-group"><label>Blood Cost</label><input id="ab-blood" type="number" value="0" /></div>
            <div class="form-group"><label>Cast Time (s)</label><input id="ab-cast-time" type="number" step="0.1" value="0" /></div>
            <div class="form-group"><label>Charges</label><input id="ab-charges" type="number" value="0" /></div>
            <div class="form-group"><label>Channel Duration (s)</label><input id="ab-channel-duration" type="number" step="0.5" value="0" /></div>
            <div class="form-group">
              <label>Effects</label>
              <div id="ab-effects-list" class="editor-list"></div>
              <div class="editor-ref-row" style="margin-top:8px">
                <select id="ab-effects-select"></select>
                <button type="button" onclick="AbilitiesTab.addExistingEffect()">Add</button>
              </div>
              <button class="editor-add-btn" type="button" onclick="AbilitiesTab.newEffect()">+ New Effect</button>
            </div>
            <div class="form-group">
              <label>Requirements</label>
              <div id="ab-requirements-picker"></div>
            </div>
            <div class="form-group">
              <label>Targeting</label>
              <div id="ab-targeting-picker"></div>
            </div>
            <div class="form-group">
              <label>Actions</label>
              <div id="ab-actions-picker"></div>
            </div>
            <div class="editor-actions">
              <button class="btn-delete" type="button" onclick="AbilitiesTab.delCurrent()">Delete</button>
              <button class="btn-primary" type="button" onclick="AbilitiesTab.save()">Save</button>
            </div>
          </div>
        </div>
      </div>
      <div class="editor-side secondary hidden" id="abilities-effect-panel"></div>
      <div class="editor-side tertiary hidden" id="abilities-mod-panel"></div>
      <div class="editor-side tertiary hidden" id="abilities-ref-panel"></div>
    </div>
  `);

  let editIndex = null;
  let draftEffects = [];
  let editingEffectId = null;
  let nestedEffectModManager = null;
  let effectRequirementsPicker = null;
  let effectActionsPicker = null;
  let effectPanelLoadSeq = 0;
  let effectPanelLoading = false;

  function abilities() {
    return window.AppData.abilities;
  }

  function effects() {
    return window.AppData.effects;
  }

  const requirementsPicker = RefPicker.make(document.getElementById('ab-requirements-picker'), {
    sourceKey: 'requirements',
    multiple: true,
    placeholder: '— add requirement —',
    disableCreate: true,
  });

  const targetingPicker = RefPicker.make(document.getElementById('ab-targeting-picker'), {
    sourceKey: 'targetings',
    multiple: false,
    placeholder: '— select targeting —',
    disableCreate: true,
  });

  const actionsPicker = RefPicker.make(document.getElementById('ab-actions-picker'), {
    sourceKey: 'actions',
    multiple: true,
    placeholder: '— add action —',
    disableCreate: true,
  });

  function countSummary(value) {
    if (Array.isArray(value)) return value.length ? `${value.length}` : '—';
    if (value && typeof value === 'object') return Object.keys(value).length ? '1' : '—';
    return '—';
  }

  function renderTable() {
    const tbody = document.getElementById('abilities-tbody');
    tbody.innerHTML = '';
    abilities().forEach((ability, index) => {
      const tr = document.createElement('tr');
      tr.className = 'clickable-row' + (index === editIndex ? ' active-row' : '');
      const effectTags = (ability.effects || []).map((effectId) => `<span class="tag">${SideEditors.lookupLabel('effects', effectId)}</span>`).join('') || '—';
      tr.innerHTML = `
        <td><code>${ability.id}</code></td>
        <td>${ability.displayName || ''}</td>
        <td>${ability.cooldown ?? 0}s</td>
        <td>${ability.resourceCost ?? 0}</td>
        <td>${effectTags}</td>
        <td>${countSummary(ability.actions)}</td>
        <td>${countSummary(ability.requirements)}</td>
        <td class="actions">
          <button class="btn-edit" type="button" onclick="AbilitiesTab.edit(${index}); event.stopPropagation();">Edit</button>
          <button class="btn-delete" type="button" onclick="AbilitiesTab.del(${index}); event.stopPropagation();">Del</button>
        </td>
      `;
      tr.addEventListener('click', () => openEditor(index));
      tbody.appendChild(tr);
    });
  }

  function renderEffectsField() {
    const listEl = document.getElementById('ab-effects-list');
    listEl.innerHTML = '';
    draftEffects.forEach((effectId) => {
      const item = document.createElement('div');
      item.className = 'editor-list-item';
      item.innerHTML = `
        <span>${SideEditors.lookupLabel('effects', effectId)}</span>
        <span class="item-actions">
          <button class="editor-mini-btn edit" type="button">Edit</button>
          <button class="editor-mini-btn delete" type="button">Del</button>
        </span>
      `;
      item.querySelector('.edit').addEventListener('click', () => openEffectPanel(effectId));
      item.querySelector('.delete').addEventListener('click', () => removeEffect(effectId));
      listEl.appendChild(item);
    });

    const select = document.getElementById('ab-effects-select');
    select.innerHTML = '<option value="">— add effect —</option>';
    effects()
      .filter((effect) => !draftEffects.includes(effect.id))
      .forEach((effect) => {
        const option = document.createElement('option');
        option.value = effect.id;
        option.textContent = effect.displayName || effect.id;
        select.appendChild(option);
      });
  }

  function openEditor(index) {
    editIndex = index;
    const isNew = index === -1;
    const ability = isNew ? {} : abilities()[index];
    draftEffects = [...(ability.effects || [])];
    document.getElementById('abilities-empty').style.display = 'none';
    document.getElementById('abilities-form').style.display = '';
    document.getElementById('ab-id').value = ability.id || '';
    document.getElementById('ab-name').value = ability.displayName || '';
    document.getElementById('ab-desc').value = ability.description || '';
    document.getElementById('ab-icon').value = ability.iconPath || '';
    document.getElementById('ab-tags').value = SideEditors.tagsToText(ability.tags);
    document.getElementById('ab-cooldown').value = ability.cooldown ?? 0;
    document.getElementById('ab-duration').value = ability.duration ?? 0;
    document.getElementById('ab-blood').value = ability.resourceCost ?? 0;
    document.getElementById('ab-cast-time').value = ability.castTime ?? 0;
    document.getElementById('ab-charges').value = ability.charges ?? 0;
    document.getElementById('ab-channel-duration').value = ability.channelDuration ?? 0;
    requirementsPicker.setValue(SideEditors.extractRefList(ability.requirements, 'requirementId'));
    targetingPicker.setValue(SideEditors.extractSingleRef(ability.targeting, 'targetingId'));
    actionsPicker.setValue(SideEditors.extractRefList(ability.actions, 'actionId'));
    document.querySelector('#abilities-form .btn-delete').style.display = isNew ? 'none' : '';
    renderEffectsField();
    closeEffectPanel();
    renderTable();
  }

  function closeEditor() {
    editIndex = null;
    draftEffects = [];
    document.getElementById('abilities-empty').style.display = '';
    document.getElementById('abilities-form').style.display = 'none';
    closeEffectPanel();
    renderTable();
  }

  function addExistingEffect() {
    const value = document.getElementById('ab-effects-select').value;
    if (!value || draftEffects.includes(value)) return;
    draftEffects.push(value);
    renderEffectsField();
  }

  function removeEffect(effectId) {
    draftEffects = draftEffects.filter((entry) => entry !== effectId);
    renderEffectsField();
  }

  function mountEffectModifierManager() {
    const listEl = document.getElementById('ab-effect-mod-list');
    const panelEl = document.getElementById('abilities-mod-panel');
    if (!listEl || !panelEl) return null;
    return SideEditors.makeModifierManager({
      listEl,
      panelEl,
      nestedPanelEl: document.getElementById('abilities-ref-panel'),
      title: 'Effect Modifier',
    });
  }

  function setEffectPanelBusy(isBusy) {
    effectPanelLoading = isBusy;
    const saveBtn = document.querySelector('#abilities-effect-panel .btn-primary');
    if (saveBtn) saveBtn.disabled = isBusy;
  }

  async function openEffectPanel(effectId) {
    editingEffectId = effectId || null;
    const panel = document.getElementById('abilities-effect-panel');
    const effect = effectId ? effects().find((entry) => entry.id === effectId) || {} : {};
    panel.classList.remove('hidden');
    panel.innerHTML = `
      <div class="editor-side-header">
        <h3>${effectId ? `Edit Effect: ${effectId}` : 'New Effect'}</h3>
        <button class="btn-secondary" type="button" onclick="AbilitiesTab.closeEffectPanel()">Close</button>
      </div>
      <div class="editor-side-body">
        <div class="form-group"><label>ID</label><input id="ab-ef-id" type="text" /></div>
        <div class="form-group"><label>Display Name</label><input id="ab-ef-name" type="text" /></div>
        <div class="form-group"><label>Description</label><textarea id="ab-ef-desc"></textarea></div>
        <div class="form-group">
          <label>Effect Asset ID</label>
          <select id="ab-ef-effect-id"></select>
        </div>
        <div class="form-group"><label>Duration (s)</label><input id="ab-ef-duration" type="number" step="0.5" min="0" value="0" /></div>
        <div class="form-group"><label>Tags (comma-separated)</label><input id="ab-ef-tags" type="text" /></div>
        <div class="form-group">
          <label>Requirements</label>
          <div id="ab-ef-requirements-picker"></div>
        </div>
        <div class="form-group">
          <label>Actions</label>
          <div id="ab-ef-actions-picker"></div>
        </div>
        <div class="form-group">
          <label>Modifiers</label>
          <div id="ab-effect-mod-list"></div>
        </div>
        <div class="editor-actions">
          ${effectId ? '<button class="btn-delete" type="button" onclick="AbilitiesTab.deleteEffect()">Delete</button>' : ''}
          <button class="btn-primary" type="button" onclick="AbilitiesTab.saveEffect()">Save Effect</button>
        </div>
      </div>
    `;
    document.getElementById('ab-ef-id').value = effect.id || '';
    document.getElementById('ab-ef-name').value = effect.displayName || '';
    document.getElementById('ab-ef-desc').value = effect.description || '';
    const effectIdSelect = document.getElementById('ab-ef-effect-id');
    const effectAssetId = effect.effectId || '';
    const loadSeq = ++effectPanelLoadSeq;
    setEffectPanelBusy(true);
    await HytaleAssetStore.fillSelect(effectIdSelect, 'effects', effectAssetId, { nullable: true });
    if (loadSeq === effectPanelLoadSeq) {
      effectIdSelect.value = effectAssetId;
      setEffectPanelBusy(false);
    }
    document.getElementById('ab-ef-duration').value = effect.duration ?? 0;
    document.getElementById('ab-ef-tags').value = SideEditors.tagsToText(effect.tags);
    effectRequirementsPicker = RefPicker.make(document.getElementById('ab-ef-requirements-picker'), {
      sourceKey: 'requirements',
      multiple: true,
      placeholder: '— add requirement —',
      disableCreate: true,
    });
    effectActionsPicker = RefPicker.make(document.getElementById('ab-ef-actions-picker'), {
      sourceKey: 'actions',
      multiple: true,
      placeholder: '— add action —',
      disableCreate: true,
    });
    effectRequirementsPicker.setValue(SideEditors.extractRefList(effect.requirements, 'requirementId'));
    effectActionsPicker.setValue(SideEditors.extractRefList(effect.actions, 'actionId'));
    nestedEffectModManager = mountEffectModifierManager();
    nestedEffectModManager.setValue(effect.modifiers || []);
  }

  function closeEffectPanel() {
    editingEffectId = null;
    effectPanelLoadSeq += 1;
    setEffectPanelBusy(false);
    nestedEffectModManager?.closeEditor();
    SideEditors.closePanel(document.getElementById('abilities-effect-panel'));
    SideEditors.closePanel(document.getElementById('abilities-mod-panel'));
    SideEditors.closePanel(document.getElementById('abilities-ref-panel'));
  }

  function saveEffect() {
    if (effectPanelLoading) {
      alert('Wait for Effect Asset IDs to finish loading.');
      return;
    }
    const id = document.getElementById('ab-ef-id').value.trim();
    if (!id) { alert('Effect ID is required'); return; }

    const existingIndex = effects().findIndex((entry) => entry.id === editingEffectId);
    if (editingEffectId == null && effects().some((entry) => entry.id === id)) { alert('ID already exists'); return; }
    const selectedEffectId = document.getElementById('ab-ef-effect-id').value.trim();
    const previousEffectId = existingIndex >= 0 ? (effects()[existingIndex]?.effectId || '') : '';
    if (!selectedEffectId && !previousEffectId) { alert('Effect Asset ID is required'); return; }
    const previous = existingIndex >= 0 ? effects()[existingIndex] : {};
    const entry = {
      id,
      displayName: document.getElementById('ab-ef-name').value.trim() || id,
      description: document.getElementById('ab-ef-desc').value.trim(),
      effectId: selectedEffectId || previousEffectId,
      duration: parseFloat(document.getElementById('ab-ef-duration').value) || 0,
      tags: SideEditors.textToTags(document.getElementById('ab-ef-tags').value),
      requirements: SideEditors.mergeRefObjects(effectRequirementsPicker?.getValue() || [], previous?.requirements, 'requirementId'),
      modifiers: nestedEffectModManager ? nestedEffectModManager.getValue() : [],
      actions: SideEditors.mergeRefObjects(effectActionsPicker?.getValue() || [], previous?.actions, 'actionId'),
    };
    if (existingIndex === -1) {
      effects().push(entry);
      if (!draftEffects.includes(id)) draftEffects.push(id);
    } else {
      const previousId = effects()[existingIndex].id;
      effects()[existingIndex] = entry;
      if (previousId !== id) {
        window.AppData.abilities.forEach((ability) => {
          ability.effects = (ability.effects || []).map((effectId) => effectId === previousId ? id : effectId);
        });
        draftEffects = draftEffects.map((effectId) => effectId === previousId ? id : effectId);
      }
      if (!draftEffects.includes(id)) draftEffects.push(id);
    }
    App.markDirty();
    renderEffectsField();
    closeEffectPanel();
  }

  function deleteEffect() {
    if (!editingEffectId) return;
    if (!confirm(`Delete effect "${editingEffectId}"?`)) return;
    const index = effects().findIndex((entry) => entry.id === editingEffectId);
    if (index !== -1) effects().splice(index, 1);
    window.AppData.abilities.forEach((ability) => {
      ability.effects = (ability.effects || []).filter((effectId) => effectId !== editingEffectId);
    });
    draftEffects = draftEffects.filter((effectId) => effectId !== editingEffectId);
    App.markDirty();
    renderEffectsField();
    closeEffectPanel();
  }

  function save() {
    const id = document.getElementById('ab-id').value.trim();
    if (!id) { alert('ID is required'); return; }
    if (editIndex == null) return;
    if (editIndex === -1 && abilities().some((ability) => ability.id === id)) { alert('ID already exists'); return; }

    const targetingId = targetingPicker.getValue();
    const previous = editIndex >= 0 ? abilities()[editIndex] : {};
    const entry = {
      id,
      displayName: document.getElementById('ab-name').value.trim() || id,
      description: document.getElementById('ab-desc').value.trim(),
      iconPath: document.getElementById('ab-icon').value.trim(),
      tags: SideEditors.textToTags(document.getElementById('ab-tags').value),
      cooldown: parseFloat(document.getElementById('ab-cooldown').value) || 0,
      duration: parseFloat(document.getElementById('ab-duration').value) || 0,
      resourceCost: parseInt(document.getElementById('ab-blood').value, 10) || 0,
      castTime: parseFloat(document.getElementById('ab-cast-time').value) || 0,
      charges: parseInt(document.getElementById('ab-charges').value, 10) || 0,
      channelDuration: parseFloat(document.getElementById('ab-channel-duration').value) || 0,
      effects: [...draftEffects],
      requirements: SideEditors.mergeRefObjects(requirementsPicker.getValue(), previous?.requirements, 'requirementId'),
      targeting: SideEditors.mergeSingleRef(targetingId, previous?.targeting, 'targetingId'),
      actions: SideEditors.mergeRefObjects(actionsPicker.getValue(), previous?.actions, 'actionId'),
    };
    if (editIndex === -1) {
      abilities().push(entry);
      editIndex = abilities().length - 1;
    } else {
      abilities()[editIndex] = entry;
    }
    App.markDirty();
    renderTable();
  }

  function del(index) {
    const ability = abilities()[index];
    if (!confirm(`Delete ability "${ability.id}"?`)) return;
    abilities().splice(index, 1);
    window.AppData.tree.forEach((skill) => {
      if (skill.abilityId === ability.id) skill.abilityId = '';
    });
    App.markDirty();
    if (editIndex === index) closeEditor();
    else renderTable();
  }

  function delCurrent() {
    if (editIndex == null || editIndex < 0) return;
    del(editIndex);
  }

  App.onTabActivated('abilities', renderTable);
  window.AbilitiesTab = {
    add: () => openEditor(-1),
    edit: openEditor,
    save,
    del,
    delCurrent,
    addExistingEffect,
    newEffect: () => openEffectPanel(null),
    closeEffectPanel,
    saveEffect,
    deleteEffect,
    close: closeEditor,
  };
})();
