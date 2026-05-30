// Reusable definition tabs with typed editors.
(() => {
  const UNSUPPORTED_TYPE = '__unsupported__';

  const COMPARE_OPS = [
    ['gte', 'gte'],
    ['lte', 'lte'],
    ['gt', 'gt'],
    ['lt', 'lt'],
    ['eq', 'eq'],
  ];

  const DEFINITION_EDITORS = {
    conditions: {
      defaultType: 'state',
      types: {
        state: {
          label: 'State Flag',
          fields: [
            { key: 'stateId', label: 'State', kind: 'ref', sourceKey: 'states', required: true },
            { key: 'operator', label: 'Operator', kind: 'select', required: true, options: [
              ['isTrue', 'isTrue'],
              ['isFalse', 'isFalse'],
            ]},
          ],
        },
        healthPercent: {
          label: 'Health Percent',
          fields: [
            { key: 'subject', label: 'Subject', kind: 'select', required: true, options: [
              ['self', 'self'],
              ['target', 'target'],
            ]},
            { key: 'operator', label: 'Operator', kind: 'select', required: true, options: [
              ['<=', '<='],
              ['<', '<'],
              ['>=', '>='],
              ['>', '>'],
              ['==', '=='],
              ['=', '='],
            ]},
            { key: 'value', label: 'Value', kind: 'number', required: true, step: '0.01' },
          ],
        },
        effectActive: {
          label: 'Effect Active',
          fields: [
            { key: 'effectId', label: 'Effect', kind: 'ref', sourceKey: 'effects', required: true },
          ],
        },
        equipmentSet: {
          label: 'Equipment Set',
          fields: [
            { key: 'setId', label: 'Set ID', kind: 'text', required: true, placeholder: 'LordRobe' },
            { key: 'operator', label: 'Operator', kind: 'select', required: true, options: [
              ['equipped', 'equipped'],
            ]},
          ],
        },
        equippedItem: {
          label: 'Equipped Item',
          fields: [
            { key: 'itemId', label: 'Item', kind: 'assetRef', assetCategory: 'items', required: true },
          ],
        },
        companionPresent: {
          label: 'Companion Present',
          fields: [
            { key: 'radius', label: 'Radius', kind: 'number', step: '0.5' },
          ],
        },
        bloodCompare: {
          label: 'Blood Compare',
          fields: [
            { key: 'op', label: 'Operator', kind: 'select', required: true, options: COMPARE_OPS },
            { key: 'value', label: 'Value (0..1)', kind: 'number', required: true, step: '0.01' },
          ],
        },
        statCompare: {
          label: 'Stat Compare',
          fields: [
            { key: 'statId', label: 'Stat', kind: 'ref', sourceKey: 'stats', required: true },
            { key: 'op', label: 'Operator', kind: 'select', required: true, options: COMPARE_OPS },
            { key: 'value', label: 'Value', kind: 'number', required: true, step: '0.01' },
          ],
        },
        cooldownReady: {
          label: 'Cooldown Ready',
          fields: [
            { key: 'abilityId', label: 'Ability', kind: 'ref', sourceKey: 'abilities', required: true },
          ],
        },
      },
    },
    requirements: {
      defaultType: 'condition',
      types: {
        condition: {
          label: 'Condition Requirement',
          fields: [
            { key: 'conditionId', label: 'Condition', kind: 'ref', sourceKey: 'conditions', required: true },
          ],
        },
        abilityUnlocked: {
          label: 'Ability Unlocked',
          fields: [
            { key: 'abilityId', label: 'Ability', kind: 'ref', sourceKey: 'abilities', required: true },
          ],
        },
        passiveUnlocked: {
          label: 'Passive Unlocked',
          fields: [
            { key: 'passiveId', label: 'Passive', kind: 'ref', sourceKey: 'passives', required: true },
          ],
        },
      },
    },
    triggers: {
      defaultType: 'onActivate',
      types: {
        onActivate: { label: 'On Activate', fields: [] },
        onKill: { label: 'On Kill', fields: [] },
        onFeed: { label: 'On Feed', fields: [] },
        onConnect: { label: 'On Connect', fields: [] },
        onFirstHit: { label: 'On First Hit', fields: [] },
        onDamageDealt: { label: 'On Damage Dealt', fields: [] },
        onDamageTaken: { label: 'On Damage Taken', fields: [] },
        onCondition: {
          label: 'On Condition',
          fields: [
            { key: 'conditionId', label: 'Condition', kind: 'ref', sourceKey: 'conditions', required: true },
            { key: 'cooldown', label: 'Cooldown (s)', kind: 'number', step: '0.5' },
          ],
        },
        onLowHealth: {
          label: 'On Low Health',
          fields: [
            { key: 'threshold', label: 'Threshold (0..1)', kind: 'number', required: true, step: '0.01' },
            { key: 'minIntervalSeconds', label: 'Min Interval (s)', kind: 'number', step: '0.5' },
          ],
        },
        onBlockBreak: {
          label: 'On Block Break',
          fields: [
            { key: 'blockId', label: 'Block ID (optional filter)', kind: 'text', placeholder: 'Stone' },
          ],
        },
      },
    },
    actions: {
      defaultType: 'applyEffect',
      types: {
        applyEffect: {
          label: 'Apply Effect',
          fields: [
            { key: 'effectId', label: 'Effect', kind: 'ref', sourceKey: 'effects', required: true },
            { key: 'targetingId', label: 'Targeting (optional, for fan-out)', kind: 'ref', sourceKey: 'targetings' },
            { key: 'conditionId', label: 'Per-target Condition (optional)', kind: 'ref', sourceKey: 'conditions' },
            { key: 'durationSeconds', label: 'Duration Override (s)', kind: 'number', step: '0.5' },
          ],
        },
        toggleEffect: {
          label: 'Toggle Effect',
          fields: [
            { key: 'effectId', label: 'Effect', kind: 'ref', sourceKey: 'effects', required: true },
          ],
        },
        removeEffect: {
          label: 'Remove Effect',
          fields: [
            { key: 'effectId', label: 'Effect', kind: 'ref', sourceKey: 'effects', required: true },
            { key: 'targetingId', label: 'Targeting (optional)', kind: 'ref', sourceKey: 'targetings' },
          ],
        },
        spawnProjectile: {
          label: 'Spawn Projectile',
          fields: [
            { key: 'projectileId', label: 'Projectile ID (optional override)', kind: 'assetRef', assetCategory: 'projectiles', placeholder: 'blood_throw_projectile' },
            { key: 'damageStatId', label: 'Damage Stat (optional)', kind: 'ref', sourceKey: 'stats' },
            { key: 'speedStatId', label: 'Speed Stat (optional)', kind: 'ref', sourceKey: 'stats' },
          ],
        },
        dealDamage: {
          label: 'Deal Damage',
          fields: [
            { key: 'targetingId', label: 'Targeting', kind: 'ref', sourceKey: 'targetings' },
            { key: 'selfDamage', label: 'Self Damage', kind: 'boolean' },
            { key: 'damageType', label: 'Damage Type', kind: 'text', placeholder: 'blood' },
            { key: 'amount', label: 'Literal Amount', kind: 'number', step: '0.01' },
            { key: 'statId', label: 'Stat (for amount or multiplier)', kind: 'ref', sourceKey: 'stats' },
            { key: 'multiplier', label: 'Multiplier (combined with statId)', kind: 'number', step: '0.01' },
          ],
        },
        executeFinalBlow: {
          label: 'Execute Final Blow',
          fields: [
            { key: 'requirementId', label: 'Requirement (legacy)', kind: 'ref', sourceKey: 'requirements' },
            { key: 'statId', label: 'Stat (health threshold source)', kind: 'ref', sourceKey: 'stats' },
            { key: 'threshold', label: 'Threshold (0..1)', kind: 'number', step: '0.01' },
          ],
        },
        teleport: {
          label: 'Teleport',
          fields: [
            { key: 'targetingId', label: 'Targeting', kind: 'ref', sourceKey: 'targetings' },
            { key: 'distance', label: 'Distance (blocks)', kind: 'number', step: '0.5' },
            { key: 'mode', label: 'Mode', kind: 'select', options: [
              ['forward', 'forward'],
              ['toTarget', 'toTarget'],
              ['toLook', 'toLook'],
            ]},
          ],
        },
        activateAbility: {
          label: 'Activate Ability',
          fields: [
            { key: 'abilityId', label: 'Ability', kind: 'ref', sourceKey: 'abilities', required: true },
          ],
        },
        grantTemporaryModifier: {
          label: 'Grant Temporary Modifier',
          fields: [
            { key: 'statId', label: 'Stat', kind: 'ref', sourceKey: 'stats', required: true },
            { key: 'amount', label: 'Amount (literal)', kind: 'number', step: '0.01' },
            { key: 'amountStatId', label: 'Amount Stat (alternative)', kind: 'ref', sourceKey: 'stats' },
            { key: 'multiplier', label: 'Multiplier', kind: 'number', step: '0.01' },
            { key: 'duration', label: 'Duration (s)', kind: 'number', step: '0.5' },
            { key: 'durationStatId', label: 'Duration Stat (alternative)', kind: 'ref', sourceKey: 'stats' },
            { key: 'stacking', label: 'Stacking', kind: 'select', options: [
              ['replace', 'replace'],
              ['stack', 'stack'],
              ['ignore', 'ignore'],
            ]},
            { key: 'targetingId', label: 'Targeting', kind: 'ref', sourceKey: 'targetings' },
          ],
        },
        modifyStat: {
          label: 'Modify Stat',
          fields: [
            { key: 'statId', label: 'Stat', kind: 'ref', sourceKey: 'stats', required: true },
            { key: 'op', label: 'Operation', kind: 'select', required: true, options: [
              ['add', 'add'],
              ['set', 'set'],
              ['mul', 'mul'],
            ]},
            { key: 'amount', label: 'Amount', kind: 'number', required: true, step: '0.01' },
            { key: 'targetingId', label: 'Targeting', kind: 'ref', sourceKey: 'targetings' },
          ],
        },
        modifyBlood: {
          label: 'Modify Blood',
          fields: [
            { key: 'op', label: 'Operation', kind: 'select', required: true, options: [
              ['add', 'add'],
              ['set', 'set'],
              ['mul', 'mul'],
            ]},
            { key: 'amount', label: 'Amount', kind: 'number', required: true, step: '0.01' },
            { key: 'valueType', label: 'Value Type', kind: 'select', required: true, options: [
              ['absolute', 'absolute'],
              ['percent', 'percent'],
            ]},
            { key: 'targetingId', label: 'Targeting', kind: 'ref', sourceKey: 'targetings' },
          ],
        },
        playSound: {
          label: 'Play Sound',
          fields: [
            { key: 'soundEventId', label: 'Sound Event', kind: 'assetRef', assetCategory: 'soundEvents', required: true },
            { key: 'scope', label: 'Scope', kind: 'select', required: true, options: [
              ['local', 'local'],
              ['world', 'world'],
            ]},
            { key: 'targetingId', label: 'Targeting', kind: 'ref', sourceKey: 'targetings' },
          ],
        },
        spawnParticles: {
          label: 'Spawn Particles',
          fields: [
            { key: 'systemId', label: 'Particle System ID', kind: 'text', required: true, placeholder: 'Vampirism_BloodBurst' },
            { key: 'targetingId', label: 'Targeting', kind: 'ref', sourceKey: 'targetings' },
            { key: 'positionOffset', label: 'Position Offset (x,y,z)', kind: 'text', placeholder: '0,1,0' },
          ],
        },
        sendMessage: {
          label: 'Send Message',
          fields: [
            { key: 'text', label: 'Message Text', kind: 'text', required: true },
            { key: 'target', label: 'Target', kind: 'select', required: true, options: [
              ['caster', 'caster'],
              ['target', 'target'],
              ['area', 'area'],
            ]},
          ],
        },
        healSelf: {
          label: 'Heal Self',
          fields: [
            { key: 'statId', label: 'Heal Stat', kind: 'ref', sourceKey: 'stats', required: true },
          ],
        },
      },
    },
    targetings: {
      defaultType: 'self',
      types: {
        self: { label: 'Self', fields: [] },
        projectile: {
          label: 'Projectile',
          fields: [
            { key: 'team', label: 'Team', kind: 'select', required: true, options: [
              ['enemy', 'enemy'],
              ['ally', 'ally'],
              ['self', 'self'],
              ['any', 'any'],
            ]},
            { key: 'maxRange', label: 'Max Range', kind: 'number', step: '0.5' },
          ],
        },
        target: {
          label: 'Target',
          fields: [
            { key: 'team', label: 'Team', kind: 'select', required: true, options: [
              ['enemy', 'enemy'],
              ['ally', 'ally'],
              ['self', 'self'],
              ['any', 'any'],
            ]},
            { key: 'maxRange', label: 'Max Range', kind: 'number', step: '0.5' },
          ],
        },
        area: {
          label: 'Area',
          fields: [
            { key: 'shape', label: 'Shape', kind: 'select', required: true, options: [
              ['circle', 'circle'],
              ['cone', 'cone'],
              ['box', 'box'],
            ]},
            { key: 'radius', label: 'Radius', kind: 'number', step: '0.5' },
            { key: 'team', label: 'Team', kind: 'select', required: true, options: [
              ['enemy', 'enemy'],
              ['ally', 'ally'],
              ['self', 'self'],
              ['any', 'any'],
            ]},
          ],
        },
        lookPosition: {
          label: 'Look Position',
          fields: [
            { key: 'maxRange', label: 'Max Range', kind: 'number', step: '0.5' },
          ],
        },
        lookRaycast: {
          label: 'Look Raycast',
          fields: [],
        },
        areaAtLook: {
          label: 'Area At Look',
          fields: [
            { key: 'radius', label: 'Radius', kind: 'number', required: true, step: '0.5' },
            { key: 'team', label: 'Team', kind: 'select', options: [
              ['enemy', 'enemy'],
              ['ally', 'ally'],
              ['any', 'any'],
            ]},
          ],
        },
      },
    },
  };

  function isPlainObject(value) {
    return value != null && typeof value === 'object' && !Array.isArray(value);
  }

  function cloneDefinition(value) {
    return isPlainObject(value) ? JSON.parse(JSON.stringify(value)) : {};
  }

  function makeDefinitionTab({ paneId, tabId, dataKey, label }) {
    const editorConfig = DEFINITION_EDITORS[dataKey];
    const pane = document.getElementById(paneId);
    pane.insertAdjacentHTML('beforeend', `
      <div class="editor-split">
        <div class="editor-main">
          <div class="pane-inner">
            <div class="pane-toolbar">
              <h2>${label}</h2>
              <button class="btn-primary" onclick="${tabId[0].toUpperCase() + tabId.slice(1)}Tab.add()">+ Add</button>
            </div>
            <table class="data-table">
              <thead><tr><th>ID</th><th>Display Name</th><th>Description</th><th>Tags</th><th>Definition</th><th>Actions</th></tr></thead>
              <tbody id="${dataKey}-tbody"></tbody>
            </table>
          </div>
        </div>
        <div class="editor-side" id="${dataKey}-editor">
          <div class="editor-side-header">
            <h3>${label.slice(0, -1)} Editor</h3>
            <button class="btn-secondary" type="button" onclick="${tabId[0].toUpperCase() + tabId.slice(1)}Tab.close()">Close</button>
          </div>
          <div class="editor-side-body">
            <div class="editor-empty" id="${dataKey}-empty">Select a row to edit or create a new ${label.slice(0, -1).toLowerCase()}.</div>
            <div id="${dataKey}-form" style="display:none">
              <div class="form-group"><label>ID</label><input id="${dataKey}-id" type="text" /></div>
              <div class="form-group"><label>Display Name</label><input id="${dataKey}-name" type="text" /></div>
              <div class="form-group"><label>Description</label><textarea id="${dataKey}-desc"></textarea></div>
              <div class="form-group"><label>Tags (comma-separated)</label><input id="${dataKey}-tags" type="text" /></div>
              <div class="form-group">
                <label>Definition Type</label>
                <select id="${dataKey}-definition-type"></select>
              </div>
              <div id="${dataKey}-definition-fields"></div>
              <div class="editor-actions">
                <button class="btn-delete" type="button" onclick="${tabId[0].toUpperCase() + tabId.slice(1)}Tab.delCurrent()">Delete</button>
                <button class="btn-primary" type="button" onclick="${tabId[0].toUpperCase() + tabId.slice(1)}Tab.save()">Save</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    `);

    let editIndex = null;
    let currentDefinition = {};

    function globalName() {
      return `${tabId[0].toUpperCase() + tabId.slice(1)}Tab`;
    }

    function list() {
      return window.AppData[dataKey];
    }

    function supportedType(type) {
      return Boolean(type && editorConfig.types[type]);
    }

    function summarize(definition) {
      if (!isPlainObject(definition)) return '—';
      const type = definition.type;
      if (!type) return '—';
      switch (type) {
        case 'state':
          return `state:${SideEditors.lookupLabel('states', definition.stateId)} ${definition.operator || ''}`.trim();
        case 'effectActive':
          return `effect:${SideEditors.lookupLabel('effects', definition.effectId)}`;
        case 'condition':
          return `condition:${SideEditors.lookupLabel('conditions', definition.conditionId)}`;
        case 'onCondition':
          return `onCondition:${SideEditors.lookupLabel('conditions', definition.conditionId)}`;
        case 'onLowHealth':
          return `onLowHealth<${definition.threshold ?? '?'}`;
        case 'onBlockBreak':
          return definition.blockId ? `onBlockBreak:${definition.blockId}` : 'onBlockBreak';
        case 'applyEffect':
        case 'toggleEffect':
        case 'removeEffect':
          return `${type}:${SideEditors.lookupLabel('effects', definition.effectId)}`;
        case 'executeFinalBlow':
          if (definition.statId) return `execute:${SideEditors.lookupLabel('stats', definition.statId)}<${definition.threshold ?? ''}`;
          return `execute:${SideEditors.lookupLabel('requirements', definition.requirementId)}`;
        case 'activateAbility':
          return `activate:${SideEditors.lookupLabel('abilities', definition.abilityId)}`;
        case 'dealDamage':
          if (definition.targetingId) return `${type}:${SideEditors.lookupLabel('targetings', definition.targetingId)}`;
          if (definition.conditionId) return `${type}:${SideEditors.lookupLabel('conditions', definition.conditionId)}`;
          return type;
        case 'healSelf':
          return `heal:${SideEditors.lookupLabel('stats', definition.statId)}`;
        case 'grantTemporaryModifier':
          return `tempMod:${SideEditors.lookupLabel('stats', definition.statId)}`;
        case 'modifyStat':
          return `modifyStat:${SideEditors.lookupLabel('stats', definition.statId)} ${definition.op || ''}`.trim();
        case 'modifyBlood':
          return `modifyBlood ${definition.op || ''} ${definition.amount ?? ''}`.trim();
        case 'playSound':
          return `sound:${definition.soundEventId || ''} (${definition.scope || ''})`;
        case 'spawnParticles':
          return `particles:${definition.systemId || ''}`;
        case 'sendMessage':
          return `msg:${(definition.text || '').slice(0, 24)}`;
        case 'bloodCompare':
          return `blood ${definition.op || ''} ${definition.value ?? ''}`;
        case 'statCompare':
          return `${SideEditors.lookupLabel('stats', definition.statId)} ${definition.op || ''} ${definition.value ?? ''}`;
        case 'cooldownReady':
          return `ready:${SideEditors.lookupLabel('abilities', definition.abilityId)}`;
        case 'teleport':
          if (definition.mode) return `teleport:${definition.mode}`;
          return `teleport:${SideEditors.lookupLabel('targetings', definition.targetingId)}`;
        default:
          return type;
      }
    }

    function renderTypeOptions(selectedType) {
      const select = document.getElementById(`${dataKey}-definition-type`);
      select.innerHTML = '';
      if (selectedType === UNSUPPORTED_TYPE && currentDefinition.type) {
        const option = document.createElement('option');
        option.value = UNSUPPORTED_TYPE;
        option.textContent = `Unsupported (${currentDefinition.type}) — preserve current definition`;
        select.appendChild(option);
      }
      Object.entries(editorConfig.types).forEach(([type, def]) => {
        // Hide deprecated/removed types from the picker unless current selection is that type
        if ((def.deprecated || def.removed) && type !== selectedType) return;
        const option = document.createElement('option');
        option.value = type;
        option.textContent = def.label;
        select.appendChild(option);
      });
      select.value = selectedType;
    }

    function fieldTemplate(field, fieldId) {
      if (field.kind === 'assetRef') {
        return `
          <div class="form-group">
            <label>${field.label}</label>
            <select id="${fieldId}"></select>
          </div>
        `;
      }
      if (field.kind === 'boolean') {
        return `
          <div class="form-group">
            <label>${field.label}</label>
            <select id="${fieldId}">
              <option value="">— select —</option>
              <option value="true">true</option>
              <option value="false">false</option>
            </select>
          </div>
        `;
      }
      if (field.kind === 'select' || field.kind === 'ref') {
        return `
          <div class="form-group">
            <label>${field.label}</label>
            <select id="${fieldId}"></select>
          </div>
        `;
      }
      if (field.kind === 'number') {
        return `
          <div class="form-group">
            <label>${field.label}</label>
            <input id="${fieldId}" type="number" step="${field.step || 'any'}" />
          </div>
        `;
      }
      return `
        <div class="form-group">
          <label>${field.label}</label>
          <input id="${fieldId}" type="text" placeholder="${field.placeholder || ''}" />
        </div>
      `;
    }

    function renderTypedFields(type, definition = {}) {
      const fieldsWrap = document.getElementById(`${dataKey}-definition-fields`);
      if (type === UNSUPPORTED_TYPE) {
        fieldsWrap.innerHTML = `
          <div class="editor-help">
            This definition type is not modeled yet. Saving will preserve the current JSON until you switch to a supported type.
          </div>
        `;
        return;
      }
      const typeDef = editorConfig.types[type];
      let banner = '';
      if (typeDef.removed) {
        banner = `
          <div class="editor-help" style="background:#3a1020;border-left:3px solid var(--accent);padding:10px;margin-bottom:12px;color:#ffb0b8">
            <strong>⛔ Action type <code>${type}</code> was removed.</strong>
            Replace it with <code>${typeDef.replacement}</code> or another generic primitive. Saving a row with this type is a blocking validation error.
          </div>
        `;
      }
      fieldsWrap.innerHTML = banner + typeDef.fields.map((field) => fieldTemplate(field, `${dataKey}-field-${field.key}`)).join('');

      typeDef.fields.forEach((field) => {
        const fieldId = `${dataKey}-field-${field.key}`;
        const input = document.getElementById(fieldId);
        const value = definition[field.key];
        if (field.kind === 'ref') {
          SideEditors.fillSelect(input, field.sourceKey, value, { nullable: true });
        } else if (field.kind === 'assetRef') {
          HytaleAssetStore.fillSelect(input, field.assetCategory, value ?? '', { nullable: true });
        } else if (field.kind === 'boolean') {
          input.value = value == null ? '' : String(Boolean(value));
        } else if (field.kind === 'select') {
          input.innerHTML = '<option value="">— select —</option>';
          field.options.forEach(([optionValue, optionLabel]) => {
            const option = document.createElement('option');
            option.value = optionValue;
            option.textContent = optionLabel;
            input.appendChild(option);
          });
          input.value = value ?? '';
        } else if (field.kind === 'number') {
          input.value = value ?? '';
        } else {
          input.value = value ?? '';
        }
      });
    }

    function getSelectedType(definition = {}) {
      if (!definition.type) return editorConfig.defaultType;
      return supportedType(definition.type) ? definition.type : UNSUPPORTED_TYPE;
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
          <td style="max-width:220px;white-space:pre-wrap">${item.description || ''}</td>
          <td>${(item.tags || []).map((tag) => `<span class="tag">${tag}</span>`).join('') || '—'}</td>
          <td><code>${summarize(item.definition)}</code></td>
          <td class="actions">
            <button class="btn-edit" type="button" onclick="${globalName()}.edit(${i}); event.stopPropagation();">Edit</button>
            <button class="btn-delete" type="button" onclick="${globalName()}.del(${i}); event.stopPropagation();">Del</button>
          </td>`;
        tr.addEventListener('click', () => openEditor(i));
        tbody.appendChild(tr);
      });
    }

    function bindTypeChange() {
      const select = document.getElementById(`${dataKey}-definition-type`);
      select.onchange = () => {
        if (select.value === UNSUPPORTED_TYPE) {
          renderTypedFields(select.value, currentDefinition);
          return;
        }
        const nextDefinition = { type: select.value };
        renderTypedFields(select.value, nextDefinition);
      };
    }

    function openEditor(index) {
      editIndex = index;
      const isNew = index === -1;
      const item = isNew ? {} : list()[index];
      currentDefinition = cloneDefinition(item.definition);
      document.getElementById(`${dataKey}-empty`).style.display = 'none';
      document.getElementById(`${dataKey}-form`).style.display = '';
      document.getElementById(`${dataKey}-id`).value = item.id || '';
      document.getElementById(`${dataKey}-name`).value = item.displayName || '';
      document.getElementById(`${dataKey}-desc`).value = item.description || '';
      document.getElementById(`${dataKey}-tags`).value = SideEditors.tagsToText(item.tags);
      const selectedType = isNew ? editorConfig.defaultType : getSelectedType(item.definition || {});
      renderTypeOptions(selectedType);
      renderTypedFields(selectedType, item.definition || { type: editorConfig.defaultType });
      bindTypeChange();
      document.querySelector(`#${dataKey}-form .btn-delete`).style.display = isNew ? 'none' : '';
      render();
    }

    function closeEditor() {
      editIndex = null;
      currentDefinition = {};
      document.getElementById(`${dataKey}-empty`).style.display = '';
      document.getElementById(`${dataKey}-form`).style.display = 'none';
      render();
    }

    function buildTypedDefinition(type) {
      if (type === UNSUPPORTED_TYPE) {
        return cloneDefinition(currentDefinition);
      }
      const typeDef = editorConfig.types[type];
      const definition = currentDefinition.type === type ? cloneDefinition(currentDefinition) : { type };
      definition.type = type;
      typeDef.fields.forEach((field) => {
        delete definition[field.key];
      });
      for (const field of typeDef.fields) {
        const input = document.getElementById(`${dataKey}-field-${field.key}`);
        let value = input.value;
        if (field.kind === 'number') {
          value = value === '' ? null : Number(value);
        } else if (field.kind === 'boolean') {
          value = value === '' ? null : value === 'true';
        }
        if (field.required && (value === '' || value == null || Number.isNaN(value))) {
          alert(`${field.label} is required.`);
          return null;
        }
        if (value === '' || value == null || Number.isNaN(value)) continue;
        definition[field.key] = value;
      }
      return definition;
    }

    function save() {
      const id = document.getElementById(`${dataKey}-id`).value.trim();
      if (!id) { alert('ID is required'); return; }
      if (editIndex === null) return;
      if (editIndex === -1 && list().some((item) => item.id === id)) { alert('ID already exists'); return; }

      const type = document.getElementById(`${dataKey}-definition-type`).value;
      if (type !== UNSUPPORTED_TYPE && editorConfig.types[type]?.removed) {
        alert(`Action type "${type}" was removed from the runtime. Switch to "${editorConfig.types[type].replacement}" (or another primitive) before saving.`);
        return;
      }
      let definition;
      try {
        definition = buildTypedDefinition(type);
      } catch (_) {
        return;
      }
      if (!definition) return;
      if (!isPlainObject(definition)) {
        alert('Definition must be an object.');
        return;
      }

      const entry = {
        id,
        displayName: document.getElementById(`${dataKey}-name`).value.trim() || id,
        description: document.getElementById(`${dataKey}-desc`).value.trim(),
        tags: SideEditors.textToTags(document.getElementById(`${dataKey}-tags`).value),
        definition,
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
    window[globalName()] = {
      add: () => openEditor(-1),
      edit: openEditor,
      save,
      del,
      delCurrent,
      close: closeEditor,
    };
  }

  makeDefinitionTab({ paneId: 'pane-conditions', tabId: 'conditions', dataKey: 'conditions', label: 'Conditions' });
  makeDefinitionTab({ paneId: 'pane-requirements', tabId: 'requirements', dataKey: 'requirements', label: 'Requirements' });
  makeDefinitionTab({ paneId: 'pane-triggers', tabId: 'triggers', dataKey: 'triggers', label: 'Triggers' });
  makeDefinitionTab({ paneId: 'pane-actions', tabId: 'actions', dataKey: 'actions', label: 'Actions' });
  makeDefinitionTab({ paneId: 'pane-targetings', tabId: 'targetings', dataKey: 'targetings', label: 'Targetings' });
})();
