// skills.js — tree node table with side editor + nested modifier panel.
(() => {
  const pane = document.getElementById('pane-skills');
  pane.insertAdjacentHTML('beforeend', `
    <div class="editor-split">
      <div class="editor-main">
        <div class="pane-inner">
          <div class="pane-toolbar">
            <h2>Skills (Tree Nodes)</h2>
            <button class="btn-primary" onclick="SkillsTab.add()">+ Add</button>
          </div>
          <table class="data-table" id="skills-table">
            <thead><tr>
              <th>ID</th><th>Display Name</th><th>Type</th><th>Rarity</th>
              <th>Cost</th><th>Links</th><th>Requires</th><th>Modifiers</th><th>Triggers</th><th>Ops</th>
            </tr></thead>
            <tbody id="skills-tbody"></tbody>
          </table>
        </div>
      </div>
      <div class="editor-side" id="skills-editor">
        <div class="editor-side-header">
          <h3>Skill Editor</h3>
          <button class="btn-secondary" type="button" onclick="SkillsTab.close()">Close</button>
        </div>
        <div class="editor-side-body">
          <div class="editor-empty" id="skills-empty">Select a skill to edit or create a new one.</div>
          <div id="skills-form" style="display:none">
            <div class="form-group"><label>ID</label><input id="sk-id" type="text" /></div>
            <div class="form-group"><label>Display Name</label><input id="sk-name" type="text" /></div>
            <div class="form-group"><label>Description</label><textarea id="sk-desc"></textarea></div>
            <div class="form-group"><label>Type</label>
              <select id="sk-type">
                <option value="passive">passive</option>
                <option value="ability">ability</option>
                <option value="upgrade">upgrade</option>
              </select>
            </div>
            <div class="form-group"><label>Rarity</label>
              <select id="sk-rarity">
                <option value="common">common</option>
                <option value="uncommon">uncommon</option>
                <option value="rare">rare</option>
                <option value="epic">epic</option>
                <option value="legendary">legendary</option>
              </select>
            </div>
            <div class="form-group"><label>Cost</label><input id="sk-cost" type="number" min="0" value="1" /></div>
            <div class="form-group"><label>Overlay Text</label><input id="sk-overlaytext" type="text" placeholder="I, II, III, IV..." /></div>
            <div class="form-group"><label>Grid X</label><input id="sk-x" type="number" value="0" /></div>
            <div class="form-group"><label>Grid Y</label><input id="sk-y" type="number" value="0" /></div>
            <div class="form-group"><label>Icon Path</label><input id="sk-icon" type="text" /></div>
            <div class="form-group"><label>Tags (comma-separated)</label><input id="sk-tags" type="text" /></div>
            <div class="form-group">
              <label>Ability Ref</label>
              <select id="sk-ability-ref"></select>
            </div>
            <div class="form-group">
              <label>Passive Ref</label>
              <select id="sk-passive-ref"></select>
            </div>
            <div class="form-group">
              <label>Requires</label>
              <div id="sk-requires-list" class="editor-list"></div>
              <div class="editor-ref-row" style="margin-top:8px">
                <select id="sk-requires-select"></select>
                <button type="button" onclick="SkillsTab.addRequirement()">Add</button>
              </div>
            </div>
            <div class="form-group">
              <label>Modifiers</label>
              <div id="sk-mod-list"></div>
            </div>
            <div class="form-group">
              <label>Triggers</label>
              <div id="sk-triggers-picker"></div>
            </div>
            <div class="form-group">
              <label>Actions</label>
              <div id="sk-actions-picker"></div>
            </div>
            <div class="editor-actions">
              <button class="btn-delete" type="button" onclick="SkillsTab.delCurrent()">Delete</button>
              <button class="btn-primary" type="button" onclick="SkillsTab.save()">Save</button>
            </div>
          </div>
        </div>
      </div>
      <div class="editor-side secondary hidden" id="skills-mod-panel"></div>
      <div class="editor-side tertiary hidden" id="skills-ref-panel"></div>
    </div>
  `);

  let editIndex = null;
  let draftRequires = [];

  const modManager = SideEditors.makeModifierManager({
    listEl: document.getElementById('sk-mod-list'),
    panelEl: document.getElementById('skills-mod-panel'),
    nestedPanelEl: document.getElementById('skills-ref-panel'),
    title: 'Skill Modifier',
  });

  function list() {
    return window.AppData.tree;
  }

  const triggersPicker = RefPicker.make(document.getElementById('sk-triggers-picker'), {
    sourceKey: 'triggers',
    multiple: true,
    placeholder: '— add trigger —',
    disableCreate: true,
  });

  const actionsPicker = RefPicker.make(document.getElementById('sk-actions-picker'), {
    sourceKey: 'actions',
    multiple: true,
    placeholder: '— add action —',
    disableCreate: true,
  });

  function renderAbilityPassiveRefs(skill = {}) {
    SideEditors.fillSelect(document.getElementById('sk-ability-ref'), 'abilities', skill.abilityId, { nullable: true });
    SideEditors.fillSelect(document.getElementById('sk-passive-ref'), 'passives', skill.passiveId, { nullable: true });
  }

  function renderTable() {
    const tbody = document.getElementById('skills-tbody');
    tbody.innerHTML = '';
    list().forEach((skill, index) => {
      const tr = document.createElement('tr');
      tr.className = 'clickable-row' + (index === editIndex ? ' active-row' : '');
      const modifiers = (skill.modifiers || []).map((modifier) => {
        const statDef = SideEditors.lookupLabel('stats', modifier.statId);
        const modDef = SideEditors.lookupLabel('modifiers', modifier.modifierId);
        return `<span class="tag">${modDef}: ${statDef} ${modifier.operation || 'ADD'} ${modifier.value}</span>`;
      }).join('') || '—';
      const requires = (skill.requires || []).map((reqId) => {
        const req = list().find((entry) => entry.id === reqId);
        return `<span class="tag">${req?.displayName || reqId}</span>`;
      }).join('') || '—';
      const links = [
        skill.abilityId ? `<span class="tag">ability: ${SideEditors.lookupLabel('abilities', skill.abilityId)}</span>` : '',
        skill.passiveId ? `<span class="tag">passive: ${SideEditors.lookupLabel('passives', skill.passiveId)}</span>` : '',
      ].filter(Boolean).join('') || '—';
      tr.innerHTML = `
        <td><code>${skill.id}</code></td>
        <td>${skill.displayName || ''}</td>
        <td><span class="badge badge-${skill.type}">${skill.type}</span></td>
        <td><span class="badge badge-${skill.rarity || 'common'}">${skill.rarity || 'common'}</span></td>
        <td>${skill.cost}</td>
        <td>${links}</td>
        <td>${requires}</td>
        <td>${modifiers}</td>
        <td>${(skill.triggers || []).length || '—'}</td>
        <td class="actions">
          <button class="btn-edit" type="button" onclick="SkillsTab.edit(${index}); event.stopPropagation();">Edit</button>
          <button class="btn-delete" type="button" onclick="SkillsTab.del(${index}); event.stopPropagation();">Del</button>
        </td>
      `;
      tr.addEventListener('click', () => openEditor(index));
      tbody.appendChild(tr);
    });
  }

  function renderRequires() {
    const listEl = document.getElementById('sk-requires-list');
    listEl.innerHTML = '';
    draftRequires.forEach((reqId) => {
      const item = document.createElement('div');
      item.className = 'editor-list-item';
      item.innerHTML = `
        <span>${SideEditors.lookupLabel('tree', reqId)}</span>
        <span class="item-actions">
          <button class="editor-mini-btn delete" type="button">Del</button>
        </span>
      `;
      item.querySelector('button').addEventListener('click', () => removeRequirement(reqId));
      listEl.appendChild(item);
    });
    renderRequiresSelect();
  }

  function renderRequiresSelect() {
    const select = document.getElementById('sk-requires-select');
    const currentId = editIndex != null && editIndex >= 0 ? list()[editIndex].id : document.getElementById('sk-id').value.trim();
    select.innerHTML = '<option value="">— add prerequisite —</option>';
    list()
      .filter((entry) => entry.id !== currentId && !draftRequires.includes(entry.id))
      .forEach((entry) => {
        const option = document.createElement('option');
        option.value = entry.id;
        option.textContent = entry.displayName || entry.id;
        select.appendChild(option);
      });
  }

  function openEditor(index) {
    editIndex = index;
    const isNew = index === -1;
    const skill = isNew ? {} : list()[index];
    document.getElementById('skills-empty').style.display = 'none';
    document.getElementById('skills-form').style.display = '';
    document.getElementById('sk-id').value = skill.id || '';
    document.getElementById('sk-name').value = skill.displayName || '';
    document.getElementById('sk-desc').value = skill.description || '';
    document.getElementById('sk-type').value = skill.type || 'passive';
    document.getElementById('sk-rarity').value = skill.rarity || 'common';
    document.getElementById('sk-cost').value = skill.cost ?? 1;
    document.getElementById('sk-overlaytext').value = skill.overlayText || '';
    document.getElementById('sk-x').value = skill.position?.x ?? 0;
    document.getElementById('sk-y').value = skill.position?.y ?? 0;
    document.getElementById('sk-icon').value = skill.iconPath || '';
    document.getElementById('sk-tags').value = SideEditors.tagsToText(skill.tags);
    triggersPicker.setValue(SideEditors.extractRefList(skill.triggers, 'triggerId'));
    actionsPicker.setValue(SideEditors.extractRefList(skill.actions, 'actionId'));
    document.querySelector('#skills-form .btn-delete').style.display = isNew ? 'none' : '';
    draftRequires = [...(skill.requires || [])];
    renderAbilityPassiveRefs(skill);
    modManager.setValue(skill.modifiers || []);
    renderRequires();
    renderTable();
  }

  function closeEditor() {
    editIndex = null;
    draftRequires = [];
    document.getElementById('skills-empty').style.display = '';
    document.getElementById('skills-form').style.display = 'none';
    modManager.closeEditor();
    renderTable();
  }

  function addRequirement() {
    const value = document.getElementById('sk-requires-select').value;
    if (!value || draftRequires.includes(value)) return;
    draftRequires.push(value);
    renderRequires();
  }

  function removeRequirement(reqId) {
    draftRequires = draftRequires.filter((entry) => entry !== reqId);
    renderRequires();
  }

  function save() {
    const id = document.getElementById('sk-id').value.trim();
    if (!id) { alert('ID is required'); return; }
    if (editIndex == null) return;
    if (editIndex === -1 && list().some((skill) => skill.id === id)) { alert('ID already exists'); return; }

    const type = document.getElementById('sk-type').value;
    const abilityId = document.getElementById('sk-ability-ref').value || '';
    const passiveId = document.getElementById('sk-passive-ref').value || '';
    const previous = editIndex >= 0 ? list()[editIndex] : {};
    const entry = {
      id,
      displayName: document.getElementById('sk-name').value.trim() || id,
      description: document.getElementById('sk-desc').value.trim(),
      type,
      rarity: document.getElementById('sk-rarity').value,
      cost: parseInt(document.getElementById('sk-cost').value, 10) || 1,
      overlayText: document.getElementById('sk-overlaytext').value.trim(),
      position: {
        x: parseInt(document.getElementById('sk-x').value, 10) || 0,
        y: parseInt(document.getElementById('sk-y').value, 10) || 0,
      },
      iconPath: document.getElementById('sk-icon').value.trim(),
      tags: SideEditors.textToTags(document.getElementById('sk-tags').value),
      abilityId: type === 'ability' ? (abilityId || id) : (type === 'upgrade' ? abilityId : ''),
      passiveId: type === 'passive' ? (passiveId || id) : (type === 'upgrade' ? passiveId : ''),
      requires: [...draftRequires],
      modifiers: modManager.getValue(),
      triggers: SideEditors.mergeRefObjects(triggersPicker.getValue(), previous?.triggers, 'triggerId'),
      actions: SideEditors.mergeRefObjects(actionsPicker.getValue(), previous?.actions, 'actionId'),
    };
    if (editIndex === -1) {
      list().push(entry);
      editIndex = list().length - 1;
    } else {
      const previousId = list()[editIndex].id;
      if (previousId !== id) {
        list().forEach((skill) => {
          skill.requires = (skill.requires || []).map((reqId) => reqId === previousId ? id : reqId);
        });
      }
      list()[editIndex] = entry;
    }
    App.markDirty();
    renderRequires();
    renderTable();
  }

  function del(index) {
    const skill = list()[index];
    if (!confirm(`Delete skill "${skill.id}"?`)) return;
    list().splice(index, 1);
    list().forEach((entry) => {
      entry.requires = (entry.requires || []).filter((reqId) => reqId !== skill.id);
    });
    App.markDirty();
    if (editIndex === index) closeEditor();
    else renderTable();
  }

  function delCurrent() {
    if (editIndex == null || editIndex < 0) return;
    del(editIndex);
  }

  App.onTabActivated('skills', renderTable);
  window.SkillsTab = {
    add: () => openEditor(-1),
    edit: openEditor,
    save,
    del,
    delCurrent,
    addRequirement,
    close: closeEditor,
  };
})();
