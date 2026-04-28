// makeInlineModEditor(containerEl)
// Creates an inline modifier list editor. Each modifier:
//   { id (ref→modifiers[]), stat (ref→stats[]), operation, value, priority, condition? (ref→states[]) }
// Returns { getValue(), setValue(arr) }

function makeInlineModEditor(container) {
  if (!document.getElementById('imod-style')) {
    const s = document.createElement('style');
    s.id = 'imod-style';
    s.textContent = `
      .imod-wrap { margin-top:4px; }
      .imod-hdr {
        display:grid;
        grid-template-columns:1fr 1fr 90px 65px 65px 90px 30px;
        gap:4px; padding:4px 0;
        font-size:10px; color:var(--text-dim); text-transform:uppercase; letter-spacing:.5px;
      }
      .imod-row {
        display:grid;
        grid-template-columns:1fr 1fr 90px 65px 65px 90px 30px;
        gap:4px; align-items:start;
        padding:5px 0; border-bottom:1px solid #1e2d42;
      }
      .imod-cell { position:relative; }
      .imod-ref-wrap { display:flex; gap:3px; }
      .imod-ref-sel {
        flex:1; min-width:0; background:var(--input-bg); border:1px solid var(--border);
        color:var(--text); padding:4px 5px; border-radius:4px; font-size:11px;
      }
      .imod-ref-new {
        flex-shrink:0; background:var(--border); border:none; color:var(--text-dim);
        border-radius:4px; padding:4px 6px; font-size:11px; cursor:pointer;
      }
      .imod-ref-new:hover { color:var(--text); }
      .imod-ref-form {
        display:none; position:absolute; z-index:20; top:100%; left:0; right:0;
        flex-direction:column; gap:4px; padding:6px;
        background:#0a1828; border:1px solid var(--border); border-radius:4px;
      }
      .imod-ref-form.open { display:flex; }
      .imod-ref-form input {
        background:var(--input-bg); border:1px solid var(--border);
        color:var(--text); padding:4px 6px; border-radius:3px; font-size:11px; width:100%;
      }
      .imod-ref-actions { display:flex; gap:4px; }
      .imod-ref-ok { background:var(--accent); color:#fff; border:none; border-radius:3px; padding:3px 8px; font-size:10px; cursor:pointer; font-weight:700; }
      .imod-ref-no { background:var(--border); color:var(--text-dim); border:none; border-radius:3px; padding:3px 8px; font-size:10px; cursor:pointer; }
      .imod-op-sel {
        width:100%; background:var(--input-bg); border:1px solid var(--border);
        color:var(--text); padding:4px 5px; border-radius:4px; font-size:11px;
      }
      .imod-num-in {
        width:100%; background:var(--input-bg); border:1px solid var(--border);
        color:var(--text); padding:4px 5px; border-radius:4px; font-size:12px;
      }
      .imod-del-btn {
        background:#3a1020; color:#e94560; border:none; border-radius:4px;
        padding:3px 6px; font-size:13px; cursor:pointer; font-weight:700; width:30px;
      }
      .imod-add-btn {
        margin-top:6px; font-size:11px; padding:5px 10px;
        background:var(--border); color:var(--text-dim);
        border:none; border-radius:4px; cursor:pointer; font-weight:600;
      }
      .imod-add-btn:hover { color:var(--text); }
    `;
    document.head.appendChild(s);
  }

  let rows = [];

  // Creates a ref-select cell bound to AppData[sourceKey].
  // nullable=true prepends an empty "— none —" option.
  function makeRefCell(sourceKey, currentVal, nullable) {
    const cell = document.createElement('div');
    cell.className = 'imod-cell';

    const refWrap = document.createElement('div');
    refWrap.className = 'imod-ref-wrap';

    const sel = document.createElement('select');
    sel.className = 'imod-ref-sel';

    function populateSel() {
      const prev = sel.value;
      sel.innerHTML = '';
      if (nullable) {
        const em = document.createElement('option');
        em.value = ''; em.textContent = '— none —';
        sel.appendChild(em);
      }
      for (const item of (window.AppData[sourceKey] || [])) {
        const o = document.createElement('option');
        o.value = item.id;
        o.textContent = item.displayName || item.id;
        o.title = item.id;
        sel.appendChild(o);
      }
      sel.value = prev || currentVal || '';
    }

    const btnNew = document.createElement('button');
    btnNew.type = 'button';
    btnNew.className = 'imod-ref-new';
    btnNew.textContent = '＋';
    btnNew.title = `New ${sourceKey.replace(/s$/, '')}`;

    const form = document.createElement('div');
    form.className = 'imod-ref-form';
    form.innerHTML = `
      <input class="irf-id" placeholder="ID" />
      <input class="irf-nm" placeholder="Display Name" />
      <div class="imod-ref-actions">
        <button type="button" class="imod-ref-ok">✓ Create</button>
        <button type="button" class="imod-ref-no">Cancel</button>
      </div>`;

    btnNew.addEventListener('click', e => {
      e.stopPropagation();
      form.classList.toggle('open');
      if (form.classList.contains('open')) form.querySelector('.irf-id').focus();
    });

    form.querySelector('.imod-ref-no').addEventListener('click', () => form.classList.remove('open'));

    form.querySelector('.imod-ref-ok').addEventListener('click', () => {
      const newId   = form.querySelector('.irf-id').value.trim();
      const newName = form.querySelector('.irf-nm').value.trim();
      if (!newId) { alert('ID is required'); return; }
      const list = window.AppData[sourceKey];
      if (list.some(x => x.id === newId)) { alert('ID already exists'); return; }
      list.push({ id: newId, displayName: newName || newId, description: '' });
      App.markDirty();
      form.querySelector('.irf-id').value = '';
      form.querySelector('.irf-nm').value = '';
      form.classList.remove('open');
      populateSel();
      sel.value = newId;
    });

    refWrap.appendChild(sel);
    refWrap.appendChild(btnNew);
    cell.appendChild(refWrap);
    cell.appendChild(form);

    populateSel();
    return { el: cell, sel };
  }

  function render() {
    container.innerHTML = '';
    const wrap = document.createElement('div');
    wrap.className = 'imod-wrap';

    if (rows.length > 0) {
      const hdr = document.createElement('div');
      hdr.className = 'imod-hdr';
      ['Modifier Def', 'Stat', 'Operation', 'Value', 'Priority', 'Condition', ''].forEach(h => {
        const span = document.createElement('span');
        span.textContent = h;
        hdr.appendChild(span);
      });
      wrap.appendChild(hdr);

      rows.forEach((row, i) => {
        const rowEl = document.createElement('div');
        rowEl.className = 'imod-row';

        const idRef   = makeRefCell('modifiers', row.id   || '', false);
        const statRef = makeRefCell('stats',     row.stat || '', false);
        row._idSel   = idRef.sel;
        row._statSel = statRef.sel;

        const opSel = document.createElement('select');
        opSel.className = 'imod-op-sel';
        ['ADD', 'MULTIPLY', 'SET', 'SUBTRACT'].forEach(op => {
          const o = document.createElement('option');
          o.value = op; o.textContent = op;
          if ((row.operation || 'ADD') === op) o.selected = true;
          opSel.appendChild(o);
        });
        row._opSel = opSel;

        const valIn = document.createElement('input');
        valIn.type = 'number'; valIn.step = 'any';
        valIn.className = 'imod-num-in';
        valIn.value = row.value ?? 0;
        row._valIn = valIn;

        const priIn = document.createElement('input');
        priIn.type = 'number';
        priIn.className = 'imod-num-in';
        priIn.value = row.priority ?? 100;
        row._priIn = priIn;

        const condRef = makeRefCell('states', row.condition || '', true);
        row._condSel = condRef.sel;

        const delBtn = document.createElement('button');
        delBtn.type = 'button';
        delBtn.className = 'imod-del-btn';
        delBtn.textContent = '×';
        delBtn.addEventListener('click', () => { rows.splice(i, 1); render(); });

        rowEl.appendChild(idRef.el);
        rowEl.appendChild(statRef.el);
        rowEl.appendChild(opSel);
        rowEl.appendChild(valIn);
        rowEl.appendChild(priIn);
        rowEl.appendChild(condRef.el);
        rowEl.appendChild(delBtn);

        wrap.appendChild(rowEl);
      });
    }

    const addBtn = document.createElement('button');
    addBtn.type = 'button';
    addBtn.className = 'imod-add-btn';
    addBtn.textContent = '+ Add Modifier';
    addBtn.addEventListener('click', () => {
      rows.push({ id: '', stat: '', operation: 'ADD', value: 0, priority: 100, condition: '' });
      render();
    });
    wrap.appendChild(addBtn);
    container.appendChild(wrap);
  }

  function getValue() {
    return rows.map(row => {
      const id   = row._idSel?.value   ?? row.id   ?? '';
      const stat = row._statSel?.value ?? row.stat ?? '';
      const cond = row._condSel?.value ?? row.condition ?? '';
      const obj  = {
        id,
        stat,
        operation: row._opSel?.value ?? row.operation ?? 'ADD',
        value:     parseFloat(row._valIn?.value ?? row.value) || 0,
        priority:  parseInt(row._priIn?.value   ?? row.priority) || 100,
      };
      if (cond) obj.condition = cond;
      return obj;
    }).filter(r => r.id || r.stat);
  }

  function setValue(arr) {
    rows = (arr || []).map(r => ({ ...r }));
    render();
  }

  setValue([]);
  return { getValue, setValue };
}
