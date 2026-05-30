package com.epicseed.vampirism;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.PluginDataDirectoryResolver;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.CatalogBackedSkillRuntimeBootstrap;
import com.epicseed.epiccore.skill.runtime.SkillDefinitionCatalog;
import com.epicseed.vampirism.bootstrap.VampirismRuntime;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.hytale.interaction.VampirismPotionTransformInteraction;
import com.epicseed.vampirism.hytale.interaction.VampirismRelicActionInteraction;
import com.epicseed.vampirism.hytale.interaction.VampirismRelicBindingsInteraction;
import com.epicseed.vampirism.hytale.interaction.VampirismRitualToolActionInteraction;
import com.epicseed.vampirism.hytale.ritual.RitualOfferingSurfaceComponent;
import com.epicseed.vampirism.hytale.ritual.RitualOfferingSurfaceInteraction;
import com.epicseed.vampirism.skill.data.SkillDataPaths;
import com.epicseed.vampirism.skill.data.SkillLoader;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector2d;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Stream;

public class Vampirism extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final CatalogBackedSkillRuntimeBootstrap skillRuntimeBootstrap =
            CatalogBackedSkillRuntimeBootstrap.create();
    private final AtomicReference<Vector2d> skillTreeBounds = new AtomicReference<>(new Vector2d(0, 0));
    private final Path persistentDataDirectory;
    private VampirismRuntime runtime;

    public Vampirism(@Nonnull JavaPluginInit init) {
        super(init);

        Path legacyDataDirectory = init.getDataDirectory().toAbsolutePath().normalize();
        this.persistentDataDirectory = resolvePersistentDataDirectory(init);
        migrateLegacyDataDirectory(legacyDataDirectory, persistentDataDirectory);

        Config<VampirismConfig> config = new Config<>(persistentDataDirectory, "Vampirism", VampirismConfig.CODEC);
        VampirismConfig.init(config);

        this.skillTreeBounds.set(loadDefinitions(skillRuntimeBootstrap.catalog()));
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("[Vampirism] Registering systems and commands...");
        this.getCodecRegistry(Interaction.CODEC).register(
                RitualOfferingSurfaceInteraction.INTERACTION_ID,
                RitualOfferingSurfaceInteraction.class,
                RitualOfferingSurfaceInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                VampirismRitualToolActionInteraction.INTERACTION_ID,
                VampirismRitualToolActionInteraction.class,
                VampirismRitualToolActionInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                VampirismRelicActionInteraction.INTERACTION_ID,
                VampirismRelicActionInteraction.class,
                VampirismRelicActionInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                VampirismRelicBindingsInteraction.INTERACTION_ID,
                VampirismRelicBindingsInteraction.class,
                VampirismRelicBindingsInteraction.CODEC);
        this.getCodecRegistry(Interaction.CODEC).register(
                VampirismPotionTransformInteraction.INTERACTION_ID,
                VampirismPotionTransformInteraction.class,
                VampirismPotionTransformInteraction.CODEC);
        RitualOfferingSurfaceComponent.TYPE = this.getEntityStoreRegistry().registerComponent(
                RitualOfferingSurfaceComponent.class,
                "Vampirism_RitualOfferingSurface",
                RitualOfferingSurfaceComponent.CODEC);
        runtime = VampirismRuntime.bootstrap(this, skillRuntimeBootstrap, skillTreeBounds::get);
        LOGGER.atInfo().log("[Vampirism] All systems registered.");
    }

    @Nonnull
    public ReloadedSkillDefinitions reloadSkillDefinitions() {
        SkillDefinitionCatalog reloadedCatalog = new SkillDefinitionCatalog();
        Vector2d reloadedBounds = loadDefinitions(reloadedCatalog);
        skillRuntimeBootstrap.replaceCatalog(reloadedCatalog);
        skillTreeBounds.set(reloadedBounds);
        return new ReloadedSkillDefinitions(
                reloadedCatalog.getAllSkills().size(),
                reloadedCatalog.getAllAbilities().size(),
                reloadedCatalog.getAllPassives().size(),
                reloadedCatalog.getAllEffects().size(),
                reloadedBounds);
    }

    @Nonnull
    public Vector2d currentSkillTreeBounds() {
        return skillTreeBounds.get();
    }

    @Nonnull
    public Path getPersistentDataDirectory() {
        return persistentDataDirectory;
    }

    @Nonnull
    private Vector2d loadDefinitions(@Nonnull SkillDefinitionCatalog definitionCatalog) {
        int maxX = 0;
        int maxY = 0;

        SkillLoader skillLoader = new SkillLoader(
                SkillDataPaths.vampirismDefaults(),
                skillRuntimeBootstrap.loadHooks());

        List<Skill> skills = skillLoader.loadSkills(definitionCatalog.skills());
        for (Skill skill : skills) {
            this.getLogger().at(Level.INFO).log(String.format("Skills Loaded %s", skill.id));
            if (Math.abs(skill.position.x) > Math.abs(maxX)) maxX = skill.position.x;
            if (Math.abs(skill.position.y) > Math.abs(maxY)) maxY = skill.position.y;
        }

        skillLoader.loadDefinitions(definitionCatalog.registries());

        LOGGER.atInfo().log(String.format("[Vampirism] Loaded %d passives, %d abilities, %d modifiers, %d effects, %d states, %d stats, %d conditions, %d requirements, %d triggers, %d actions, %d targetings.",
                definitionCatalog.getAllPassives().size(),
                definitionCatalog.getAllAbilities().size(),
                definitionCatalog.getAllModifiers().size(),
                definitionCatalog.getAllEffects().size(),
                definitionCatalog.getAllStates().size(),
                definitionCatalog.getAllStats().size(),
                definitionCatalog.getAllReusable(com.epicseed.epiccore.skill.runtime.ReusableDefKind.CONDITION).size(),
                definitionCatalog.getAllReusable(com.epicseed.epiccore.skill.runtime.ReusableDefKind.REQUIREMENT).size(),
                definitionCatalog.getAllReusable(com.epicseed.epiccore.skill.runtime.ReusableDefKind.TRIGGER).size(),
                definitionCatalog.getAllReusable(com.epicseed.epiccore.skill.runtime.ReusableDefKind.ACTION).size(),
                definitionCatalog.getAllReusable(com.epicseed.epiccore.skill.runtime.ReusableDefKind.TARGETING).size()));

        return new Vector2d(Math.abs(maxX), Math.abs(maxY));
    }

    public record ReloadedSkillDefinitions(int skillCount,
                                           int abilityCount,
                                           int passiveCount,
                                           int effectCount,
                                           @Nonnull Vector2d bounds) {
    }

    @Override
    protected void shutdown() {
        if (runtime != null) {
            runtime.shutdown();
        }
        RitualOfferingSurfaceInteraction.clearRuntime();
        LOGGER.atInfo().log("[Vampirism] Plugin disabled.");
    }

    @Nonnull
    private Path resolvePersistentDataDirectory(@Nonnull JavaPluginInit init) {
        Path resolvedDirectory = PluginDataDirectoryResolver.resolvePersistentDataDirectory(
                init.getDataDirectory(),
                new PluginIdentifier(init.getPluginManifest()));
        try {
            Files.createDirectories(resolvedDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create Vampirism persistent data directory: " + resolvedDirectory, e);
        }
        if (!resolvedDirectory.equals(init.getDataDirectory().toAbsolutePath().normalize())) {
            LOGGER.atInfo().log("[Vampirism] Using persistent data directory: " + resolvedDirectory);
        }
        return resolvedDirectory;
    }

    private void migrateLegacyDataDirectory(@Nonnull Path legacyDataDirectory,
                                            @Nonnull Path resolvedDataDirectory) {
        if (legacyDataDirectory.equals(resolvedDataDirectory) || !Files.exists(legacyDataDirectory)) {
            return;
        }
        if (Files.exists(legacyDataDirectory.resolve("manifest.json"))) {
            return;
        }
        try (Stream<Path> entries = Files.list(legacyDataDirectory)) {
            for (Path entry : entries.toList()) {
                Files.move(entry, resolvedDataDirectory.resolve(entry.getFileName().toString()));
            }
            Files.deleteIfExists(legacyDataDirectory);
            LOGGER.atInfo().log("[Vampirism] Migrated legacy plugin data from "
                    + legacyDataDirectory
                    + " to "
                    + resolvedDataDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to migrate legacy Vampirism data directory: " + legacyDataDirectory, e);
        }
    }
}
