/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.data;

import android.app.Instrumentation;
import android.content.Context;

import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.common.cache.CacheDatabase;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** This UT is a guardrail to schema migration managed by Room. */
public final class RoomSchemaMigrationGuardrailTest extends AdServicesUnitTestCase {
    // Note that this is not the context of this test, but the different context whose assets folder
    // is adservices/service-core/schemas
    private static final Context TARGET_CONTEXT =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private static final Instrumentation INSTRUMENTATION =
            InstrumentationRegistry.getInstrumentation();

    private static final List<Class<? extends RoomDatabase>> DATABASE_CLASSES =
            Arrays.stream(RoomDatabaseRegistration.class.getDeclaredFields())
                    .map(Field::getType)
                    .filter(aClass -> aClass.getSuperclass() == RoomDatabase.class)
                    .map(c -> (Class<? extends RoomDatabase>) c)
                    .collect(Collectors.toList());

    private static final Map<String, MigrationTestHelper> MIGRATION_TEST_HELPER_MAP =
            DATABASE_CLASSES.stream()
                    .collect(
                            Collectors.toMap(
                                    Class::getCanonicalName,
                                    database ->
                                            new MigrationTestHelper(INSTRUMENTATION, database)));

    private static final Map<Class<? extends RoomDatabase>, List<Object>> ADDITIONAL_CONVERTOR =
            ImmutableMap.of(
                    CustomAudienceDatabase.class,
                    ImmutableList.of(new DBCustomAudience.Converters(true, true, true)));

    private static final List<DatabaseWithVersion> BYPASS_DATABASE_VERSIONS_NEW_FIELD_ONLY =
            ImmutableList.of(new DatabaseWithVersion(CustomAudienceDatabase.class, 2));
    private static final List<Class<? extends RoomDatabase>>
            BYPASS_DATABASE_CLASS_MIGRATION_TEST_ENFORCEMENT =
                    ImmutableList.of(CacheDatabase.class);
    // TODO(b/318501421): Implement missing tests.
    private static final List<DatabaseWithVersion> BYPASS_DATABASE_MIGRATION_TEST_FOR_VERSION =
            ImmutableList.of(
                    new DatabaseWithVersion(CustomAudienceDatabase.class, 1),
                    new DatabaseWithVersion(CustomAudienceDatabase.class, 3),
                    new DatabaseWithVersion(CustomAudienceDatabase.class, 4),
                    new DatabaseWithVersion(AdSelectionDatabase.class, 4),
                    new DatabaseWithVersion(AdSelectionDatabase.class, 5),
                    new DatabaseWithVersion(SharedStorageDatabase.class, 2));

    private List<String> mErrors;

    @Before
    public void setup() {
        mErrors = new ArrayList<>();
    }

    @After
    public void teardown() {
        if (!mErrors.isEmpty()) {
            throw new RuntimeException(
                    String.format(
                            "Finish validating room databases with error \n%s",
                            mErrors.stream().distinct().collect(Collectors.joining("\n"))));
        }
    }

    @Test
    public void validateDatabaseMigrationAllowedChanges() {
        List<DatabaseWithVersion> databaseClassesWithNewestVersion =
                validateAndGetDatabaseClassesWithNewestVersionNumber();
        for (DatabaseWithVersion databaseWithVersion : databaseClassesWithNewestVersion) {
            validateJsonSchemaPopulatedCorrectly(databaseWithVersion);
            validateNewTablesAndFieldsOnly(databaseWithVersion);
            validateDatabaseMigrationTestExists(databaseWithVersion);
        }
    }

    private void validateDatabaseMigrationTestExists(DatabaseWithVersion databaseWithVersion) {
        if (databaseWithVersion.mVersion == 1) {
            return;
        }
        if (BYPASS_DATABASE_CLASS_MIGRATION_TEST_ENFORCEMENT.contains(
                databaseWithVersion.mRoomDatabaseClass)) {
            return;
        }

        String databaseClassName = databaseWithVersion.mRoomDatabaseClass.getCanonicalName();
        String testClassName = String.format("%sMigrationTest", databaseClassName);
        try {
            Class<?> testClass = Class.forName(testClassName);
            for (int i = 1; i < databaseWithVersion.mVersion; i++) {
                if (BYPASS_DATABASE_MIGRATION_TEST_FOR_VERSION.contains(
                        new DatabaseWithVersion(databaseWithVersion.mRoomDatabaseClass, i))) {
                    return;
                }
                String migrationTestMethodPrefix = String.format("testMigration%dTo%d", i, i + 1);
                if (!Arrays.stream(testClass.getMethods())
                        .anyMatch(method -> method.getName().contains(migrationTestMethodPrefix))) {
                    mErrors.add(
                            String.format(
                                    "Migration test %s* missing for database %s",
                                    migrationTestMethodPrefix, databaseClassName));
                }
            }
        } catch (ClassNotFoundException classNotFoundException) {
            mErrors.add(
                    String.format("Database migration test class %s is missing.", testClassName));
        }
    }

