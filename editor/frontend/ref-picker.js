// Reusable reference picker component.
// RefPicker.make(containerEl, config) creates a tag-based picker inside containerEl.
// config:
//   {
//     sourceKey,
//     multiple=true,
//     placeholder,
//     disableCreate=false,
//     idPlaceholder='ID',
//     namePlaceholder='Display Name',
//     createItem?(id, secondaryValue) => object
//   }
// Returns { getValue(), setValue(val) }

const RefPicker = (() => {
  let cssInjected = false;

  function ensureCSS() {
    if (cssInjected) return;
    cssInjected = true;
    const s = document.createElement('style');
    s.textContent = `
      .rp-wrap { display:flex; flex-direction:column; gap:5px; }
      .rp-tags { display:flex; flex-wrap:wrap; gap:3px; min-height:22px; }
      .rp-controls { display:flex; gap:5px; }
      .rp-sel {
        flex:1; background:var(--input-bg); border:1px solid var(--border);
        color:var(--text); padding:5px 8px; border-radius:5px; font-size:12px;
      }
      .rp-btn-new {
        font-size:11px; padding:5px 9px; white-space:nowrap; cursor:pointer;
        border:none; border-radius:5px; background:var(--border); color:var(--text-dim); font-weight:600;
      }
      .rp-btn-new:hover { color:var(--text); }
      .rp-create {
        display:none; flex-wrap:wrap; gap:5px; padding:8px;
        background:#0a1828; border-radius:5px; border:1px solid var(--border); margin-top:2px;
      }
      .rp-create.open { display:flex; }
      .rp-create input {
        flex:1; min-width:100px; background:var(--input-bg); border:1px solid var(--border);
        color:var(--text); padding:4px 8px; border-radius:4px; font-size:12px;
      }
      .rp-create .rp-ok { background:var(--accent); color:#fff; }
      .rp-create .rp-no { background:var(--border); color:var(--text-dim); }
      .rp-create button {
        border:none; border-radius:4px; padding:4px 9px; font-size:11px; font-weight:700; cursor:pointer;
      }
      .ref-tag { display:inline-flex; align-items:center; gap:3px; }
      .ref-tag-del {
        background:none; border:none; color:var(--accent); cursor:pointer;
        font-size:14px; line-height:1; padding:0 1px; font-weight:700;
      }
    `;
    document.head.appendChild(s);
  }

  function make(container, {
    sourceKey,
    multiple = true,
    placeholder = '— add —',
    disableCreate = false,
    idPlaceholder = 'ID',
    namePlaceholder = 'Display Name',
    createItem,
  }) {
    ensureCSS();
    let selected = [];

    container.innerHTML = `
      <div class="rp-wrap">
        <div class="rp-tags"></div>
        <div class="rp-controls">
          <select class="rp-sel"><option value="">${placeholder}</option></select>
          <button type="button" class="rp-btn-new"${disableCreate ? ' style="display:none"' : ''}>+ New</button>
        </div>
        <div class="rp-create">
          <input class="rp-ni" placeholder="${idPlaceholder}" />
          <input class="rp-nn" placeholder="${namePlaceholder}" />
          <button type="button" class="rp-ok">✓ Create</button>
          <button type="button" class="rp-no">✗</button>
        </div>
      </div>`;

    const wrap    = container.querySelector('.rp-wrap');
    const tagsEl  = wrap.querySelector('.rp-tags');
    const selEl   = wrap.querySelector('.rp-sel');
    const btnNew  = wrap.querySelector('.rp-btn-new');
    const createEl = wrap.querySelector('.rp-create');

    function opts() { return window.AppData[sourceKey] || []; }

    function rebuildSelect() {
      selEl.innerHTML = `<option value="">${placeholder}</option>`;
      for (const item of opts()) {
        if (!multiple && selected.includes(item.id)) continue;
        const o = document.createElement('option');
        o.value = item.id;
        o.textContent = item.displayName ? `${item.displayName} (${item.id})` : item.id;
        selEl.appendChild(o);
      }
    }

    function rebuildTags() {
      tagsEl.innerHTML = '';
      for (const val of selected) {
        const item = opts().find(o => o.id === val);
        const label = item?.displayName || val;
        const span = document.createElement('span');
        span.className = 'tag ref-tag';
        span.innerHTML = `${label} <button type="button" class="ref-tag-del" data-v="${val}">×</button>`;
        tagsEl.appendChild(span);
      }
    }

    tagsEl.addEventListener('click', e => {
      const b = e.target.closest('.ref-tag-del');
      if (!b) return;
      selected = selected.filter(v => v !== b.dataset.v);
      rebuildTags(); rebuildSelect();
    });

    selEl.addEventListener('change', () => {
      const v = selEl.value; if (!v) return;
      if (!multiple) selected = [v];
      else if (!selected.includes(v)) selected.push(v);
      selEl.value = '';
      rebuildTags(); rebuildSelect();
    });

    btnNew.addEventListener('click', () => {
      createEl.classList.toggle('open');
      if (createEl.classList.contains('open')) createEl.querySelector('.rp-ni').focus();
    });

    createEl.querySelector('.rp-no').addEventListener('click', () => {
      createEl.classList.remove('open');
    });

    createEl.querySelector('.rp-ok').addEventListener('click', () => {
      const newId   = createEl.querySelector('.rp-ni').value.trim();
      const newName = createEl.querySelector('.rp-nn').value.trim();
      if (!newId) { alert('ID is required'); return; }
      const list = window.AppData[sourceKey];
      if (list.some(x => x.id === newId)) { alert('ID already exists'); return; }
      list.push(createItem ? createItem(newId, newName) : { id: newId, displayName: newName || newId, description: '' });
      App.markDirty();
      if (!selected.includes(newId)) {
        if (!multiple) selected = [newId]; else selected.push(newId);
      }
      createEl.classList.remove('open');
      createEl.querySelector('.rp-ni').value = '';
      createEl.querySelector('.rp-nn').value = '';
      rebuildTags(); rebuildSelect();
    });

    function getValue() {
      return multiple ? [...selected] : (selected[0] ?? null);
    }

    function setValue(val) {
      selected = !val ? [] : Array.isArray(val) ? [...val] : [String(val)];
      rebuildTags(); rebuildSelect();
    }

    rebuildSelect(); rebuildTags();
    return { getValue, setValue };
  }

  return { make };
})();
