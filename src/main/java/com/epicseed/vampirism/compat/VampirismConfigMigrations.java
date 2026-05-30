package com.epicseed.vampirism.compat;

import com.epicseed.epiccore.compat.SchemaVersionPolicy;
import com.epicseed.epiccore.compat.VersionedDocumentMigration;
import com.epicseed.epiccore.compat.VersionedMigrationRegistry;
import com.epicseed.vampirism.config.VampirismConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.Nonnull;

public final class VampirismConfigMigrations {

    public static final String DOCUMENT_KIND = "vampirism-config";

    private VampirismConfigMigrations() {
    }

    @Nonnull
    public static SchemaVersionPolicy versionPolicy() {
        return new SchemaVersionPolicy(VampirismConfig.SCHEMA_VERSION_FIELD, 0);
    }

    @Nonnull
    public static VersionedMigrationRegistry registry() {
        return new VersionedMigrationRegistry()
                .currentVersion(DOCUMENT_KIND, VampirismConfig.CURRENT_SCHEMA_VERSION)
                .register(new InitialVersionStampMigration());
    }

    private static final class InitialVersionStampMigration implements VersionedDocumentMigration {

        @Nonnull
        @Override
        public String documentKind() {
            return DOCUMENT_KIND;
        }

        @Override
        public int fromVersion() {
            return 0;
        }

        @Override
        public int toVersion() {
            return VampirismConfig.CURRENT_SCHEMA_VERSION;
        }

        @Override
        public void migrate(@Nonnull ObjectNode document) {
        }

        @Nonnull
        @Override
        public String id() {
            return "vampirism-config-initial-version";
        }
    }
}
