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
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.migration.IMeasurementDbMigrator;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV2;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV3;
import com.android.adservices.data.measurement.migration.MeasurementDbMigratorV4;
import com.android.adservices.data.topics.TopicsTables;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * Helper to manage the PP API database. Designed as a singleton to make sure that all PP API usages
 * get the same reference.
 */
public final class DbHelper extends SQLiteOpenHelper {

    static final int LATEST_DATABASE_VERSION = 4;
    private static final String DATABASE_NAME = "adservices.db";

    private static DbHelper sSingleton = null;

    /**
     * Ideally we'd want to keep only the {@link #LATEST_DATABASE_VERSION} parameter. This field
     * helps initialize and upgrade the DB to a certain version, which is useful for DB migration
     * tests.
     */
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
        this.mDbVersion = dbVersion;
    }

    /** Returns an instance of the DbHelper given a context. */
    @NonNull
    public static DbHelper getInstance(@NonNull Context ctx) {
        synchronized (DbHelper.class) {
            if (sSingleton == null) {
                sSingleton = new DbHelper(ctx, DATABASE_NAME, LATEST_DATABASE_VERSION);
            }
            return sSingleton;
        }
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        LogUtil.d("DbHelper.onCreate.");
        for (String sql : TopicsTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
        for (String sql : MeasurementTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
        for (String sql : MeasurementTables.CREATE_INDEXES) {
            db.execSQL(sql);
        }
        onUpgrade(db, 0, mDbVersion);
    }

    /**
     * Wraps getReadableDatabase to catch SQLiteException and log error.
     */
    @Nullable
    public SQLiteDatabase safeGetReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e("Failed to get a readable database", e);
            return null;
        }
    }

    /**
     * Wraps getWritableDatabase to catch SQLiteException and log error.
     */
    @Nullable
    public SQLiteDatabase safeGetWritableDatabase() {
        try {
            return super.getWritableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e("Failed to get a writeable database", e);
            return null;
        }
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtil.d("DbHelper.onUpgrade.");
        getOrderedDbMigrators()
                .forEach(dbMigrator -> dbMigrator.performMigration(db, oldVersion, newVersion));
    }

    private static List<IMeasurementDbMigrator> getOrderedDbMigrators() {
        return ImmutableList.of(
                new MeasurementDbMigratorV2(),
                new MeasurementDbMigratorV3(),
                new MeasurementDbMigratorV4());
    }
}
