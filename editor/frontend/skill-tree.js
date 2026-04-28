// skill-tree.js — Canvas+SVG skill tree editor tab.
// Reads/writes window.AppData.tree. Calls App.markDirty() on mutations.
(() => {
  // ── DOM injection ──────────────────────────────────────────────────────────
  const pane = document.getElementById('pane-skill-tree');
  pane.insertAdjacentHTML('beforeend', `
    <style>
      #st-wrap { display:flex; flex:1; min-height:0; overflow:hidden; }
      #st-main { flex:1; min-width:0; display:flex; overflow:hidden; }

      /* sidebar */
      #st-sidebar {
        width:320px; min-width:320px; background:#16213e;
        border-right:2px solid #0f3460; display:flex; flex-direction:column; z-index:10;
      }
      #st-toolbar { display:flex; flex-wrap:wrap; gap:6px; padding:10px; border-bottom:1px solid #0f3460; }
      #st-toolbar button { cursor:pointer; border:none; border-radius:6px; padding:7px 12px; font-size:12px; font-weight:600; }
      #st-toolbar .btn-ghost {
        background:#1a1a2e; color:#b0c4de; border:1px solid #0f3460;
      }
      #st-conn-row { padding:6px 10px; font-size:12px; color:#8899aa; border-bottom:1px solid #0f3460; display:flex; align-items:center; gap:8px; }
      #st-conn-row select { background:#0d1b2a; border:1px solid #0f3460; color:#e0e0e0; border-radius:4px; padding:3px 6px; font-size:12px; }

      /* skill panel */
      #st-skill-panel { flex:1; overflow-y:auto; padding:12px; }
      #st-skill-panel h3 { font-size:13px; color:#e94560; margin-bottom:10px; letter-spacing:.5px; text-transform:uppercase; }
      .st-form-group { margin-bottom:10px; }
      .st-form-group label { display:block; font-size:11px; color:#8899aa; text-transform:uppercase; margin-bottom:4px; }
      .st-form-group input, .st-form-group select, .st-form-group textarea {
        width:100%; background:#0d1b2a; border:1px solid #0f3460; color:#e0e0e0;
        padding:7px 10px; border-radius:5px; font-size:13px;
      }
      .st-form-group textarea { resize:vertical; min-height:50px; }
      .st-toggle-row {
        display:flex; align-items:center; gap:8px;
        padding:8px 10px; background:#0f1e35; border:1px solid #0f3460; border-radius:5px;
      }
      .st-toggle-row input[type="checkbox"] {
        width:16px; height:16px; accent-color:#e94560; cursor:pointer;
      }
      .st-toggle-row span { font-size:13px; color:#e0e0e0; }
      .st-pos-row { display:grid; grid-template-columns:1fr 1fr; gap:8px; }
      #st-panel-actions { display:flex; gap:8px; margin-top:12px; }
      #st-panel-actions button { cursor:pointer; border:none; border-radius:6px; padding:7px 14px; font-size:13px; font-weight:600; }

      /* icon picker */
      #st-icon-picker-wrap { margin-top:4px; }
      #st-icon-picker-preview-row {
        display:flex; align-items:center; gap:8px; margin-bottom:6px; min-height:40px;
      }
      #st-icon-preview-img {
        width:40px; height:40px; border-radius:6px; background:#0d1b2a;
        border:2px solid #0f3460; object-fit:contain; flex-shrink:0;
      }
      #st-icon-name-label {
        flex:1; font-size:11px; color:#8899aa; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;
      }
      #st-icon-picker-grid {
        display:flex; flex-wrap:wrap; gap:5px; max-height:110px; overflow-y:auto; padding:2px;
      }
      .st-icon-thumb {
        width:38px; height:38px; border-radius:5px; border:2px solid #0f3460; cursor:pointer;
        object-fit:contain; background:#0d1b2a; transition:border-color .12s, transform .12s;
      }
      .st-icon-thumb:hover { border-color:#e94560; transform:scale(1.08); }
      .st-icon-thumb.selected { border-color:#f0c040; }

      /* requires/modifiers lists */
      .st-req-item, .st-mod-item {
        display:flex; align-items:center; justify-content:space-between;
        background:#0f1e35; border-radius:5px; padding:5px 8px; margin-bottom:4px; font-size:12px;
      }
      .st-req-item button, .st-mod-item button {
        cursor:pointer; border:none; background:#3a1020; color:#e94560;
        border-radius:4px; padding:1px 6px; font-size:11px;
      }
      #st-req-add-row { display:flex; gap:6px; margin-top:4px; }
      #st-req-add-row select, #st-req-add-row button {
        cursor:pointer; border:none; border-radius:5px; padding:5px 10px; font-size:12px; font-weight:600;
      }

      /* skill list */
      #st-rightbar {
        order:5;
        width:260px; min-width:260px; background:#16213e; border-left:2px solid #0f3460;
        display:flex; flex-direction:column; overflow:hidden;
      }
      #st-rightbar-header {
        padding:12px 14px; border-bottom:1px solid #0f3460;
        font-size:11px; color:#8899aa; text-transform:uppercase; letter-spacing:.5px;
      }
      #st-skill-list { flex:1; overflow-y:auto; padding:10px; }
      .st-list-item { padding:6px 8px; border-radius:5px; cursor:pointer; font-size:12px; display:flex; align-items:center; gap:6px; }
      .st-list-item:hover { background:#1c2940; }
      .st-list-item.active { background:#0f3460; }
      .st-type-dot { width:8px; height:8px; border-radius:50%; flex-shrink:0; }

      /* canvas area */
      #st-canvas-area { order:4; flex:1; position:relative; overflow:hidden; cursor:default; }
      #st-canvas-area.panning { cursor:grabbing; }
      #st-grid-bg { position:absolute; top:0; left:0; right:0; bottom:0; pointer-events:none; }
      #st-svg { position:absolute; top:0; left:0; width:100%; height:100%; pointer-events:none; overflow:visible; }
      .trail-glow-anim { animation:trailGlow .8s ease-in-out infinite alternate; }
      @keyframes trailGlow { from{opacity:.6} to{opacity:1} }
      #st-nodes { position:absolute; top:0; left:0; width:100%; height:100%; }
      .skill-node {
        position:absolute; display:flex; flex-direction:column;
        align-items:center; justify-content:center; cursor:pointer;
        border:2px solid #444; text-align:center; user-select:none;
        overflow:hidden;
      }
      .skill-node.dragging { opacity:.7; }
      .skill-node.is-disabled { filter:saturate(.35); }
      .skill-node .node-id   { font-weight:700; color:#e0e0e0; line-height:1.2; padding:0 4px; }
      .skill-node .node-type { color:#8899aa; }
      .skill-node .node-cost { color:#f0c040; }
      .skill-node .node-rarity { font-weight:600; }
      .skill-node .node-icon { object-fit:contain; flex-shrink:0; }
      .skill-node .node-state {
        font-size:10px; font-weight:700; letter-spacing:.4px; text-transform:uppercase;
        color:#000; background:#f0c040; border-radius:999px; padding:2px 6px; margin-top:3px;
      }
      .skill-node .node-overlay {
        position:absolute; right:6px; bottom:4px; min-width:16px;
        font-weight:700; color:#ffffff; text-align:right; pointer-events:none;
        text-shadow:0 1px 2px rgba(0,0,0,.9);
      }
      #st-hud { position:absolute; bottom:8px; right:12px; font-size:11px; color:rgba(255,255,255,.3); pointer-events:none; }

    </style>

    <div id="st-wrap">
      <!-- SIDEBAR -->
      <div id="st-sidebar">
        <div id="st-toolbar">
          <button class="btn-primary"   onclick="ST.addSkill()">+ Add Skill</button>
          <button class="btn-secondary" onclick="ST.resetView()">⌖ Reset View</button>
          <button class="btn-ghost"     onclick="ST.toggleConnStyle()" id="st-conn-btn">⇌ Lines</button>
        </div>
        <div id="st-conn-row">
          Connections:
          <select id="st-conn-select" onchange="ST.setConnStyle(this.value)">
            <option value="trails">Trail Tiles</option>
            <option value="lines">Lines</option>
          </select>
        </div>
         <div id="st-skill-panel">
           <div id="st-empty-state" style="color:#8899aa;font-size:13px;padding:8px">
             Click a skill to edit it, or use <strong>+ Add Skill</strong>.
           </div>
          <div id="st-edit-form" style="display:none">
            <h3 id="st-edit-title">Edit Skill</h3>
            <div class="st-form-group">
              <label>ID</label>
              <input id="st-f-id" type="text" placeholder="BecomeAVampire" />
            </div>
            <div class="st-form-group">
              <label>Display Name</label>
              <input id="st-f-displayname" type="text" />
            </div>
            <div class="st-form-group">
              <label>Description</label>
              <textarea id="st-f-desc"></textarea>
            </div>
             <div class="st-form-group">
               <label>Type</label>
               <select id="st-f-type">
                 <option value="passive">passive</option>
                 <option value="ability">ability</option>
                 <option value="upgrade">upgrade</option>
               </select>
             </div>
             <div class="st-form-group">
               <label>Ability Ref</label>
               <select id="st-f-ability-ref">
                 <option value="">— none —</option>
               </select>
             </div>
             <div class="st-form-group">
               <label>Passive Ref</label>
               <select id="st-f-passive-ref">
                 <option value="">— none —</option>
               </select>
             </div>
             <div class="st-form-group">
               <label>Rarity</label>
               <select id="st-f-rarity">
                <option value="common">common</option>
                <option value="uncommon">uncommon</option>
                <option value="rare">rare</option>
                <option value="epic">epic</option>
                <option value="legendary">legendary</option>
              </select>
            </div>
             <div class="st-form-group">
                <label>Cost</label>
                <input id="st-f-cost" type="number" value="1" min="0" />
              </div>
              <div class="st-form-group">
                <label>Overlay Text</label>
                <input id="st-f-overlaytext" type="text" placeholder="I, II, III, IV..." />
              </div>
              <div class="st-form-group">
                <label>Availability</label>
                <label class="st-toggle-row">
                 <input id="st-f-enabled" type="checkbox" checked />
                 <span>Enabled</span>
               </label>
             </div>
              <div class="st-form-group">
                <label>Icon</label>
                <div id="st-icon-picker-wrap">
                 <div id="st-icon-picker-preview-row">
                   <img id="st-icon-preview-img" src="" alt="" />
                   <span id="st-icon-name-label">None</span>
                   <button class="btn-ghost" style="font-size:11px;padding:4px 8px" onclick="ST.clearIcon()">✕ Clear</button>
                 </div>
                 <div id="st-icon-picker-grid"></div>
                 <input id="st-f-iconpath" type="text" placeholder="Outlander.png" style="display:none;margin-top:6px" oninput="ST.onIconTextInput()" />
               </div>
             </div>
            <div class="st-pos-row">
              <div class="st-form-group">
                <label>Grid X</label>
                <input id="st-f-x" type="number" value="0" />
              </div>
              <div class="st-form-group">
                <label>Grid Y</label>
                <input id="st-f-y" type="number" value="0" />
              </div>
            </div>
            <div class="st-form-group">
              <label>Requires</label>
              <div id="st-requires-list"></div>
              <div id="st-req-add-row">
                <select id="st-req-select" style="flex:1"><option value="">— add requirement —</option></select>
                <button class="btn-secondary" onclick="ST.addRequirement()">Add</button>
              </div>
            </div>
            <div class="st-form-group">
              <label>Inline Modifiers</label>
              <div id="st-mod-list"></div>
            </div>
            <div id="st-panel-actions">
              <button class="btn-primary" onclick="ST.saveSkill()">Save</button>
              <button class="btn-danger"  onclick="ST.deleteSkill()">Delete</button>
            </div>
          </div>
        </div>
       </div>

        <div id="st-main">
          <!-- CANVAS -->
          <div id="st-canvas-area">
            <canvas id="st-grid-bg"></canvas>
            <svg id="st-svg"></svg>
            <div id="st-nodes"></div>
            <div id="st-hud">Middle-mouse or Space+drag to pan · Scroll to zoom · Drag nodes to reposition</div>
          </div>
          <div class="editor-side secondary hidden" id="st-mod-panel"></div>
          <div class="editor-side tertiary hidden" id="st-ref-panel"></div>
          <div id="st-rightbar">
            <div id="st-rightbar-header">All Skills (<span id="st-skill-count">0</span>)</div>
            <div id="st-skill-list"></div>
          </div>
        </div>
      </div>
  `);

  // ── Constants ──────────────────────────────────────────────────────────────
  const TYPE_COLORS = {
    passive: { bg: '#1a3a5c', border: '#2980b9' },
    ability: { bg: '#3a1a1a', border: '#e05252' },
    upgrade: { bg: '#1a3a1a', border: '#52c152' },
  };
  const RARITY_COLORS = {
    common: '#aaaaaa', uncommon: '#2ecc71', rare: '#3498db', epic: '#9b59b6', legendary: '#f39c12',
  };
  const CELL = 96;
  const HALF = CELL / 2;

  // ── State ──────────────────────────────────────────────────────────────────
  let selectedId   = null;
  let connStyle    = 'trails';
  let viewX = 0, viewY = 0, scale = 1;
  let dragState    = null;
  let panState     = null;
  let spaceDown    = false;
  let availableIcons = [];
  let useIconApi = false;

  // ── DOM refs ───────────────────────────────────────────────────────────────
  const wrap    = document.getElementById('st-canvas-area');
  const nodes   = document.getElementById('st-nodes');
  const gridBg  = document.getElementById('st-grid-bg');
  const svg     = document.getElementById('st-svg');
  const modManager = SideEditors.makeModifierManager({
    listEl: document.getElementById('st-mod-list'),
    panelEl: document.getElementById('st-mod-panel'),
    nestedPanelEl: document.getElementById('st-ref-panel'),
    title: 'Skill Modifier',
  });

  function skills() { return window.AppData.tree; }
  function iconSrc(filename) { return filename ? `/api/icons/${encodeURIComponent(filename)}` : ''; }

  async function loadIcons() {
    try {
      const res = await fetch('/api/icons');
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      availableIcons = await res.json();
      useIconApi = true;
    } catch (err) {
      console.warn('Could not load icons:', err);
      availableIcons = [];
      useIconApi = false;
    }

    if (selectedId) {
      const skill = skills().find((item) => item.id === selectedId);
      if (skill) populateIconPicker(skill.iconPath || '');
    }
    render();
  }

  function populateIconPicker(currentIconPath) {
    const grid = document.getElementById('st-icon-picker-grid');
    const preview = document.getElementById('st-icon-preview-img');
    const label = document.getElementById('st-icon-name-label');
    const textInput = document.getElementById('st-f-iconpath');

    textInput.value = currentIconPath || '';

    if (!useIconApi) {
      grid.style.display = 'none';
      textInput.style.display = '';
      preview.src = '';
      preview.style.display = 'none';
      label.textContent = currentIconPath || 'None';
      return;
    }

    textInput.style.display = 'none';
    grid.style.display = '';
    preview.style.display = '';

    if (currentIconPath) {
      preview.src = iconSrc(currentIconPath);
      label.textContent = currentIconPath;
    } else {
      preview.src = '';
      label.textContent = 'None';
    }

    grid.innerHTML = '';
    availableIcons.forEach((filename) => {
      const img = document.createElement('img');
      img.className = 'st-icon-thumb' + (filename === currentIconPath ? ' selected' : '');
      img.src = iconSrc(filename);
      img.title = filename;
      img.alt = filename;
      img.addEventListener('click', () => selectIcon(filename));
      grid.appendChild(img);
    });
  }

  function selectIcon(filename) {
    if (!selectedId) return;
    const skill = skills().find((item) => item.id === selectedId);
    if (!skill) return;
    skill.iconPath = filename;
    document.getElementById('st-f-iconpath').value = filename;
    App.markDirty();
    populateIconPicker(filename);
    renderNodes();
  }

  function clearIcon() {
    if (!selectedId) return;
    const skill = skills().find((item) => item.id === selectedId);
    if (!skill) return;
    skill.iconPath = '';
    document.getElementById('st-f-iconpath').value = '';
    App.markDirty();
    populateIconPicker('');
    renderNodes();
  }

  function onIconTextInput() {
    if (!selectedId) return;
    const skill = skills().find((item) => item.id === selectedId);
    if (!skill) return;
    skill.iconPath = document.getElementById('st-f-iconpath').value.trim();
    App.markDirty();
    renderNodes();
  }

  // ── Coordinate helpers ─────────────────────────────────────────────────────
  function gridToScreen(gx, gy) {
    return { x: gx * CELL * scale + viewX, y: -gy * CELL * scale + viewY };
  }
  function screenToGrid(sx, sy) {
    return {
      x: Math.round((sx - viewX) / (CELL * scale)),
      y: Math.round(-(sy - viewY) / (CELL * scale)),
    };
  }

  // ── Render ─────────────────────────────────────────────────────────────────
  function render() { renderConnections(); renderNodes(); renderList(); }

  function renderNodes() {
    const ids = new Set(skills().map(s => s.id));
    nodes.querySelectorAll('.skill-node').forEach(el => { if (!ids.has(el.dataset.id)) el.remove(); });

    skills().forEach(skill => {
      let el = nodes.querySelector(`.skill-node[data-id="${CSS.escape(skill.id)}"]`);
      if (!el) {
        el = document.createElement('div');
        el.className = 'skill-node';
        el.dataset.id = skill.id;
        el.addEventListener('mousedown', onNodeMouseDown);
        el.addEventListener('click', onNodeClick);
        nodes.appendChild(el);
      }
      const pos = gridToScreen(skill.position.x, skill.position.y);
      const sz  = CELL * scale;
      el.style.cssText = `left:${pos.x - sz/2}px;top:${pos.y - sz/2}px;width:${sz}px;height:${sz}px;border-radius:${10*scale}px;`;
      const tc = TYPE_COLORS[skill.type] || TYPE_COLORS.passive;
      el.style.background   = tc.bg;
      el.style.borderColor  = skill.id === selectedId ? '#f0c040' : tc.border;
      el.style.borderWidth  = (2 * scale) + 'px';
      el.style.borderStyle  = 'solid';
      el.style.boxShadow    = skill.id === selectedId ? `0 0 0 ${3*scale}px rgba(240,192,64,.4)` : '';
      const fs1 = Math.max(7, Math.round(9 * scale));
      const fs2 = Math.max(6, Math.round(7 * scale));
      const rc  = RARITY_COLORS[skill.rarity] || RARITY_COLORS.common;
      const iconSize = Math.round(36 * scale);
      const isEnabled = skill.enabled !== false;
      const iconHtml = (useIconApi && skill.iconPath)
        ? `<img class="node-icon" src="${iconSrc(skill.iconPath)}" alt="" style="width:${iconSize}px;height:${iconSize}px" />`
        : '';
      const overlayText = skill.overlayText ? String(skill.overlayText).trim() : '';
      el.classList.toggle('is-disabled', !isEnabled);
      el.style.opacity = isEnabled ? '1' : '0.72';
      el.innerHTML = `
        ${iconHtml}
        <div class="node-id" style="font-size:${fs1}px">${skill.displayName || skill.id}</div>
        ${iconHtml ? '' : `<div class="node-type" style="font-size:${fs2}px;color:#8899aa">${skill.type}</div>`}
        <div class="node-cost" style="font-size:${Math.max(7,Math.round(9*scale))}px">⭐ ${skill.cost}</div>
        <div class="node-rarity" style="font-size:${fs2}px;color:${rc}">${skill.rarity || 'common'}</div>
        ${isEnabled ? '' : `<div class="node-state">WIP</div>`}
        ${overlayText ? `<div class="node-overlay" style="font-size:${Math.max(8, Math.round(11 * scale))}px">${overlayText}</div>` : ''}
      `;
    });
  }

  function renderConnections() {
    svg.innerHTML = '';
    const map = new Map(skills().map(s => [s.id, s]));
    if (connStyle === 'lines') {
      skills().forEach(skill => {
        const to = gridToScreen(skill.position.x, skill.position.y);
        (skill.requires || []).forEach(reqId => {
          const req = map.get(reqId); if (!req) return;
          const from = gridToScreen(req.position.x, req.position.y);
          const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
          line.setAttribute('x1', from.x); line.setAttribute('y1', from.y);
          line.setAttribute('x2', to.x);   line.setAttribute('y2', to.y);
          line.setAttribute('stroke', TYPE_COLORS[skill.type]?.border || '#888');
          line.setAttribute('stroke-width', Math.max(1, 2 * scale));
          line.setAttribute('stroke-dasharray', '6 4');
          line.setAttribute('opacity', '0.55');
          svg.appendChild(line);
        });
      });
    } else {
      const STRIP = Math.max(2, Math.round(12 * scale));
      const revDep = new Map();
      skills().forEach(s => (s.requires||[]).forEach(r => { if (!revDep.has(r)) revDep.set(r,[]); revDep.get(r).push(s.id); }));
      const glowKeys = new Set();
      if (selectedId) (revDep.get(selectedId)||[]).forEach(u => glowKeys.add(selectedId+'->'+u));
      const conns = [];
      skills().forEach(s => (s.requires||[]).forEach(r => { const req=map.get(r); if(req) conns.push({req,skill:s}); }));
      conns.forEach(({req,skill}) => drawTrail(req.position, skill.position, STRIP, '#5aaeff', 1.0, false));
      conns.forEach(({req,skill}) => { if(glowKeys.has(req.id+'->'+skill.id)) drawTrail(req.position, skill.position, STRIP, '#ffe066', 1.0, true); });
    }
  }

  function drawTrail(from, to, stripPx, color, opacity, glow) {
    const p0 = gridToScreen(from.x, from.y);
    const p1 = gridToScreen(to.x, to.y);
    const pc = gridToScreen(to.x, from.y);
    if (from.x === to.x) {
      svgRect(p0.x - stripPx/2, Math.min(p0.y, p1.y), stripPx, Math.abs(p1.y - p0.y), color, opacity, glow);
    } else if (from.y === to.y) {
      svgRect(Math.min(p0.x, p1.x), p0.y - stripPx/2, Math.abs(p1.x - p0.x), stripPx, color, opacity, glow);
    } else {
      svgRect(Math.min(p0.x, pc.x), p0.y - stripPx/2, Math.abs(pc.x - p0.x), stripPx, color, opacity, glow);
      svgRect(pc.x - stripPx/2, Math.min(pc.y, p1.y), stripPx, Math.abs(p1.y - pc.y), color, opacity, glow);
    }
  }

  function svgRect(x, y, w, h, color, opacity, glow) {
    if (w < 0.5 || h < 0.5) return;
    const r = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    r.setAttribute('x', x); r.setAttribute('y', y);
    r.setAttribute('width', w); r.setAttribute('height', h);
    r.setAttribute('fill', color); r.setAttribute('opacity', opacity);
    if (glow) r.classList.add('trail-glow-anim');
    svg.appendChild(r);
  }

  function renderList() {
    const list = document.getElementById('st-skill-list');
    document.getElementById('st-skill-count').textContent = skills().length;
    list.innerHTML = '';
    skills().forEach(s => {
      const el = document.createElement('div');
      el.className = 'st-list-item' + (s.id === selectedId ? ' active' : '');
      const tc = TYPE_COLORS[s.type] || TYPE_COLORS.passive;
      const label = `${s.displayName || s.id}${s.enabled === false ? ' [WIP]' : ''}`;
      el.innerHTML = `<span class="st-type-dot" style="background:${s.enabled === false ? '#000000' : tc.border}"></span>${label}`;
      el.style.opacity = s.enabled === false ? '0.72' : '1';
      el.onclick = () => selectSkill(s.id);
      list.appendChild(el);
    });
    list.querySelector('.st-list-item.active')?.scrollIntoView({ block: 'nearest' });
  }

  // ── Grid ───────────────────────────────────────────────────────────────────
  function drawGrid() {
    const w = wrap.clientWidth, h = wrap.clientHeight;
    gridBg.width = w; gridBg.height = h;
    const ctx = gridBg.getContext('2d');
    ctx.clearRect(0, 0, w, h);
    const step = CELL * scale;
    const offX = ((viewX % step) + step) % step;
    const offY = ((viewY % step) + step) % step;
    ctx.strokeStyle = 'rgba(255,255,255,0.04)'; ctx.lineWidth = 1;
    for (let x = offX; x < w; x += step) { ctx.beginPath(); ctx.moveTo(x,0); ctx.lineTo(x,h); ctx.stroke(); }
    for (let y = offY; y < h; y += step) { ctx.beginPath(); ctx.moveTo(0,y); ctx.lineTo(w,y); ctx.stroke(); }
    ctx.strokeStyle = 'rgba(255,255,255,0.12)'; ctx.lineWidth = 1.5;
    if (viewX >= 0 && viewX <= w) { ctx.beginPath(); ctx.moveTo(viewX,0); ctx.lineTo(viewX,h); ctx.stroke(); }
    if (viewY >= 0 && viewY <= h) { ctx.beginPath(); ctx.moveTo(0,viewY); ctx.lineTo(w,viewY); ctx.stroke(); }
  }

  window.addEventListener('resize', () => { drawGrid(); render(); });

  // ── Pan & Zoom ─────────────────────────────────────────────────────────────
  function resetView() {
    const w = wrap.clientWidth  || wrap.offsetWidth  || 800;
    const h = wrap.clientHeight || wrap.offsetHeight || 600;
    viewX = w / 2; viewY = h / 2; scale = 1;
    drawGrid(); render();
  }

  wrap.addEventListener('wheel', e => {
    e.preventDefault();
    const f = e.deltaY < 0 ? 1.1 : 0.9;
    const rect = wrap.getBoundingClientRect();
    const mx = e.clientX - rect.left, my = e.clientY - rect.top;
    viewX = mx - (mx - viewX) * f; viewY = my - (my - viewY) * f;
    scale = Math.min(3, Math.max(0.2, scale * f));
    drawGrid(); render();
  }, { passive: false });

  wrap.addEventListener('contextmenu', e => e.preventDefault());
  wrap.addEventListener('mousedown', e => {
    if (e.button === 1 || e.button === 2 || (e.button === 0 && spaceDown)) {
      e.preventDefault();
      panState = { startMouseX: e.clientX, startMouseY: e.clientY, startViewX: viewX, startViewY: viewY };
      wrap.classList.add('panning');
    }
  });
  window.addEventListener('mousemove', e => {
    if (panState) {
      viewX = panState.startViewX + (e.clientX - panState.startMouseX);
      viewY = panState.startViewY + (e.clientY - panState.startMouseY);
      drawGrid(); render();
    }
    if (dragState) moveDrag(e);
  });
  window.addEventListener('mouseup', () => {
    if (panState) { panState = null; wrap.classList.remove('panning'); }
    if (dragState) { nodes.querySelector(`.skill-node[data-id="${CSS.escape(dragState.id)}"]`)?.classList.remove('dragging'); dragState = null; }
  });
  window.addEventListener('keydown', e => { if (e.code === 'Space') spaceDown = true; });
  window.addEventListener('keyup',   e => { if (e.code === 'Space') spaceDown = false; });

  // ── Drag ───────────────────────────────────────────────────────────────────
  function onNodeMouseDown(e) {
    if (e.button !== 0 || spaceDown) return;
    e.stopPropagation();
    const id = e.currentTarget.dataset.id;
    const s  = skills().find(s => s.id === id); if (!s) return;
    dragState = { id, startMouseX: e.clientX, startMouseY: e.clientY, startGridX: s.position.x, startGridY: s.position.y, moved: false };
    e.currentTarget.classList.add('dragging');
  }
  function moveDrag(e) {
    if (Math.abs(e.clientX - dragState.startMouseX) > 4 || Math.abs(e.clientY - dragState.startMouseY) > 4) dragState.moved = true;
    if (!dragState.moved) return;
    const rect = wrap.getBoundingClientRect();
    const grid = screenToGrid(e.clientX - rect.left, e.clientY - rect.top);
    const s = skills().find(s => s.id === dragState.id); if (!s) return;
    s.position.x = grid.x; s.position.y = grid.y;
    App.markDirty(); render();
    if (selectedId === dragState.id) { document.getElementById('st-f-x').value = grid.x; document.getElementById('st-f-y').value = grid.y; }
  }
  function onNodeClick(e) { if (dragState?.moved) return; selectSkill(e.currentTarget.dataset.id); }

  // ── Selection / Form ───────────────────────────────────────────────────────
  function selectSkill(id) {
    selectedId = id;
    const s = skills().find(s => s.id === id); if (!s) return;
    document.getElementById('st-empty-state').style.display = 'none';
    document.getElementById('st-edit-form').style.display   = '';
    document.getElementById('st-edit-title').textContent = 'Edit: ' + (s.displayName || id);
    document.getElementById('st-f-id').value          = s.id;
    document.getElementById('st-f-displayname').value = s.displayName || '';
    document.getElementById('st-f-desc').value        = s.description || '';
    document.getElementById('st-f-type').value        = s.type || 'passive';
    SideEditors.fillSelect(document.getElementById('st-f-ability-ref'), 'abilities', s.abilityId, { nullable: true });
    SideEditors.fillSelect(document.getElementById('st-f-passive-ref'), 'passives', s.passiveId, { nullable: true });
    document.getElementById('st-f-rarity').value      = s.rarity || 'common';
    document.getElementById('st-f-cost').value        = s.cost ?? 1;
    document.getElementById('st-f-overlaytext').value = s.overlayText || '';
    document.getElementById('st-f-enabled').checked   = s.enabled !== false;
    document.getElementById('st-f-x').value           = s.position.x;
    document.getElementById('st-f-y').value           = s.position.y;
    document.getElementById('st-f-iconpath').value    = s.iconPath || '';
    populateIconPicker(s.iconPath || '');
    renderRequiresList(s); renderReqSelect(s); modManager.setValue(s.modifiers || []);
    render();
  }

  function renderRequiresList(s) {
    const el = document.getElementById('st-requires-list');
    el.innerHTML = '';
    (s.requires || []).forEach(r => {
      const d = document.createElement('div'); d.className = 'st-req-item';
      d.innerHTML = `<span>${r}</span><button onclick="ST.removeRequirement('${r}')">✕</button>`;
      el.appendChild(d);
    });
  }
  function renderReqSelect(s) {
    const sel = document.getElementById('st-req-select');
    sel.innerHTML = '<option value="">— add requirement —</option>';
    skills().filter(x => x.id !== s.id && !(s.requires||[]).includes(x.id)).forEach(x => {
      const opt = document.createElement('option'); opt.value = x.id; opt.textContent = x.id;
      sel.appendChild(opt);
    });
  }
  // ── Skill CRUD ─────────────────────────────────────────────────────────────
  function addSkill() {
    const occupied = new Set(skills().map(s => `${s.position.x},${s.position.y}`));
    let gx = 0, gy = 0;
    outer: for (let r = 0; r < 50; r++) for (let dx = -r; dx <= r; dx++) for (let dy = -r; dy <= r; dy++) {
      if ((Math.abs(dx) === r || Math.abs(dy) === r) && !occupied.has(`${dx},${dy}`)) { gx=dx; gy=dy; break outer; }
    }
    const id = 'NewSkill_' + (skills().length + 1);
    skills().push({
      id,
      displayName: '',
      description: '',
      enabled: true,
      cost: 1,
      overlayText: '',
      iconPath: '',
      position: { x: gx, y: gy },
      type: 'passive',
      rarity: 'common',
      passiveId: id,
      abilityId: '',
      tags: [],
      requires: [],
      modifiers: [],
      triggers: [],
      actions: [],
    });
    App.markDirty(); render(); selectSkill(id);
  }

  function saveSkill() {
    if (!selectedId) return;
    const s = skills().find(s => s.id === selectedId); if (!s) return;
    const newId = document.getElementById('st-f-id').value.trim();
    if (!newId) { alert('ID cannot be empty'); return; }
    if (newId !== selectedId && skills().some(x => x.id === newId)) { alert('A skill with that ID already exists'); return; }
    if (newId !== selectedId) {
      skills().forEach(x => { x.requires = (x.requires||[]).map(r => r === selectedId ? newId : r); });
      s.id = newId; selectedId = newId;
    }
    s.displayName = document.getElementById('st-f-displayname').value.trim() || newId;
    s.description = document.getElementById('st-f-desc').value.trim();
    s.type        = document.getElementById('st-f-type').value;
    const abilityRef = document.getElementById('st-f-ability-ref').value || '';
    const passiveRef = document.getElementById('st-f-passive-ref').value || '';
    s.abilityId   = s.type === 'ability' ? (abilityRef || newId) : (s.type === 'upgrade' ? abilityRef : '');
    s.passiveId   = s.type === 'passive' ? (passiveRef || newId) : (s.type === 'upgrade' ? passiveRef : '');
    s.rarity      = document.getElementById('st-f-rarity').value;
    s.cost        = parseInt(document.getElementById('st-f-cost').value) || 1;
    s.overlayText = document.getElementById('st-f-overlaytext').value.trim();
    s.enabled     = document.getElementById('st-f-enabled').checked;
    s.position.x  = parseInt(document.getElementById('st-f-x').value) || 0;
    s.position.y  = parseInt(document.getElementById('st-f-y').value) || 0;
    s.iconPath    = document.getElementById('st-f-iconpath').value.trim();
    s.modifiers   = modManager.getValue();
    App.markDirty();
    document.getElementById('st-edit-title').textContent = 'Edit: ' + s.displayName;
    render();
  }

  function deleteSkill() {
    if (!selectedId || !confirm(`Delete "${selectedId}"?`)) return;
    const idx = skills().findIndex(s => s.id === selectedId);
    if (idx !== -1) skills().splice(idx, 1);
    skills().forEach(s => { s.requires = (s.requires||[]).filter(r => r !== selectedId); });
    App.markDirty(); selectedId = null;
    modManager.closeEditor();
    document.getElementById('st-empty-state').style.display = '';
    document.getElementById('st-edit-form').style.display   = 'none';
    render();
  }

  function addRequirement() {
    const s = skills().find(s => s.id === selectedId); if (!s) return;
    const v = document.getElementById('st-req-select').value; if (!v) return;
    if (!(s.requires||[]).includes(v)) { if (!s.requires) s.requires=[]; s.requires.push(v); }
    App.markDirty(); renderRequiresList(s); renderReqSelect(s); render();
  }
  function removeRequirement(reqId) {
    const s = skills().find(s => s.id === selectedId); if (!s) return;
    s.requires = (s.requires||[]).filter(r => r !== reqId);
    App.markDirty(); renderRequiresList(s); renderReqSelect(s); render();
  }

  function openModModal(index) {
    modManager.openEditor(index);
  }

  // ── Conn style ─────────────────────────────────────────────────────────────
  function setConnStyle(v) { connStyle = v; render(); }
  function toggleConnStyle() {
    const next = connStyle === 'trails' ? 'lines' : 'trails';
    document.getElementById('st-conn-select').value = next;
    setConnStyle(next);
  }

  // ── Tab activation ─────────────────────────────────────────────────────────
  function refresh() {
    resetView();
    if (selectedId && !skills().find(s => s.id === selectedId)) {
      selectedId = null;
      modManager.closeEditor();
      document.getElementById('st-empty-state').style.display = '';
      document.getElementById('st-edit-form').style.display   = 'none';
    } else if (selectedId) {
      selectSkill(selectedId);
    }
    render();
  }

  App.onTabActivated('skill-tree', refresh);

  // ── Public API (called from inline onclick) ────────────────────────────────
  window.ST = {
    addSkill,
    saveSkill,
    deleteSkill,
    resetView,
    setConnStyle,
    toggleConnStyle,
    addRequirement,
    removeRequirement,
    openModModal,
    clearIcon,
    onIconTextInput,
  };

  // Initial draw — wait two frames so the pane has been laid out
  requestAnimationFrame(() => requestAnimationFrame(() => resetView()));
  loadIcons();
})();
