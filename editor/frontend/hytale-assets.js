const HytaleAssetStore = (() => {
  const API = '/api/hytale-assets';
  const FILE_API = '/api/hytale-assets/file';
  const cache = new Map();

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  async function request(url, options) {
    const res = await fetch(url, options);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    return res.json();
  }

  async function loadCategory(category, { force = false } = {}) {
    if (!force && cache.has(category)) {
      return cache.get(category);
    }
    const promise = request(`${API}?category=${encodeURIComponent(category)}`).catch((error) => {
      cache.delete(category);
      throw error;
    });
    cache.set(category, promise);
    return promise;
  }

  async function getFile(category, id) {
    return request(`${FILE_API}?category=${encodeURIComponent(category)}&id=${encodeURIComponent(id)}`);
  }

  async function saveFile(category, id, data) {
    const payload = await request(`${FILE_API}?category=${encodeURIComponent(category)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id, data }),
    });
    cache.delete(category);
    return payload;
  }

  async function deleteFile(category, id) {
    const payload = await request(`${FILE_API}?category=${encodeURIComponent(category)}&id=${encodeURIComponent(id)}`, {
      method: 'DELETE',
    });
    cache.delete(category);
    return payload;
  }

  async function getResolvableRefs(category, currentValue = '') {
    const payload = await loadCategory(category);
    const values = [...new Set([...(payload.localRefs || []), ...(payload.officialRefs || [])])].sort();
    if (currentValue && !values.includes(currentValue)) {
      values.unshift(currentValue);
    }
    return values;
  }

  async function fillSelect(selectEl, category, currentValue = '', { nullable = true } = {}) {
    const values = await getResolvableRefs(category, currentValue);
    selectEl.innerHTML = '';
    if (nullable) {
      const option = document.createElement('option');
      option.value = '';
      option.textContent = '— none —';
      selectEl.appendChild(option);
    }
    values.forEach((value) => {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = value;
      selectEl.appendChild(option);
    });
    selectEl.value = currentValue ?? '';
  }

  function invalidate(category) {
    if (category) cache.delete(category);
    else cache.clear();
  }

  return {
    loadCategory,
    getFile,
    saveFile,
    deleteFile,
    fillSelect,
    getResolvableRefs,
    invalidate,
  };
})();

(() => {
  let cssInjected = false;

  const INTERACTION_TYPE_OPTIONS = [
    ['ApplyEffect', 'ApplyEffect'],
    ['ApplyForce', 'ApplyForce'],
    ['BlockCondition', 'BlockCondition'],
    ['BreakBlock', 'BreakBlock'],
    ['Chaining', 'Chaining'],
    ['ChangeBlock', 'ChangeBlock'],
    ['ChangeStat', 'ChangeStat'],
    ['ChangeState', 'ChangeState'],
    ['Charging', 'Charging'],
    ['Command', 'Command'],
    ['Condition', 'Condition'],
    ['ContextualUseNPC', 'ContextualUseNPC'],
    ['DamageEntity', 'DamageEntity'],
    ['DestroyTreasureCondition', 'DestroyTreasureCondition'],
    ['Door', 'Door'],
    ['EffectCondition', 'EffectCondition'],
    ['Explode', 'Explode'],
    ['FertilizeSoil', 'FertilizeSoil'],
    ['FirstClick', 'FirstClick'],
    ['LaunchProjectile', 'LaunchProjectile'],
    ['MemoriesCondition', 'MemoriesCondition'],
    ['ModifyInventory', 'ModifyInventory'],
    ['MovementCondition', 'MovementCondition'],
    ['OpenContainer', 'OpenContainer'],
    ['OpenCustomUI', 'OpenCustomUI'],
    ['OpenProcessingBench', 'OpenProcessingBench'],
    ['OpenTreasureContainer', 'OpenTreasureContainer'],
    ['Parallel', 'Parallel'],
    ['PlaceBlock', 'PlaceBlock'],
    ['PlacementCountCondition', 'PlacementCountCondition'],
    ['Projectile', 'Projectile'],
    ['RefillContainer', 'RefillContainer'],
    ['RemoveEntity', 'RemoveEntity'],
    ['Repeat', 'Repeat'],
    ['Replace', 'Replace'],
    ['Seating', 'Seating'],
    ['Selector', 'Selector'],
    ['Serial', 'Serial'],
    ['Simple', 'Simple'],
    ['SpawnDeployableFromRaycast', 'SpawnDeployableFromRaycast'],
    ['SpawnNPC', 'SpawnNPC'],
    ['SpawnPrefab', 'SpawnPrefab'],
    ['StatsCondition', 'StatsCondition'],
    ['StatsConditionWithModifier', 'StatsConditionWithModifier'],
    ['TeleportInstance', 'TeleportInstance'],
    ['UseBlock', 'UseBlock'],
    ['UseEntity', 'UseEntity'],
    ['UseWateringCan', 'UseWateringCan'],
    ['Wielding', 'Wielding'],
    ['Custom', 'Custom / other'],
  ];

  const GAME_MODE_OPTIONS = [
    ['Adventure', 'Adventure'],
    ['Creative', 'Creative'],
  ];

  const MATCH_OPTIONS = [
    ['All', 'All'],
    ['None', 'None'],
  ];

  const VALUE_TYPE_OPTIONS = [
    ['Absolute', 'Absolute'],
    ['Percent', 'Percent'],
  ];

  const ENTITY_OPTIONS = [
    ['Target', 'Target'],
    ['User', 'User'],
  ];

  const CONTEXT_OPTIONS = [
    ['Milk', 'Milk'],
    ['Shear', 'Shear'],
  ];

  const SELECTOR_ID_OPTIONS = [
    ['AOECircle', 'AOECircle'],
    ['Horizontal', 'Horizontal'],
    ['Stab', 'Stab'],
  ];

  const ORIGIN_SOURCE_OPTIONS = [
    ['Block', 'Block'],
    ['Entity', 'Entity'],
  ];

  const CHANGE_VELOCITY_TYPE_OPTIONS = [
    ['Add', 'Add'],
    ['Set', 'Set'],
  ];

  const DEPLOYABLE_TYPE_OPTIONS = [
    ['Aoe', 'Aoe'],
  ];

  const DEPLOYABLE_SHAPE_OPTIONS = [
    ['Cylinder', 'Cylinder'],
  ];

  const REMOVAL_BEHAVIOR_OPTIONS = [
    ['Complete', 'Complete'],
    ['Infinite', 'Infinite'],
    ['Duration', 'Duration'],
  ];

  const ROTATION_MODE_OPTIONS = [
    ['None', 'None'],
    ['Velocity', 'Velocity'],
    ['VelocityDamped', 'VelocityDamped'],
    ['VelocityRoll', 'VelocityRoll'],
  ];

  const PHYSICS_TYPE_OPTIONS = [
    ['Standard', 'Standard'],
  ];

  const BLOCK_MATERIAL_OPTIONS = [
    ['Empty', 'Empty'],
    ['Solid', 'Solid'],
  ];

  const BLOCK_DRAW_TYPE_OPTIONS = [
    ['Cube', 'Cube'],
    ['CubeWithModel', 'CubeWithModel'],
    ['Empty', 'Empty'],
    ['Model', 'Model'],
  ];

  const BLOCK_OPACITY_OPTIONS = [
    ['Cutout', 'Cutout'],
    ['Semitransparent', 'Semitransparent'],
    ['Solid', 'Solid'],
    ['Transparent', 'Transparent'],
  ];

  const BLOCK_VARIANT_ROTATION_OPTIONS = [
    ['All', 'All'],
    ['Debug', 'Debug'],
    ['DoublePipe', 'DoublePipe'],
    ['NESW', 'NESW'],
    ['None', 'None'],
    ['Pipe', 'Pipe'],
    ['UpDown', 'UpDown'],
    ['UpDownNESW', 'UpDownNESW'],
    ['Wall', 'Wall'],
  ];

  const TABS = [
    {
      tabId: 'hytale-effects',
      paneId: 'pane-hytale-effects',
      category: 'effects',
      title: 'Entity Effects',
      noun: 'entity effect',
      idPlaceholder: 'Status/Vampirism/BloodFrenzy',
      help: 'Creates Hytale-native files under Server/Entity/Effects/.',
      fields: [
        { key: 'Parent', label: 'Parent Effect', kind: 'assetRef', assetCategory: 'effects', placeholder: 'Status/Burn' },
        { key: 'StatusEffectIcon', label: 'Status Effect Icon', kind: 'text', placeholder: 'UI/StatusEffects/Burn.png' },
        { key: 'OverlapBehavior', label: 'Overlap Behavior', kind: 'select', default: 'Overwrite', options: [
          ['Overwrite', 'Overwrite'],
          ['Extend', 'Extend'],
          ['Ignore', 'Ignore'],
        ]},
        { key: 'RemovalBehavior', label: 'Removal Behavior', kind: 'select', nullable: true, options: REMOVAL_BEHAVIOR_OPTIONS },
        { key: 'Duration', label: 'Duration', kind: 'number', step: '0.1', default: 3 },
        { key: 'Infinite', label: 'Infinite', kind: 'boolean', default: false },
        { key: 'Debuff', label: 'Debuff', kind: 'boolean', default: false },
        { key: 'ModelChange', label: 'Model Change', kind: 'assetRef', assetCategory: 'models', placeholder: 'Bat' },
        { key: 'ModelOverride.Model', label: 'Model Override', kind: 'assetRef', assetCategory: 'models', placeholder: 'Bat' },
        { key: 'ModelOverride.Texture', label: 'Model Override Texture', kind: 'text', placeholder: 'Characters/Vampire/Vampire.png' },
        { key: 'DeathMessageKey', label: 'Death Message Key', kind: 'text', placeholder: 'server.general.deathCause.burn' },
        { key: 'ApplicationEffects.EntityTopTint', label: 'Entity Top Tint', kind: 'text', placeholder: '#FF0000' },
        { key: 'ApplicationEffects.EntityBottomTint', label: 'Entity Bottom Tint', kind: 'text', placeholder: '#220000' },
        { key: 'ApplicationEffects.EntityAnimationId', label: 'Entity Animation ID', kind: 'text', placeholder: 'Bat_Fly' },
        { key: 'ApplicationEffects.ScreenEffect', label: 'Screen Effect', kind: 'text', placeholder: 'ScreenEffects/Fire.png' },
        { key: 'ApplicationEffects.ModelVFXId', label: 'Model VFX ID', kind: 'text', placeholder: 'Burn' },
        { key: 'ApplicationEffects.WorldSoundEventId', label: 'World Sound Event', kind: 'assetRef', assetCategory: 'soundEvents', placeholder: 'SFX_Effect_Burn_World' },
        { key: 'ApplicationEffects.LocalSoundEventId', label: 'Local Sound Event', kind: 'assetRef', assetCategory: 'soundEvents', placeholder: 'SFX_Effect_Burn_Local' },
        { key: 'ApplicationEffects.HorizontalSpeedMultiplier', label: 'Horizontal Speed Multiplier', kind: 'number', step: '0.1' },
        { key: 'ApplicationEffects.KnockbackMultiplier', label: 'Knockback Multiplier', kind: 'number', step: '0.1' },
        { key: 'ApplicationEffects.MouseSensitivityAdjustmentTarget', label: 'Mouse Sensitivity Target', kind: 'number', step: '0.1' },
        { key: 'ApplicationEffects.MouseSensitivityAdjustmentDuration', label: 'Mouse Sensitivity Duration', kind: 'number', step: '0.1' },
        { key: 'ApplicationEffects.AbilityEffects.Disabled', label: 'Disabled Interaction Types', kind: 'stringList', placeholder: 'PlaceBlock' },
        { key: 'ApplicationEffects.MovementEffects.DisableAll', label: 'Disable All Movement', kind: 'boolean', nullable: true },
        { key: 'ApplicationEffects.MovementEffects.DisableForward', label: 'Disable Forward', kind: 'boolean', nullable: true },
        { key: 'ApplicationEffects.MovementEffects.DisableBackward', label: 'Disable Backward', kind: 'boolean', nullable: true },
        { key: 'ApplicationEffects.MovementEffects.DisableLeft', label: 'Disable Left', kind: 'boolean', nullable: true },
        { key: 'ApplicationEffects.MovementEffects.DisableRight', label: 'Disable Right', kind: 'boolean', nullable: true },
        { key: 'ApplicationEffects.MovementEffects.DisableSprint', label: 'Disable Sprint', kind: 'boolean', nullable: true },
        { key: 'ApplicationEffects.MovementEffects.DisableJump', label: 'Disable Jump', kind: 'boolean', nullable: true },
        { key: 'ApplicationEffects.MovementEffects.DisableCrouch', label: 'Disable Crouch', kind: 'boolean', nullable: true },
        { key: 'DamageCalculatorCooldown', label: 'Damage Calculator Cooldown', kind: 'number', step: '0.1' },
        { key: 'DamageCalculator.BaseDamage.Fire', label: 'Fire Damage', kind: 'number', step: '0.1' },
        { key: 'DamageCalculator.BaseDamage.Physical', label: 'Physical Damage', kind: 'number', step: '0.1' },
        { key: 'DamageCalculator.BaseDamage.Projectile', label: 'Projectile Damage', kind: 'number', step: '0.1' },
        { key: 'DamageEffects.WorldSoundEventId', label: 'Damage World Sound', kind: 'assetRef', assetCategory: 'soundEvents', placeholder: 'SFX_Effect_Burn_World' },
        { key: 'DamageEffects.PlayerSoundEventId', label: 'Damage Player Sound', kind: 'assetRef', assetCategory: 'soundEvents', placeholder: 'SFX_Effect_Burn_Local' },
        { key: 'DamageResistance.Fire', label: 'Fire Resistance', kind: 'number', step: '0.1' },
        { key: 'DamageResistance.Physical', label: 'Physical Resistance', kind: 'number', step: '0.1' },
        { key: 'DamageResistance.Projectile', label: 'Projectile Resistance', kind: 'number', step: '0.1' },
        { key: 'StatModifiers.Health', label: 'Health Modifier', kind: 'number', step: '0.1' },
        { key: 'ValueType', label: 'Stat Modifier Value Type', kind: 'select', nullable: true, options: [
          ['Absolute', 'Absolute'],
          ['Percent', 'Percent'],
        ]},
      ],
    },
    {
      tabId: 'items',
      paneId: 'pane-items',
      category: 'items',
      title: 'Items',
      noun: 'item',
      idPlaceholder: 'Vampirism/VampirismRelic',
      help: 'Creates Hytale-native files under Server/Item/Items/.',
      fields: [
        { key: 'TranslationProperties.Name', label: 'Display Name', kind: 'text', placeholder: 'Vampiric Staff' },
        { key: 'TranslationProperties.Description', label: 'Description', kind: 'text', placeholder: 'Relic description' },
        { key: 'Parent', label: 'Parent Item', kind: 'assetRef', assetCategory: 'items', placeholder: 'Tool/Prototype/Prototype_Tool_Staff_Mana' },
        { key: 'Icon', label: 'Icon Path', kind: 'text', placeholder: 'Icons/Items/EditorTools/Paint.png' },
        { key: 'Model', label: 'Model Path', kind: 'text', placeholder: 'Items/Tools/Prototype/Prototype_Mana_Staff.blockymodel' },
        { key: 'Texture', label: 'Texture Path', kind: 'text', placeholder: 'Items/Tools/Prototype/Prototype_Mana_Staff_Texture.png' },
        { key: 'Set', label: 'Set', kind: 'text', placeholder: 'Scarak' },
        { key: 'ItemSoundSetId', label: 'Item Sound Set ID', kind: 'text', placeholder: 'Staff' },
        { key: 'Categories', label: 'Categories', kind: 'stringList', placeholder: 'Weapon' },
        { key: 'ResourceTypes', label: 'Resource Types', kind: 'stringList', placeholder: 'Wood' },
        { key: 'Tags.Type', label: 'Tag Types', kind: 'stringList', placeholder: 'Tool' },
        { key: 'Tags.Family', label: 'Tag Families', kind: 'stringList', placeholder: 'Weapon' },
        { key: 'MaxStack', label: 'Max Stack', kind: 'number', step: '1', default: 1 },
        { key: 'Quality', label: 'Quality', kind: 'assetRef', assetCategory: 'itemQualities', placeholder: 'Common' },
        { key: 'ItemLevel', label: 'Item Level', kind: 'number', step: '1' },
        { key: 'PlayerAnimationsId', label: 'Player Animations ID', kind: 'assetRef', assetCategory: 'itemAnimations', placeholder: 'Item' },
        { key: 'Interactions.Primary', label: 'Primary Root Interaction', kind: 'assetRef', assetCategory: 'rootInteractions', placeholder: 'VampirismRelic_Primary' },
        { key: 'Interactions.Secondary', label: 'Secondary Root Interaction', kind: 'assetRef', assetCategory: 'rootInteractions', placeholder: 'VampirismRelic_Secondary' },
        { key: 'Interactions.Ability1', label: 'Ability1 Root Interaction', kind: 'assetRef', assetCategory: 'rootInteractions', placeholder: 'VampirismRelic_Ability1' },
        { key: 'Interactions.Ability2', label: 'Ability2 Root Interaction', kind: 'assetRef', assetCategory: 'rootInteractions', placeholder: 'VampirismRelic_Ability2' },
        { key: 'Interactions.Use', label: 'Use Root Interaction', kind: 'assetRef', assetCategory: 'rootInteractions', placeholder: 'VampirismRelic_Use' },
        { key: 'InteractionConfig.UseDistance.Adventure', label: 'Use Distance Adventure', kind: 'number', step: '1' },
        { key: 'InteractionConfig.UseDistance.Creative', label: 'Use Distance Creative', kind: 'number', step: '1' },
        { key: 'InteractionConfig.AllEntities', label: 'Interaction All Entities', kind: 'boolean', default: false },
        { key: 'BlockType.Material', label: 'Block Material', kind: 'select', nullable: true, options: BLOCK_MATERIAL_OPTIONS },
        { key: 'BlockType.DrawType', label: 'Block Draw Type', kind: 'select', nullable: true, options: BLOCK_DRAW_TYPE_OPTIONS },
        { key: 'BlockType.Opacity', label: 'Block Opacity', kind: 'select', nullable: true, options: BLOCK_OPACITY_OPTIONS },
        { key: 'BlockType.Model', label: 'Block Model', kind: 'text', placeholder: 'Blocks/Bench/Bench_Alchemy.blockymodel' },
        { key: 'BlockType.Texture', label: 'Block Texture', kind: 'text', placeholder: 'Blocks/Bench/Bench_Alchemy.png' },
        { key: 'BlockType.BlockEntityMode', label: 'Block Entity Mode', kind: 'text', placeholder: 'BlockEntity' },
        { key: 'BlockType.VariantRotation', label: 'Block Variant Rotation', kind: 'select', nullable: true, options: BLOCK_VARIANT_ROTATION_OPTIONS },
        { key: 'BlockType.MaximumDamage', label: 'Block Maximum Damage', kind: 'number', step: '1' },
        { key: 'BlockType.ReplaceOnDestroy', label: 'Block Replace On Destroy', kind: 'text', placeholder: 'Air' },
        { key: 'BlockType.Bench.Tier', label: 'Bench Tier', kind: 'number', step: '1' },
        { key: 'BlockType.Bench.CraftingQueueSize', label: 'Bench Queue Size', kind: 'number', step: '1' },
      ],
    },
    {
      tabId: 'projectiles',
      paneId: 'pane-projectiles',
      category: 'projectiles',
      title: 'Projectiles',
      noun: 'projectile',
      idPlaceholder: 'blood_throw_projectile',
      help: 'Creates Hytale-native files under Server/Projectiles/.',
      fields: [
        { key: 'Parent', label: 'Parent Projectile', kind: 'assetRef', assetCategory: 'projectiles', placeholder: 'Arrow_FullCharge' },
        { key: 'Appearance', label: 'Appearance', kind: 'text', default: 'Projectile' },
        { key: 'Radius', label: 'Radius', kind: 'number', step: '0.01', default: 0.2 },
        { key: 'Height', label: 'Height', kind: 'number', step: '0.01', default: 0.2 },
        { key: 'HorizontalCenterShot', label: 'Horizontal Center Shot', kind: 'number', step: '0.01', default: 0.15 },
        { key: 'VerticalCenterShot', label: 'Vertical Center Shot', kind: 'number', step: '0.01', default: 0.1 },
        { key: 'DepthShot', label: 'Depth Shot', kind: 'number', step: '0.01', default: 1.0 },
        { key: 'PitchAdjustShot', label: 'Pitch Adjust Shot', kind: 'boolean', default: true },
        { key: 'SticksVertically', label: 'Sticks Vertically', kind: 'boolean', default: false },
        { key: 'Bounciness', label: 'Bounciness', kind: 'number', step: '0.1' },
        { key: 'DeathEffectsOnHit', label: 'Death Effects On Hit', kind: 'boolean', nullable: true },
        { key: 'MuzzleVelocity', label: 'Muzzle Velocity', kind: 'number', step: '0.1', default: 42 },
        { key: 'TerminalVelocity', label: 'Terminal Velocity', kind: 'number', step: '0.1', default: 42 },
        { key: 'Gravity', label: 'Gravity', kind: 'number', step: '0.1', default: 0 },
        { key: 'ImpactSlowdown', label: 'Impact Slowdown', kind: 'number', step: '0.1', default: 0 },
        { key: 'TimeToLive', label: 'Time To Live', kind: 'number', step: '0.1', default: 3 },
        { key: 'Damage', label: 'Damage', kind: 'number', step: '0.1', default: 10 },
        { key: 'DeadTime', label: 'Dead Time', kind: 'number', step: '0.01', default: 0.05 },
        { key: 'DeadTimeMiss', label: 'Dead Time Miss', kind: 'number', step: '0.01', default: 0.0 },
        { key: 'HitSoundEventId', label: 'Hit Sound Event', kind: 'assetRef', assetCategory: 'soundEvents' },
        { key: 'MissSoundEventId', label: 'Miss Sound Event', kind: 'assetRef', assetCategory: 'soundEvents' },
        { key: 'HitParticles.SystemId', label: 'Hit Particles System', kind: 'text', placeholder: 'Particles/Impact/Blood' },
        { key: 'MissParticles.SystemId', label: 'Miss Particles System', kind: 'text', placeholder: 'Particles/Miss/Blood' },
        { key: 'BounceParticles.SystemId', label: 'Bounce Particles System', kind: 'text', placeholder: 'Particles/Bounce/Blood' },
        { key: 'DeathParticles.SystemId', label: 'Death Particles System', kind: 'text', placeholder: 'Particles/Death/Blood' },
        { key: 'ExplosionConfig.EntityDamageRadius', label: 'Explosion Entity Damage Radius', kind: 'number', step: '0.1' },
        { key: 'ExplosionConfig.BlockDamageRadius', label: 'Explosion Block Damage Radius', kind: 'number', step: '0.1' },
        { key: 'ExplosionConfig.EntityDamageFalloff', label: 'Explosion Entity Damage Falloff', kind: 'number', step: '0.1' },
        { key: 'ExplosionConfig.Knockback.Force', label: 'Explosion Knockback Force', kind: 'number', step: '0.1' },
        { key: 'ExplosionConfig.Knockback.VelocityType', label: 'Explosion Knockback Type', kind: 'select', nullable: true, options: [['Set', 'Set']] },
      ],
    },
    {
      tabId: 'projectile-configs',
      paneId: 'pane-projectile-configs',
      category: 'projectileConfigs',
      title: 'Projectile Configs',
      noun: 'projectile config',
      idPlaceholder: 'Vampirism/Projectile_Config_BloodThrow',
      help: 'Creates Hytale-native files under Server/ProjectileConfigs/ with selection-first inputs for known references.',
      fields: [
        { key: 'Parent', label: 'Parent Config', kind: 'assetRef', assetCategory: 'projectileConfigs', placeholder: 'Projectile_Config_Goblin_Lobber_Bomb' },
        { key: 'Model', label: 'Model', kind: 'assetRef', assetCategory: 'models', placeholder: 'Beast/Bat' },
        { key: 'LaunchForce', label: 'Launch Force', kind: 'number', step: '0.1' },
        { key: 'SpawnOffset.X', label: 'Spawn Offset X', kind: 'number', step: '0.01' },
        { key: 'SpawnOffset.Y', label: 'Spawn Offset Y', kind: 'number', step: '0.01' },
        { key: 'SpawnOffset.Z', label: 'Spawn Offset Z', kind: 'number', step: '0.01' },
        { key: 'SpawnRotationOffset.Pitch', label: 'Spawn Rotation Pitch', kind: 'number', step: '0.1' },
        { key: 'SpawnRotationOffset.Yaw', label: 'Spawn Rotation Yaw', kind: 'number', step: '0.1' },
        { key: 'SpawnRotationOffset.Roll', label: 'Spawn Rotation Roll', kind: 'number', step: '0.1' },
        { key: 'Physics.Type', label: 'Physics Type', kind: 'select', nullable: true, options: PHYSICS_TYPE_OPTIONS },
        { key: 'Physics.Gravity', label: 'Physics Gravity', kind: 'number', step: '0.1' },
        { key: 'Physics.Drag', label: 'Physics Drag', kind: 'number', step: '0.01' },
        { key: 'Physics.TerminalVelocityAir', label: 'Terminal Velocity Air', kind: 'number', step: '0.1' },
        { key: 'Physics.TerminalVelocityWater', label: 'Terminal Velocity Water', kind: 'number', step: '0.1' },
        { key: 'Physics.RotationMode', label: 'Rotation Mode', kind: 'select', nullable: true, options: ROTATION_MODE_OPTIONS },
        { key: 'Physics.Bounciness', label: 'Physics Bounciness', kind: 'number', step: '0.1' },
        { key: 'Physics.BounceCount', label: 'Bounce Count', kind: 'number', step: '1' },
        { key: 'Physics.BounceLimit', label: 'Bounce Limit', kind: 'number', step: '0.1' },
        { key: 'Physics.AllowRolling', label: 'Allow Rolling', kind: 'boolean', nullable: true },
        { key: 'Physics.RollingFrictionFactor', label: 'Rolling Friction Factor', kind: 'number', step: '0.01' },
        { key: 'LaunchWorldSoundEventId', label: 'Launch World Sound', kind: 'assetRef', assetCategory: 'soundEvents' },
        { key: 'LaunchLocalSoundEventId', label: 'Launch Local Sound', kind: 'assetRef', assetCategory: 'soundEvents' },
        { key: 'Interactions.ProjectileHit.Interactions', label: 'Projectile Hit Interactions', kind: 'assetList', assetCategory: 'interactions' },
        { key: 'Interactions.ProjectileMiss.Interactions', label: 'Projectile Miss Interactions', kind: 'assetList', assetCategory: 'interactions' },
        { key: 'Interactions.ProjectileSpawn.Interactions', label: 'Projectile Spawn Interactions', kind: 'assetList', assetCategory: 'interactions' },
        { key: 'Interactions.ProjectileBounce.Interactions', label: 'Projectile Bounce Interactions', kind: 'assetList', assetCategory: 'interactions' },
      ],
    },
    {
      tabId: 'interactions',
      paneId: 'pane-interactions',
      category: 'interactions',
      title: 'Interactions',
      noun: 'interaction',
      idPlaceholder: 'Vampirism/BloodThrow_Use',
      help: 'Creates Hytale-native files under Server/Item/Interactions/. Supports all official Type options, typed common fields, and preserves advanced nested data on round-trip.',
      fields: [
        { key: 'Type', label: 'Type', kind: 'select', default: 'LaunchProjectile', options: INTERACTION_TYPE_OPTIONS },
        { key: 'CustomType', label: 'Custom Type', kind: 'text', showWhenType: ['Custom'] },
        { key: 'RunTime', label: 'Run Time', kind: 'number', step: '0.1' },
        { key: 'RequiredGameMode', label: 'Required Game Mode', kind: 'select', nullable: true, options: GAME_MODE_OPTIONS },
        { key: 'Crouching', label: 'Crouching', kind: 'boolean', nullable: true, showWhenType: ['Condition'] },
        { key: 'HorizontalSpeedMultiplier', label: 'Horizontal Speed Multiplier', kind: 'number', step: '0.1' },
        { key: 'ProjectileId', label: 'Projectile ID', kind: 'assetRef', assetCategory: 'projectiles', placeholder: 'blood_throw_projectile', showWhenType: ['LaunchProjectile'] },
        { key: 'Command', label: 'Command', kind: 'text', placeholder: 'vampirism use bloodthrow', showWhenType: ['Command'] },
        { key: 'EffectId', label: 'Effect ID', kind: 'assetRef', assetCategory: 'effects', placeholder: 'Potion_Morph_Bat', showWhenType: ['ApplyEffect'] },
        { key: 'EntityEffectIds', label: 'Entity Effects', kind: 'assetList', assetCategory: 'effects', showWhenType: ['EffectCondition'] },
        { key: 'Match', label: 'Match', kind: 'select', nullable: true, options: MATCH_OPTIONS, showWhenType: ['EffectCondition'] },
        { key: 'Costs.Stamina', label: 'Stamina Cost', kind: 'number', step: '0.1', showWhenType: ['StatsCondition', 'StatsConditionWithModifier'] },
        { key: 'ValueType', label: 'Value Type', kind: 'select', nullable: true, options: VALUE_TYPE_OPTIONS, showWhenType: ['StatsCondition'] },
        { key: 'Interactions', label: 'Interactions', kind: 'assetList', assetCategory: 'interactions', showWhenType: ['Serial', 'Parallel'] },
        { key: 'ForkInteractions.Interactions', label: 'Fork Interactions', kind: 'assetList', assetCategory: 'interactions', showWhenType: ['Repeat'] },
        { key: 'Repeat', label: 'Repeat Count', kind: 'number', step: '1', showWhenType: ['Repeat'] },
        { key: 'Selector.Id', label: 'Selector ID', kind: 'select', nullable: true, options: SELECTOR_ID_OPTIONS, showWhenType: ['Selector'] },
        { key: 'Selector.StartDistance', label: 'Selector Start Distance', kind: 'number', step: '0.1', showWhenType: ['Selector'] },
        { key: 'Selector.EndDistance', label: 'Selector End Distance', kind: 'number', step: '0.1', showWhenType: ['Selector'] },
        { key: 'Selector.ExtendTop', label: 'Selector Extend Top', kind: 'number', step: '0.1', showWhenType: ['Selector'] },
        { key: 'Selector.ExtendBottom', label: 'Selector Extend Bottom', kind: 'number', step: '0.1', showWhenType: ['Selector'] },
        { key: 'Selector.TestLineOfSight', label: 'Selector Test Line Of Sight', kind: 'boolean', nullable: true, showWhenType: ['Selector'] },
        { key: 'HitEntity.Interactions', label: 'Hit Entity Interactions', kind: 'assetList', assetCategory: 'interactions', showWhenType: ['Selector'] },
        { key: 'HitBlock.Interactions', label: 'Hit Block Interactions', kind: 'assetList', assetCategory: 'interactions', showWhenType: ['Selector'] },
        { key: 'ChainingAllowance', label: 'Chaining Allowance', kind: 'number', step: '1', showWhenType: ['Chaining'] },
        { key: 'ChainId', label: 'Chain ID', kind: 'text', showWhenType: ['Chaining'] },
        { key: 'AllowIndefiniteHold', label: 'Allow Indefinite Hold', kind: 'boolean', nullable: true, showWhenType: ['Charging'] },
        { key: 'DisplayProgress', label: 'Display Progress', kind: 'boolean', nullable: true, showWhenType: ['Charging'] },
        { key: 'Context', label: 'Context', kind: 'select', nullable: true, options: CONTEXT_OPTIONS, showWhenType: ['ContextualUseNPC'] },
        { key: 'ItemToRemove.Id', label: 'Item To Remove ID', kind: 'text', showWhenType: ['ModifyInventory'] },
        { key: 'ItemToRemove.Quantity', label: 'Item To Remove Quantity', kind: 'number', step: '1', showWhenType: ['ModifyInventory'] },
        { key: 'Entity', label: 'Entity', kind: 'select', nullable: true, options: ENTITY_OPTIONS, showWhenType: ['RemoveEntity'] },
        { key: 'Page.Id', label: 'Page ID', kind: 'text', showWhenType: ['OpenCustomUI'] },
        { key: 'BlockTypeToPlace', label: 'Block Type To Place', kind: 'text', showWhenType: ['PlaceBlock'] },
        { key: 'RemoveItemInHand', label: 'Remove Item In Hand', kind: 'boolean', nullable: true, showWhenType: ['PlaceBlock'] },
        { key: 'Duration', label: 'Duration', kind: 'number', step: '0.1', showWhenType: ['UseWateringCan'] },
        { key: 'RadiusX', label: 'Radius X', kind: 'number', step: '0.1', showWhenType: ['UseWateringCan'] },
        { key: 'RadiusZ', label: 'Radius Z', kind: 'number', step: '0.1', showWhenType: ['UseWateringCan'] },
        { key: 'RefreshModifiers', label: 'Refresh Modifiers', kind: 'boolean', nullable: true, showWhenType: ['UseWateringCan', 'FertilizeSoil'] },
        { key: 'EntityId', label: 'Entity ID', kind: 'text', showWhenType: ['SpawnNPC'] },
        { key: 'SpawnOffset.X', label: 'Spawn Offset X', kind: 'number', step: '0.1', showWhenType: ['SpawnNPC'] },
        { key: 'SpawnOffset.Y', label: 'Spawn Offset Y', kind: 'number', step: '0.1', showWhenType: ['SpawnNPC'] },
        { key: 'SpawnOffset.Z', label: 'Spawn Offset Z', kind: 'number', step: '0.1', showWhenType: ['SpawnNPC'] },
        { key: 'PrefabPath', label: 'Prefab Path', kind: 'text', showWhenType: ['SpawnPrefab'] },
        { key: 'Offset.X', label: 'Offset X', kind: 'number', step: '0.1', showWhenType: ['SpawnPrefab'] },
        { key: 'Offset.Y', label: 'Offset Y', kind: 'number', step: '0.1', showWhenType: ['SpawnPrefab'] },
        { key: 'Offset.Z', label: 'Offset Z', kind: 'number', step: '0.1', showWhenType: ['SpawnPrefab'] },
        { key: 'RotationYaw', label: 'Rotation Yaw', kind: 'number', step: '0.1', showWhenType: ['SpawnPrefab'] },
        { key: 'OriginSource', label: 'Origin Source', kind: 'select', nullable: true, options: ORIGIN_SOURCE_OPTIONS, showWhenType: ['SpawnPrefab', 'TeleportInstance'] },
        { key: 'Force', label: 'Force', kind: 'number', step: '0.1', showWhenType: ['SpawnPrefab', 'ApplyForce'] },
        { key: 'InstanceName', label: 'Instance Name', kind: 'text', showWhenType: ['TeleportInstance'] },
        { key: 'InstanceKey', label: 'Instance Key', kind: 'text', showWhenType: ['TeleportInstance'] },
        { key: 'PositionOffset.X', label: 'Position Offset X', kind: 'number', step: '0.1', showWhenType: ['TeleportInstance'] },
        { key: 'PositionOffset.Y', label: 'Position Offset Y', kind: 'number', step: '0.1', showWhenType: ['TeleportInstance'] },
        { key: 'PositionOffset.Z', label: 'Position Offset Z', kind: 'number', step: '0.1', showWhenType: ['TeleportInstance'] },
        { key: 'Rotation.Yaw', label: 'Rotation Yaw', kind: 'number', step: '0.1', showWhenType: ['TeleportInstance'] },
        { key: 'PersonalReturnPoint', label: 'Personal Return Point', kind: 'boolean', nullable: true, showWhenType: ['TeleportInstance'] },
        { key: 'CloseOnBlockRemove', label: 'Close On Block Remove', kind: 'boolean', nullable: true, showWhenType: ['TeleportInstance'] },
        { key: 'RemoveBlockAfter', label: 'Remove Block After', kind: 'boolean', nullable: true, showWhenType: ['TeleportInstance'] },
        { key: 'Config.Type', label: 'Deployable Type', kind: 'select', nullable: true, options: DEPLOYABLE_TYPE_OPTIONS, showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.Id', label: 'Deployable ID', kind: 'text', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.Shape', label: 'Deployable Shape', kind: 'select', nullable: true, options: DEPLOYABLE_SHAPE_OPTIONS, showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.LiveDuration', label: 'Deployable Live Duration', kind: 'number', step: '0.1', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.MaxLiveCount', label: 'Deployable Max Live Count', kind: 'number', step: '1', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.Model', label: 'Deployable Model', kind: 'assetRef', assetCategory: 'models', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.ModelPreview', label: 'Deployable Preview Model', kind: 'assetRef', assetCategory: 'models', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.ModelScale', label: 'Deployable Model Scale', kind: 'number', step: '0.1', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.StartRadius', label: 'Deployable Start Radius', kind: 'number', step: '0.1', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.EndRadius', label: 'Deployable End Radius', kind: 'number', step: '0.1', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.Height', label: 'Deployable Height', kind: 'number', step: '0.1', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Config.RadiusChangeTime', label: 'Deployable Radius Change Time', kind: 'number', step: '0.1', showWhenType: ['SpawnDeployableFromRaycast'] },
        { key: 'Direction.X', label: 'Direction X', kind: 'number', step: '0.1', showWhenType: ['ApplyForce'] },
        { key: 'Direction.Y', label: 'Direction Y', kind: 'number', step: '0.1', showWhenType: ['ApplyForce'] },
        { key: 'Direction.Z', label: 'Direction Z', kind: 'number', step: '0.1', showWhenType: ['ApplyForce'] },
        { key: 'ChangeVelocityType', label: 'Change Velocity Type', kind: 'select', nullable: true, options: CHANGE_VELOCITY_TYPE_OPTIONS, showWhenType: ['ApplyForce'] },
        { key: 'WaitForGround', label: 'Wait For Ground', kind: 'boolean', nullable: true, showWhenType: ['ApplyForce'] },
        { key: 'Effects.ItemAnimationId', label: 'Item Animation ID', kind: 'text' },
        { key: 'Effects.ItemPlayerAnimationsId', label: 'Item Player Animations ID', kind: 'assetRef', assetCategory: 'itemAnimations' },
        { key: 'Effects.ClearAnimationOnFinish', label: 'Clear Animation On Finish', kind: 'boolean', nullable: true },
        { key: 'Effects.WaitForAnimationToFinish', label: 'Wait For Animation To Finish', kind: 'boolean', nullable: true },
        { key: 'Effects.WorldSoundEventId', label: 'World Sound Event', kind: 'assetRef', assetCategory: 'soundEvents' },
        { key: 'Effects.LocalSoundEventId', label: 'Local Sound Event', kind: 'assetRef', assetCategory: 'soundEvents' },
        { key: 'Next', label: 'Next Interaction', kind: 'assetRef', assetCategory: 'interactions', placeholder: 'Vampirism/OtherInteraction' },
        { key: 'Failed', label: 'Failed Interaction', kind: 'assetRef', assetCategory: 'interactions', placeholder: 'Vampirism/OtherInteraction' },
      ],
    },
    {
      tabId: 'root-interactions',
      paneId: 'pane-root-interactions',
      category: 'rootInteractions',
      title: 'Root Interactions',
      noun: 'root interaction',
      idPlaceholder: 'Vampirism/BloodThrow_Use',
      help: 'Creates Hytale-native files under Server/Item/RootInteractions/. Supports typed settings, cooldowns, tags, and preserves inline interaction objects.',
      fields: [
        { key: 'Parent', label: 'Parent Root Interaction', kind: 'assetRef', assetCategory: 'rootInteractions' },
        { key: 'Interactions', label: 'Interactions', kind: 'assetList', assetCategory: 'interactions' },
        { key: 'RequireNewClick', label: 'Require New Click', kind: 'boolean', nullable: true },
        { key: 'ClickQueuingTimeout', label: 'Click Queuing Timeout', kind: 'number', step: '0.1' },
        { key: 'Cooldown.Id', label: 'Cooldown ID', kind: 'text', placeholder: 'Attack_Primary' },
        { key: 'Cooldown.Cooldown', label: 'Cooldown Time', kind: 'number', step: '0.1' },
        { key: 'Cooldown.ClickBypass', label: 'Cooldown Click Bypass', kind: 'boolean', nullable: true },
        { key: 'Cooldown.Charges', label: 'Cooldown Charges', kind: 'number', step: '1' },
        { key: 'Settings.Adventure.AllowSkipChainOnClick', label: 'Adventure Allow Skip Chain On Click', kind: 'boolean', nullable: true },
        { key: 'Settings.Adventure.Cooldown.Id', label: 'Adventure Cooldown ID', kind: 'text', placeholder: 'Attack_Primary' },
        { key: 'Settings.Adventure.Cooldown.Cooldown', label: 'Adventure Cooldown Time', kind: 'number', step: '0.1' },
        { key: 'Settings.Adventure.Cooldown.ClickBypass', label: 'Adventure Cooldown Click Bypass', kind: 'boolean', nullable: true },
        { key: 'Settings.Adventure.Cooldown.Charges', label: 'Adventure Cooldown Charges', kind: 'number', step: '1' },
        { key: 'Settings.Creative.AllowSkipChainOnClick', label: 'Creative Allow Skip Chain On Click', kind: 'boolean', nullable: true },
        { key: 'Settings.Creative.Cooldown.Id', label: 'Creative Cooldown ID', kind: 'text', placeholder: 'Attack_Primary' },
        { key: 'Settings.Creative.Cooldown.Cooldown', label: 'Creative Cooldown Time', kind: 'number', step: '0.1' },
        { key: 'Settings.Creative.Cooldown.ClickBypass', label: 'Creative Cooldown Click Bypass', kind: 'boolean', nullable: true },
        { key: 'Settings.Creative.Cooldown.Charges', label: 'Creative Cooldown Charges', kind: 'number', step: '1' },
        { key: 'Rules.Interrupting', label: 'Interrupting Rules', kind: 'stringList', placeholder: 'Attack' },
        { key: 'Tags.Attack', label: 'Attack Tags', kind: 'stringList', placeholder: 'Melee' },
      ],
    },
  ];

  const BROWSER_CATEGORIES = [
    ['effects', 'Entity Effects'],
    ['entityStats', 'Entity Stats'],
    ['soundEvents', 'Sound Events'],
    ['items', 'Items'],
    ['itemAnimations', 'Item Animations'],
    ['itemQualities', 'Item Qualities'],
    ['models', 'Models'],
    ['projectiles', 'Projectiles'],
    ['projectileConfigs', 'Projectile Configs'],
    ['interactions', 'Interactions'],
    ['rootInteractions', 'Root Interactions'],
  ];

  function ensureCss() {
    if (cssInjected) return;
    cssInjected = true;
    const style = document.createElement('style');
    style.textContent = `
      .ha-split { display:flex; flex:1; min-height:0; overflow:hidden; }
      .ha-main { flex:1; min-width:0; overflow:auto; }
      .ha-side { width:420px; min-width:420px; background:var(--surface); border-left:1px solid var(--border); display:flex; flex-direction:column; }
      .ha-side-body { flex:1; overflow:auto; padding:16px; }
      .ha-status { margin-bottom:12px; font-size:12px; color:var(--text-dim); }
      .ha-status.ok { color: var(--accent2); }
      .ha-status.warn { color: #f0c040; }
      .ha-status.err { color: #e94560; }
      .ha-empty { color:var(--text-dim); font-size:13px; line-height:1.6; padding:4px; }
      .ha-warning-list { margin:10px 0 0; padding-left:18px; color:#f0c040; font-size:12px; }
      .ha-summary { margin:6px 0 12px; color:var(--text-dim); font-size:12px; }
      .ha-meta { margin-bottom:12px; font-size:12px; color:var(--text-dim); line-height:1.5; }
      .ha-grid { display:grid; grid-template-columns:1fr 1fr; gap:10px; }
      .ha-grid .form-group { margin-bottom:0; }
      .ha-field-wrap.hidden { display:none; }
      .ha-tags { display:flex; flex-wrap:wrap; gap:4px; min-height:22px; margin-bottom:6px; }
      .ha-tag { display:inline-flex; align-items:center; gap:4px; background:var(--border); color:var(--text); border-radius:4px; padding:3px 7px; font-size:11px; }
      .ha-tag button { border:none; background:none; color:var(--accent); cursor:pointer; font-weight:700; }
      .ha-ops { white-space:nowrap; }
      .ha-ops button { margin-right:4px; }
      .ha-table-warning { color:#f0c040; font-size:11px; }
      .ha-side .editor-actions { margin-top:18px; }
      .ha-browser-toolbar { display:flex; gap:8px; margin-bottom:12px; }
      .ha-browser-toolbar input,
      .ha-browser-toolbar select { flex:1; }
      .ha-origin { font-size:11px; color:var(--text-dim); }
    `;
    document.head.appendChild(style);
  }

  function escapeHtml(value) {
    return String(value ?? '')
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function clone(value) {
    return JSON.parse(JSON.stringify(value));
  }

  function isPlainObject(value) {
    return value && typeof value === 'object' && !Array.isArray(value);
  }

  function isStringArray(value) {
    return Array.isArray(value) && value.every((entry) => typeof entry === 'string');
  }

  function shallowCloneObject(value) {
    return value && typeof value === 'object' && !Array.isArray(value) ? clone(value) : {};
  }

  function pathParts(path) {
    return String(path || '').split('.').filter(Boolean);
  }

  function getByPath(value, path) {
    let current = value;
    for (const part of pathParts(path)) {
      if (!current || typeof current !== 'object' || Array.isArray(current)) {
        return undefined;
      }
      current = current[part];
    }
    return current;
  }

  function setByPath(target, path, value) {
    const parts = pathParts(path);
    if (!parts.length) return;
    let current = target;
    for (let i = 0; i < parts.length - 1; i += 1) {
      const part = parts[i];
      if (!current[part] || typeof current[part] !== 'object' || Array.isArray(current[part])) {
        current[part] = {};
      }
      current = current[part];
    }
    current[parts[parts.length - 1]] = value;
  }

  function deleteByPath(target, path) {
    const parts = pathParts(path);
    if (!parts.length) return;
    let current = target;
    for (let i = 0; i < parts.length - 1; i += 1) {
      const part = parts[i];
      if (!current || typeof current !== 'object' || Array.isArray(current)) {
        return;
      }
      current = current[part];
    }
    if (current && typeof current === 'object' && !Array.isArray(current)) {
      delete current[parts[parts.length - 1]];
    }
  }

  function fieldId(state, field, suffix = 'input') {
    return `${state.category}-${field.key}-${suffix}`;
  }

  function inputTemplate(state, field) {
    const wrapAttrs = `class="form-group ha-field-wrap" data-field-wrap="${state.category}-${field.key}"`;
    if (field.kind === 'assetRef') {
      return `
        <div ${wrapAttrs}>
          <label>${field.label}</label>
          <select id="${fieldId(state, field)}"></select>
        </div>
      `;
    }
    if (field.kind === 'assetList' || field.kind === 'stringList') {
      return `
        <div ${wrapAttrs}>
          <label>${field.label}</label>
          <div class="ha-tags" id="${fieldId(state, field, 'tags')}"></div>
          <div class="editor-ref-row">
            ${field.kind === 'assetList'
              ? `<select id="${fieldId(state, field)}"></select>`
              : `<input id="${fieldId(state, field)}" type="text" placeholder="${escapeHtml(field.placeholder || 'Add value')}" />`
            }
            <button type="button" id="${fieldId(state, field, 'add')}">Add</button>
          </div>
        </div>
      `;
    }
    if (field.kind === 'select' || field.kind === 'boolean') {
      return `
        <div ${wrapAttrs}>
          <label>${field.label}</label>
          <select id="${fieldId(state, field)}"></select>
        </div>
      `;
    }
    if (field.kind === 'number') {
      return `
        <div ${wrapAttrs}>
          <label>${field.label}</label>
          <input id="${fieldId(state, field)}" type="number" step="${field.step || 'any'}" placeholder="${escapeHtml(field.placeholder || '')}" />
        </div>
      `;
    }
    return `
      <div ${wrapAttrs}>
        <label>${field.label}</label>
        <input id="${fieldId(state, field)}" type="text" placeholder="${escapeHtml(field.placeholder || '')}" />
      </div>
    `;
  }

  function configFor(tabId) {
    return TABS.find((entry) => entry.tabId === tabId);
  }

  function createPane(config) {
    const pane = document.getElementById(config.paneId);
    pane.innerHTML = `
      <div class="ha-split">
        <div class="ha-main">
          <div class="pane-inner">
            <div class="pane-toolbar">
              <h2>${config.title}</h2>
              <button class="btn-primary" type="button" data-action="add">+ Add</button>
            </div>
            <div class="ha-status" id="${config.category}-status">${config.help}</div>
            <table class="data-table">
              <thead><tr><th>ID</th><th>Path</th><th>Summary</th><th>Warnings</th><th>Ops</th></tr></thead>
              <tbody id="${config.category}-tbody"></tbody>
            </table>
          </div>
        </div>
        <div class="ha-side">
          <div class="editor-side-header">
            <h3>${config.title} Editor</h3>
            <button class="btn-secondary" type="button" data-action="close">Close</button>
          </div>
          <div class="ha-side-body">
            <div class="ha-empty" id="${config.category}-empty">Select a ${config.noun} to edit or create a new one.</div>
            <div id="${config.category}-form" style="display:none">
              <div class="ha-meta">Saved under <code>${config.category}</code> → correct Hytale runtime path is chosen automatically.</div>
              <div class="form-group"><label>ID / relative path</label><input id="${config.category}-asset-id" type="text" placeholder="${escapeHtml(config.idPlaceholder)}" /></div>
              <div class="ha-summary" id="${config.category}-summary"></div>
              ${renderFieldGrid(config)}
              <ul class="ha-warning-list" id="${config.category}-warnings" style="display:none"></ul>
              <div class="editor-actions">
                <button class="btn-delete" type="button" data-action="delete">Delete</button>
                <button class="btn-primary" type="button" data-action="save">Save</button>
              </div>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  function renderFieldGrid(config) {
    const fieldsHtml = config.fields.map((field) => inputTemplate({ category: config.category }, field)).join('');
    return `<div class="ha-grid">${fieldsHtml}</div>`;
  }

  function makeState(config) {
    return {
      ...config,
      currentId: null,
      currentPath: '',
      currentSummary: '',
      currentWarnings: [],
      currentData: {},
      extraData: {},
      listValues: {},
      listEntries: {},
    };
  }

  function defaultData(config) {
    const data = {};
    config.fields.forEach((field) => {
      if (field.default !== undefined) {
        setByPath(data, field.key, field.default);
      }
      if (field.kind === 'assetList' || field.kind === 'stringList') {
        setByPath(data, field.key, []);
      }
    });
    return data;
  }

  function splitKnownAndExtra(config, data) {
    const known = shallowCloneObject(data);
    const extra = shallowCloneObject(data);
    config.fields.forEach((field) => {
      deleteByPath(extra, field.key);
    });

    if (config.category === 'interactions') {
      const typeValue = known.Type;
      const supportedTypes = new Set(config.fields[0].options.map(([value]) => value));
      if (typeValue && !supportedTypes.has(typeValue)) {
        known.CustomType = typeValue;
        known.Type = 'Custom';
      }
      [
        'Next',
        'Failed',
      ].forEach((fieldPath) => {
        const value = getByPath(known, fieldPath);
        if (isPlainObject(value)) {
          setByPath(extra, fieldPath, clone(value));
          deleteByPath(known, fieldPath);
        }
      });
      delete extra.CustomType;
    }

    return { known, extra };
  }

  function setStatus(state, message, cls = '') {
    const el = document.getElementById(`${state.category}-status`);
    el.textContent = message;
    el.className = `ha-status ${cls}`.trim();
  }

  function renderWarnings(state, warnings = []) {
    const list = document.getElementById(`${state.category}-warnings`);
    if (!warnings.length) {
      list.style.display = 'none';
      list.innerHTML = '';
      return;
    }
    list.style.display = '';
    list.innerHTML = warnings.map((warning) => `<li>${escapeHtml(warning)}</li>`).join('');
  }

  function renderSummary(state, summary = '', pathValue = '') {
    const el = document.getElementById(`${state.category}-summary`);
    const pieces = [];
    if (summary) pieces.push(summary);
    if (pathValue) pieces.push(pathValue);
    el.textContent = pieces.join(' — ');
  }

  function populateSelect(input, field, value) {
    if (field.kind === 'boolean') {
      input.innerHTML = '';
      if (field.nullable) {
        const empty = document.createElement('option');
        empty.value = '';
        empty.textContent = '— none —';
        input.appendChild(empty);
      }
      ['true', 'false'].forEach((optionValue) => {
        const option = document.createElement('option');
        option.value = optionValue;
        option.textContent = optionValue;
        input.appendChild(option);
      });
      input.value = value == null ? (field.default == null ? '' : String(field.default)) : String(value);
      return;
    }
    input.innerHTML = '';
    if (field.nullable) {
      const empty = document.createElement('option');
      empty.value = '';
      empty.textContent = '— none —';
      input.appendChild(empty);
    }
    field.options.forEach(([optionValue, optionLabel]) => {
      const option = document.createElement('option');
      option.value = optionValue;
      option.textContent = optionLabel;
      input.appendChild(option);
    });
    input.value = value ?? field.default ?? '';
  }

  async function renderAssetListField(state, field, initialValues = []) {
    const tagsEl = document.getElementById(fieldId(state, field, 'tags'));
    const select = document.getElementById(fieldId(state, field));
    const addBtn = document.getElementById(fieldId(state, field, 'add'));

    state.listEntries[field.key] = Array.isArray(initialValues)
      ? initialValues.map((value) => ({
        kind: typeof value === 'string' ? 'ref' : 'extra',
        value: clone(value),
      }))
      : [];
    await HytaleAssetStore.fillSelect(select, field.assetCategory, '', { nullable: true });

    function rerender() {
      tagsEl.innerHTML = '';
      let preservedExtras = 0;
      state.listEntries[field.key].forEach((entry, index) => {
        if (entry.kind !== 'ref') {
          preservedExtras += 1;
          return;
        }
        const tag = document.createElement('span');
        tag.className = 'ha-tag';
        tag.innerHTML = `${escapeHtml(entry.value)} <button type="button">×</button>`;
        tag.querySelector('button').addEventListener('click', () => {
          state.listEntries[field.key] = state.listEntries[field.key].filter((_, currentIndex) => currentIndex !== index);
          rerender();
        });
        tagsEl.appendChild(tag);
      });
      if (preservedExtras > 0) {
        const tag = document.createElement('span');
        tag.className = 'ha-tag';
        tag.textContent = `${preservedExtras} inline item(s) preserved`;
        tagsEl.appendChild(tag);
      }
    }

    function addValue() {
      const value = select.value.trim();
      if (!value || state.listEntries[field.key].some((entry) => entry.kind === 'ref' && entry.value === value)) return;
      state.listEntries[field.key].push({ kind: 'ref', value });
      select.value = '';
      rerender();
    }

    addBtn.onclick = addValue;
    rerender();
  }

  function renderStringListField(state, field, initialValues = []) {
    const tagsEl = document.getElementById(fieldId(state, field, 'tags'));
    const input = document.getElementById(fieldId(state, field));
    const addBtn = document.getElementById(fieldId(state, field, 'add'));

    state.listValues[field.key] = Array.isArray(initialValues)
      ? initialValues.filter((value) => typeof value === 'string').map((value) => value.trim()).filter(Boolean)
      : [];

    function rerender() {
      tagsEl.innerHTML = '';
      state.listValues[field.key].forEach((value, index) => {
        const tag = document.createElement('span');
        tag.className = 'ha-tag';
        tag.innerHTML = `${escapeHtml(value)} <button type="button">×</button>`;
        tag.querySelector('button').addEventListener('click', () => {
          state.listValues[field.key] = state.listValues[field.key].filter((_, currentIndex) => currentIndex !== index);
          rerender();
        });
        tagsEl.appendChild(tag);
      });
    }

    function addValue() {
      const value = String(input.value || '').trim();
      if (!value || state.listValues[field.key].includes(value)) return;
      state.listValues[field.key].push(value);
      input.value = '';
      rerender();
    }

    addBtn.onclick = addValue;
    input.onkeydown = (event) => {
      if (event.key === 'Enter') {
        event.preventDefault();
        addValue();
      }
    };
    rerender();
  }

  function applyFieldVisibility(state) {
    const typeInput = document.getElementById(fieldId(state, { key: 'Type' }));
    const selectedType = typeInput ? typeInput.value : '';
    state.fields.forEach((field) => {
      const wrap = document.querySelector(`[data-field-wrap="${state.category}-${field.key}"]`);
      if (!wrap) return;
      if (!field.showWhenType) {
        wrap.classList.remove('hidden');
        return;
      }
      wrap.classList.toggle('hidden', !field.showWhenType.includes(selectedType));
    });
  }

  async function openEditor(state, id = null) {
    const emptyEl = document.getElementById(`${state.category}-empty`);
    const formEl = document.getElementById(`${state.category}-form`);
    emptyEl.style.display = 'none';
    formEl.style.display = '';

    state.currentId = id;
    state.currentPath = '';
    state.currentWarnings = [];
    state.currentSummary = '';

    let rawData = defaultData(state);
    if (id) {
      const payload = await HytaleAssetStore.getFile(state.category, id);
      rawData = payload.data || {};
      state.currentPath = payload.path || '';
      state.currentWarnings = payload.warnings || [];
      state.currentSummary = payload.summary || '';
    }

    const { known, extra } = splitKnownAndExtra(state, rawData);
    state.currentData = known;
    state.extraData = clone(extra);
    document.getElementById(`${state.category}-asset-id`).value = id || '';
    renderSummary(state, state.currentSummary, state.currentPath);
    renderWarnings(state, state.currentWarnings);

    for (const field of state.fields) {
      const input = document.getElementById(fieldId(state, field));
      const value = getByPath(known, field.key);
      if (!input && field.kind !== 'assetList') continue;

      if (field.kind === 'select' || field.kind === 'boolean') {
        populateSelect(input, field, value);
      } else if (field.kind === 'assetRef') {
        await HytaleAssetStore.fillSelect(input, field.assetCategory, value ?? '', { nullable: true });
      } else if (field.kind === 'assetList') {
        await renderAssetListField(state, field, value);
      } else if (field.kind === 'stringList') {
        renderStringListField(state, field, value);
      } else if (field.kind === 'number') {
        input.value = value ?? '';
      } else {
        input.value = value ?? '';
      }
    }

    const typeInput = document.getElementById(fieldId(state, { key: 'Type' }));
    if (typeInput) {
      typeInput.onchange = () => applyFieldVisibility(state);
    }
    applyFieldVisibility(state);
    document.querySelector(`#${state.category}-form [data-action="delete"]`).style.display = id ? '' : 'none';
  }

  function closeEditor(state) {
    state.currentId = null;
    state.currentPath = '';
    state.currentSummary = '';
    state.currentWarnings = [];
    state.currentData = {};
    state.extraData = {};
    state.listValues = {};
    state.listEntries = {};
    document.getElementById(`${state.category}-empty`).style.display = '';
    document.getElementById(`${state.category}-form`).style.display = 'none';
    renderWarnings(state, []);
    renderSummary(state, '', '');
  }

  function parseMaybeJson(field, raw) {
    const text = String(raw || '').trim();
    if (!text) return undefined;
    if (field.looseString && !['{', '[', '"'].includes(text[0])) {
      return text;
    }
    return JSON.parse(text);
  }

  function collectFormData(state) {
    const data = shallowCloneObject(state.extraData);
    for (const field of state.fields) {
      if (field.showWhenType) {
        const typeInput = document.getElementById(fieldId(state, { key: 'Type' }));
        if (typeInput && !field.showWhenType.includes(typeInput.value)) {
          continue;
        }
      }

      if (field.kind === 'assetList') {
        const values = (state.listEntries[field.key] || [])
          .map((entry) => (entry.kind === 'ref' ? String(entry.value || '').trim() : entry.value))
          .filter((value) => (typeof value === 'string' ? value : true));
        if (values.length) setByPath(data, field.key, values);
        continue;
      }

      if (field.kind === 'stringList') {
        const values = (state.listValues[field.key] || []).map((value) => String(value || '').trim()).filter(Boolean);
        if (values.length) setByPath(data, field.key, values);
        continue;
      }

      const input = document.getElementById(fieldId(state, field));
      let value = input.value;
      if (field.kind === 'number') {
        if (value === '') continue;
        value = Number(value);
        if (Number.isNaN(value)) throw new Error(`${field.label} must be a number.`);
      } else if (field.kind === 'boolean') {
        if (field.nullable && value === '') continue;
        value = value === 'true';
      } else {
        value = String(value || '').trim();
      }

      if ((field.kind === 'text' || field.kind === 'assetRef' || field.kind === 'select') && !value) {
        continue;
      }
      setByPath(data, field.key, value);
    }

    if (state.category === 'interactions' && data.Type === 'Custom') {
      const customType = String(data.CustomType || '').trim();
      if (!customType) {
        throw new Error('Custom Type is required when Type is Custom.');
      }
      data.Type = customType;
      delete data.CustomType;
    } else {
      delete data.CustomType;
    }

    return data;
  }

  function renderTable(state, payload) {
    const tbody = document.getElementById(`${state.category}-tbody`);
    tbody.innerHTML = '';
    payload.localAssets.forEach((asset) => {
      const tr = document.createElement('tr');
      tr.className = 'clickable-row';
      tr.innerHTML = `
        <td><code>${escapeHtml(asset.id)}</code></td>
        <td><code>${escapeHtml(asset.path)}</code></td>
        <td>${escapeHtml(asset.summary || '—')}</td>
        <td>${asset.warnings?.length ? `<span class="ha-table-warning">${asset.warnings.length} warning(s)</span>` : '—'}</td>
        <td class="ha-ops">
          <button class="btn-edit" type="button">Edit</button>
          <button class="btn-delete" type="button">Del</button>
        </td>
      `;
      tr.addEventListener('click', () => openEditor(state, asset.id));
      tr.querySelector('.btn-edit').addEventListener('click', (event) => {
        event.stopPropagation();
        openEditor(state, asset.id);
      });
      tr.querySelector('.btn-delete').addEventListener('click', async (event) => {
        event.stopPropagation();
        await deleteCurrent(state, asset.id);
      });
      tbody.appendChild(tr);
    });
  }

  function createBrowserPane() {
    const pane = document.getElementById('pane-hytale-browser');
    if (!pane) return;
    pane.innerHTML = `
      <div class="pane-inner">
        <div class="pane-toolbar">
          <h2>Hytale Asset Browser</h2>
        </div>
        <div class="ha-status" id="hytale-browser-status">Explore official + local JSON-backed Hytale asset categories.</div>
        <div class="ha-browser-toolbar">
          <select id="hytale-browser-category"></select>
          <input id="hytale-browser-search" type="text" placeholder="Filter IDs" />
        </div>
        <table class="data-table">
          <thead><tr><th>ID</th><th>Origin</th></tr></thead>
          <tbody id="hytale-browser-tbody"></tbody>
        </table>
      </div>
    `;

    const categorySelect = document.getElementById('hytale-browser-category');
    categorySelect.innerHTML = BROWSER_CATEGORIES.map(([value, label]) => `<option value="${escapeHtml(value)}">${escapeHtml(label)}</option>`).join('');
    categorySelect.value = 'effects';
    categorySelect.addEventListener('change', refreshBrowser);
    document.getElementById('hytale-browser-search').addEventListener('input', refreshBrowser);
  }

  async function refreshBrowser() {
    const statusEl = document.getElementById('hytale-browser-status');
    const tbody = document.getElementById('hytale-browser-tbody');
    const category = document.getElementById('hytale-browser-category')?.value;
    const query = (document.getElementById('hytale-browser-search')?.value || '').trim().toLowerCase();
    if (!statusEl || !tbody || !category) return;

    try {
      const payload = await HytaleAssetStore.loadCategory(category, { force: true });
      const originById = new Map();
      (payload.officialRefs || []).forEach((id) => originById.set(id, 'official'));
      (payload.localRefs || []).forEach((id) => {
        originById.set(id, originById.has(id) ? 'official + local' : 'local');
      });

      const ids = [...originById.keys()]
        .sort((a, b) => a.localeCompare(b))
        .filter((id) => !query || id.toLowerCase().includes(query));

      tbody.innerHTML = ids.map((id) => `
        <tr>
          <td><code>${escapeHtml(id)}</code></td>
          <td class="ha-origin">${escapeHtml(originById.get(id) || '—')}</td>
        </tr>
      `).join('');

      statusEl.textContent = `${payload.label}: ${payload.officialRefs?.length || 0} official refs, ${payload.localRefs?.length || 0} local refs.`;
      statusEl.className = 'ha-status';
    } catch (error) {
      statusEl.textContent = `Load failed: ${error.message}`;
      statusEl.className = 'ha-status err';
      tbody.innerHTML = '';
    }
  }

  async function refresh(state) {
    try {
      const payload = await HytaleAssetStore.loadCategory(state.category, { force: true });
      renderTable(state, payload);
      setStatus(state, state.help);
    } catch (error) {
      setStatus(state, `Load failed: ${error.message}`, 'err');
    }
  }

  async function saveCurrent(state) {
    try {
      const id = document.getElementById(`${state.category}-asset-id`).value.trim();
      if (!id) {
        alert('ID is required.');
        return;
      }
      const data = collectFormData(state);
      const payload = await HytaleAssetStore.saveFile(state.category, id, data);
      state.currentId = payload.id;
      state.currentPath = payload.path;
      state.currentSummary = payload.summary || '';
      state.currentWarnings = payload.warnings || [];
      renderSummary(state, state.currentSummary, state.currentPath);
      renderWarnings(state, state.currentWarnings);
      setStatus(state, `Saved ${payload.id}`, payload.warnings?.length ? 'warn' : 'ok');
      HytaleAssetStore.invalidate();
      await refresh(state);
      await openEditor(state, payload.id);
      App.notifyTab('actions');
    } catch (error) {
      alert(error.message);
      setStatus(state, `Save failed: ${error.message}`, 'err');
    }
  }

  async function deleteCurrent(state, idOverride = '') {
    const id = idOverride || document.getElementById(`${state.category}-asset-id`).value.trim();
    if (!id) return;
    if (!confirm(`Delete ${state.noun} "${id}"?`)) return;

    try {
      await HytaleAssetStore.deleteFile(state.category, id);
      HytaleAssetStore.invalidate();
      setStatus(state, `Deleted ${id}`, 'ok');
      await refresh(state);
      if (!idOverride || state.currentId === id) {
        closeEditor(state);
      }
      App.notifyTab('actions');
    } catch (error) {
      alert(error.message);
      setStatus(state, `Delete failed: ${error.message}`, 'err');
    }
  }

  ensureCss();
  TABS.forEach((config) => {
    createPane(config);
    const state = makeState(config);
    const pane = document.getElementById(config.paneId);
    pane.querySelector('[data-action="add"]').addEventListener('click', () => openEditor(state));
    pane.querySelector('[data-action="close"]').addEventListener('click', () => closeEditor(state));
    pane.querySelector('[data-action="save"]').addEventListener('click', () => saveCurrent(state));
    pane.querySelector('[data-action="delete"]').addEventListener('click', () => deleteCurrent(state));
    App.onTabActivated(config.tabId, () => refresh(state));
  });
  createBrowserPane();
  App.onTabActivated('hytale-browser', refreshBrowser);
})();