    private List<DatabaseWithVersion> validateAndGetDatabaseClassesWithNewestVersionNumber() {
        ImmutableList.Builder<DatabaseWithVersion> result = new ImmutableList.Builder<>();
        for (Class<? extends RoomDatabase> clazz : DATABASE_CLASSES) {
            try {
                int newestDatabaseVersion = getNewestDatabaseVersion(clazz);
                result.add(new DatabaseWithVersion(clazz, newestDatabaseVersion));
            } catch (Exception e) {
                mErrors.add(
                        String.format(
                                "Fail to get database version for %s, with error %s.",
                                clazz.getCanonicalName(), e.getMessage()));
            }
        }
        return result.build();
    }

    private int getNewestDatabaseVersion(Class<? extends RoomDatabase> database)
            throws NoSuchFieldException, IllegalAccessException {
        return database.getField("DATABASE_VERSION").getInt(null);
    }

    private void validateNewTablesAndFieldsOnly(DatabaseWithVersion databaseWithVersion) {
        // Custom audience table v1 to v2 is violating the policy. Skip it.
        if (BYPASS_DATABASE_VERSIONS_NEW_FIELD_ONLY.contains(databaseWithVersion)) {
            return;
        }
        int newestDatabaseVersion = databaseWithVersion.mVersion;
        Class<? extends RoomDatabase> roomDatabaseClass = databaseWithVersion.mRoomDatabaseClass;
        if (databaseWithVersion.mVersion == 1) {
            return;
        }

        Map<String, Map<String, SqliteColumnInfo>> oldTables;
        Map<String, Map<String, SqliteColumnInfo>> newTables;

        try {
            oldTables =
                    SqliteSchemaExtractor.getTableSchema(
                            getInMemoryDatabaseFromMigrationHelper(
                                    roomDatabaseClass, newestDatabaseVersion - 1));
            newTables =
                    SqliteSchemaExtractor.getTableSchema(
                            getInMemoryDatabaseFromMigrationHelper(
                                    roomDatabaseClass, newestDatabaseVersion));
        } catch (IOException e) {
            mErrors.add(
                    String.format(
                            "Database %s schema not exported or exported with error.",
                            roomDatabaseClass.getName()));
            return;
        }

        // We don't care new table in a new DB version. So iterate through the old version.
        for (Map.Entry<String, Map<String, SqliteColumnInfo>> e : oldTables.entrySet()) {
            String tableName = e.getKey();

            // table in old version must show in new.
            if (!newTables.containsKey(tableName)) {
                mErrors.add(
                        String.format(
                                "New version DB is missing table %s present in old version",
                                tableName));
                continue;
            }

            Map<String, SqliteColumnInfo> oldTableSchema = e.getValue();
            Map<String, SqliteColumnInfo> newTableSchema = newTables.get(tableName);

            for (Map.Entry<String, SqliteColumnInfo> oldField : oldTableSchema.entrySet()) {
                if (!oldField.getValue()
                        .equalsWithoutDefaultValue(newTableSchema.get(oldField.getKey()))) {
                    mErrors.add(
                            String.format(
                                    "Table %s and field %s: Missing field in new version or"
                                            + " mismatch field in new and old version.",
                                    tableName, oldField.getValue()));
                }
            }

            List<SqliteColumnInfo> newFields =
                    newTableSchema.values().stream()
                            .filter(field -> !oldTableSchema.containsKey(field.getName()))
                            .collect(Collectors.toList());

            for (SqliteColumnInfo field : newFields) {
                if (field.isNotnull() && field.getDefaultValue() == null) {
                    mErrors.add(
                            String.format(
                                    "Table %s and field %s: new field in table must be nullable.",
                                    tableName, field.getName()));
                }
                if (field.getPrimaryKey() != 0) {
                    mErrors.add(
                            String.format(
                                    "Table %s and field %s: primary key should not change.",
                                    tableName, field.getName()));
                }
            }
        }
    }

