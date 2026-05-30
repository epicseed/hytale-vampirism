package com.epicseed.vampirism.compat;

import com.epicseed.epiccore.compat.VersionedMigrationRegistry;
import com.epicseed.epiccore.hytale.config.VersionedConfigFileMigrator;
import com.epicseed.epiccore.hytale.logging.EpicLogger;
import com.epicseed.vampirism.config.VampirismConfig;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nonnull;

public final class VampirismCompatibility {

    private static final EpicLogger LOGGER = EpicLogger.forMod("Vampirism").subsystem("Compatibility");

    private VampirismCompatibility() {
    }

    public static void migrateConfig(@Nonnull Path pluginDataDirectory) throws IOException {
        VersionedConfigFileMigrator.migrateIfPresent(
                pluginDataDirectory,
                "Vampirism",
                VampirismConfigMigrations.DOCUMENT_KIND,
                VampirismConfigMigrations.registry(),
                VampirismConfigMigrations.versionPolicy(),
                LOGGER);
    }

    @Nonnull
    public static VersionedMigrationRegistry profileMigrations() {
        return VampirismProfileMigrations.registry();
    }

    public static int currentConfigSchemaVersion() {
        return VampirismConfig.CURRENT_SCHEMA_VERSION;
    }
}
