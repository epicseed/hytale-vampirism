// passives.js — passives table with side editor + nested modifier panel.
(() => {
  const pane = document.getElementById('pane-passives');
  pane.insertAdjacentHTML('beforeend', `
    <div class="editor-split">
      <div class="editor-main">
        <div class="pane-inner">
          <div class="pane-toolbar">
            <h2>Passives</h2>
            <button class="btn-primary" onclick="PassivesTab.add()">+ Add</button>
          </div>
          <table class="data-table" id="passives-table">
            <thead><tr>
              <th>ID</th><th>Display Name</th><th>Modifiers</th><th>Triggers</th><th>Actions</th><th>Requirements</th><th>Ops</th>
            </tr></thead>
            <tbody id="passives-tbody"></tbody>
          </table>
        </div>
      </div>
      <div class="editor-side" id="passives-editor">
        <div class="editor-side-header">
          <h3>Passive Editor</h3>
          <button class="btn-secondary" type="button" onclick="PassivesTab.close()">Close</button>
        </div>
        <div class="editor-side-body">
          <div class="editor-empty" id="passives-empty">Select a passive to edit or create a new one.</div>
          <div id="passives-form" style="display:none">
            <div class="form-group"><label>ID</label><input id="pa-id" type="text" /></div>
            <div class="form-group"><label>Display Name</label><input id="pa-name" type="text" /></div>
            <div class="form-group"><label>Description</label><textarea id="pa-desc"></textarea></div>
            <div class="form-group"><label>Icon Path</label><input id="pa-icon" type="text" /></div>
            <div class="form-group"><label>Tags (comma-separated)</label><input id="pa-tags" type="text" /></div>
            <div class="form-group">
              <label>Requirements</label>
              <div id="pa-requirements-picker"></div>
            </div>
            <div class="form-group">
              <label>Triggers</label>
              <div id="pa-triggers-picker"></div>
            </div>
            <div class="form-group">
              <label>Actions</label>
              <div id="pa-actions-picker"></div>
            </div>
            <div class="form-group">
              <label>Modifiers</label>
              <div id="pa-mod-list"></div>
            </div>
            <div class="editor-actions">
              <button class="btn-delete" type="button" onclick="PassivesTab.delCurrent()">Delete</button>
              <button class="btn-primary" type="button" onclick="PassivesTab.save()">Save</button>
            </div>
          </div>
        </div>
      </div>
      <div class="editor-side secondary hidden" id="passives-mod-panel"></div>
      <div class="editor-side tertiary hidden" id="passives-ref-panel"></div>
    </div>
  `);

  let editIndex = null;
  const modManager = SideEditors.makeModifierManager({
    listEl: document.getElementById('pa-mod-list'),
    panelEl: document.getElementById('passives-mod-panel'),
    nestedPanelEl: document.getElementById('passives-ref-panel'),
    title: 'Passive Modifier',
  });

  function list() {
    return window.AppData.passives;
  }

  const requirementsPicker = RefPicker.make(document.getElementById('pa-requirements-picker'), {
    sourceKey: 'requirements',
    multiple: true,
    placeholder: '— add requirement —',
    disableCreate: true,
  });

  const triggersPicker = RefPicker.make(document.getElementById('pa-triggers-picker'), {
    sourceKey: 'triggers',
    multiple: true,
    placeholder: '— add trigger —',
    disableCreate: true,
  });

  const actionsPicker = RefPicker.make(document.getElementById('pa-actions-picker'), {
    sourceKey: 'actions',
    multiple: true,
    placeholder: '— add action —',
    disableCreate: true,
  });

  function renderTable() {
    const tbody = document.getElementById('passives-tbody');
    tbody.innerHTML = '';
    list().forEach((passive, index) => {
      const tr = document.createElement('tr');
      tr.className = 'clickable-row' + (index === editIndex ? ' active-row' : '');
      const modifiers = (passive.modifiers || []).map((modifier) => {
        const stat = SideEditors.lookupLabel('stats', modifier.statId);
        const mod = SideEditors.lookupLabel('modifiers', modifier.modifierId);
        return `<span class="tag">${mod}: ${stat} ${modifier.operation || 'ADD'} ${modifier.value}</span>`;
      }).join('') || '—';
      tr.innerHTML = `
        <td><code>${passive.id}</code></td>
        <td>${passive.displayName || ''}</td>
        <td>${modifiers}</td>
        <td>${(passive.triggers || []).length || '—'}</td>
        <td>${(passive.actions || []).length || '—'}</td>
        <td>${(passive.requirements || []).length || '—'}</td>
        <td class="actions">
          <button class="btn-edit" type="button" onclick="PassivesTab.edit(${index}); event.stopPropagation();">Edit</button>
          <button class="btn-delete" type="button" onclick="PassivesTab.del(${index}); event.stopPropagation();">Del</button>
        </td>
      `;
      tr.addEventListener('click', () => openEditor(index));
      tbody.appendChild(tr);
    });
  }

  function openEditor(index) {
    editIndex = index;
    const isNew = index === -1;
    const passive = isNew ? {} : list()[index];
    document.getElementById('passives-empty').style.display = 'none';
    document.getElementById('passives-form').style.display = '';
    document.getElementById('pa-id').value = passive.id || '';
    document.getElementById('pa-name').value = passive.displayName || '';
    document.getElementById('pa-desc').value = passive.description || '';
    document.getElementById('pa-icon').value = passive.iconPath || '';
    document.getElementById('pa-tags').value = SideEditors.tagsToText(passive.tags);
    requirementsPicker.setValue(SideEditors.extractRefList(passive.requirements, 'requirementId'));
    triggersPicker.setValue(SideEditors.extractRefList(passive.triggers, 'triggerId'));
    actionsPicker.setValue(SideEditors.extractRefList(passive.actions, 'actionId'));
    document.querySelector('#passives-form .btn-delete').style.display = isNew ? 'none' : '';
    modManager.setValue(passive.modifiers || []);
    renderTable();
  }

  function closeEditor() {
    editIndex = null;
    document.getElementById('passives-empty').style.display = '';
    document.getElementById('passives-form').style.display = 'none';
    modManager.closeEditor();
    renderTable();
  }

  function save() {
    const id = document.getElementById('pa-id').value.trim();
    if (!id) { alert('ID is required'); return; }
    if (editIndex == null) return;
    if (editIndex === -1 && list().some((passive) => passive.id === id)) { alert('ID already exists'); return; }

    const previous = editIndex >= 0 ? list()[editIndex] : {};
    const entry = {
      id,
      displayName: document.getElementById('pa-name').value.trim() || id,
      description: document.getElementById('pa-desc').value.trim(),
      iconPath: document.getElementById('pa-icon').value.trim(),
      tags: SideEditors.textToTags(document.getElementById('pa-tags').value),
      requirements: SideEditors.mergeRefObjects(requirementsPicker.getValue(), previous?.requirements, 'requirementId'),
      modifiers: modManager.getValue(),
      triggers: SideEditors.mergeRefObjects(triggersPicker.getValue(), previous?.triggers, 'triggerId'),
      actions: SideEditors.mergeRefObjects(actionsPicker.getValue(), previous?.actions, 'actionId'),
    };
    if (editIndex === -1) {
      list().push(entry);
      editIndex = list().length - 1;
    } else {
      list()[editIndex] = entry;
    }
    App.markDirty();
    renderTable();
  }

  function del(index) {
    const passive = list()[index];
    if (!confirm(`Delete passive "${passive.id}"?`)) return;
    list().splice(index, 1);
    window.AppData.tree.forEach((skill) => {
      if (skill.passiveId === passive.id) skill.passiveId = '';
    });
    App.markDirty();
    if (editIndex === index) closeEditor();
    else renderTable();
  }

  function delCurrent() {
    if (editIndex == null || editIndex < 0) return;
    del(editIndex);
  }

  App.onTabActivated('passives', renderTable);
  window.PassivesTab = {
    add: () => openEditor(-1),
    edit: openEditor,
    save,
    del,
    delCurrent,
    close: closeEditor,
  };
})();
