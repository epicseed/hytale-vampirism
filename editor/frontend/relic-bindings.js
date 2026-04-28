// relic-bindings.js — edit the aggregated `relicBindings` data backed by relicBindings.json.
// Fixed 5 slots (primary/secondary/ability1/ability2/use), each mapped to an abilityId.
(() => {
  const SLOTS = [
    { key: 'primary',   label: 'Primary (M1)' },
    { key: 'secondary', label: 'Secondary (M2)' },
    { key: 'ability1',  label: 'Ability 1' },
    { key: 'ability2',  label: 'Ability 2' },
    { key: 'use',       label: 'Use' },
  ];

  const pane = document.getElementById('pane-relic-bindings');
  pane.insertAdjacentHTML('beforeend', `
    <div class="pane-inner" style="max-width:640px">
      <div class="pane-toolbar">
        <h2>Relic Bindings</h2>
      </div>
      <div class="editor-help" style="margin-bottom:12px">
        Binds each relic input slot to an <code>abilityId</code>. Consumed by <code>VampirismRelicCommand</code>.
      </div>
      <div id="relic-bindings-form">
        ${SLOTS.map((slot) => `
          <div class="form-group">
            <label>${slot.label} &nbsp;<code style="font-size:10px;color:var(--text-dim)">${slot.key}</code></label>
            <select data-slot="${slot.key}"></select>
            <div class="editor-help" data-warning-for="${slot.key}" style="color:#f0c040;margin-top:4px;display:none"></div>
          </div>
        `).join('')}
      </div>
    </div>
  `);

  function data() {
    if (!window.AppData.relicBindings || typeof window.AppData.relicBindings !== 'object' || Array.isArray(window.AppData.relicBindings)) {
      window.AppData.relicBindings = {};
    }
    return window.AppData.relicBindings;
  }

  function abilityExists(id) {
    return Boolean(id && (window.AppData.abilities || []).some((a) => a.id === id));
  }

  function validateSlot(slot, value) {
    const warnEl = document.querySelector(`[data-warning-for="${slot}"]`);
    if (!warnEl) return;
    if (!value) {
      warnEl.style.display = '';
      warnEl.textContent = '⚠ Slot is empty — relic will not respond for this input.';
    } else if (!abilityExists(value)) {
      warnEl.style.display = '';
      warnEl.textContent = `⚠ Ability "${value}" is not defined in the skill tree.`;
    } else {
      warnEl.style.display = 'none';
      warnEl.textContent = '';
    }
  }

  function render() {
    const bindings = data();
    SLOTS.forEach((slot) => {
      const select = document.querySelector(`select[data-slot="${slot.key}"]`);
      if (!select) return;
      const current = bindings[slot.key] || '';
      SideEditors.fillSelect(select, 'abilities', current, { nullable: true });
      // Ensure the current value is preserved even if the ability doesn't exist in AppData.
      if (current && !abilityExists(current)) {
        const opt = document.createElement('option');
        opt.value = current;
        opt.textContent = `${current} (missing)`;
        select.appendChild(opt);
        select.value = current;
      }
      select.onchange = () => {
        const next = select.value || '';
        if (next) bindings[slot.key] = next;
        else delete bindings[slot.key];
        App.markDirty();
        validateSlot(slot.key, next);
      };
      validateSlot(slot.key, current);
    });
  }

  App.onTabActivated('relic-bindings', render);
  window.RelicBindingsTab = { render };
})();
