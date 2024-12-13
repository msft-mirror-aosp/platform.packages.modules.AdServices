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

package com.android.adservices.data.measurement;

import android.annotation.Nullable;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.errorlogging.AdServicesErrorLoggerImpl;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.errorlogging.AdServicesErrorLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.errorprone.annotations.concurrent.GuardedBy;

/** Datastore manager for SQLite database. */
@VisibleForTesting
public final class SQLDatastoreManager extends DatastoreManager {

    private static final Object LOCK = new Object();

    @GuardedBy("LOCK")
    private static SQLDatastoreManager sSingleton;

    private final MeasurementDbHelper mDbHelper;

    /** Gets the singleton instance of {@link SQLDatastoreManager}. */
    static synchronized SQLDatastoreManager getInstance() {
        synchronized (LOCK) {
            if (sSingleton == null) {
                Context context = ApplicationContextSingleton.get();
                sSingleton = new SQLDatastoreManager(context);
            }
            return sSingleton;
        }
    }

    @VisibleForTesting
    SQLDatastoreManager(Context context) {
        this(MeasurementDbHelper.getInstance(), AdServicesErrorLoggerImpl.getInstance());
    }

    @VisibleForTesting
    public SQLDatastoreManager(MeasurementDbHelper dbHelper, AdServicesErrorLogger errorLogger) {
        super(errorLogger);
        mDbHelper = dbHelper;
    }

    @Override
    @Nullable
    protected ITransaction createNewTransaction() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return null;
        }
        return new SQLTransaction(db);
    }

    @Override
    @VisibleForTesting
    public IMeasurementDao getMeasurementDao() {
        return new MeasurementDao(
                () ->
                        mDbHelper.getDbFileSize()
                                >= FlagsFactory.getFlags().getMeasurementDbSizeLimit(),
                () -> FlagsFactory.getFlags().getMeasurementReportingRetryLimit(),
                () -> FlagsFactory.getFlags().getMeasurementReportingRetryLimitEnabled());
    }

    @Override
    protected int getDataStoreVersion() {
        return mDbHelper.getReadableDatabase().getVersion();
    }
}
