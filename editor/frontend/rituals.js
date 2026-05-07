(() => {
  const pane = document.getElementById('pane-rituals');
  const GLYPH_ASSET_API = '/api/ritual-glyphs';
  const FALLBACK_GLYPH_ASSETS = [
    { id: 'fang_wake', displayName: 'Fang Wake', url: '/api/ritual-glyphs/fang_wake' },
    { id: 'moon_scar', displayName: 'Moon Scar', url: '/api/ritual-glyphs/moon_scar' },
    { id: 'blood_spiral', displayName: 'Blood Spiral', url: '/api/ritual-glyphs/blood_spiral' },
    { id: 'vein_eye', displayName: 'Vein Eye', url: '/api/ritual-glyphs/vein_eye' },
    { id: 'crown_claw', displayName: 'Crown Claw', url: '/api/ritual-glyphs/crown_claw' },
    { id: 'generic', displayName: 'Generic', url: '/api/ritual-glyphs/generic' },
  ];

  const state = {
    mode: 'builder',
    assetGlyphs: [],
    assetGlyphsLoaded: false,
    assetGlyphError: '',
    loadingAssetGlyphs: false,
    templateIndex: 0,
    pointIndex: 0,
    glyphIndex: 0,
    glyphStepIndex: 0,
    linkIndex: 0,
    glyphPreviewDrag: null,
    glyphPreviewExtents: {},
    layoutPreviewDrag: null,
    layoutPreviewExtents: {},
    layoutPreviewSeconds: 0,
  };

  let cssInjected = false;
  let assetGlyphLoadPromise = null;

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
        width:340px;
        min-width:340px;
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
      .ritual-mode-toggle {
        display:flex;
        gap:8px;
        padding:16px;
        border-bottom:1px solid #20375a;
      }
      .ritual-mode-toggle button,
      .ritual-inline-actions button,
      .ritual-list-item-actions button,
      .ritual-step-row button,
      .ritual-empty button,
      .ritual-link-toolbar button,
      .ritual-point-summary-actions button {
        cursor:pointer;
        border:none;
        border-radius:5px;
        padding:5px 9px;
        font-size:11px;
        font-weight:700;
      }
      .ritual-mode-toggle button.active {
        background:var(--accent);
        color:#fff;
      }
      .ritual-mode-toggle button:not(.active) {
        background:#0f1d33;
        color:var(--text-dim);
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
      .ritual-inline-actions {
        display:flex;
        gap:8px;
        align-items:center;
        flex-wrap:wrap;
      }
      .ritual-builder-grid,
      .ritual-glyph-grid {
        display:grid;
        grid-template-columns:minmax(360px, 520px) minmax(340px, 1fr);
        gap:16px;
        align-items:start;
      }
      .ritual-layout-stage,
      .ritual-glyph-stage {
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
      .ritual-layout-stage svg,
      .ritual-glyph-stage svg,
      .ritual-glyph-stage img {
        position:absolute;
        inset:10%;
        width:80%;
        height:80%;
      }
      .ritual-glyph-stage img {
        object-fit:contain;
        opacity:0.9;
        pointer-events:none;
        user-select:none;
      }
      .ritual-layout-stage svg,
      .ritual-glyph-stage svg {
        z-index:2;
        touch-action:none;
      }
      .ritual-help {
        margin-top:10px;
        font-size:11px;
        line-height:1.55;
        color:var(--text-dim);
      }
      .ritual-warning {
        margin-top:8px;
        color:#f0c040;
        font-size:11px;
      }
      .ritual-step-header,
      .ritual-link-toolbar {
        display:flex;
        justify-content:space-between;
        gap:8px;
        align-items:center;
        margin-bottom:10px;
      }
      .ritual-step-list,
      .ritual-point-summary-list {
        display:flex;
        flex-direction:column;
        gap:8px;
      }
      .ritual-step-row,
      .ritual-point-summary-row {
        background:#102038;
        border:1px solid #1c3456;
        border-radius:8px;
        padding:8px;
      }
      .ritual-step-row.active,
      .ritual-point-summary-row.active {
        border-color:var(--accent);
        background:#182a45;
      }
      .ritual-step-row {
        display:grid;
        grid-template-columns:auto repeat(3, minmax(0, 1fr)) auto auto auto;
        gap:6px;
        align-items:center;
      }
      .ritual-point-summary-row {
        display:flex;
        justify-content:space-between;
        gap:12px;
        align-items:center;
      }
      .ritual-step-index,
      .ritual-link-index {
        min-width:72px;
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
      .ritual-layout-inspector {
        display:grid;
        gap:12px;
      }
      .ritual-distance-pill {
        display:inline-flex;
        align-items:center;
        gap:4px;
        border-radius:999px;
        padding:4px 8px;
        background:#102038;
        color:var(--text);
        font-size:11px;
        font-weight:700;
      }
      .ritual-slider-group {
        display:grid;
        gap:8px;
      }
      .ritual-slider-group input[type="range"] {
        width:100%;
      }
      .ritual-point-summary-meta {
        display:flex;
        gap:6px;
        flex-wrap:wrap;
      }
      .ritual-point-summary-actions {
        display:flex;
        gap:6px;
        flex-wrap:wrap;
      }
      .ritual-layout-link-toolbar {
        margin-top:2px;
      }
      .ritual-layout-link-time-row {
        display:grid;
        grid-template-columns:56px minmax(0, 1fr) 18px minmax(0, 1fr) 88px 96px auto auto auto auto;
        gap:6px;
        align-items:center;
      }
      .ritual-layout-link-time-row select,
      .ritual-layout-link-time-row input {
        width:100%;
        background:var(--input-bg);
        border:1px solid var(--border);
        color:var(--text);
        padding:6px 8px;
        border-radius:5px;
        font-size:12px;
      }
      .ritual-layout-link-time-row .tag {
        margin:0;
        white-space:nowrap;
      }
      .ritual-layout-link-arrow {
        text-align:center;
        color:var(--text-dim);
        font-weight:700;
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
      @media (max-width: 1380px) {
        .ritual-builder-grid,
        .ritual-glyph-grid,
        .ritual-json-preview {
          grid-template-columns:1fr;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function getGlyphDoc() {
    if (!window.AppData.ritualGlyphs || typeof window.AppData.ritualGlyphs !== 'object' || Array.isArray(window.AppData.ritualGlyphs)) {
      window.AppData.ritualGlyphs = { glyphs: [] };
    }
    if (!Array.isArray(window.AppData.ritualGlyphs.glyphs)) {
      window.AppData.ritualGlyphs.glyphs = [];
    }
    return window.AppData.ritualGlyphs;
  }

  function getTemplateDoc() {
    if (!window.AppData.ritualTemplates || typeof window.AppData.ritualTemplates !== 'object' || Array.isArray(window.AppData.ritualTemplates)) {
      window.AppData.ritualTemplates = { templates: [] };
    }
    if (!Array.isArray(window.AppData.ritualTemplates.templates)) {
      window.AppData.ritualTemplates.templates = [];
    }
    return window.AppData.ritualTemplates;
  }

  function glyphs() {
    return getGlyphDoc().glyphs;
  }

  function templates() {
    return getTemplateDoc().templates;
  }

  function ensureRitualDataShape() {
    const glyphMap = new Map();
    const normalizedGlyphs = glyphs().map((glyph, index) => normalizeGlyph(glyph, index)).filter(Boolean);
    normalizedGlyphs.forEach((glyph) => glyphMap.set(glyph.glyphId, glyph));
    getGlyphDoc().glyphs = normalizedGlyphs;

    const normalizedTemplates = templates().map((template, index) => normalizeTemplate(template, index, glyphMap)).filter(Boolean);
    getTemplateDoc().templates = normalizedTemplates;
    getGlyphDoc().glyphs = sortGlyphs([...glyphMap.values()]);
  }

  function normalizeGlyph(glyph, index = 0) {
    const fallbackId = normalizeId(glyph?.glyphId || glyph?.symbolId || `glyph_${index + 1}`) || `glyph_${index + 1}`;
    return {
      glyphId: fallbackId,
      symbolId: normalizeId(glyph?.symbolId || fallbackId) || fallbackId,
      displayName: String(glyph?.displayName || humanizeId(fallbackId)).trim() || humanizeId(fallbackId),
      traceTolerance: parseNumericInput(glyph?.traceTolerance, 0.48),
      mistakeStabilityPenalty: parseNumericInput(glyph?.mistakeStabilityPenalty, 6),
      mistakeCorruptionPenalty: parseNumericInput(glyph?.mistakeCorruptionPenalty, 5),
      traceSteps: normalizeTraceSteps(glyph?.traceSteps),
    };
  }

  function normalizeTemplate(template, index, glyphMap) {
    const fallbackGlyph = ensureUsableGlyph(glyphMap);
    const normalizedTemplate = {
      ritualId: String(template?.ritualId || `ritual_${index + 1}`).trim() || `ritual_${index + 1}`,
      displayName: String(template?.displayName || `New Ritual ${index + 1}`).trim() || `New Ritual ${index + 1}`,
      requiredAnchorBlockId: String(template?.requiredAnchorBlockId || 'Furniture_Ancient_Coffin').trim() || 'Furniture_Ancient_Coffin',
      pointTolerance: parseNumericInput(template?.pointTolerance, 0.95),
      channelDurationSeconds: parseNumericInput(template?.channelDurationSeconds, 8),
      baseStability: parseNumericInput(template?.baseStability, 70),
      baseCorruption: parseNumericInput(template?.baseCorruption, 8),
      instabilityThreshold: parseNumericInput(template?.instabilityThreshold, 30),
      activationLinks: [],
      points: [],
    };

    const pointEntries = Array.isArray(template?.points) ? template.points : [];
    normalizedTemplate.points = pointEntries.map((point, pointIndex) => normalizePoint(point, pointIndex, glyphMap, fallbackGlyph?.glyphId)).filter(Boolean);
    normalizedTemplate.activationLinks = normalizeActivationLinks(template?.activationLinks, normalizedTemplate.points);
    return normalizedTemplate;
  }

  function normalizePoint(point, index, glyphMap, fallbackGlyphId) {
    const inlineGlyph = normalizeInlineGlyph(point, index);
    if (inlineGlyph) {
      glyphMap.set(inlineGlyph.glyphId, inlineGlyph);
    }
    const resolvedFallbackId = fallbackGlyphId || inlineGlyph?.glyphId || ensureUsableGlyph(glyphMap)?.glyphId || 'generic';
    const glyphId = normalizeId(point?.glyphId || inlineGlyph?.glyphId || point?.symbolId || resolvedFallbackId) || resolvedFallbackId;
    if (!glyphMap.has(glyphId)) {
      glyphMap.set(glyphId, makeGlyphFromAsset(glyphId));
    }
    return {
      id: String(point?.id || `point_${index + 1}`).trim() || `point_${index + 1}`,
      offsetX: parseNumericInput(point?.offsetX, 0),
      offsetY: parseNumericInput(point?.offsetY, 0.15),
      offsetZ: parseNumericInput(point?.offsetZ, 0),
      glyphId,
    };
  }

  function normalizeInlineGlyph(point, index) {
    const legacySymbolId = normalizeId(point?.symbolId);
    const legacyGlyphId = normalizeId(point?.glyphId || legacySymbolId);
    const hasLegacyPayload = legacyGlyphId
      && (Array.isArray(point?.traceSteps) || point?.symbolName || point?.traceTolerance != null || point?.mistakeStabilityPenalty != null || point?.mistakeCorruptionPenalty != null);
    if (!hasLegacyPayload) return null;
    return normalizeGlyph({
      glyphId: legacyGlyphId,
      symbolId: legacySymbolId || legacyGlyphId,
      displayName: point?.symbolName || humanizeId(legacyGlyphId),
      traceTolerance: point?.traceTolerance,
      mistakeStabilityPenalty: point?.mistakeStabilityPenalty,
      mistakeCorruptionPenalty: point?.mistakeCorruptionPenalty,
      traceSteps: point?.traceSteps,
    }, index);
  }

  function normalizeActivationLinks(links, points) {
    const pointIds = new Set((points || []).map((point) => point.id));
    const normalized = Array.isArray(links) ? links.map((link, index) => normalizeActivationLink(link, index, pointIds, points)).filter(Boolean) : [];
    return normalized;
  }

  function normalizeActivationLink(link, index, pointIds, points) {
    let fromPointId = String(link?.fromPointId || '').trim();
    let toPointId = String(link?.toPointId || '').trim();
    if ((!fromPointId || !pointIds.has(fromPointId)) && points?.length) {
      fromPointId = points[0].id;
    }
    if ((!toPointId || !pointIds.has(toPointId)) && points?.length > 1) {
      toPointId = points[Math.min(index + 1, points.length - 1)].id;
    }
    if (!fromPointId || !toPointId) return null;
    return {
      fromPointId,
      toPointId,
      startTimeSeconds: Math.max(0, parseNumericInput(link?.startTimeSeconds, 0)),
      activeDurationSeconds: Math.max(0, parseNumericInput(link?.activeDurationSeconds, 0)),
    };
  }

  function normalizeTraceSteps(traceSteps) {
    const normalized = Array.isArray(traceSteps) ? traceSteps.map((step) => ({
      offsetX: parseNumericInput(step?.offsetX, 0),
      offsetY: parseNumericInput(step?.offsetY, 0),
      offsetZ: parseNumericInput(step?.offsetZ, 0),
    })) : [];
    return normalized.length ? normalized : [makeStep(0, 0, 0)];
  }

  function ensureUsableGlyph(glyphMap) {
    if (glyphMap.size) {
      return [...glyphMap.values()][0];
    }
    const fallback = makeGlyphFromAsset(bestAssetGlyph()?.id || 'generic');
    glyphMap.set(fallback.glyphId, fallback);
    return fallback;
  }

  function sortGlyphs(entries) {
    return [...entries].sort((a, b) => {
      if (a.glyphId === 'generic') return 1;
      if (b.glyphId === 'generic') return -1;
      return a.displayName.localeCompare(b.displayName);
    });
  }

  function clampSelection() {
    ensureRitualDataShape();
    const allTemplates = templates();
    const allGlyphs = glyphs();

    if (allGlyphs.length === 0) {
      state.glyphIndex = 0;
      state.glyphStepIndex = 0;
    } else {
      state.glyphIndex = clampIndex(state.glyphIndex, allGlyphs.length);
      const glyph = allGlyphs[state.glyphIndex];
      glyph.traceSteps = normalizeTraceSteps(glyph.traceSteps);
      state.glyphStepIndex = clampIndex(state.glyphStepIndex, glyph.traceSteps.length);
    }

    if (allTemplates.length === 0) {
      state.templateIndex = 0;
      state.pointIndex = 0;
      state.linkIndex = 0;
      return;
    }

    state.templateIndex = clampIndex(state.templateIndex, allTemplates.length);
    const template = allTemplates[state.templateIndex];
    template.points = Array.isArray(template.points) ? template.points : [];
    template.activationLinks = normalizeActivationLinks(template.activationLinks, template.points);
    state.linkIndex = template.activationLinks.length ? clampIndex(state.linkIndex, template.activationLinks.length) : 0;
    if (template.points.length === 0) {
      state.pointIndex = 0;
      return;
    }
    state.pointIndex = clampIndex(state.pointIndex, template.points.length);
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

  function selectedGlyph() {
    clampSelection();
    if (state.mode === 'builder') {
      const point = selectedPoint();
      if (point) {
        return glyphById(point.glyphId) || null;
      }
    }
    return glyphs()[state.glyphIndex] || null;
  }

  function selectedGlyphStep() {
    const glyph = selectedGlyph();
    if (!glyph || !glyph.traceSteps?.length) return null;
    return glyph.traceSteps[state.glyphStepIndex] || null;
  }

  function glyphById(glyphId) {
    return glyphs().find((glyph) => glyph.glyphId === normalizeId(glyphId));
  }

  function glyphAssetById(symbolId) {
    return availableAssetGlyphs().find((glyph) => glyph.id === normalizeId(symbolId));
  }

  function bestAssetGlyph() {
    return availableAssetGlyphs().find((glyph) => glyph.id !== 'generic') || availableAssetGlyphs()[0] || FALLBACK_GLYPH_ASSETS[0];
  }

  function availableAssetGlyphs() {
    const dedup = new Map();
    [...FALLBACK_GLYPH_ASSETS, ...state.assetGlyphs].forEach((glyph) => {
      if (glyph?.id) dedup.set(glyph.id, glyph);
    });
    glyphs().forEach((glyph) => {
      const symbolId = normalizeId(glyph.symbolId);
      if (symbolId && !dedup.has(symbolId)) {
        dedup.set(symbolId, {
          id: symbolId,
          displayName: humanizeId(symbolId),
          url: `${GLYPH_ASSET_API}/${symbolId}`,
        });
      }
    });
    return [...dedup.values()].sort((a, b) => a.displayName.localeCompare(b.displayName));
  }

  function makeGlyph(index = glyphs().length + 1) {
    const asset = unusedAssetGlyph() || bestAssetGlyph();
    const glyphId = uniqueGlyphId(asset?.id || `glyph_${index}`);
    return normalizeGlyph({
      glyphId,
      symbolId: normalizeId(asset?.id || glyphId) || glyphId,
      displayName: asset?.displayName || `New Glyph ${index}`,
      traceTolerance: 0.48,
      mistakeStabilityPenalty: 6,
      mistakeCorruptionPenalty: 5,
      traceSteps: [makeStep(0, 0, 0)],
    }, index);
  }

  function makeGlyphFromAsset(symbolId) {
    const asset = glyphAssetById(symbolId) || { id: normalizeId(symbolId) || 'generic', displayName: humanizeId(symbolId || 'generic') };
    return normalizeGlyph({
      glyphId: normalizeId(asset.id) || 'generic',
      symbolId: normalizeId(asset.id) || 'generic',
      displayName: asset.displayName || humanizeId(asset.id),
      traceTolerance: 0.48,
      mistakeStabilityPenalty: 6,
      mistakeCorruptionPenalty: 5,
      traceSteps: [makeStep(0, 0, 0)],
    });
  }

  function makeTemplate(index = templates().length + 1) {
    const glyph = glyphs()[0] || makeGlyph(1);
    if (!glyphs().length) {
      glyphs().push(glyph);
    }
    return {
      ritualId: `ritual_${index}`,
      displayName: `New Ritual ${index}`,
      requiredAnchorBlockId: 'Furniture_Ancient_Coffin',
      pointTolerance: 0.95,
      channelDurationSeconds: 8,
      baseStability: 70,
      baseCorruption: 8,
      instabilityThreshold: 30,
      activationLinks: [],
      points: [makePoint(1, glyph.glyphId)],
    };
  }

  function makePoint(index = 1, glyphId = null) {
    const glyph = glyphById(glyphId) || glyphs()[0] || makeGlyph(1);
    if (!glyphById(glyph.glyphId)) {
      glyphs().push(glyph);
    }
    return {
      id: `point_${index}`,
      offsetX: 0,
      offsetY: 0.15,
      offsetZ: 0,
      glyphId: glyph.glyphId,
    };
  }

  function makeLink(template, index = 0) {
    const points = template?.points || [];
    const from = points[0];
    const to = points.length > 1 ? points[Math.min(index + 1, points.length - 1)] : points[0];
    return {
      fromPointId: from?.id || '',
      toPointId: to?.id || from?.id || '',
      startTimeSeconds: roundValue(index * 0.5, 2),
      activeDurationSeconds: 0,
    };
  }

  function makeStep(offsetX = 0, offsetY = 0, offsetZ = 0) {
    return { offsetX, offsetY, offsetZ };
  }

  function uniqueGlyphId(base, exceptGlyphId = null) {
    const normalizedBase = normalizeId(base) || 'glyph';
    let next = normalizedBase;
    let counter = 2;
    while (glyphs().some((glyph) => glyph.glyphId === next && glyph.glyphId !== exceptGlyphId)) {
      next = `${normalizedBase}_${counter}`;
      counter += 1;
    }
    return next;
  }

  function unusedAssetGlyph() {
    const used = new Set(glyphs().map((glyph) => normalizeId(glyph.symbolId)));
    return availableAssetGlyphs().find((glyph) => !used.has(glyph.id));
  }

  async function ensureAssetGlyphsLoaded() {
    if (state.assetGlyphsLoaded || state.loadingAssetGlyphs) return assetGlyphLoadPromise;
    state.loadingAssetGlyphs = true;
    assetGlyphLoadPromise = fetch(GLYPH_ASSET_API)
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`);
        return res.json();
      })
      .then((payload) => {
        state.assetGlyphs = Array.isArray(payload.glyphs) ? payload.glyphs : [];
        state.assetGlyphError = '';
        state.assetGlyphsLoaded = true;
      })
      .catch((error) => {
        state.assetGlyphError = error.message;
        state.assetGlyphs = [];
      })
      .finally(() => {
        state.loadingAssetGlyphs = false;
        render();
      });
    return assetGlyphLoadPromise;
  }

  function render() {
    ensureCss();
    clampSelection();
    const scrollState = captureScrollState();
    const template = selectedTemplate();
    const point = selectedPoint();
    const glyph = selectedGlyph();

    pane.innerHTML = `
      <div class="ritual-layout">
        <aside class="ritual-sidebar">
          <div class="ritual-mode-toggle">
            <button class="${state.mode === 'builder' ? 'active' : ''}" type="button" data-mode="builder">Ritual Builder</button>
            <button class="${state.mode === 'glyphs' ? 'active' : ''}" type="button" data-mode="glyphs">Glyph Library</button>
          </div>
          ${state.mode === 'builder' ? renderBuilderSidebar(template) : renderGlyphSidebar(glyph)}
        </aside>
        <main class="ritual-main">
          <div class="ritual-main-inner">
            ${state.mode === 'builder' ? renderBuilderEditor(template, point) : renderGlyphEditor(glyph)}
          </div>
        </main>
      </div>
    `;

    attachHandlers();
    if (state.mode === 'builder') {
      renderLayoutPreview();
    } else {
      renderGlyphPreview();
    }
    restoreScrollState(scrollState);
  }

  function captureScrollState() {
    return {
      paneTop: pane.scrollTop,
      paneLeft: pane.scrollLeft,
      mainTop: pane.querySelector('.ritual-main')?.scrollTop ?? 0,
      mainLeft: pane.querySelector('.ritual-main')?.scrollLeft ?? 0,
      sidebarSections: Array.from(pane.querySelectorAll('.ritual-sidebar-section')).map((section) => ({
        top: section.scrollTop,
        left: section.scrollLeft,
      })),
    };
  }

  function restoreScrollState(scrollState) {
    if (!scrollState) return;
    pane.scrollTop = scrollState.paneTop;
    pane.scrollLeft = scrollState.paneLeft;
    const ritualMain = pane.querySelector('.ritual-main');
    if (ritualMain) {
      ritualMain.scrollTop = scrollState.mainTop;
      ritualMain.scrollLeft = scrollState.mainLeft;
    }
    pane.querySelectorAll('.ritual-sidebar-section').forEach((section, index) => {
      const snapshot = scrollState.sidebarSections[index];
      if (!snapshot) return;
      section.scrollTop = snapshot.top;
      section.scrollLeft = snapshot.left;
    });
  }

  function renderBuilderSidebar(template) {
    return `
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
    `;
  }

  function renderGlyphSidebar(glyph) {
    return `
      <div class="pane-toolbar">
        <h2>Reusable Glyphs</h2>
        <button class="btn-primary" type="button" data-action="add-glyph">+ Glyph</button>
      </div>
      <div class="ritual-sidebar-section">
        <div class="ritual-list">${renderGlyphList(glyph)}</div>
      </div>
    `;
  }

  function renderTemplateList() {
    return templates().map((template, index) => `
      <div class="ritual-list-item ${index === state.templateIndex ? 'active' : ''}">
        <div class="ritual-list-item-header">
          <div class="ritual-list-item-main" data-template-select="${index}">
            <div class="ritual-list-item-title">${escapeHtml(template.displayName || template.ritualId || `Template ${index + 1}`)}</div>
            <div class="ritual-list-item-subtitle">
              <code>${escapeHtml(template.ritualId || '(missing ritualId)')}</code>
              · ${(template.points || []).length} point(s)
              · ${(template.activationLinks || []).length} link(s)
            </div>
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
    return (template.points || []).map((point, index) => {
      const glyph = glyphById(point.glyphId);
      return `
        <div class="ritual-list-item ${index === state.pointIndex ? 'active' : ''}">
          <div class="ritual-list-item-header">
            <div class="ritual-list-item-main" data-point-select="${index}">
              <div class="ritual-list-item-title">${escapeHtml(point.id || `Point ${index + 1}`)}</div>
              <div class="ritual-list-item-subtitle">
                ${escapeHtml(glyph?.displayName || humanizeId(point.glyphId))}
                · dx ${escapeHtml(numberForInput(point.offsetX))}
                · dz ${escapeHtml(numberForInput(point.offsetZ))}
              </div>
            </div>
            <div class="ritual-list-item-actions">
              <button class="btn-delete" type="button" data-point-delete="${index}">✕</button>
            </div>
          </div>
        </div>
      `;
    }).join('') || `<div class="ritual-empty">This ritual has no points yet.<button class="btn-primary" type="button" data-action="add-point">Add first point</button></div>`;
  }

  function renderGlyphList(activeGlyph) {
    return glyphs().map((glyph, index) => `
      <div class="ritual-list-item ${glyph?.glyphId === activeGlyph?.glyphId ? 'active' : ''}">
        <div class="ritual-list-item-header">
          <div class="ritual-list-item-main" data-glyph-select="${index}">
            <div class="ritual-list-item-title">${escapeHtml(glyph.displayName || glyph.glyphId || `Glyph ${index + 1}`)}</div>
            <div class="ritual-list-item-subtitle">
              <code>${escapeHtml(glyph.glyphId)}</code>
              · asset <code>${escapeHtml(glyph.symbolId)}</code>
              · ${(glyph.traceSteps || []).length} trace step(s)
            </div>
          </div>
          <div class="ritual-list-item-actions">
            <button class="btn-delete" type="button" data-glyph-delete="${index}">✕</button>
          </div>
        </div>
      </div>
    `).join('') || `<div class="ritual-empty">No reusable glyphs yet.<button class="btn-primary" type="button" data-action="add-glyph">Create first glyph</button></div>`;
  }

  function renderBuilderEditor(template, point) {
    if (!template) {
      return `
        <section class="ritual-card">
          <div class="ritual-empty">
            <div>No ritual template selected yet.</div>
            <button class="btn-primary" type="button" data-action="add-template">Create ritual template</button>
          </div>
        </section>
      `;
    }

    const glyph = point ? glyphById(point.glyphId) : null;
    return `
      <section class="ritual-card">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:12px">
          <h3 style="margin:0">Template Settings</h3>
          <div class="editor-help">The editor writes <code>rituals/templates.json</code> and <code>rituals/glyphs.json</code> on Save.</div>
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
        ${point ? renderPointEditor(point, glyph) : `<div class="ritual-empty">Create a point to start placing glyphs around the center block.</div>`}
      </section>
      <section class="ritual-card">
        <h3>Ritual Layout Builder</h3>
        ${renderLayoutSection(template, point)}
      </section>
      ${renderJsonSection(template, point, glyph)}
    `;
  }

  function renderGlyphEditor(glyph) {
    if (!glyph) {
      return `
        <section class="ritual-card">
          <div class="ritual-empty">
            <div>No reusable glyph selected yet.</div>
            <button class="btn-primary" type="button" data-action="add-glyph">Create glyph</button>
          </div>
        </section>
      `;
    }
    const asset = glyphAssetById(glyph.symbolId);
    const referencingPoints = glyphUsage(glyph.glyphId);
    return `
      <section class="ritual-card">
        <div style="display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:12px">
          <h3 style="margin:0">Glyph Definition</h3>
          <div class="editor-help">${referencingPoints.length} point(s) reference this glyph.</div>
        </div>
        <div class="ritual-form-grid">
          ${inputGroup('Glyph ID', 'text', glyph.glyphId, { 'data-glyph-field': 'glyphId', placeholder: 'fang_wake' })}
          ${renderGlyphAssetSelect(glyph.symbolId)}
          ${inputGroup('Display Name', 'text', glyph.displayName, { 'data-glyph-field': 'displayName', placeholder: 'Fang Wake' })}
          ${inputGroup('Trace Tolerance', 'number', glyph.traceTolerance, { 'data-glyph-field': 'traceTolerance', step: '0.01' })}
          ${inputGroup('Mistake Stability Penalty', 'number', glyph.mistakeStabilityPenalty, { 'data-glyph-field': 'mistakeStabilityPenalty', step: '0.1' })}
          ${inputGroup('Mistake Corruption Penalty', 'number', glyph.mistakeCorruptionPenalty, { 'data-glyph-field': 'mistakeCorruptionPenalty', step: '0.1' })}
        </div>
        ${state.assetGlyphError ? `<div class="ritual-warning">⚠ Glyph asset list fell back to defaults: ${escapeHtml(state.assetGlyphError)}</div>` : ''}
      </section>
      <section class="ritual-card">
        <h3>Glyph Trace Authoring</h3>
        <div class="ritual-glyph-grid">
          <div>
            <div class="ritual-glyph-stage" id="ritual-glyph-stage">
              <img id="ritual-glyph-image" alt="Selected ritual glyph asset" src="${escapeAttr(asset?.url || `${GLYPH_ASSET_API}/${glyph.symbolId}`)}" />
              <svg id="ritual-glyph-svg" viewBox="0 0 100 100" preserveAspectRatio="none"></svg>
            </div>
            <div class="ritual-help">
              Draw the reusable trace directly over the sigil art. This edits the shared glyph definition, so every ritual point referencing <code>${escapeHtml(glyph.glyphId)}</code> will reuse the same stroke in game.
            </div>
          </div>
          <div>
            <div class="ritual-step-header">
              <div>
                <strong>${escapeHtml(glyph.displayName)}</strong><br />
                <span class="editor-help"><code>${escapeHtml(glyph.glyphId)}</code> → asset <code>${escapeHtml(glyph.symbolId)}</code></span>
              </div>
              <div style="display:flex;gap:8px">
                <button class="btn-primary" type="button" data-action="add-glyph-step">+ Step</button>
                <button class="btn-delete" type="button" data-action="remove-glyph-step"${glyph.traceSteps.length ? '' : ' disabled'}>Remove Selected</button>
              </div>
            </div>
            <div class="ritual-step-list">
              ${glyph.traceSteps.map((step, index) => renderGlyphStepRow(step, index)).join('')}
            </div>
          </div>
        </div>
      </section>
      <section class="ritual-card">
        <h3>Glyph JSON Preview</h3>
        <div class="ritual-json-preview">
          <div class="form-group">
            <label>Selected Glyph</label>
            <textarea readonly>${escapeTextarea(JSON.stringify(glyph, null, 2))}</textarea>
          </div>
          <div class="form-group">
            <label>Usage</label>
            <textarea readonly>${escapeTextarea(JSON.stringify(referencingPoints, null, 2))}</textarea>
          </div>
        </div>
      </section>
    `;
  }

  function renderPointEditor(point, glyph) {
    return `
      <div class="ritual-form-grid">
        ${inputGroup('Point ID', 'text', point.id, { 'data-point-field': 'id', placeholder: 'north' })}
        ${renderPointGlyphSelect(point.glyphId)}
        ${inputGroup('Offset X', 'number', point.offsetX, { 'data-point-field': 'offsetX', step: '0.01' })}
        ${inputGroup('Offset Y', 'number', point.offsetY, { 'data-point-field': 'offsetY', step: '0.01' })}
        ${inputGroup('Offset Z', 'number', point.offsetZ, { 'data-point-field': 'offsetZ', step: '0.01' })}
      </div>
      <div class="ritual-help">
        Pick a reusable glyph here; the actual trace data now lives in the glyph library. Use the layout canvas below to drag the node around the center block and edit link timing in-context.
      </div>
      <div class="ritual-inline-actions" style="margin-top:10px">
        <span class="ritual-distance-pill">Glyph: ${escapeHtml(glyph?.displayName || humanizeId(point.glyphId))}</span>
        <span class="ritual-distance-pill">dx ${escapeHtml(numberForInput(point.offsetX))}</span>
        <span class="ritual-distance-pill">dy ${escapeHtml(numberForInput(point.offsetY))}</span>
        <span class="ritual-distance-pill">dz ${escapeHtml(numberForInput(point.offsetZ))}</span>
        <span class="ritual-distance-pill">radius ${escapeHtml(numberForInput(pointRadius(point)))}</span>
        <button class="btn-secondary" type="button" data-action="open-point-glyph">Edit Glyph</button>
      </div>
    `;
  }

  function renderLayoutSection(template, point) {
    const previewTime = clampPreviewTime(template.channelDurationSeconds, state.layoutPreviewSeconds);
    state.layoutPreviewSeconds = previewTime;
    return `
      <div class="ritual-builder-grid">
        <div>
          <div class="ritual-layout-stage" id="ritual-layout-stage">
            <svg id="ritual-layout-svg" viewBox="0 0 100 100" preserveAspectRatio="none"></svg>
          </div>
          <div class="ritual-help">
            Drag any node to reposition it around the center block. Distances are written directly as <code>offsetX</code>/<code>offsetZ</code>, so the canvas mirrors the runtime ritual plane.
          </div>
        </div>
        <div class="ritual-layout-inspector">
          <div class="ritual-slider-group">
            <label data-layout-preview-label>Timeline preview at <strong>${escapeHtml(numberForInput(previewTime))}s</strong> of <strong>${escapeHtml(numberForInput(template.channelDurationSeconds))}s</strong></label>
            <input type="range" min="0" max="${escapeAttr(numberForInput(Math.max(template.channelDurationSeconds, 0.1)))}" step="0.1" value="${escapeAttr(numberForInput(previewTime))}" data-layout-preview-time />
          </div>
          <div data-layout-link-editor>
            ${renderLayoutLinkEditor(template, previewTime)}
          </div>
        </div>
      </div>
    `;
  }

  function renderLayoutLinkEditor(template, previewSeconds) {
    const links = template.activationLinks || [];
    const canAddLink = (template.points || []).length >= 2;
    const selectedExists = links.length > 0;
    const rows = links.map((link, index) => renderLayoutLinkEditorRow(link, index, template, previewSeconds)).join('');
    return `
      <div class="ritual-link-toolbar ritual-layout-link-toolbar">
        <div class="editor-help">Edit the activation link timeline directly here. Each row is one link.</div>
        <div style="display:flex;gap:8px">
          <button class="btn-primary" type="button" data-action="add-link"${canAddLink ? '' : ' disabled'}>+ Link</button>
          <button class="btn-delete" type="button" data-action="remove-link"${selectedExists ? '' : ' disabled'}>Remove Selected</button>
        </div>
      </div>
      <div class="ritual-point-summary-list">
        ${rows || `<div class="ritual-empty">No activation links yet.${canAddLink ? '<button class="btn-primary" type="button" data-action="add-link">Add first link</button>' : '<span>Create at least two points to author activation links.</span>'}</div>`}
      </div>
    `;
  }

  function renderLayoutLinkEditorRow(link, index, template, previewSeconds) {
    const pointOptions = (template.points || []).map((point) => `<option value="${escapeAttr(point.id)}"${point.id === link.fromPointId ? ' selected' : ''}>${escapeHtml(point.id)}</option>`).join('');
    const toOptions = (template.points || []).map((point) => `<option value="${escapeAttr(point.id)}"${point.id === link.toPointId ? ' selected' : ''}>${escapeHtml(point.id)}</option>`).join('');
    const stateAtPreview = linkStateAt(link, previewSeconds);
    return `
      <div class="ritual-point-summary-row ritual-layout-link-time-row ${index === state.linkIndex ? 'active' : ''}">
        <button class="btn-secondary" type="button" data-link-select="${index}" title="Select link">#${index + 1}</button>
        <select data-link-field="fromPointId" data-link-index="${index}" title="From point">${pointOptions}</select>
        <div class="ritual-layout-link-arrow">→</div>
        <select data-link-field="toPointId" data-link-index="${index}" title="To point">${toOptions}</select>
        <input type="number" step="0.1" value="${escapeAttr(numberForInput(link.startTimeSeconds))}" data-link-field="startTimeSeconds" data-link-index="${index}" title="Start time (seconds)" />
        <input type="number" step="0.1" value="${escapeAttr(numberForInput(link.activeDurationSeconds))}" data-link-field="activeDurationSeconds" data-link-index="${index}" title="Duration (seconds)" />
        <span class="tag">${escapeHtml(linkPreviewLabel(stateAtPreview))}</span>
        <button class="btn-secondary" type="button" data-link-move="up" data-link-index="${index}" title="Move up">↑</button>
        <button class="btn-secondary" type="button" data-link-move="down" data-link-index="${index}" title="Move down">↓</button>
        <button class="btn-delete" type="button" data-link-delete="${index}" title="Delete link">✕</button>
      </div>
    `;
  }

  function renderGlyphStepRow(step, index) {
    return `
      <div class="ritual-step-row ${index === state.glyphStepIndex ? 'active' : ''}">
        <div class="ritual-step-index" data-glyph-step-select="${index}">Step ${index + 1}</div>
        <input type="number" step="0.01" value="${escapeAttr(numberForInput(step.offsetX))}" data-glyph-step-field="offsetX" data-glyph-step-index="${index}" />
        <input type="number" step="0.01" value="${escapeAttr(numberForInput(step.offsetY))}" data-glyph-step-field="offsetY" data-glyph-step-index="${index}" />
        <input type="number" step="0.01" value="${escapeAttr(numberForInput(step.offsetZ))}" data-glyph-step-field="offsetZ" data-glyph-step-index="${index}" />
        <button class="btn-secondary" type="button" data-glyph-step-move="up" data-glyph-step-index="${index}" title="Move up">↑</button>
        <button class="btn-secondary" type="button" data-glyph-step-move="down" data-glyph-step-index="${index}" title="Move down">↓</button>
        <button class="btn-delete" type="button" data-glyph-step-delete="${index}" title="Delete step">✕</button>
      </div>
    `;
  }

  function renderPointGlyphSelect(currentGlyphId) {
    const options = glyphs()
      .map((glyph) => `<option value="${escapeAttr(glyph.glyphId)}"${glyph.glyphId === normalizeId(currentGlyphId) ? ' selected' : ''}>${escapeHtml(glyph.displayName)} (${escapeHtml(glyph.glyphId)})</option>`)
      .join('');
    return `
      <div class="form-group">
        <label>Reusable Glyph</label>
        <select data-point-field="glyphId"${glyphs().length ? '' : ' disabled'}>${options}</select>
      </div>
    `;
  }

  function renderGlyphAssetSelect(currentSymbolId) {
    const options = availableAssetGlyphs()
      .map((glyph) => `<option value="${escapeAttr(glyph.id)}"${glyph.id === normalizeId(currentSymbolId) ? ' selected' : ''}>${escapeHtml(glyph.displayName)} (${escapeHtml(glyph.id)})</option>`)
      .join('');
    return `
      <div class="form-group">
        <label>Glyph Asset</label>
        <select data-glyph-field="symbolId">${options}</select>
      </div>
    `;
  }

  function renderJsonSection(template, point, glyph) {
    return `
      <section class="ritual-card">
        <h3>JSON Preview</h3>
        <div class="ritual-json-preview">
          <div class="form-group">
            <label>Selected Template</label>
            <textarea readonly>${escapeTextarea(JSON.stringify(template, null, 2))}</textarea>
          </div>
          <div class="form-group">
            <label>${point ? 'Selected Point + Glyph' : 'Glyph Library'}</label>
            <textarea readonly>${escapeTextarea(JSON.stringify(point ? { point, glyph } : { glyphs: glyphs() }, null, 2))}</textarea>
          </div>
        </div>
      </section>
    `;
  }

  function attachHandlers() {
    pane.querySelectorAll('[data-mode]').forEach((button) => {
      button.addEventListener('click', () => {
        state.mode = button.dataset.mode;
        render();
      });
    });

    pane.querySelectorAll('[data-action="add-template"]').forEach((button) => {
      button.addEventListener('click', () => {
        templates().push(makeTemplate(templates().length + 1));
        state.templateIndex = templates().length - 1;
        state.pointIndex = 0;
        state.linkIndex = 0;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-template-select]').forEach((button) => {
      button.addEventListener('click', () => {
        state.templateIndex = Number(button.dataset.templateSelect);
        state.pointIndex = 0;
        state.linkIndex = 0;
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
        state.templateIndex = clampIndex(state.templateIndex, templates().length);
        state.pointIndex = 0;
        state.linkIndex = 0;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-action="add-point"]').forEach((button) => {
      button.addEventListener('click', () => {
        const template = selectedTemplate();
        if (!template) return;
        template.points.push(makePoint(template.points.length + 1));
        state.pointIndex = template.points.length - 1;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-point-select]').forEach((button) => {
      button.addEventListener('click', () => {
        state.pointIndex = Number(button.dataset.pointSelect);
        render();
      });
    });

    pane.querySelectorAll('[data-point-delete]').forEach((button) => {
      button.addEventListener('click', () => {
        const index = Number(button.dataset.pointDelete);
        const template = selectedTemplate();
        const point = template?.points?.[index];
        if (!template || !point) return;
        if (!confirm(`Delete point "${point.id}"?`)) return;
        template.points.splice(index, 1);
        template.activationLinks = (template.activationLinks || []).filter((link) => link.fromPointId !== point.id && link.toPointId !== point.id);
        state.pointIndex = clampIndex(state.pointIndex, template.points.length);
        state.linkIndex = clampIndex(state.linkIndex, template.activationLinks.length);
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-action="add-glyph"]').forEach((button) => {
      button.addEventListener('click', () => {
        glyphs().push(makeGlyph(glyphs().length + 1));
        state.glyphIndex = glyphs().length - 1;
        state.glyphStepIndex = 0;
        state.mode = 'glyphs';
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-glyph-select]').forEach((button) => {
      button.addEventListener('click', () => {
        state.glyphIndex = Number(button.dataset.glyphSelect);
        state.glyphStepIndex = 0;
        render();
      });
    });

    pane.querySelectorAll('[data-glyph-delete]').forEach((button) => {
      button.addEventListener('click', () => {
        const index = Number(button.dataset.glyphDelete);
        const glyph = glyphs()[index];
        if (!glyph) return;
        const usage = glyphUsage(glyph.glyphId);
        if (usage.length) {
          alert(`"${glyph.displayName}" is still referenced by ${usage.length} ritual point(s). Reassign those points before deleting the glyph.`);
          return;
        }
        if (!confirm(`Delete reusable glyph "${glyph.displayName}"?`)) return;
        glyphs().splice(index, 1);
        state.glyphIndex = clampIndex(state.glyphIndex, glyphs().length);
        state.glyphStepIndex = 0;
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
        if (field === 'glyphId') {
          point.glyphId = normalizeId(input.value) || point.glyphId;
          const index = glyphs().findIndex((glyph) => glyph.glyphId === point.glyphId);
          if (index >= 0) {
            state.glyphIndex = index;
          }
        } else {
          updateField(point, field, input.value, false);
        }
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-glyph-field]').forEach((input) => {
      input.addEventListener('change', () => {
        const glyph = selectedGlyph();
        if (!glyph) return;
        const field = input.dataset.glyphField;
        if (field === 'glyphId') {
          const previous = glyph.glyphId;
          glyph.glyphId = uniqueGlyphId(input.value || previous, previous);
          templates().forEach((template) => {
            (template.points || []).forEach((point) => {
              if (point.glyphId === previous) {
                point.glyphId = glyph.glyphId;
              }
            });
          });
        } else if (typeof glyph[field] === 'number') {
          glyph[field] = parseNumericInput(input.value, glyph[field]);
        } else {
          glyph[field] = String(input.value || '').trim();
        }
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-action="open-point-glyph"]').forEach((button) => {
      button.addEventListener('click', () => {
        const targetIndex = button.dataset.pointTarget != null ? Number(button.dataset.pointTarget) : state.pointIndex;
        const point = selectedTemplate()?.points?.[targetIndex];
        const glyphIndex = glyphs().findIndex((glyph) => glyph.glyphId === point?.glyphId);
        if (glyphIndex >= 0) {
          state.glyphIndex = glyphIndex;
        }
        state.mode = 'glyphs';
        state.glyphStepIndex = 0;
        render();
      });
    });

    pane.querySelectorAll('[data-glyph-step-select]').forEach((button) => {
      button.addEventListener('click', () => {
        state.glyphStepIndex = Number(button.dataset.glyphStepSelect);
        render();
      });
    });

    pane.querySelectorAll('[data-glyph-step-field]').forEach((input) => {
      input.addEventListener('change', () => {
        const glyph = selectedGlyph();
        if (!glyph) return;
        const step = glyph.traceSteps?.[Number(input.dataset.glyphStepIndex)];
        if (!step) return;
        step[input.dataset.glyphStepField] = parseNumericInput(input.value, 0);
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-glyph-step-delete]').forEach((button) => {
      button.addEventListener('click', () => {
        const glyph = selectedGlyph();
        if (!glyph) return;
        const index = Number(button.dataset.glyphStepDelete);
        glyph.traceSteps.splice(index, 1);
        if (!glyph.traceSteps.length) {
          glyph.traceSteps.push(makeStep(0, 0, 0));
        }
        state.glyphStepIndex = clampIndex(state.glyphStepIndex, glyph.traceSteps.length);
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-glyph-step-move]').forEach((button) => {
      button.addEventListener('click', () => {
        const glyph = selectedGlyph();
        if (!glyph) return;
        const index = Number(button.dataset.glyphStepIndex);
        const direction = button.dataset.glyphStepMove === 'up' ? -1 : 1;
        const target = index + direction;
        if (target < 0 || target >= glyph.traceSteps.length) return;
        const [entry] = glyph.traceSteps.splice(index, 1);
        glyph.traceSteps.splice(target, 0, entry);
        state.glyphStepIndex = target;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-action="add-glyph-step"]').forEach((button) => {
      button.addEventListener('click', () => {
        const glyph = selectedGlyph();
        if (!glyph) return;
        glyph.traceSteps.push(makeStep(0, 0, 0));
        state.glyphStepIndex = glyph.traceSteps.length - 1;
        App.markDirty();
        render();
      });
    });

    pane.querySelectorAll('[data-action="remove-glyph-step"]').forEach((button) => {
      button.addEventListener('click', () => {
        const glyph = selectedGlyph();
        if (!glyph || !glyph.traceSteps.length) return;
        glyph.traceSteps.splice(state.glyphStepIndex, 1);
        if (!glyph.traceSteps.length) {
          glyph.traceSteps.push(makeStep(0, 0, 0));
        }
        state.glyphStepIndex = clampIndex(state.glyphStepIndex, glyph.traceSteps.length);
        App.markDirty();
        render();
      });
    });

    const previewTime = pane.querySelector('[data-layout-preview-time]');
    if (previewTime) {
      previewTime.addEventListener('input', () => {
        state.layoutPreviewSeconds = clampPreviewTime(selectedTemplate()?.channelDurationSeconds || 0, parseNumericInput(previewTime.value, 0));
        renderLayoutPreview();
      });
    }
  }

  function renderGlyphPreview() {
    const svg = document.getElementById('ritual-glyph-svg');
    const glyph = selectedGlyph();
    if (!svg || !glyph) return;

    const steps = Array.isArray(glyph.traceSteps) ? glyph.traceSteps : [];
    const extent = currentGlyphPreviewExtent(glyph);
    const points = steps.map((step) => glyphStepToPreviewPoint(step, extent));
    const polyline = points.map((entry) => `${entry.x},${entry.y}`).join(' ');

    svg.innerHTML = `
      <line x1="50" y1="4" x2="50" y2="96" stroke="rgba(255,255,255,0.18)" stroke-width="0.4" stroke-dasharray="1.8 1.8"></line>
      <line x1="4" y1="50" x2="96" y2="50" stroke="rgba(255,255,255,0.18)" stroke-width="0.4" stroke-dasharray="1.8 1.8"></line>
      ${points.length > 1 ? `<polyline points="${polyline}" fill="none" stroke="rgba(233,69,96,0.92)" stroke-width="1.4" stroke-linejoin="round" stroke-linecap="round"></polyline>` : ''}
      ${points.map((entry, index) => `
        <circle cx="${entry.x}" cy="${entry.y}" r="${index === state.glyphStepIndex ? 3.5 : 2.6}" fill="${index === state.glyphStepIndex ? '#2ecc71' : '#e94560'}" stroke="#ffffff" stroke-width="0.8" data-glyph-step-handle="${index}" style="cursor:grab"></circle>
        <text x="${entry.x}" y="${entry.y - 5}" fill="#ffffff" font-size="4" text-anchor="middle" font-weight="700" pointer-events="none">${index + 1}</text>
      `).join('')}
    `;

    svg.onpointerdown = (event) => {
      const handle = event.target.closest('[data-glyph-step-handle]');
      if (handle) {
        state.glyphStepIndex = Number(handle.dataset.glyphStepHandle);
      }
      if (!steps.length) return;
      state.glyphPreviewDrag = {
        pointerId: event.pointerId,
        glyphKey: glyphPreviewKey(glyph),
        extent,
      };
      updateGlyphStepFromPointer(event);
      attachGlyphPreviewDragHandlers();
      render();
    };
  }

  function attachGlyphPreviewDragHandlers() {
    const handlePointerMove = (event) => {
      if (!state.glyphPreviewDrag) return;
      updateGlyphStepFromPointer(event);
    };
    const handlePointerUp = () => {
      if (!state.glyphPreviewDrag) return;
      state.glyphPreviewDrag = null;
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
      window.removeEventListener('pointercancel', handlePointerUp);
      render();
    };
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', handlePointerUp);
  }

  function updateGlyphStepFromPointer(event) {
    const svg = document.getElementById('ritual-glyph-svg');
    const glyph = selectedGlyph();
    const step = selectedGlyphStep();
    if (!svg || !glyph || !step) return;

    const rect = svg.getBoundingClientRect();
    const localX = ((event.clientX - rect.left) / rect.width) * 100;
    const localY = ((event.clientY - rect.top) / rect.height) * 100;
    const extent = currentGlyphPreviewExtent(glyph);
    const next = previewPointToGlyphStep(localX, localY, extent);
    step.offsetX = next.offsetX;
    step.offsetZ = next.offsetZ;
    App.markDirty();
    renderGlyphPreview();
    updateRenderedGlyphStepInputs();
  }

  function updateRenderedGlyphStepInputs() {
    const glyph = selectedGlyph();
    if (!glyph) return;
    (glyph.traceSteps || []).forEach((step, index) => {
      ['offsetX', 'offsetY', 'offsetZ'].forEach((field) => {
        const input = pane.querySelector(`[data-glyph-step-field="${field}"][data-glyph-step-index="${index}"]`);
        if (input) {
          input.value = numberForInput(step[field]);
        }
      });
    });
  }

  function renderLayoutPreview() {
    const svg = document.getElementById('ritual-layout-svg');
    const template = selectedTemplate();
    if (!svg || !template) return;

    const extent = currentLayoutPreviewExtent(template);
    const selectedPointId = selectedPoint()?.id;
    const previewSeconds = clampPreviewTime(template.channelDurationSeconds, state.layoutPreviewSeconds);
    const visibleLinks = (template.activationLinks || []).filter((link) => linkVisibleAt(link, previewSeconds));

    const lines = visibleLinks.map((link) => {
      const from = template.points.find((point) => point.id === link.fromPointId);
      const to = template.points.find((point) => point.id === link.toPointId);
      if (!from || !to) return '';
      const a = pointToLayoutPreviewPoint(from, extent);
      const b = pointToLayoutPreviewPoint(to, extent);
      const selected = link === template.activationLinks[state.linkIndex];
      return `<line x1="${a.x}" y1="${a.y}" x2="${b.x}" y2="${b.y}" stroke="${selected ? '#2ecc71' : '#e94560'}" stroke-width="${selected ? '1.7' : '1.2'}" stroke-linecap="round"></line>`;
    }).join('');

    svg.innerHTML = `
      <rect x="46" y="46" width="8" height="8" rx="1.5" fill="#f0c040" fill-opacity="0.85" stroke="#ffffff" stroke-width="0.6"></rect>
      <circle cx="50" cy="50" r="39" fill="none" stroke="rgba(255,255,255,0.10)" stroke-width="0.5" stroke-dasharray="2 2"></circle>
      <line x1="50" y1="6" x2="50" y2="94" stroke="rgba(255,255,255,0.18)" stroke-width="0.4" stroke-dasharray="1.8 1.8"></line>
      <line x1="6" y1="50" x2="94" y2="50" stroke="rgba(255,255,255,0.18)" stroke-width="0.4" stroke-dasharray="1.8 1.8"></line>
      ${lines}
      ${(template.points || []).map((point, index) => {
        const preview = pointToLayoutPreviewPoint(point, extent);
        const glyph = glyphById(point.glyphId);
        const selected = point.id === selectedPointId;
        return `
          <circle cx="${preview.x}" cy="${preview.y}" r="${selected ? 4.3 : 3.3}" fill="${selected ? '#2ecc71' : '#e94560'}" stroke="#ffffff" stroke-width="0.8" data-layout-point="${index}" style="cursor:grab"></circle>
          <text x="${preview.x}" y="${preview.y - 5}" fill="#ffffff" font-size="4" text-anchor="middle" font-weight="700" pointer-events="none">${escapeSvgText(glyph?.displayName || point.id)}</text>
        `;
      }).join('')}
    `;

    svg.onpointerdown = (event) => {
      const handle = event.target.closest('[data-layout-point]');
      if (handle) {
        state.pointIndex = Number(handle.dataset.layoutPoint);
      }
      if (!selectedPoint()) return;
      state.layoutPreviewDrag = {
        pointerId: event.pointerId,
        templateKey: layoutPreviewTemplateKey(template),
        extent,
      };
      updatePointFromPointer(event);
      attachLayoutPreviewDragHandlers();
      render();
    };
    updateLayoutPreviewInspector(template, previewSeconds);
  }

  function attachLayoutPreviewDragHandlers() {
    const handlePointerMove = (event) => {
      if (!state.layoutPreviewDrag) return;
      updatePointFromPointer(event);
    };
    const handlePointerUp = () => {
      if (!state.layoutPreviewDrag) return;
      state.layoutPreviewDrag = null;
      window.removeEventListener('pointermove', handlePointerMove);
      window.removeEventListener('pointerup', handlePointerUp);
      window.removeEventListener('pointercancel', handlePointerUp);
      render();
    };
    window.addEventListener('pointermove', handlePointerMove);
    window.addEventListener('pointerup', handlePointerUp);
    window.addEventListener('pointercancel', handlePointerUp);
  }

  function updatePointFromPointer(event) {
    const svg = document.getElementById('ritual-layout-svg');
    const template = selectedTemplate();
    const point = selectedPoint();
    if (!svg || !template || !point) return;

    const rect = svg.getBoundingClientRect();
    const localX = ((event.clientX - rect.left) / rect.width) * 100;
    const localY = ((event.clientY - rect.top) / rect.height) * 100;
    const extent = currentLayoutPreviewExtent(template);
    const next = previewPointToLayoutPoint(localX, localY, extent);
    point.offsetX = next.offsetX;
    point.offsetZ = next.offsetZ;
    App.markDirty();
    renderLayoutPreview();
    updateRenderedPointInputs();
  }

  function updateRenderedPointInputs() {
    const point = selectedPoint();
    if (!point) return;
    ['offsetX', 'offsetY', 'offsetZ'].forEach((field) => {
      const input = pane.querySelector(`[data-point-field="${field}"]`);
      if (input) {
        input.value = numberForInput(point[field]);
      }
    });
  }

  function updateLayoutPreviewInspector(template, previewSeconds) {
    const label = pane.querySelector('[data-layout-preview-label]');
    if (label) {
      label.innerHTML = `Timeline preview at <strong>${escapeHtml(numberForInput(previewSeconds))}s</strong> of <strong>${escapeHtml(numberForInput(template.channelDurationSeconds))}s</strong>`;
    }
    const list = pane.querySelector('[data-layout-link-editor]');
    if (list) {
      list.innerHTML = renderLayoutLinkEditor(template, previewSeconds);
      bindLayoutLinkEditorHandlers(list);
    }
  }

  function bindLayoutLinkEditorHandlers(container) {
    container.querySelectorAll('[data-link-select]').forEach((button) => {
      button.addEventListener('click', () => {
        state.linkIndex = Number(button.dataset.linkSelect);
        renderLayoutPreview();
      });
    });
    container.querySelectorAll('[data-link-field]').forEach((input) => {
      input.addEventListener('change', () => {
        const template = selectedTemplate();
        const link = template?.activationLinks?.[Number(input.dataset.linkIndex)];
        if (!template || !link) return;
        const field = input.dataset.linkField;
        link[field] = field.endsWith('Seconds') ? Math.max(0, parseNumericInput(input.value, 0)) : String(input.value || '').trim();
        App.markDirty();
        renderLayoutPreview();
      });
    });
    container.querySelectorAll('[data-link-delete]').forEach((button) => {
      button.addEventListener('click', () => {
        const template = selectedTemplate();
        if (!template) return;
        const index = Number(button.dataset.linkDelete);
        template.activationLinks.splice(index, 1);
        state.linkIndex = clampIndex(state.linkIndex, template.activationLinks.length);
        App.markDirty();
        render();
      });
    });
    container.querySelectorAll('[data-link-move]').forEach((button) => {
      button.addEventListener('click', () => {
        const template = selectedTemplate();
        if (!template) return;
        const index = Number(button.dataset.linkIndex);
        const direction = button.dataset.linkMove === 'up' ? -1 : 1;
        const target = index + direction;
        if (target < 0 || target >= template.activationLinks.length) return;
        const [entry] = template.activationLinks.splice(index, 1);
        template.activationLinks.splice(target, 0, entry);
        state.linkIndex = target;
        App.markDirty();
        render();
      });
    });
    container.querySelectorAll('[data-action="add-link"]').forEach((button) => {
      button.addEventListener('click', () => {
        const template = selectedTemplate();
        if (!template || (template.points || []).length < 2) return;
        template.activationLinks.push(makeLink(template, template.activationLinks.length));
        state.linkIndex = template.activationLinks.length - 1;
        App.markDirty();
        render();
      });
    });
    container.querySelectorAll('[data-action="remove-link"]').forEach((button) => {
      button.addEventListener('click', () => {
        const template = selectedTemplate();
        if (!template || !template.activationLinks.length) return;
        template.activationLinks.splice(state.linkIndex, 1);
        state.linkIndex = clampIndex(state.linkIndex, template.activationLinks.length);
        App.markDirty();
        render();
      });
    });
  }

  function glyphUsage(glyphId) {
    const usage = [];
    templates().forEach((template) => {
      (template.points || []).forEach((point) => {
        if (point.glyphId === glyphId) {
          usage.push({
            ritualId: template.ritualId,
            pointId: point.id,
          });
        }
      });
    });
    return usage;
  }

  function pointRadius(point) {
    return roundValue(Math.sqrt((point.offsetX ** 2) + (point.offsetZ ** 2)), 3);
  }

  function glyphPreviewExtent(steps) {
    const maxMagnitude = glyphPreviewMagnitude(steps);
    return Math.max(0.38, maxMagnitude * 1.3);
  }

  function glyphPreviewMagnitude(steps) {
    const maxMagnitude = (steps || []).reduce((max, step) => Math.max(
      max,
      Math.abs(Number(step.offsetX) || 0),
      Math.abs(Number(step.offsetZ) || 0),
    ), 0);
    return maxMagnitude;
  }

  function currentGlyphPreviewExtent(glyph) {
    const glyphKey = glyphPreviewKey(glyph);
    if (state.glyphPreviewDrag?.glyphKey === glyphKey && Number.isFinite(state.glyphPreviewDrag?.extent)) {
      return state.glyphPreviewDrag.extent;
    }
    const stored = state.glyphPreviewExtents[glyphKey];
    const requiredExtent = Math.max(0.38, glyphPreviewMagnitude(glyph?.traceSteps || []));
    const nextExtent = stored == null
      ? glyphPreviewExtent(glyph?.traceSteps || [])
      : Math.max(stored, requiredExtent);
    state.glyphPreviewExtents[glyphKey] = nextExtent;
    return nextExtent;
  }

  function glyphPreviewKey(glyph) {
    return glyph?.glyphId || `glyph-${state.glyphIndex}`;
  }

  function glyphStepToPreviewPoint(step, extent) {
    const radius = 38;
    return {
      x: 50 + ((Number(step.offsetX) || 0) / extent) * radius,
      y: 50 - ((Number(step.offsetZ) || 0) / extent) * radius,
    };
  }

  function previewPointToGlyphStep(x, y, extent) {
    const radius = 38;
    return {
      offsetX: roundValue(((x - 50) / radius) * extent, 3),
      offsetZ: roundValue(-((y - 50) / radius) * extent, 3),
    };
  }

  function layoutPreviewExtent(points) {
    const maxMagnitude = layoutPreviewMagnitude(points);
    return Math.max(3.5, maxMagnitude * 1.18);
  }

  function layoutPreviewMagnitude(points) {
    return (points || []).reduce((max, point) => Math.max(
      max,
      Math.abs(Number(point.offsetX) || 0),
      Math.abs(Number(point.offsetZ) || 0),
    ), 0);
  }

  function currentLayoutPreviewExtent(template) {
    const templateKey = layoutPreviewTemplateKey(template);
    if (state.layoutPreviewDrag?.templateKey === templateKey && Number.isFinite(state.layoutPreviewDrag?.extent)) {
      return state.layoutPreviewDrag.extent;
    }
    const stored = state.layoutPreviewExtents[templateKey];
    const requiredExtent = Math.max(3.5, layoutPreviewMagnitude(template?.points || []));
    const nextExtent = stored == null
      ? layoutPreviewExtent(template?.points || [])
      : Math.max(stored, requiredExtent);
    state.layoutPreviewExtents[templateKey] = nextExtent;
    return nextExtent;
  }

  function layoutPreviewTemplateKey(template) {
    return template?.ritualId || `template-${state.templateIndex}`;
  }

  function pointToLayoutPreviewPoint(point, extent) {
    const radius = 39;
    return {
      x: 50 + ((Number(point.offsetX) || 0) / extent) * radius,
      y: 50 - ((Number(point.offsetZ) || 0) / extent) * radius,
    };
  }

  function previewPointToLayoutPoint(x, y, extent) {
    const radius = 39;
    return {
      offsetX: roundValue(((x - 50) / radius) * extent, 3),
      offsetZ: roundValue(-((y - 50) / radius) * extent, 3),
    };
  }

  function linkVisibleAt(link, seconds) {
    if (seconds < link.startTimeSeconds) return false;
    if (link.activeDurationSeconds <= 0) return true;
    return seconds <= link.startTimeSeconds + link.activeDurationSeconds;
  }

  function linkStateAt(link, seconds) {
    if (seconds < link.startTimeSeconds) return 'pending';
    if (link.activeDurationSeconds <= 0) return 'active';
    if (seconds <= link.startTimeSeconds + link.activeDurationSeconds) return 'active';
    return 'ended';
  }

  function linkPreviewLabel(stateAtPreview) {
    if (stateAtPreview === 'active') return 'visible now';
    if (stateAtPreview === 'ended') return 'already ended';
    return 'not started';
  }

  function clampPreviewTime(duration, value) {
    return Math.min(Math.max(0, parseNumericInput(value, 0)), Math.max(parseNumericInput(duration, 0), 0));
  }

  function updateField(target, field, rawValue, rerender) {
    if (!target || !field) return;
    if (typeof target[field] === 'number') {
      target[field] = parseNumericInput(rawValue, target[field]);
    } else {
      target[field] = String(rawValue ?? '').trim();
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

  function clampIndex(value, length) {
    if (!length) return 0;
    return Math.max(0, Math.min(Number(value) || 0, length - 1));
  }

  function parseNumericInput(value, fallback = 0) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  function roundValue(value, precision = 3) {
    const factor = 10 ** precision;
    return Math.round(Number(value || 0) * factor) / factor;
  }

  function numberForInput(value) {
    const num = Number(value);
    return Number.isFinite(num) ? String(num) : '0';
  }

  function normalizeId(value) {
    return String(value || '').trim().toLowerCase().replaceAll(/\s+/g, '_');
  }

  function humanizeId(value) {
    return String(value || '')
      .replaceAll('-', '_')
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

  function escapeSvgText(value) {
    return escapeHtml(value).slice(0, 18);
  }

  App.onTabActivated('rituals', () => {
    ensureAssetGlyphsLoaded();
    render();
  });

  window.RitualsTab = { render };
})();
