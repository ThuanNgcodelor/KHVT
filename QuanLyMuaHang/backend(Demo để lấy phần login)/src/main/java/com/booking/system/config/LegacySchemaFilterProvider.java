package com.booking.system.config;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;

import java.util.Locale;

/**
 * Keeps Hibernate's transitional ddl-auto=update ownership limited to legacy
 * BookingBase tables. All hr_* schema changes must go through Flyway.
 */
public final class LegacySchemaFilterProvider implements SchemaFilterProvider {

    private static final SchemaFilter LEGACY_ONLY = new SchemaFilter() {
        @Override
        public boolean includeNamespace(Namespace namespace) {
            return true;
        }

        @Override
        public boolean includeTable(Table table) {
            return !isHrObject(table.getName());
        }

        @Override
        public boolean includeSequence(Sequence sequence) {
            return !isHrObject(sequence.getName().getSequenceName().getCanonicalName());
        }
    };

    @Override
    public SchemaFilter getCreateFilter() {
        return LEGACY_ONLY;
    }

    @Override
    public SchemaFilter getDropFilter() {
        return LEGACY_ONLY;
    }

    @Override
    public SchemaFilter getTruncatorFilter() {
        return LEGACY_ONLY;
    }

    @Override
    public SchemaFilter getMigrateFilter() {
        return LEGACY_ONLY;
    }

    @Override
    public SchemaFilter getValidateFilter() {
        return SchemaFilter.ALL;
    }

    private static boolean isHrObject(String objectName) {
        if (objectName == null) {
            return false;
        }
        String normalized = objectName
                .replace("`", "")
                .replace("\"", "")
                .toLowerCase(Locale.ROOT);
        return normalized.startsWith("hr_");
    }
}
