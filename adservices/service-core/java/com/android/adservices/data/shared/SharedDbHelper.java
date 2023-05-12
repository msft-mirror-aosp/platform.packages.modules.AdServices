/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.shared;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.shared.migration.ISharedDbMigrator;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Database Helper for Shared AdServices database. */
public class SharedDbHelper extends SQLiteOpenHelper {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private static final String DATABASE_NAME = "adservices_shared.db";

    public static final int CURRENT_DATABASE_VERSION = 1;
    public static final int OLD_DATABASE_FINAL_VERSION = 1;

    private static SharedDbHelper sSingleton = null;
    private final File mDbFile;
    private final int mDbVersion;
    private final DbHelper mDbHelper;

    @VisibleForTesting
    public SharedDbHelper(
            @NonNull Context context, @NonNull String dbName, int dbVersion, DbHelper dbHelper) {
        super(context, dbName, null, dbVersion);
        mDbFile = context.getDatabasePath(dbName);
        this.mDbVersion = dbVersion;
        this.mDbHelper = dbHelper;
    }

    /** Returns an instance of the SharedDbHelper given a context. */
    @NonNull
    public static SharedDbHelper getInstance(@NonNull Context ctx) {
        synchronized (SharedDbHelper.class) {
            if (sSingleton == null) {
                sSingleton =
                        new SharedDbHelper(
                                ctx,
                                DATABASE_NAME,
                                CURRENT_DATABASE_VERSION,
                                DbHelper.getInstance(ctx));
            }
            return sSingleton;
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        sLogger.d(
                "SharedDbHelper.onCreate with version %d. Name: %s", mDbVersion, mDbFile.getName());
        SQLiteDatabase oldDb = mDbHelper.safeGetWritableDatabase();
        if (hasAllTables(oldDb, EnrollmentTables.ENROLLMENT_TABLES)) {
            sLogger.d("SharedDbHelper.onCreate copying data from old db");
            // Migrate Data:
            // 1. Create V1 (old DbHelper's last database version) version of tables
            createV1Schema(db);
            // 2. TODO(b/277964933) Copy data from old database
            // migrateOldDataToNewDatabase(oldDb, db);
            // 3. TODO(b/277964933) Delete tables from old database
            // 4. TODO(b/277964933) Upgrade schema to the latest version
            // upgradeSchema(db, OLD_DATABASE_FINAL_VERSION, eDbVersion);
        } else {
            sLogger.d("SharedDbHelper.onCreate creating empty database");
            createSchema(db);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        sLogger.d(
                "SharedDbHelper.onUpgrade. Attempting to upgrade version from %d to %d.",
                oldVersion, newVersion);
        upgradeSchema(db, oldVersion, newVersion);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
    }

    private List<ISharedDbMigrator> getOrderedDbMigrators() {
        return ImmutableList.of();
    }

    private boolean hasAllTables(SQLiteDatabase db, String[] tableArray) {
        List<String> selectionArgList = new ArrayList<>(Arrays.asList(tableArray));
        selectionArgList.add("table"); // Schema type to match
        String[] selectionArgs = new String[selectionArgList.size()];
        selectionArgList.toArray(selectionArgs);
        return DatabaseUtils.queryNumEntries(
                        db,
                        "sqlite_master",
                        "name IN ("
                                + Stream.generate(() -> "?")
                                        .limit(tableArray.length)
                                        .collect(Collectors.joining(","))
                                + ")"
                                + " AND type = ?",
                        selectionArgs)
                == tableArray.length;
    }

    /** Wraps getWritableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException e) {
            sLogger.e(e, "Failed to get a writeable database");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED);
            return null;
        }
    }

    /** Wraps getReadableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            sLogger.e(e, "Failed to get a readable database");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PPAPI_NAME_UNSPECIFIED);
            return null;
        }
    }

    public long getDbFileSize() {
        return mDbFile != null && mDbFile.exists() ? mDbFile.length() : -1;
    }

    private void createV1Schema(SQLiteDatabase db) {
        EnrollmentTables.CREATE_STATEMENTS.forEach(db::execSQL);
    }

    private void createSchema(SQLiteDatabase db) {
        if (mDbVersion == CURRENT_DATABASE_VERSION) {
            EnrollmentTables.CREATE_STATEMENTS.forEach(db::execSQL);
        } else {
            // If the provided DB version is not the latest, create the starting schema and upgrade
            // to that. This branch is primarily for testing purpose.
            createV1Schema(db);
            upgradeSchema(db, OLD_DATABASE_FINAL_VERSION, mDbVersion);
        }
    }

    private void migrateOldDataToNewDatabase(SQLiteDatabase oldDb, SQLiteDatabase db) {
        // Ordered iteration to populate tables to avoid
        // foreign key constraint failures.
        Arrays.stream(EnrollmentTables.ENROLLMENT_TABLES)
                .forEachOrdered((table) -> copyTable(oldDb, db, table));
    }

    private void copyTable(SQLiteDatabase oldDb, SQLiteDatabase newDb, String table) {
        try (Cursor cursor = oldDb.query(table, null, null, null, null, null, null, null)) {
            // TODO(b/277964933) Filter out Duplicate Site Records
            // Set<String> site = new Set<String>();

            while (cursor.moveToNext()) {
                ContentValues contentValues = new ContentValues();
                DatabaseUtils.cursorRowToContentValues(cursor, contentValues);
                newDb.insert(table, null, contentValues);
            }
        }
    }

    private void upgradeSchema(SQLiteDatabase db, int oldVersion, int newVersion) {
        sLogger.d(
                "SharedDbHelper.upgradeToLatestSchema. "
                        + "Attempting to upgrade version from %d to %d.",
                oldVersion, newVersion);
        getOrderedDbMigrators()
                .forEach(dbMigrator -> dbMigrator.performMigration(db, oldVersion, newVersion));
    }
}