    private void validateJsonSchemaPopulatedCorrectly(DatabaseWithVersion databaseWithVersion) {
        Map<String, Map<String, SqliteColumnInfo>> databaseSchemaBuildFromDatabaseClass =
                SqliteSchemaExtractor.getTableSchema(
                        getInMemoryDatabaseFromDatabaseClass(
                                databaseWithVersion.mRoomDatabaseClass));

        Map<String, Map<String, SqliteColumnInfo>> databaseSchemaBuildFromJson;
        try {
            databaseSchemaBuildFromJson =
                    SqliteSchemaExtractor.getTableSchema(
                            getInMemoryDatabaseFromMigrationHelper(
                                    databaseWithVersion.mRoomDatabaseClass,
                                    databaseWithVersion.mVersion));
        } catch (IOException e) {
            mErrors.add(
                    String.format(
                            "Database %s schema not exported or exported with error.",
                            databaseWithVersion.mRoomDatabaseClass.getName()));
            return;
        }

        if (!databaseSchemaBuildFromJson
                        .keySet()
                        .containsAll(databaseSchemaBuildFromDatabaseClass.keySet())
                || !databaseSchemaBuildFromDatabaseClass
                        .keySet()
                        .containsAll(databaseSchemaBuildFromJson.keySet())) {
            mErrors.add(
                    String.format(
                            "Database %s, schema json is not updated, extra or missing table, did"
                                    + " you forget to build package locally?\n"
                                    + "\tTables in class are [%s]\n"
                                    + "\ttables in json are [%s]. ",
                            databaseWithVersion.mRoomDatabaseClass.getCanonicalName(),
                            String.join(",", databaseSchemaBuildFromDatabaseClass.keySet()),
                            String.join(",", databaseSchemaBuildFromJson.keySet())));
            return;
        }

        for (Map.Entry<String, Map<String, SqliteColumnInfo>> table :
                databaseSchemaBuildFromDatabaseClass.entrySet()) {
            String name = table.getKey();
            Map<String, SqliteColumnInfo> tableFromClass = table.getValue();
            Map<String, SqliteColumnInfo> tableFromJson = databaseSchemaBuildFromJson.get(name);
            if (!tableFromJson.equals(tableFromClass)) {
                mErrors.add(
                        String.format(
                                "Database %s, table %s class definition mismatches the exported"
                                        + " schema, did you forget to build package locally?\n"
                                        + "\tSchema created by class: %s\n"
                                        + "\tSchema created by bundle: %s",
                                databaseWithVersion.mRoomDatabaseClass.getCanonicalName(),
                                name,
                                table.getValue().values().stream()
                                        .map(SqliteColumnInfo::toString)
                                        .collect(Collectors.joining(",")),
                                tableFromJson.values().stream()
                                        .map(SqliteColumnInfo::toString)
                                        .collect(Collectors.joining(","))));
            }
        }
    }

    @NotNull
    private static SupportSQLiteDatabase getInMemoryDatabaseFromDatabaseClass(
            Class<? extends RoomDatabase> database) {
        RoomDatabase.Builder<? extends RoomDatabase> builder =
                Room.inMemoryDatabaseBuilder(TARGET_CONTEXT, database);
        if (ADDITIONAL_CONVERTOR.containsKey(database)) {
            for (Object convertor : ADDITIONAL_CONVERTOR.get(database)) {
                builder.addTypeConverter(convertor);
            }
        }
        return builder.build().getOpenHelper().getReadableDatabase();
    }

    private static SupportSQLiteDatabase getInMemoryDatabaseFromMigrationHelper(
            Class<? extends RoomDatabase> databaseClass, int version) throws IOException {
        return MIGRATION_TEST_HELPER_MAP
                .get(databaseClass.getCanonicalName())
                .createDatabase(databaseClass.getName() + version, version);
    }

    private static class DatabaseWithVersion {
        private final Class<? extends RoomDatabase> mRoomDatabaseClass;
        private final int mVersion;

        DatabaseWithVersion(Class<? extends RoomDatabase> roomDatabaseClass, int version) {
            mRoomDatabaseClass = roomDatabaseClass;
            mVersion = version;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DatabaseWithVersion)) return false;
            DatabaseWithVersion that = (DatabaseWithVersion) o;
            return mVersion == that.mVersion && mRoomDatabaseClass.equals(that.mRoomDatabaseClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mRoomDatabaseClass, mVersion);
        }

        @Override
        public String toString() {
            return "DatabaseWithVersion{"
                    + "mRoomDatabaseClass="
                    + mRoomDatabaseClass
                    + ", mVersion="
                    + mVersion
                    + '}';
        }
    }
}
