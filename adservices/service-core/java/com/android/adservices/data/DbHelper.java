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
import com.android.adservices.data.topics.TopicsTables;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;

/**
 * Helper to manage the PP API database. Designed as a singleton to make sure that all PP API usages
 * get the same reference.
 */
public class DbHelper extends SQLiteOpenHelper {

    static final int LATEST_DATABASE_VERSION = 2;
    private static final String DATABASE_NAME = "adservices.db";

    private static DbHelper sSingleton = null;
    private final File mDbFile;

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
        for (String sql : EnrollmentTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
    }

    /**
     * Wraps getReadableDatabase to catch SQLiteException and log error.
     */
    @Nullable
    public SQLiteDatabase safeGetReadableDatabase() {
        try {
            return super.getReadableDatabase();
        } catch (SQLiteException e) {
            LogUtil.e(e, "Failed to get a readable database");
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
            LogUtil.e(e, "Failed to get a writeable database");
            return null;
        }
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        LogUtil.d("DbHelper.onUpgrade.");
        getOrderedDbMigrators()
                .forEach(dbMigrator -> dbMigrator.performMigration(db, oldVersion, newVersion));
    }

    public long getDbFileSize() {
        return mDbFile != null && mDbFile.exists() ? mDbFile.length() : -1;
    }

    private static List<IMeasurementDbMigrator> getOrderedDbMigrators() {
        return ImmutableList.of(new MeasurementDbMigratorV2());
    }
}
