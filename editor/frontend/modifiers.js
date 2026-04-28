// Generic factory for simple id/displayName/description tabs using side editors.
function makeSimpleTab({ paneId, tabId, dataKey, label, globalName }) {
  const pane = document.getElementById(paneId);
  pane.insertAdjacentHTML('beforeend', `
    <div class="editor-split">
      <div class="editor-main">
        <div class="pane-inner">
          <div class="pane-toolbar">
            <h2>${label}</h2>
            <button class="btn-primary" onclick="${globalName}.add()">+ Add</button>
          </div>
          <table class="data-table">
            <thead><tr><th>ID</th><th>Display Name</th><th>Description</th><th>Actions</th></tr></thead>
            <tbody id="${dataKey}-tbody"></tbody>
          </table>
        </div>
      </div>
      <div class="editor-side" id="${dataKey}-editor">
        <div class="editor-side-header">
          <h3>${label.slice(0, -1)} Editor</h3>
          <button class="btn-secondary" type="button" onclick="${globalName}.close()">Close</button>
        </div>
        <div class="editor-side-body">
          <div class="editor-empty" id="${dataKey}-empty">Select a row to edit or create a new ${label.slice(0, -1).toLowerCase()}.</div>
          <div id="${dataKey}-form" style="display:none">
            <div class="form-group"><label>ID</label><input id="${dataKey}-id" type="text" /></div>
            <div class="form-group"><label>Display Name</label><input id="${dataKey}-name" type="text" /></div>
            <div class="form-group"><label>Description</label><textarea id="${dataKey}-desc"></textarea></div>
            <div class="editor-actions">
              <button class="btn-delete" type="button" onclick="${globalName}.delCurrent()">Delete</button>
              <button class="btn-primary" type="button" onclick="${globalName}.save()">Save</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  `);

  let editIndex = null;

  function list() {
    return window.AppData[dataKey];
  }

  function render() {
    const tbody = document.getElementById(`${dataKey}-tbody`);
    tbody.innerHTML = '';
    list().forEach((item, i) => {
      const tr = document.createElement('tr');
      tr.className = 'clickable-row' + (i === editIndex ? ' active-row' : '');
      tr.innerHTML = `
        <td><code>${item.id}</code></td>
        <td>${item.displayName || ''}</td>
        <td style="max-width:300px;white-space:pre-wrap">${item.description || ''}</td>
        <td class="actions">
          <button class="btn-edit" type="button" onclick="${globalName}.edit(${i}); event.stopPropagation();">Edit</button>
          <button class="btn-delete" type="button" onclick="${globalName}.del(${i}); event.stopPropagation();">Del</button>
        </td>`;
      tr.addEventListener('click', () => openEditor(i));
      tbody.appendChild(tr);
    });
  }

  function openEditor(index) {
    editIndex = index;
    const isNew = index === -1;
    const item = isNew ? {} : list()[index];
    document.getElementById(`${dataKey}-empty`).style.display = 'none';
    document.getElementById(`${dataKey}-form`).style.display = '';
    document.getElementById(`${dataKey}-id`).value = item.id || '';
    document.getElementById(`${dataKey}-name`).value = item.displayName || '';
    document.getElementById(`${dataKey}-desc`).value = item.description || '';
    document.querySelector(`#${dataKey}-form .btn-delete`).style.display = isNew ? 'none' : '';
    render();
  }

  function closeEditor() {
    editIndex = null;
    document.getElementById(`${dataKey}-empty`).style.display = '';
    document.getElementById(`${dataKey}-form`).style.display = 'none';
    render();
  }

  function save() {
    const id = document.getElementById(`${dataKey}-id`).value.trim();
    if (!id) { alert('ID is required'); return; }
    if (editIndex === null) return;
    if (editIndex === -1 && list().some((item) => item.id === id)) { alert('ID already exists'); return; }
    const entry = {
      id,
      displayName: document.getElementById(`${dataKey}-name`).value.trim(),
      description: document.getElementById(`${dataKey}-desc`).value.trim(),
    };
    if (editIndex === -1) {
      list().push(entry);
      editIndex = list().length - 1;
    } else {
      list()[editIndex] = entry;
    }
    App.markDirty();
    render();
  }

  function del(index) {
    if (!confirm(`Delete "${list()[index].id}"?`)) return;
    list().splice(index, 1);
    App.markDirty();
    if (editIndex === index) closeEditor();
    else render();
  }

  function delCurrent() {
    if (editIndex == null || editIndex < 0) return;
    del(editIndex);
  }

  App.onTabActivated(tabId, render);
  window[globalName] = {
    add: () => openEditor(-1),
    edit: openEditor,
    save,
    del,
    delCurrent,
    close: closeEditor,
  };
}

makeSimpleTab({ paneId: 'pane-modifiers', tabId: 'modifiers', dataKey: 'modifiers', label: 'Modifiers', globalName: 'ModifiersTab' });
