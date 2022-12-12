/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;

import com.android.adservices.LogUtil;
import com.android.adservices.data.enrollment.EnrollmentTables;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.migration.IMeasurementDbMigrator;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV2;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV3;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.data.topics.migration.ITopicsDbMigrator;
import com.android.adservices.data.topics.migration.TopicDbMigratorV5;
import com.android.adservices.service.FlagsFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * Helper to manage the PP API database. Designed as a singleton to make sure that all PP API usages
 * get the same reference.
 */
public class DbHelper extends SQLiteOpenHelper {
    // Version 5: Add TopicContributors Table for Topics API, guarded by feature flag.
    public static final int DATABASE_VERSION_V5 = 5;

    static final int CURRENT_DATABASE_VERSION = 3;
    private static final String DATABASE_NAME = "adservices.db";

    private static DbHelper sSingleton = null;
    private final File mDbFile;
    // The version when the database is actually created
    private final int mDbVersion;

    /**
     * It's only public to unit test.
     *
     * @param context the context
     * @param dbName Name of database to query
     * @param dbVersion db version
     */
    @VisibleForTesting
    public DbHelper(@NonNull Context context, @NonNull String dbName, int dbVersion) {
        super(context, dbName, null, dbVersion);
        mDbFile = context.getDatabasePath(dbName);
        this.mDbVersion = dbVersion;
    }

    /** Returns an instance of the DbHelper given a context. */
    @NonNull
    public static DbHelper getInstance(@NonNull Context ctx) {
        synchronized (DbHelper.class) {
            if (sSingleton == null) {
                sSingleton = new DbHelper(ctx, DATABASE_NAME, getDatabaseVersionToCreate());
            }
            return sSingleton;
        }
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        LogUtil.d("DbHelper.onCreate with version %d. Name: %s", mDbVersion, mDbFile.getName());
        for (String sql : TopicsTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
        for (String sql : MeasurementTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
        for (String sql : MeasurementTables.CREATE_INDEXES) {
            db.execSQL(sql);
        }
        for (String sql : EnrollmentTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    /** Wraps getReadableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e(e, "Failed to get a readable database");
            return null;
        }
    }

    /** Wraps getWritableDatabase to catch SQLiteException and log error. */
    @Nullable
    public SQLiteDatabase safeGetWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e(e, "Failed to get a writeable database");
            return null;
        }
    }

    // TODO(b/255964885): Consolidate DB Migrator Class across Rubidium
    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtil.d(
                "DbHelper.onUpgrade. Attempting to upgrade version from %d to %d.",
                oldVersion, newVersion);
        getOrderedDbMigrators()
                .forEach(dbMigrator -> dbMigrator.performMigration(db, oldVersion, newVersion));
        try {
            topicsGetOrderedDbMigrators()
                    .forEach(dbMigrator -> dbMigrator.performMigration(db, oldVersion, newVersion));
        } catch (IllegalArgumentException e) {
            LogUtil.e(
                    "Topics DB Upgrade is not performed! oldVersion: %d, newVersion: %d.",
                    oldVersion, newVersion);
        }
    }

    // TODO(b/261934022): Support a framework as upgrade.
    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Only downgrade if it's triggered by value change of Flag enable_database_schema_version_5
        if (oldVersion == DATABASE_VERSION_V5
                && newVersion == CURRENT_DATABASE_VERSION
                && !FlagsFactory.getFlags().getEnableDatabaseSchemaVersion5()) {
            LogUtil.e(
                    "Has to downgrade database version from %d to %d. The reason is"
                            + " TopicContributorsTable was enabled and now disabled. Dropping"
                            + " TopicContributorsTable...",
                    DATABASE_VERSION_V5, CURRENT_DATABASE_VERSION);
            db.execSQL("DROP TABLE IF EXISTS " + TopicsTables.TopicContributorsContract.TABLE);
            return;
        }

        super.onDowngrade(db, oldVersion, newVersion);
    }

    public long getDbFileSize() {
        return mDbFile != null && mDbFile.exists() ? mDbFile.length() : -1;
    }

    /**
     * Check whether TopContributors Table is supported in current database. TopContributors is
     * introduced in Version 3.
     */
    public boolean supportsTopicContributorsTable() {
        return mDbVersion >= DATABASE_VERSION_V5;
    }

    /** Get Migrators in order for Measurement. */
    @VisibleForTesting
    public List<IMeasurementDbMigrator> getOrderedDbMigrators() {
        return ImmutableList.of(new MeasurementDbMigratorV2(), new MeasurementDbMigratorV3());
    }

    /** Get Migrators in order for Topics. */
    @VisibleForTesting
    public List<ITopicsDbMigrator> topicsGetOrderedDbMigrators() {
        return ImmutableList.of(new TopicDbMigratorV5());
    }

    // Get the database version to create. It may be different as CURRENT_DATABASE_VERSION,
    // depending
    // on Flags status.
    @VisibleForTesting
    static int getDatabaseVersionToCreate() {
        return FlagsFactory.getFlags().getEnableDatabaseSchemaVersion5()
                ? DATABASE_VERSION_V5
                : CURRENT_DATABASE_VERSION;
    }
}
