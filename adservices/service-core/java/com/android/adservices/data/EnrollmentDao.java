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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import com.android.adservices.LogUtil;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.data.measurement.SqliteObjectMapper;
import com.android.adservices.service.EnrollmentData;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/** Data Access Object for the EnrollmentData. */
public class EnrollmentDao {

    private static EnrollmentDao sSingleton;

    private final DbHelper mDbHelper;

    /**
     * It's only public to unit test.
     *
     * @param dbHelper The database to query
     */
    @VisibleForTesting
    public EnrollmentDao(DbHelper dbHelper) {
        mDbHelper = dbHelper;
    }

    /** Returns an instance of the EnrollmentDao given a context. */
    @NonNull
    public static EnrollmentDao getInstance(@NonNull Context context) {
        synchronized (EnrollmentDao.class) {
            if (sSingleton == null) {
                sSingleton = new EnrollmentDao(DbHelper.getInstance(context));
            }
            return sSingleton;
        }
    }

    /**
     * Returns the {@link EnrollmentData}.
     *
     * @param enrollmentId ID provided to the adtech during the enrollment process.
     * @return the EnrollmentData; Null in case of SQL failure
     */
    @Nullable
    @VisibleForTesting
    public EnrollmentData getEnrollmentData(String enrollmentId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor =
                db.query(
                        MeasurementTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        MeasurementTables.EnrollmentDataContract.ENROLLMENT_ID + " = ? ",
                        new String[] {enrollmentId},
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    /**
     * Returns the {@link EnrollmentData} given measurement registration URLs.
     *
     * @param url could be source registration url or trigger registration url.
     * @return the EnrollmentData; Null in case of SQL failure.
     */
    @Nullable
    @VisibleForTesting
    public EnrollmentData getEnrollmentDataGivenUrl(String url) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor =
                db.query(
                        MeasurementTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        MeasurementTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL
                                + " LIKE '%"
                                + url
                                + "%' OR "
                                + MeasurementTables.EnrollmentDataContract
                                        .ATTRIBUTION_TRIGGER_REGISTRATION_URL
                                + " LIKE '%"
                                + url
                                + "%'",
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    /**
     * Returns the {@link EnrollmentData} given AdTech SDK Name.
     *
     * @param sdkName List of SDKs belonging to the same enrollment.
     * @return the EnrollmentData; Null in case of SQL failure
     */
    @Nullable
    @VisibleForTesting
    public EnrollmentData getEnrollmentDataGivenSdkName(String sdkName) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return null;
        }
        try (Cursor cursor =
                db.query(
                        MeasurementTables.EnrollmentDataContract.TABLE,
                        /*columns=*/ null,
                        MeasurementTables.EnrollmentDataContract.SDK_NAMES
                                + " LIKE '%"
                                + sdkName
                                + "%'",
                        null,
                        /*groupBy=*/ null,
                        /*having=*/ null,
                        /*orderBy=*/ null,
                        /*limit=*/ null)) {
            if (cursor == null || cursor.getCount() == 0) {
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
        }
    }

    /**
     * Inserts {@link EnrollmentData} into DB table.
     *
     * @param enrollmentData the EnrollmentData to insert.
     */
    @VisibleForTesting
    public void insertEnrollmentData(EnrollmentData enrollmentData) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(
                MeasurementTables.EnrollmentDataContract.ENROLLMENT_ID,
                enrollmentData.getEnrollmentId());
        values.put(
                MeasurementTables.EnrollmentDataContract.COMPANY_ID, enrollmentData.getCompanyId());
        values.put(
                MeasurementTables.EnrollmentDataContract.SDK_NAMES,
                String.join(" ", enrollmentData.getSdkNames()));
        values.put(
                MeasurementTables.EnrollmentDataContract.ATTRIBUTION_SOURCE_REGISTRATION_URL,
                String.join(" ", enrollmentData.getAttributionSourceRegistrationUrl()));
        values.put(
                MeasurementTables.EnrollmentDataContract.ATTRIBUTION_TRIGGER_REGISTRATION_URL,
                String.join(" ", enrollmentData.getAttributionTriggerRegistrationUrl()));
        values.put(
                MeasurementTables.EnrollmentDataContract.ATTRIBUTION_REPORTING_URL,
                String.join(" ", enrollmentData.getAttributionReportingUrl()));
        values.put(
                MeasurementTables.EnrollmentDataContract
                        .REMARKETING_RESPONSE_BASED_REGISTRATION_URL,
                String.join(" ", enrollmentData.getRemarketingResponseBasedRegistrationUrl()));
        values.put(
                MeasurementTables.EnrollmentDataContract.ENCRYPTION_KEY_URL,
                String.join(" ", enrollmentData.getEncryptionKeyUrl()));
        try {
            db.insert(
                    MeasurementTables.EnrollmentDataContract.TABLE,
                    /*nullColumnHack=*/ null,
                    values);
        } catch (SQLException e) {
            LogUtil.e("Failed to insert EnrollmentData. Exception : " + e.getMessage());
        }
    }

    /**
     * Deletes {@link EnrollmentData} from DB table.
     *
     * @param enrollmentId ID provided to the adtech at the end of the enrollment process.
     */
    @VisibleForTesting
    public void deleteEnrollmentData(String enrollmentId) {
        Objects.requireNonNull(enrollmentId);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }
        try {
            db.delete(
                    MeasurementTables.EnrollmentDataContract.TABLE,
                    MeasurementTables.EnrollmentDataContract.ENROLLMENT_ID + " = ?",
                    new String[] {enrollmentId});
        } catch (SQLException e) {
            LogUtil.e("Failed to delete EnrollmentData." + e.getMessage());
        }
    }

    /** Deletes the whole EnrollmentData table. */
    @VisibleForTesting
    public void deleteEnrollmentDataTable() {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }

        // Handle this in a transaction.
        db.beginTransaction();
        try {
            db.delete(MeasurementTables.EnrollmentDataContract.TABLE, null, null);
            // Mark the transaction successful.
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
