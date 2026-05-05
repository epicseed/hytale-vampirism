(() => {
  const pane = document.getElementById('pane-rituals');
  const GLYPH_API = '/api/ritual-glyphs';
  const FALLBACK_GLYPHS = [
    { id: 'fang_wake', displayName: 'Fang Wake', url: '/api/ritual-glyphs/fang_wake' },
    { id: 'moon_scar', displayName: 'Moon Scar', url: '/api/ritual-glyphs/moon_scar' },
    { id: 'blood_spiral', displayName: 'Blood Spiral', url: '/api/ritual-glyphs/blood_spiral' },
    { id: 'vein_eye', displayName: 'Vein Eye', url: '/api/ritual-glyphs/vein_eye' },
    { id: 'crown_claw', displayName: 'Crown Claw', url: '/api/ritual-glyphs/crown_claw' },
    { id: 'generic', displayName: 'Generic', url: '/api/ritual-glyphs/generic' },
  ];

  const state = {
    glyphs: [],
    glyphsLoaded: false,
    glyphError: '',
    loadingGlyphs: false,
    templateIndex: 0,
    pointIndex: 0,
    stepIndex: 0,
    previewDrag: null,
  };

  let cssInjected = false;
  let glyphLoadPromise = null;

  function ensureCss() {
    if (cssInjected) return;
    cssInjected = true;
    const style = document.createElement('style');
    style.textContent = `
      .ritual-layout {
        display:flex;
        flex:1;
        min-height:0;
        overflow:hidden;
      }
      .ritual-sidebar {
        width:320px;
        min-width:320px;
        border-right:1px solid var(--border);
        background:#13243d;
        display:flex;
        flex-direction:column;
        min-height:0;
      }
      .ritual-sidebar .pane-toolbar {
        margin:0;
        padding:16px 16px 10px;
      }
      .ritual-sidebar-section {
        padding:0 16px 16px;
        overflow:auto;
      }
      .ritual-list {
        display:flex;
        flex-direction:column;
        gap:8px;
      }
      .ritual-list-item {
        border:1px solid #274163;
        background:#0f1d33;
        border-radius:8px;
        padding:10px;
      }
      .ritual-list-item.active {
        border-color:var(--accent);
        background:#182a45;
      }
      .ritual-list-item-header {
        display:flex;
        gap:8px;
        align-items:flex-start;
      }
      .ritual-list-item-main {
        flex:1;
        min-width:0;
        cursor:pointer;
      }
      .ritual-list-item-title {
        font-weight:700;
        font-size:13px;
      }
      .ritual-list-item-subtitle {
        color:var(--text-dim);
        font-size:11px;
        margin-top:3px;
        word-break:break-word;
      }
      .ritual-list-item-actions {
        display:flex;
        gap:4px;
      }
      .ritual-list-item-actions button,
      .ritual-step-row button,
      .ritual-empty button {
        cursor:pointer;
        border:none;
        border-radius:5px;
        padding:5px 9px;
        font-size:11px;
        font-weight:700;
      }
      .ritual-main {
        flex:1;
        min-width:0;
        overflow:auto;
      }
      .ritual-main-inner {
        padding:20px;
        display:flex;
        flex-direction:column;
        gap:18px;
      }
      .ritual-card {
        background:var(--surface);
        border:1px solid var(--border);
        border-radius:10px;
        padding:16px;
      }
      .ritual-card h3 {
        font-size:13px;
        color:var(--accent);
        text-transform:uppercase;
        letter-spacing:.5px;
        margin-bottom:12px;
      }
      .ritual-form-grid {
        display:grid;
        grid-template-columns:repeat(auto-fit, minmax(180px, 1fr));
        gap:12px;
      }
      .ritual-form-grid .form-group {
        margin-bottom:0;
      }
      .ritual-preview-layout {
        display:grid;
        grid-template-columns:minmax(320px, 460px) minmax(320px, 1fr);
        gap:16px;
        align-items:start;
      }
      .ritual-preview-stage {
        position:relative;
        aspect-ratio:1;
        border:1px solid var(--border);
        border-radius:10px;
        overflow:hidden;
        background:
          linear-gradient(to right, rgba(255,255,255,0.04) 1px, transparent 1px),
          linear-gradient(to bottom, rgba(255,255,255,0.04) 1px, transparent 1px),
          radial-gradient(circle at center, rgba(233,69,96,0.14), rgba(13,27,42,0.92));
        background-size:10% 10%, 10% 10%, auto;
      }
      .ritual-preview-stage img,
      .ritual-preview-stage svg {
        position:absolute;
        inset:10%;
        width:80%;
        height:80%;
      }
      .ritual-preview-stage img {
        object-fit:contain;
        pointer-events:none;
        user-select:none;
        opacity:0.9;
      }
      .ritual-preview-stage svg {
        z-index:2;
        touch-action:none;
      }
      .ritual-preview-help {
        margin-top:10px;
        font-size:11px;
        line-height:1.5;
        color:var(--text-dim);
      }
      .ritual-step-header {
        display:flex;
        justify-content:space-between;
        gap:8px;
        align-items:center;
        margin-bottom:10px;
      }
      .ritual-step-list {
        display:flex;
        flex-direction:column;
        gap:8px;
      }
      .ritual-step-row {
        display:grid;
        grid-template-columns:auto repeat(3, minmax(0, 1fr)) auto auto auto;
        gap:6px;
        align-items:center;
        background:#102038;
        border:1px solid #1c3456;
        border-radius:8px;
        padding:8px;
      }
      .ritual-step-row.active {
        border-color:var(--accent);
        background:#182a45;
      }
      .ritual-step-index {
        min-width:70px;
        font-size:12px;
        font-weight:700;
        color:var(--text);
        cursor:pointer;
      }
      .ritual-step-row input {
        width:100%;
        background:var(--input-bg);
        border:1px solid var(--border);
        color:var(--text);
        padding:6px 8px;
        border-radius:5px;
        font-size:12px;
      }
      .ritual-json-preview {
        display:grid;
        grid-template-columns:1fr 1fr;
        gap:12px;
      }
      .ritual-json-preview textarea {
        width:100%;
        min-height:180px;
        resize:vertical;
        background:var(--input-bg);
        border:1px solid var(--border);
        color:var(--text);
        padding:8px 10px;
        border-radius:5px;
        font-size:12px;
        font-family:ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      }
      .ritual-empty {
        display:flex;
        flex-direction:column;
        gap:10px;
        align-items:flex-start;
        color:var(--text-dim);
        line-height:1.6;
      }
      .ritual-warning {
        margin-top:8px;
        color:#f0c040;
        font-size:11px;
      }
      @media (max-width: 1280px) {
        .ritual-preview-layout,
        .ritual-json-preview {
          grid-template-columns:1fr;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function getDoc() {
    if (!window.AppData.ritualTemplates || typeof window.AppData.ritualTemplates !== 'object' || Array.isArray(window.AppData.ritualTemplates)) {
      window.AppData.ritualTemplates = { templates: [] };
    }
    if (!Array.isArray(window.AppData.ritualTemplates.templates)) {
      window.AppData.ritualTemplates.templates = [];
    }
    return window.AppData.ritualTemplates;
  }

  function templates() {
    return getDoc().templates;
  }

  function clampSelection() {
    const allTemplates = templates();
    if (allTemplates.length === 0) {
      state.templateIndex = 0;
      state.pointIndex = 0;
      state.stepIndex = 0;
      return;
    }
    state.templateIndex = Math.max(0, Math.min(state.templateIndex, allTemplates.length - 1));
    const template = allTemplates[state.templateIndex];
    template.points = Array.isArray(template.points) ? template.points : [];
    if (template.points.length === 0) {
      state.pointIndex = 0;
      state.stepIndex = 0;
      return;
    }
    state.pointIndex = Math.max(0, Math.min(state.pointIndex, template.points.length - 1));
    const point = template.points[state.pointIndex];
    point.traceSteps = Array.isArray(point.traceSteps) ? point.traceSteps : [];
    if (point.traceSteps.length === 0) {
      point.traceSteps.push(makeStep(0, 0, 0));
    }
    state.stepIndex = Math.max(0, Math.min(state.stepIndex, point.traceSteps.length - 1));
  }

  function selectedTemplate() {
    clampSelection();
    return templates()[state.templateIndex] || null;
  }

  function selectedPoint() {
    const template = selectedTemplate();
    if (!template || !template.points?.length) return null;
    return template.points[state.pointIndex] || null;
  }

  function selectedStep() {
    const point = selectedPoint();
    if (!point || !point.traceSteps?.length) return null;
    return point.traceSteps[state.stepIndex] || null;
  }

  function availableGlyphs() {
    const dedup = new Map();
    [...FALLBACK_GLYPHS, ...state.glyphs].forEach((glyph) => {
      if (glyph?.id) dedup.set(glyph.id, glyph);
    });
    templates().forEach((template) => {
      (template.points || []).forEach((point) => {
        const id = normalizeSymbolId(point.symbolId || '');
        if (id && !dedup.has(id)) {
          dedup.set(id, { id, displayName: humanizeId(id), url: `${GLYPH_API}/${id}` });
        }
      });
    });
    return [...dedup.values()].sort((a, b) => {
      if (a.id === 'generic') return 1;
      if (b.id === 'generic') return -1;
      return a.displayName.localeCompare(b.displayName);
    });
  }

  function glyphById(id) {
    return availableGlyphs().find((glyph) => glyph.id === normalizeSymbolId(id));
  }

  function defaultGlyph() {
    return availableGlyphs().find((glyph) => glyph.id !== 'generic') || availableGlyphs()[0] || FALLBACK_GLYPHS[0];
  }

  function makeTemplate(index = templates().length + 1) {
    return {
      ritualId: `ritual_${index}`,
      displayName: `New Ritual ${index}`,
      requiredAnchorBlockId: 'Furniture_Ancient_Coffin',
      pointTolerance: 0.95,
      channelDurationSeconds: 8,
      baseStability: 70,
      baseCorruption: 8,
      instabilityThreshold: 30,
      points: [makePoint(1)],
    };
  }

  function makePoint(index = 1) {
    const glyph = defaultGlyph();
    return {
      id: `point_${index}`,
      offsetX: 0,
      offsetY: 0.15,
      offsetZ: 0,
      symbolId: glyph?.id || 'generic',
      symbolName: glyph?.displayName || 'Generic',
      traceTolerance: 0.48,
      mistakeStabilityPenalty: 6,
      mistakeCorruptionPenalty: 5,
      traceSteps: [makeStep(0, 0, 0)],
    };
  }

  function makeStep(offsetX = 0, offsetY = 0, offsetZ = 0) {
    return { offsetX, offsetY, offsetZ };
  }

  async function ensureGlyphsLoaded() {
    if (state.glyphsLoaded || state.loadingGlyphs) return glyphLoadPromise;
    state.loadingGlyphs = true;
    glyphLoadPromise = fetch(GLYPH_API)
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
      })
      .then((payload) => {
        state.glyphs = Array.isArray(payload.glyphs) ? payload.glyphs : [];
        state.glyphError = '';
        state.glyphsLoaded = true;
      })
      .catch((error) => {
        state.glyphError = error.message;
        state.glyphs = [];
      })
      .finally(() => {
        state.loadingGlyphs = false;
        render();
      });
    return glyphLoadPromise;
  }

  function render() {
    ensureCss();
    clampSelection();
    const template = selectedTemplate();
    const point = selectedPoint();

    pane.innerHTML = `
      <div class="ritual-layout">
        <aside class="ritual-sidebar">
          <div class="pane-toolbar">
            <h2>Ritual Templates</h2>
            <button class="btn-primary" type="button" data-action="add-template">+ Template</button>
          </div>
          <div class="ritual-sidebar-section">
            <div class="ritual-list">${renderTemplateList()}</div>
          </div>
          <div class="pane-toolbar">
            <h2>Template Points</h2>
            <button class="btn-primary" type="button" data-action="add-point"${template ? '' : ' disabled'}>+ Point</button>
          </div>
          <div class="ritual-sidebar-section">
            <div class="ritual-list">${renderPointList(template)}</div>
          </div>
        </aside>
        <main class="ritual-main">
          <div class="ritual-main-inner">
            ${template ? renderEditor(template, point) : renderEmptyState()}
          </div>
        </main>
      </div>
    `;

    attachHandlers();
    renderPreview();
  }

  function renderTemplateList() {
    return templates().map((template, index) => `
      <div class="ritual-list-item ${index === state.templateIndex ? 'active' : ''}">
        <div class="ritual-list-item-header">
          <div class="ritual-list-item-main" data-template-select="${index}">
            <div class="ritual-list-item-title">${escapeHtml(template.displayName || template.ritualId || `Template ${index + 1}`)}</div>
            <div class="ritual-list-item-subtitle"><code>${escapeHtml(template.ritualId || '(missing ritualId)')}</code> · ${(template.points || []).length} point(s)</div>
          </div>
          <div class="ritual-list-item-actions">
            <button class="btn-delete" type="button" data-template-delete="${index}">✕</button>
          </div>
        </div>
      </div>
    `).join('') || `<div class="ritual-empty">No ritual templates yet.<button class="btn-primary" type="button" data-action="add-template">Create first template</button></div>`;
  }

  function renderPointList(template) {
    if (!template) {
      return `<div class="ritual-empty">Select or create a ritual template first.</div>`;
    }
    return (template.points || []).map((point, index) => `
      <div class="ritual-list-item ${index === state.pointIndex ? 'active' : ''}">
        <div class="ritual-list-item-header">
          <div class="ritual-list-item-main" data-point-select="${index}">
            <div class="ritual-list-item-title">${escapeHtml(point.symbolName || point.id || `Point ${index + 1}`)}</div>
            <div class="ritual-list-item-subtitle"><code>${escapeHtml(point.id || '(missing id)')}</code> · ${(point.traceSteps || []).length} trace step(s)</div>
          </div>
          <div class="ritual-list-item-actions">
            <button class="btn-delete" type="button" data-point-delete="${index}">✕</button>
          </div>
        </div>
      </div>
    `).join('') || `<div class="ritual-empty">This ritual has no points yet.<button class="btn-primary" type="button" data-action="add-point">Add first point</button></div>`;
  }

  function renderEditor(template, point) {
    return `
      <section class="ritual-card">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;gap:10px">
          <h3 style="margin:0">Template Settings</h3>
          <div class="editor-help">This writes directly to <code>data/vampirism/rituals/templates.json</code> via the main Save button.</div>
        </div>
        <div class="ritual-form-grid">
          ${inputGroup('Ritual ID', 'text', template.ritualId, { 'data-template-field': 'ritualId', placeholder: 'awakening' })}
          ${inputGroup('Display Name', 'text', template.displayName, { 'data-template-field': 'displayName', placeholder: 'Crimson Awakening' })}
          ${inputGroup('Required Anchor Block ID', 'text', template.requiredAnchorBlockId, { 'data-template-field': 'requiredAnchorBlockId', placeholder: 'Furniture_Ancient_Coffin' })}
          ${inputGroup('Point Tolerance', 'number', template.pointTolerance, { 'data-template-field': 'pointTolerance', step: '0.01' })}
          ${inputGroup('Channel Duration (s)', 'number', template.channelDurationSeconds, { 'data-template-field': 'channelDurationSeconds', step: '0.1' })}
          ${inputGroup('Base Stability', 'number', template.baseStability, { 'data-template-field': 'baseStability', step: '0.1' })}
          ${inputGroup('Base Corruption', 'number', template.baseCorruption, { 'data-template-field': 'baseCorruption', step: '0.1' })}
          ${inputGroup('Instability Threshold', 'number', template.instabilityThreshold, { 'data-template-field': 'instabilityThreshold', step: '0.1' })}
        </div>
      </section>
      <section class="ritual-card">
        <h3>Point Settings</h3>
        ${point ? renderPointEditor(point) : `<div class="ritual-empty">Create a point to start authoring the sigil trace.</div>`}
      </section>
      ${point ? renderPreviewSection(template, point) : ''}
      ${point ? renderJsonSection(template, point) : ''}
    `;
  }

  function renderPointEditor(point) {
    return `
      <div class="ritual-form-grid">
        ${inputGroup('Point ID', 'text', point.id, { 'data-point-field': 'id', placeholder: 'north' })}
        ${renderGlyphSelect(point.symbolId)}
        ${inputGroup('Symbol Name', 'text', point.symbolName, { 'data-point-field': 'symbolName', placeholder: 'Fang Wake' })}
        ${inputGroup('Offset X', 'number', point.offsetX, { 'data-point-field': 'offsetX', step: '0.01' })}
        ${inputGroup('Offset Y', 'number', point.offsetY, { 'data-point-field': 'offsetY', step: '0.01' })}
        ${inputGroup('Offset Z', 'number', point.offsetZ, { 'data-point-field': 'offsetZ', step: '0.01' })}
        ${inputGroup('Trace Tolerance', 'number', point.traceTolerance, { 'data-point-field': 'traceTolerance', step: '0.01' })}
        ${inputGroup('Mistake Stability Penalty', 'number', point.mistakeStabilityPenalty, { 'data-point-field': 'mistakeStabilityPenalty', step: '0.1' })}
        ${inputGroup('Mistake Corruption Penalty', 'number', point.mistakeCorruptionPenalty, { 'data-point-field': 'mistakeCorruptionPenalty', step: '0.1' })}
      </div>
      ${state.glyphError ? `<div class="ritual-warning">⚠ Glyph list fell back to defaults: ${escapeHtml(state.glyphError)}</div>` : ''}
    `;
  }

  function renderGlyphSelect(currentSymbolId) {
    const currentId = normalizeSymbolId(currentSymbolId);
    const options = availableGlyphs()
      .map((glyph) => `<option value="${escapeAttr(glyph.id)}"${glyph.id === currentId ? ' selected' : ''}>${escapeHtml(glyph.displayName)} (${escapeHtml(glyph.id)})</option>`)
      .join('');
    return `
      <div class="form-group">
        <label>Glyph Symbol</label>
        <select data-point-field="symbolId">${options}</select>
      </div>
    `;
  }

  function renderPreviewSection(template, point) {
    const glyph = glyphById(point.symbolId) || defaultGlyph();
    return `
      <section class="ritual-card">
        <h3>Glyph Trace Authoring</h3>
        <div class="ritual-preview-layout">
          <div>
            <div class="ritual-preview-stage" id="ritual-preview-stage">
              <img id="ritual-glyph-image" alt="Selected ritual glyph" src="${escapeAttr(glyph?.url || '')}" />
              <svg id="ritual-glyph-svg" viewBox="0 0 100 100" preserveAspectRatio="none"></svg>
            </div>
            <div class="ritual-preview-help">
              Click anywhere on the glyph to move the selected step. Drag the numbered handles to refine the shape over the sigil. The overlay uses local <code>offsetX</code>/<code>offsetZ</code> coordinates, so what you draw here is what goes into the runtime template.
            </div>
          </div>
          <div>
            <div class="ritual-step-header">
              <div>
                <strong>${escapeHtml(point.symbolName || glyph?.displayName || 'Glyph')}</strong><br />
                <span class="editor-help"><code>${escapeHtml(template.ritualId)}</code> / <code>${escapeHtml(point.id)}</code></span>
              </div>
              <div style="display:flex;gap:8px">
                <button class="btn-primary" type="button" data-action="add-step">+ Step</button>
                <button class="btn-delete" type="button" data-action="remove-step"${(point.traceSteps || []).length ? '' : ' disabled'}>Remove Selected</button>
              </div>
            </div>
            <div class="ritual-step-list">
              ${(point.traceSteps || []).map((step, index) => renderStepRow(step, index)).join('')}
            </div>
          </div>
        </div>
      </section>
    `;
  }

  function renderStepRow(step, index) {
    return `
      <div class="ritual-step-row ${index === state.stepIndex ? 'active' : ''}">
        <div class="ritual-step-index" data-step-select="${index}">Step ${index + 1}</div>
        <input type="number" step="0.01" value="${escapeAttr(numberForInput(step.offsetX))}" data-step-field="offsetX" data-step-index="${index}" />
        <input type="number" step="0.01" value="${escapeAttr(numberForInput(step.offsetY))}" data-step-field="offsetY" data-step-index="${index}" />
        <input type="number" step="0.01" value="${escapeAttr(numberForInput(step.offsetZ))}" data-step-field="offsetZ" data-step-index="${index}" />
        <button class="btn-secondary" type="button" data-step-move="up" data-step-index="${index}" title="Move up">↑</button>
        <button class="btn-secondary" type="button" data-step-move="down" data-step-index="${index}" title="Move down">↓</button>
        <button class="btn-delete" type="button" data-step-delete="${index}" title="Delete step">✕</button>
      </div>
    `;
  }

  function renderJsonSection(template, point) {
    return `
      <section class="ritual-card">
        <h3>JSON Preview</h3>
        <div class="ritual-json-preview">
          <div class="form-group">
            <label>Selected Point</label>
            <textarea readonly>${escapeTextarea(JSON.stringify(point, null, 2))}</textarea>
          </div>
          <div class="form-group">
            <label>Selected Template</label>
            <textarea readonly>${escapeTextarea(JSON.stringify(template, null, 2))}</textarea>
          </div>
        </div>
      </section>
    `;
  }

  function renderEmptyState() {
    return `
      <section class="ritual-card">
        <div class="ritual-empty">
          <div>No ritual template selected yet.</div>
          <button class="btn-primary" type="button" data-action="add-template">Create ritual template</button>
        </div>
      </section>
    `;
  }

  function attachHandlers() {
    pane.querySelectorAll('[data-action="add-template"]').forEach((button) => {
      button.addEventListener('click', () => {
        templates().push(makeTemplate(templates().length + 1));
        state.templateIndex = templates().length - 1;
        state.pointIndex = 0;
        state.stepIndex = 0;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-template-select]').forEach((button) => {
      button.addEventListener('click', () => {
        state.templateIndex = Number(button.dataset.templateSelect);
        state.pointIndex = 0;
        state.stepIndex = 0;
        render();
      });
    });

    pane.querySelectorAll('[data-template-delete]').forEach((button) => {
      button.addEventListener('click', () => {
        const index = Number(button.dataset.templateDelete);
        const template = templates()[index];
        if (!template) return;
        if (!confirm(`Delete ritual template "${template.displayName || template.ritualId}"?`)) return;
        templates().splice(index, 1);
        state.templateIndex = Math.max(0, Math.min(state.templateIndex, templates().length - 1));
        state.pointIndex = 0;
        state.stepIndex = 0;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-action="add-point"]').forEach((button) => {
      button.addEventListener('click', () => {
        const template = selectedTemplate();
        if (!template) return;
        template.points = Array.isArray(template.points) ? template.points : [];
        template.points.push(makePoint(template.points.length + 1));
        state.pointIndex = template.points.length - 1;
        state.stepIndex = 0;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-point-select]').forEach((button) => {
      button.addEventListener('click', () => {
        state.pointIndex = Number(button.dataset.pointSelect);
        state.stepIndex = 0;
        render();
      });
    });

    pane.querySelectorAll('[data-point-delete]').forEach((button) => {
      button.addEventListener('click', () => {
        const index = Number(button.dataset.pointDelete);
        const template = selectedTemplate();
        const point = template?.points?.[index];
        if (!template || !point) return;
        if (!confirm(`Delete point "${point.id || point.symbolName}"?`)) return;
        template.points.splice(index, 1);
        state.pointIndex = Math.max(0, Math.min(state.pointIndex, (template.points || []).length - 1));
        state.stepIndex = 0;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-template-field]').forEach((input) => {
      input.addEventListener('change', () => updateField(selectedTemplate(), input.dataset.templateField, input.value, true));
    });

    pane.querySelectorAll('[data-point-field]').forEach((input) => {
      input.addEventListener('change', () => {
        const point = selectedPoint();
        if (!point) return;
        const field = input.dataset.pointField;
        updateField(point, field, input.value, false);
        if (field === 'symbolId') {
          const glyph = glyphById(input.value);
          point.symbolId = glyph?.id || normalizeSymbolId(input.value);
          point.symbolName = glyph?.displayName || humanizeId(point.symbolId);
        }
        render();
      });
    });

    pane.querySelectorAll('[data-step-select]').forEach((button) => {
      button.addEventListener('click', () => {
        state.stepIndex = Number(button.dataset.stepSelect);
        render();
      });
    });

    pane.querySelectorAll('[data-step-field]').forEach((input) => {
      input.addEventListener('change', () => {
        const point = selectedPoint();
        if (!point) return;
        const step = point.traceSteps?.[Number(input.dataset.stepIndex)];
        if (!step) return;
        step[input.dataset.stepField] = parseNumericInput(input.value);
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-step-delete]').forEach((button) => {
      button.addEventListener('click', () => {
        const point = selectedPoint();
        if (!point) return;
        const index = Number(button.dataset.stepDelete);
        point.traceSteps.splice(index, 1);
        if (point.traceSteps.length === 0) {
          point.traceSteps.push(makeStep(0, 0, 0));
        }
        state.stepIndex = Math.max(0, Math.min(state.stepIndex, point.traceSteps.length - 1));
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-step-move]').forEach((button) => {
      button.addEventListener('click', () => {
        const point = selectedPoint();
        if (!point) return;
        const index = Number(button.dataset.stepIndex);
        const direction = button.dataset.stepMove === 'up' ? -1 : 1;
        const target = index + direction;
        if (target < 0 || target >= point.traceSteps.length) return;
        const [entry] = point.traceSteps.splice(index, 1);
        point.traceSteps.splice(target, 0, entry);
        state.stepIndex = target;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-action="add-step"]').forEach((button) => {
      button.addEventListener('click', () => {
        const point = selectedPoint();
        if (!point) return;
        point.traceSteps.push(makeStep(0, 0, 0));
        state.stepIndex = point.traceSteps.length - 1;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-action="remove-step"]').forEach((button) => {
      button.addEventListener('click', () => {
        const point = selectedPoint();
        if (!point || !point.traceSteps.length) return;
        point.traceSteps.splice(state.stepIndex, 1);
        if (point.traceSteps.length === 0) {
          point.traceSteps.push(makeStep(0, 0, 0));
        }
        state.stepIndex = Math.max(0, Math.min(state.stepIndex, point.traceSteps.length - 1));
        App.markDirty();
        render();
      });
    });
  }

  function renderPreview() {
    const svg = document.getElementById('ritual-glyph-svg');
    const point = selectedPoint();
    if (!svg || !point) return;

    const steps = Array.isArray(point.traceSteps) ? point.traceSteps : [];
    const extent = previewExtent(steps);
    const points = steps.map((step) => stepToPreviewPoint(step, extent));
    const polyline = points.map((entry) => `${entry.x},${entry.y}`).join(' ');

    svg.innerHTML = `
      <line x1="50" y1="4" x2="50" y2="96" stroke="rgba(255,255,255,0.18)" stroke-width="0.4" stroke-dasharray="1.8 1.8"></line>
      <line x1="4" y1="50" x2="96" y2="50" stroke="rgba(255,255,255,0.18)" stroke-width="0.4" stroke-dasharray="1.8 1.8"></line>
      ${points.length > 1 ? `<polyline points="${polyline}" fill="none" stroke="rgba(233,69,96,0.9)" stroke-width="1.4" stroke-linejoin="round" stroke-linecap="round"></polyline>` : ''}
      ${points.map((entry, index) => `
        <circle cx="${entry.x}" cy="${entry.y}" r="${index === state.stepIndex ? 3.5 : 2.6}" fill="${index === state.stepIndex ? '#2ecc71' : '#e94560'}" stroke="#ffffff" stroke-width="0.8" data-step-handle="${index}" style="cursor:grab"></circle>
        <text x="${entry.x}" y="${entry.y - 5}" fill="#ffffff" font-size="4" text-anchor="middle" font-weight="700" pointer-events="none">${index + 1}</text>
      `).join('')}
    `;

    svg.onpointerdown = (event) => {
      const handle = event.target.closest('[data-step-handle]');
      if (handle) {
        state.stepIndex = Number(handle.dataset.stepHandle);
      }
      if (!steps.length) return;
      state.previewDrag = { pointerId: event.pointerId };
      updateStepFromPointer(event);
      attachPreviewDragHandlers();
      render();
    };
  }

  function attachPreviewDragHandlers() {
    const handlePointerMove = (event) => {
      if (!state.previewDrag) return;
      updateStepFromPointer(event);
    };
    const handlePointerUp = () => {
      if (!state.previewDrag) return;
      state.previewDrag = null;
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
      window.removeEventListener('pointercancel', handlePointerUp);
      render();
    };
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', handlePointerUp);
  }

  function updateStepFromPointer(event) {
    const svg = document.getElementById('ritual-glyph-svg');
    const point = selectedPoint();
    const step = selectedStep();
    if (!svg || !point || !step) return;

    const rect = svg.getBoundingClientRect();
    const localX = ((event.clientX - rect.left) / rect.width) * 100;
    const localY = ((event.clientY - rect.top) / rect.height) * 100;
    const extent = previewExtent(point.traceSteps || []);
    const next = previewPointToStep(localX, localY, extent);
    step.offsetX = next.offsetX;
    step.offsetZ = next.offsetZ;
    App.markDirty();
    renderPreview();
    updateRenderedStepInputs();
  }

  function updateRenderedStepInputs() {
    const point = selectedPoint();
    if (!point) return;
    (point.traceSteps || []).forEach((step, index) => {
      ['offsetX', 'offsetY', 'offsetZ'].forEach((field) => {
        const input = pane.querySelector(`[data-step-field="${field}"][data-step-index="${index}"]`);
        if (input) {
          input.value = numberForInput(step[field]);
        }
      });
    });
  }

  function previewExtent(steps) {
    const maxMagnitude = (steps || []).reduce((max, step) => {
      return Math.max(max, Math.abs(Number(step.offsetX) || 0), Math.abs(Number(step.offsetZ) || 0));
    }, 0);
    return Math.max(0.38, maxMagnitude * 1.3);
  }

  function stepToPreviewPoint(step, extent) {
    const radius = 38;
    return {
      x: 50 + ((Number(step.offsetX) || 0) / extent) * radius,
      y: 50 - ((Number(step.offsetZ) || 0) / extent) * radius,
    };
  }

  function previewPointToStep(x, y, extent) {
    const radius = 38;
    return {
      offsetX: roundStepValue(((x - 50) / radius) * extent),
      offsetZ: roundStepValue(-((y - 50) / radius) * extent),
    };
  }

  function updateField(target, field, rawValue, rerender) {
    if (!target || !field) return;
    if (typeof target[field] === 'number') {
      target[field] = parseNumericInput(rawValue);
    } else {
      target[field] = rawValue;
    }
    App.markDirty();
    if (rerender) render();
  }

  function inputGroup(label, type, value, attrs = {}) {
    const attributes = Object.entries(attrs)
      .map(([key, attrValue]) => `${key}="${escapeAttr(attrValue)}"`)
      .join(' ');
    return `
      <div class="form-group">
        <label>${label}</label>
        <input type="${type}" value="${escapeAttr(type === 'number' ? numberForInput(value) : value ?? '')}" ${attributes} />
      </div>
    `;
  }

  function parseNumericInput(value) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function roundStepValue(value) {
    return Math.round(value * 1000) / 1000;
  }

  function numberForInput(value) {
    const num = Number(value);
    return Number.isFinite(num) ? String(num) : '0';
  }

  function normalizeSymbolId(value) {
    return String(value || '').trim().toLowerCase();
  }

  function humanizeId(value) {
    return String(value || '')
      .split('_')
      .filter(Boolean)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ');
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function escapeAttr(value) {
    return escapeHtml(value);
  }

  function escapeTextarea(value) {
    return String(value ?? '').replaceAll('</textarea>', '&lt;/textarea&gt;');
  }

  App.onTabActivated('rituals', () => {
    ensureGlyphsLoaded();
    render();
  });

  window.RitualsTab = { render };
})();
