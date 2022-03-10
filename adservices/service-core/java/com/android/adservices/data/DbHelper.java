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
import com.android.adservices.data.topics.TopicsTables;

/**
 * Helper to manage the PP API database. Designed as a singleton to make sure that all PP API usages
 * get the same reference.
 */
public final class DbHelper extends SQLiteOpenHelper {

    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "adservices.db";

    // This database instance will be used for testing to avoid interference with the one used for
    // production above.
    private static final String DATABASE_NAME_FOR_TEST = "adservices_test.db";

    private static DbHelper sSingleton = null;

    public DbHelper(@NonNull Context context, @NonNull String dbName) {
        super(context, dbName, null, DATABASE_VERSION);
    }

    /** Returns an instance of the DbHelper given a context. */
    @NonNull
    public static DbHelper getInstance(@NonNull Context ctx) {
        synchronized (DbHelper.class) {
            if (sSingleton == null) {
                sSingleton = new DbHelper(ctx, DATABASE_NAME);
            }
            return sSingleton;
        }
    }

    /** Returns an instance of the DbHelper given a context. */
    @NonNull
    public static synchronized DbHelper getInstanceForTest(@NonNull Context ctx) {
        if (sSingleton == null) {
            sSingleton = new DbHelper(ctx, DATABASE_NAME_FOR_TEST);
        }
        return sSingleton;
    }

    @Override
    public void onCreate(@NonNull SQLiteDatabase db) {
        for (String sql : TopicsTables.CREATE_STATEMENTS) {
            db.execSQL(sql);
        }
    }

    @Override
    public void onUpgrade(@NonNull SQLiteDatabase db, int oldVersion, int newVersion) {
        // TODO: handle upgrade when we introduce db schema change.
        LogUtil.d("DbHelper.onUpgrade.");
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
}
