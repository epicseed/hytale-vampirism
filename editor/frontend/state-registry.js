// state-registry.js — edit the aggregated `stateRegistry` data backed by stateRegistry.json.
// Each row: { stateId, effectId } mapping a runtime state key to an entity effect.
(() => {
  const BUILTIN_STATE_IDS = [
    'IS_IN_ANCIENT_FORM',
    'IS_INVISIBLE',
    'IS_IN_BLOOD_THIRST',
  ];

  const pane = document.getElementById('pane-state-registry');
  pane.insertAdjacentHTML('beforeend', `
    <div class="pane-inner">
      <div class="pane-toolbar">
        <h2>State Registry</h2>
        <button class="btn-primary" type="button" onclick="StateRegistryTab.add()">+ Add Binding</button>
      </div>
      <div class="editor-help" style="margin-bottom:12px">
        Maps runtime state keys to entity effects. Consumed by <code>SkillRuntimeStateResolver</code>.
        Built-in keys: <code>${BUILTIN_STATE_IDS.join('</code> · <code>')}</code>.
        Freeform entries are allowed for forward-compat.
      </div>
      <table class="data-table">
        <thead><tr><th>State ID</th><th>Effect</th><th style="width:1%">Actions</th></tr></thead>
        <tbody id="state-registry-tbody"></tbody>
      </table>
      <datalist id="state-registry-state-suggestions">
        ${BUILTIN_STATE_IDS.map((id) => `<option value="${id}"></option>`).join('')}
      </datalist>
    </div>
  `);

  function list() {
    if (!Array.isArray(window.AppData.stateRegistry)) window.AppData.stateRegistry = [];
    return window.AppData.stateRegistry;
  }

  function render() {
    const tbody = document.getElementById('state-registry-tbody');
    tbody.innerHTML = '';
    const entries = list();
    if (!entries.length) {
      tbody.innerHTML = `<tr><td colspan="3" style="color:var(--text-dim);font-style:italic">No state bindings yet.</td></tr>`;
      return;
    }
    entries.forEach((row, index) => {
      const tr = document.createElement('tr');
      tr.innerHTML = `
        <td>
          <input type="text" list="state-registry-state-suggestions"
                 data-index="${index}" data-field="stateId"
                 value="${(row.stateId || '').replace(/"/g, '&quot;')}"
                 placeholder="IS_EXAMPLE" style="width:100%" />
        </td>
        <td>
          <select data-index="${index}" data-field="effectId" style="width:100%"></select>
        </td>
        <td class="actions">
          <button class="btn-delete" type="button" onclick="StateRegistryTab.del(${index})">Del</button>
        </td>
      `;
      tbody.appendChild(tr);
      const select = tr.querySelector('select[data-field="effectId"]');
      SideEditors.fillSelect(select, 'effects', row.effectId || '', { nullable: true });
    });

    tbody.querySelectorAll('input, select').forEach((el) => {
      el.addEventListener('change', onCellChange);
      if (el.tagName === 'INPUT') el.addEventListener('blur', onCellChange);
    });
  }

  function onCellChange(event) {
    const el = event.target;
    const index = Number(el.dataset.index);
    const field = el.dataset.field;
    const entries = list();
    if (!entries[index]) return;
    const next = String(el.value || '').trim();
    if (entries[index][field] === next) return;
    entries[index][field] = next;
    App.markDirty();
  }

  function add() {
    list().push({ stateId: '', effectId: '' });
    App.markDirty();
    render();
  }

  function del(index) {
    const row = list()[index];
    if (!row) return;
    if (row.stateId || row.effectId) {
      if (!confirm(`Delete binding for "${row.stateId || '(empty)'}"?`)) return;
    }
    list().splice(index, 1);
    App.markDirty();
    render();
  }

  App.onTabActivated('state-registry', render);
  window.StateRegistryTab = { add, del, render };
})();
