// stats.js — dedicated stats editor with runtime metadata and Hytale bindings.
(() => {
  const pane = document.getElementById('pane-stats');
  pane.insertAdjacentHTML('beforeend', `
    <div class="editor-split">
      <div class="editor-main">
        <div class="pane-inner">
          <div class="pane-toolbar">
            <h2>Stats</h2>
            <button class="btn-primary" onclick="StatsTab.add()">+ Add</button>
          </div>
          <table class="data-table" id="stats-table">
            <thead><tr>
              <th>ID</th><th>Display Name</th><th>Category</th><th>Base</th><th>Binding</th><th>Usage</th><th>Ops</th>
            </tr></thead>
            <tbody id="stats-tbody"></tbody>
          </table>
        </div>
      </div>
      <div class="editor-side" id="stats-editor">
        <div class="editor-side-header">
          <h3>Stat Editor</h3>
          <button class="btn-secondary" type="button" onclick="StatsTab.close()">Close</button>
        </div>
        <div class="editor-side-body">
          <div class="editor-empty" id="stats-empty">Select a stat to edit or create a new one.</div>
          <div id="stats-form" style="display:none">
            <div class="form-group"><label>ID</label><input id="st-id" type="text" /></div>
            <div class="form-group"><label>Display Name</label><input id="st-name" type="text" /></div>
            <div class="form-group"><label>Description</label><textarea id="st-desc"></textarea></div>
            <div class="form-group">
              <label>Category</label>
              <select id="st-category">
                <option value="">— none —</option>
                <option value="ability">Ability</option>
                <option value="blood">Blood</option>
                <option value="combat">Combat</option>
                <option value="form">Form</option>
                <option value="movement">Movement</option>
                <option value="projectile">Projectile</option>
                <option value="survival">Survival</option>
                <option value="utility">Utility</option>
              </select>
            </div>
            <div class="form-group">
              <label>Implementation Status</label>
              <select id="st-status">
                <option value="">— none —</option>
                <option value="implemented">Implemented</option>
                <option value="partial">Partial</option>
                <option value="planned">Planned</option>
              </select>
            </div>
            <div class="form-group"><label>Base Value</label><input id="st-base" type="number" step="0.01" /></div>
            <div class="form-group"><label>Unit / Hint</label><input id="st-unit" type="text" placeholder="% · seconds · blocks · multiplier" /></div>
            <div class="form-group">
              <label>Hytale Binding</label>
              <select id="st-binding-type">
                <option value="">— none —</option>
                <option value="effect">Effect</option>
                <option value="projectile">Projectile</option>
                <option value="projectileConfig">Projectile Config</option>
                <option value="interaction">Interaction</option>
                <option value="rootInteraction">Root Interaction</option>
                <option value="item">Item</option>
                <option value="soundEvent">Sound Event</option>
                <option value="model">Model</option>
              </select>
              <div class="editor-ref-row" style="margin-top:8px">
                <select id="st-binding-id" disabled></select>
              </div>
              <div class="editor-help">Selection-only binding to local or official Hytale assets.</div>
            </div>
            <div class="form-group">
              <label>Usage Summary</label>
              <input id="st-usage" type="text" readonly />
            </div>
            <div class="form-group"><label>Notes</label><textarea id="st-notes"></textarea></div>
            <div class="editor-actions">
              <button class="btn-delete" type="button" onclick="StatsTab.delCurrent()">Delete</button>
              <button class="btn-primary" type="button" id="stats-save-btn" onclick="StatsTab.save()">Save</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `);

  const BINDING_CATEGORY_BY_TYPE = {
    effect: 'effects',
    projectile: 'projectiles',
    projectileConfig: 'projectileConfigs',
    interaction: 'interactions',
    rootInteraction: 'rootInteractions',
    item: 'items',
    soundEvent: 'soundEvents',
    model: 'models',
  };

  let editIndex = null;
  let bindingLoadToken = 0;
  let bindingLoading = false;

  function list() {
    return window.AppData.stats;
  }

  function usageBuckets() {
    const buckets = new Map();
    const bump = (statId, bucket) => {
      if (!statId) return;
      const current = buckets.get(statId) || { total: 0, modifiers: 0, actions: 0 };
      current.total += 1;
      current[bucket] += 1;
      buckets.set(statId, current);
    };
    const scanStatKeys = (value) => {
      if (!value || typeof value !== 'object') return;
      if (Array.isArray(value)) {
        value.forEach(scanStatKeys);
        return;
      }
      Object.entries(value).forEach(([key, nested]) => {
        if ((key === 'statId' || key.endsWith('StatId')) && typeof nested === 'string' && nested) {
          bump(nested, 'actions');
        } else {
          scanStatKeys(nested);
        }
      });
    };

    (window.AppData.tree || []).forEach((skill) => {
      (skill.modifiers || []).forEach((modifier) => bump(modifier.statId, 'modifiers'));
    });
    (window.AppData.passives || []).forEach((passive) => {
      (passive.modifiers || []).forEach((modifier) => bump(modifier.statId, 'modifiers'));
    });
    (window.AppData.effects || []).forEach((effect) => {
      (effect.modifiers || []).forEach((modifier) => bump(modifier.statId, 'modifiers'));
    });
    (window.AppData.actions || []).forEach((action) => scanStatKeys(action.definition || {}));

    return buckets;
  }

  function formatBinding(stat) {
    const type = stat.binding?.type || '';
    const assetId = stat.binding?.assetId || '';
    if (!type) return '—';
    return assetId ? `${type}:${assetId}` : type;
  }

  function formatUsage(statId) {
    const usage = usageBuckets().get(statId);
    if (!usage) return '—';
    const parts = [];
    if (usage.modifiers) parts.push(`${usage.modifiers} mod`);
    if (usage.actions) parts.push(`${usage.actions} action`);
    return `${usage.total} refs${parts.length ? ` · ${parts.join(' · ')}` : ''}`;
  }

  function setBindingLoading(isLoading) {
    bindingLoading = isLoading;
    const saveBtn = document.getElementById('stats-save-btn');
    if (saveBtn) saveBtn.disabled = isLoading;
  }

  async function refreshBindingSelect(type, currentValue = '') {
    const select = document.getElementById('st-binding-id');
    const category = BINDING_CATEGORY_BY_TYPE[type];
    const token = ++bindingLoadToken;
    if (!category) {
      select.innerHTML = '<option value="">— none —</option>';
      select.value = '';
      select.disabled = true;
      setBindingLoading(false);
      return;
    }
    select.disabled = true;
    setBindingLoading(true);
    try {
      await HytaleAssetStore.fillSelect(select, category, currentValue, { nullable: true });
      if (token !== bindingLoadToken) return;
      select.disabled = false;
      select.value = currentValue || '';
    } finally {
      if (token === bindingLoadToken) setBindingLoading(false);
    }
  }

  function renderTable() {
    const tbody = document.getElementById('stats-tbody');
    tbody.innerHTML = '';
    list().forEach((stat, index) => {
      const tr = document.createElement('tr');
      tr.className = 'clickable-row' + (index === editIndex ? ' active-row' : '');
      tr.innerHTML = `
        <td><code>${stat.id}</code></td>
        <td>${stat.displayName || ''}</td>
        <td>${stat.category || '—'}</td>
        <td>${stat.baseValue === '' ? '—' : stat.baseValue}</td>
        <td>${formatBinding(stat)}</td>
        <td>${formatUsage(stat.id)}</td>
        <td class="actions">
          <button class="btn-edit" type="button" onclick="StatsTab.edit(${index}); event.stopPropagation();">Edit</button>
          <button class="btn-delete" type="button" onclick="StatsTab.del(${index}); event.stopPropagation();">Del</button>
        </td>
      `;
      tr.addEventListener('click', () => openEditor(index));
      tbody.appendChild(tr);
    });
  }

  async function openEditor(index) {
    editIndex = index;
    const isNew = index === -1;
    const stat = isNew ? {} : list()[index];
    document.getElementById('stats-empty').style.display = 'none';
    document.getElementById('stats-form').style.display = '';
    document.getElementById('st-id').value = stat.id || '';
    document.getElementById('st-name').value = stat.displayName || '';
    document.getElementById('st-desc').value = stat.description || '';
    document.getElementById('st-category').value = stat.category || '';
    document.getElementById('st-status').value = stat.status || '';
    document.getElementById('st-base').value = stat.baseValue === '' || stat.baseValue == null ? '' : stat.baseValue;
    document.getElementById('st-unit').value = stat.unit || '';
    document.getElementById('st-binding-type').value = stat.binding?.type || '';
    document.getElementById('st-usage').value = stat.id ? formatUsage(stat.id) : '—';
    document.getElementById('st-notes').value = stat.notes || '';
    document.querySelector('#stats-form .btn-delete').style.display = isNew ? 'none' : '';
    await refreshBindingSelect(stat.binding?.type || '', stat.binding?.assetId || '');
    renderTable();
  }

  function closeEditor() {
    editIndex = null;
    setBindingLoading(false);
    document.getElementById('stats-empty').style.display = '';
    document.getElementById('stats-form').style.display = 'none';
    renderTable();
  }

  function save() {
    if (bindingLoading) {
      alert('Wait for the Hytale binding options to finish loading.');
      return;
    }
    if (editIndex == null) return;
    const id = document.getElementById('st-id').value.trim();
    if (!id) { alert('ID is required'); return; }
    if (editIndex === -1 && list().some((stat) => stat.id === id)) { alert('ID already exists'); return; }

    const baseRaw = document.getElementById('st-base').value.trim();
    const bindingType = document.getElementById('st-binding-type').value;
    const bindingId = document.getElementById('st-binding-id').value;
    const entry = {
      id,
      displayName: document.getElementById('st-name').value.trim() || id,
      description: document.getElementById('st-desc').value.trim(),
      category: document.getElementById('st-category').value,
      status: document.getElementById('st-status').value,
      unit: document.getElementById('st-unit').value.trim(),
      notes: document.getElementById('st-notes').value.trim(),
      binding: bindingType ? { type: bindingType, assetId: bindingId || '' } : {},
    };
    if (baseRaw) entry.baseValue = Number(baseRaw);

    if (editIndex === -1) {
      list().push(entry);
      editIndex = list().length - 1;
    } else {
      list()[editIndex] = entry;
    }
    App.markDirty();
    document.getElementById('st-usage').value = formatUsage(id);
    renderTable();
  }

  function del(index) {
    const stat = list()[index];
    if (!confirm(`Delete stat "${stat.id}"?`)) return;
    list().splice(index, 1);
    App.markDirty();
    if (editIndex === index) closeEditor();
    else renderTable();
  }

  function delCurrent() {
    if (editIndex == null || editIndex < 0) return;
    del(editIndex);
  }

  document.getElementById('st-binding-type').addEventListener('change', async (event) => {
    await refreshBindingSelect(event.target.value, '');
  });

  App.onTabActivated('stats', renderTable);
  window.StatsTab = {
    add: () => openEditor(-1),
    edit: openEditor,
    save,
    del,
    delCurrent,
    close: closeEditor,
  };
})();
