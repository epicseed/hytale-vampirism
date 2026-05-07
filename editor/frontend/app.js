// Global state and server communication.
// All tab modules read/write window.AppData and call App.markDirty() when mutating.
const App = (() => {
  const API = '/api/data';

  window.AppData = {
    tree:      [],
    passives:  [],
    abilities: [],
    modifiers: [],
    effects:   [],
    states:    [],
    stats:     [],
    conditions:   [],
    requirements: [],
    triggers:     [],
    actions:      [],
    targetings:   [],
    stateRegistry: [],
    relicBindings: {},
    ritualGlyphs: { glyphs: [] },
    ritualTemplates: { templates: [] },
  };

  let dirty = false;

  function isPlainObject(value) {
    return value != null && typeof value === 'object' && !Array.isArray(value);
  }

  function cloneObject(value) {
    return isPlainObject(value) ? JSON.parse(JSON.stringify(value)) : {};
  }

  function cloneObjectList(value) {
    return Array.isArray(value) ? value.filter(isPlainObject).map((entry) => cloneObject(entry)) : [];
  }

  function cloneStringList(value) {
    return Array.isArray(value) ? value.map((entry) => String(entry || '').trim()).filter(Boolean) : [];
  }

  function normalizeModifier(row = {}) {
    const legacyCondition = row.condition ? [{ type: 'state', stateId: row.condition, operator: 'isTrue' }] : [];
    return {
      modifierId: row.modifierId || row.id || '',
      statId: row.statId || row.stat || '',
      operation: row.operation || 'ADD',
      value: Number(row.value || 0),
      priority: Number(row.priority ?? 100),
      conditions: cloneObjectList(row.conditions).length ? cloneObjectList(row.conditions) : legacyCondition,
      target: cloneObject(row.target),
    };
  }

  function normalizeSkill(skill = {}) {
    return {
      id: skill.id || '',
      displayName: skill.displayName || skill.id || '',
      description: skill.description || '',
      overlayText: skill.overlayText || '',
      enabled: skill.enabled !== false,
      cost: Number(skill.cost ?? 1),
      position: {
        x: Number(skill.position?.x ?? 0),
        y: Number(skill.position?.y ?? 0),
      },
      type: skill.type || 'passive',
      rarity: skill.rarity || 'common',
      iconPath: skill.iconPath || '',
      abilityId: skill.abilityId || (skill.type === 'ability' ? skill.id || '' : ''),
      passiveId: skill.passiveId || (skill.type === 'passive' ? skill.id || '' : ''),
      tags: cloneStringList(skill.tags),
      requires: cloneStringList(skill.requires),
      modifiers: (skill.modifiers || []).map(normalizeModifier),
      triggers: cloneObjectList(skill.triggers),
      actions: cloneObjectList(skill.actions),
    };
  }

  function normalizeAbility(ability = {}) {
    return {
      id: ability.id || '',
      displayName: ability.displayName || ability.id || '',
      description: ability.description || '',
      iconPath: ability.iconPath || '',
      tags: cloneStringList(ability.tags),
      cooldown: Number(ability.cooldown || 0),
      duration: Number(ability.duration || 0),
      resourceCost: Number(ability.resourceCost ?? ability.bloodCost ?? 0),
      castTime: Number(ability.castTime || 0),
      charges: Number(ability.charges || 0),
      channelDuration: Number(ability.channelDuration || 0),
      effects: cloneStringList(ability.effects),
      requirements: cloneObjectList(ability.requirements),
      targeting: cloneObject(ability.targeting),
      actions: cloneObjectList(ability.actions),
    };
  }

  function normalizePassive(passive = {}) {
    return {
      id: passive.id || '',
      displayName: passive.displayName || passive.id || '',
      description: passive.description || '',
      iconPath: passive.iconPath || '',
      tags: cloneStringList(passive.tags),
      requirements: cloneObjectList(passive.requirements),
      modifiers: (passive.modifiers || []).map(normalizeModifier),
      triggers: cloneObjectList(passive.triggers),
      actions: cloneObjectList(passive.actions),
    };
  }

  function normalizeStat(stat = {}) {
    const next = {
      id: stat.id || '',
      displayName: stat.displayName || stat.id || '',
      description: stat.description || '',
      category: stat.category || '',
      unit: stat.unit || '',
      status: stat.status || '',
      baseValue: stat.baseValue ?? '',
      binding: cloneObject(stat.binding),
      notes: stat.notes || '',
    };
    if (next.baseValue !== '' && !Number.isFinite(Number(next.baseValue))) {
      next.baseValue = '';
    }
    return next;
  }

  function normalizeEffect(effect = {}) {
    return {
      id: effect.id || '',
      displayName: effect.displayName || effect.id || '',
      description: effect.description || '',
      effectId: effect.effectId || '',
      duration: Number(effect.duration || 0),
      tags: cloneStringList(effect.tags),
      requirements: cloneObjectList(effect.requirements),
      modifiers: (effect.modifiers || []).map(normalizeModifier),
      actions: cloneObjectList(effect.actions),
    };
  }

  function normalizeReusableDef(entry = {}) {
    return {
      id: entry.id || '',
      displayName: entry.displayName || entry.id || '',
      description: entry.description || '',
      tags: cloneStringList(entry.tags),
      definition: cloneObject(entry.definition),
    };
  }

  function normalizeStateRegistry(entries) {
    if (!Array.isArray(entries)) return [];
    return entries.filter(isPlainObject).map((entry) => ({
      stateId: String(entry.stateId || '').trim(),
      effectId: String(entry.effectId || '').trim(),
    }));
  }

  function normalizeRelicBindings(value) {
    const src = isPlainObject(value) ? value : {};
    const slots = ['primary', 'secondary', 'ability1', 'ability2', 'use'];
    const out = {};
    for (const slot of slots) {
      const v = src[slot];
      if (v != null && String(v).trim()) out[slot] = String(v).trim();
    }
    // Preserve any extra keys the runtime may add in the future
    for (const [k, v] of Object.entries(src)) {
      if (!slots.includes(k) && v != null && String(v).trim()) out[k] = v;
    }
    return out;
  }

  function normalizeRitualTemplates(value) {
    const doc = cloneObject(value);
    if (!Array.isArray(doc.templates)) {
      doc.templates = [];
    }
    return doc;
  }

  function normalizeRitualGlyphs(value) {
    const doc = cloneObject(value);
    if (!Array.isArray(doc.glyphs)) {
      doc.glyphs = [];
    }
    return doc;
  }

  function normalizeData(data) {
    window.AppData.tree = Array.isArray(data.tree) ? data.tree.map(normalizeSkill) : [];
    window.AppData.passives = Array.isArray(data.passives) ? data.passives.map(normalizePassive) : [];
    window.AppData.abilities = Array.isArray(data.abilities) ? data.abilities.map(normalizeAbility) : [];
    window.AppData.modifiers = Array.isArray(data.modifiers) ? data.modifiers : [];
    window.AppData.effects = Array.isArray(data.effects) ? data.effects.map(normalizeEffect) : [];
    window.AppData.states = Array.isArray(data.states) ? data.states : [];
    window.AppData.stats = Array.isArray(data.stats) ? data.stats.map(normalizeStat) : [];
    window.AppData.conditions = Array.isArray(data.conditions) ? data.conditions.map(normalizeReusableDef) : [];
    window.AppData.requirements = Array.isArray(data.requirements) ? data.requirements.map(normalizeReusableDef) : [];
    window.AppData.triggers = Array.isArray(data.triggers) ? data.triggers.map(normalizeReusableDef) : [];
    window.AppData.actions = Array.isArray(data.actions) ? data.actions.map(normalizeReusableDef) : [];
    window.AppData.targetings = Array.isArray(data.targetings) ? data.targetings.map(normalizeReusableDef) : [];
    window.AppData.stateRegistry = normalizeStateRegistry(data.stateRegistry);
    window.AppData.relicBindings = normalizeRelicBindings(data.relicBindings);
    window.AppData.ritualGlyphs = normalizeRitualGlyphs(data.ritualGlyphs);
    window.AppData.ritualTemplates = normalizeRitualTemplates(data.ritualTemplates);
  }

  function compactObject(value) {
    if (!isPlainObject(value)) return {};
    return Object.fromEntries(Object.entries(value).filter(([, entry]) => {
      if (entry == null) return false;
      if (typeof entry === 'string') return entry !== '';
      if (Array.isArray(entry)) return entry.length > 0;
      if (isPlainObject(entry)) return Object.keys(entry).length > 0;
      return true;
    }));
  }

  function serializeStat(stat = {}) {
    const entry = {
      id: stat.id || '',
      displayName: stat.displayName || stat.id || '',
      description: stat.description || '',
    };
    if (stat.category) entry.category = stat.category;
    if (stat.unit) entry.unit = stat.unit;
    if (stat.status) entry.status = stat.status;
    if (stat.notes) entry.notes = stat.notes;
    if (stat.baseValue !== '' && stat.baseValue != null && Number.isFinite(Number(stat.baseValue))) {
      entry.baseValue = Number(stat.baseValue);
    }
    const binding = compactObject({
      type: stat.binding?.type || '',
      assetId: stat.binding?.assetId || '',
    });
    if (Object.keys(binding).length) entry.binding = binding;
    return entry;
  }

  function serializePassive(passive = {}) {
    const entry = {
      id: passive.id || '',
      displayName: passive.displayName || passive.id || '',
      description: passive.description || '',
      iconPath: passive.iconPath || '',
    };
    if (Array.isArray(passive.tags) && passive.tags.length) entry.tags = [...passive.tags];
    if (Array.isArray(passive.requirements) && passive.requirements.length) entry.requirements = passive.requirements.map(cloneObject);
    if (Array.isArray(passive.modifiers) && passive.modifiers.length) entry.modifiers = JSON.parse(JSON.stringify(passive.modifiers));
    if (Array.isArray(passive.triggers) && passive.triggers.length) entry.triggers = passive.triggers.map(cloneObject);
    if (Array.isArray(passive.actions) && passive.actions.length) entry.actions = passive.actions.map(cloneObject);
    return entry;
  }

  function serializeSkill(skill = {}) {
    const entry = {
      id: skill.id || '',
      displayName: skill.displayName || skill.id || '',
      description: skill.description || '',
      overlayText: skill.overlayText || '',
      enabled: skill.enabled !== false,
      cost: Number(skill.cost ?? 1),
      position: {
        x: Number(skill.position?.x ?? 0),
        y: Number(skill.position?.y ?? 0),
      },
      type: skill.type || 'passive',
      rarity: skill.rarity || 'common',
      iconPath: skill.iconPath || '',
      abilityId: skill.abilityId || '',
      passiveId: skill.passiveId || '',
      tags: Array.isArray(skill.tags) ? [...skill.tags] : [],
      requires: Array.isArray(skill.requires) ? [...skill.requires] : [],
      modifiers: Array.isArray(skill.modifiers) ? JSON.parse(JSON.stringify(skill.modifiers)) : [],
      triggers: Array.isArray(skill.triggers) ? skill.triggers.map(cloneObject) : [],
      actions: Array.isArray(skill.actions) ? skill.actions.map(cloneObject) : [],
    };
    if (!entry.overlayText) delete entry.overlayText;
    return entry;
  }

  function buildSavePayload() {
    return {
      ...window.AppData,
      tree: Array.isArray(window.AppData.tree) ? window.AppData.tree.map(serializeSkill) : [],
      passives: Array.isArray(window.AppData.passives) ? window.AppData.passives.map(serializePassive) : [],
      stats: Array.isArray(window.AppData.stats) ? window.AppData.stats.map(serializeStat) : [],
    };
  }

  function setStatus(msg, cls = '') {
    const el = document.getElementById('file-status');
    el.textContent = msg;
    el.className = cls;
  }

  function markDirty() {
    dirty = true;
    setStatus('● Unsaved changes', 'dirty');
  }

  async function load() {
    setStatus('Loading…');
    try {
      const res = await fetch(API);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      normalizeData(data);
      dirty = false;
      setStatus('✓ Skill data', 'saved');
      notifyAll();
    } catch (e) {
      setStatus(`⚠ Load failed: ${e.message}`);
      console.error(e);
    }
  }

  async function save() {
    try {
      const res = await fetch(API, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(buildSavePayload(), null, 2),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      dirty = false;
      setStatus('✓ Saved', 'saved');
    } catch (e) {
      setStatus(`⚠ Save failed: ${e.message}`, 'dirty');
      console.error(e);
    }
  }

  async function reload() {
    if (dirty && !confirm('Discard unsaved changes?')) return;
    await load();
  }

  const refreshCallbacks = {};

  function onTabActivated(tabId, cb) {
    refreshCallbacks[tabId] = cb;
  }

  function notifyAll() {
    for (const cb of Object.values(refreshCallbacks)) cb();
  }

  function notifyTab(tabId) {
    refreshCallbacks[tabId]?.();
  }

  document.querySelectorAll('.tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
      document.querySelectorAll('.tab-pane').forEach(p => p.classList.remove('active'));
      tab.classList.add('active');
      const paneId = 'pane-' + tab.dataset.tab;
      document.getElementById(paneId).classList.add('active');
      notifyTab(tab.dataset.tab);
    });
  });

  document.addEventListener('keydown', e => {
    if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); save(); }
  });

  function openModal(overlayId) {
    document.getElementById(overlayId).classList.add('open');
  }
  function closeModal(overlayId) {
    document.getElementById(overlayId).classList.remove('open');
  }

  window.addEventListener('DOMContentLoaded', load);

  return { load, save, reload, markDirty, onTabActivated, notifyTab, openModal, closeModal };
})();
