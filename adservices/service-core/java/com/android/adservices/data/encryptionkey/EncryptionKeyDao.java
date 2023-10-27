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

package com.android.adservices.data.encryptionkey;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Data Access Object for EncryptionKey. */
public class EncryptionKeyDao implements IEncryptionKeyDao {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private static EncryptionKeyDao sSingleton;
    private final SharedDbHelper mDbHelper;

    @VisibleForTesting
    public EncryptionKeyDao(SharedDbHelper dbHelper) {
        mDbHelper = dbHelper;
    }

    /** Returns an instance of the EncryptionKeyDao given a context. */
    @NonNull
    public static EncryptionKeyDao getInstance(@NonNull Context context) {
        synchronized (EncryptionKeyDao.class) {
            if (sSingleton == null) {
                sSingleton = new EncryptionKeyDao(SharedDbHelper.getInstance(context));
            }
            return sSingleton;
        }
    }

    @Override
    public List<EncryptionKey> getEncryptionKeyFromEnrollmentId(String enrollmentId)
            throws SQLException {
        List<EncryptionKey> encryptionKeyList = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID
                                + " = ? ", // The columns for the WHERE clause
                        new String[] {enrollmentId}, // The values for the WHERE clause
                        null, // don't group the rows
                        null, // don't filter by row groups
                        null, // The sort order
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i("No EncryptionKey in DB with enrollment id: %s.", enrollmentId);
                return encryptionKeyList;
            }

            sLogger.i("Found %s keys for enrollment id %s.", cursor.getCount(), enrollmentId);
            while (cursor.moveToNext()) {
                encryptionKeyList.add(SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor));
            }
            return encryptionKeyList;
        } catch (SQLException e) {
            sLogger.e(e, "Failed to find EncryptionKey in DB with enrollment id: " + enrollmentId);
        }
        return encryptionKeyList;
    }

    @Override
    public List<EncryptionKey> getEncryptionKeyFromEnrollmentIdAndKeyType(
            String enrollmentId, EncryptionKey.KeyType keyType) throws SQLException {
        List<EncryptionKey> encryptionKeyList = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID
                                + " = ? "
                                + " AND "
                                + EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE
                                + " = ?",
                        new String[] {enrollmentId, keyType.toString()},
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i("No EncryptionKey in DB with enrollment id: %s.", enrollmentId);
                return encryptionKeyList;
            }

            sLogger.i(
                    "Found %s keys for key type: %s, enrollment id %s.",
                    cursor.getCount(), keyType, enrollmentId);
            while (cursor.moveToNext()) {
                encryptionKeyList.add(SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor));
            }
            return encryptionKeyList;
        } catch (SQLException e) {
            sLogger.e(e, "Failed to find EncryptionKey in DB with enrollment id:" + enrollmentId);
        }
        return encryptionKeyList;
    }

    @Override
    @Nullable
    public EncryptionKey getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
            String enrollmentId, int keyCommitmentId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID
                                + " = ? "
                                + " AND "
                                + EncryptionKeyTables.EncryptionKeyContract.KEY_COMMITMENT_ID
                                + " = ?",
                        new String[] {enrollmentId, String.valueOf(keyCommitmentId)},
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i(
                        "No EncryptionKey in DB with enrollmentId: %s, keyCommitmentId: %s.",
                        enrollmentId, String.valueOf(keyCommitmentId));
                return null;
            }
            cursor.moveToNext();
            return SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor);
        } catch (SQLException e) {
            sLogger.e(
                    e,
                    "Failed to find EncryptionKey in DB with keyCommitmentId: " + keyCommitmentId);
        }
        return null;
    }

    @Override
    public List<EncryptionKey> getEncryptionKeyFromReportingOrigin(
            Uri reportingOrigin, EncryptionKey.KeyType keyType) {
        List<EncryptionKey> encryptionKeyList = new ArrayList<>();
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        EncryptionKeyTables.EncryptionKeyContract.REPORTING_ORIGIN
                                + " = ? "
                                + " AND "
                                + EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE
                                + " = ?",
                        new String[] {reportingOrigin.toString(), keyType.toString()},
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i(
                        "No EncryptionKey in DB with reportingOrigin: %s, keyType: %s",
                        reportingOrigin.toString(), keyType.toString());
                return encryptionKeyList;
            }

            sLogger.i(
                    "Found %s keys for key type: %s, reporting origin %s.",
                    cursor.getCount(), keyType, reportingOrigin);
            while (cursor.moveToNext()) {
                encryptionKeyList.add(SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor));
            }
            return encryptionKeyList;
        } catch (SQLException e) {
            sLogger.e(
                    e,
                    "Failed to find EncryptionKey in DB with reportingOrigin: " + reportingOrigin);
        }
        return encryptionKeyList;
    }

    @Override
    public List<EncryptionKey> getAllEncryptionKeys() {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        List<EncryptionKey> encryptionKeys = new ArrayList<>();
        try (Cursor cursor =
                db.query(
                        EncryptionKeyTables.EncryptionKeyContract.TABLE,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)) {
            if (cursor == null || cursor.getCount() <= 0) {
                sLogger.i("No Encryption keys in DB.");
                return encryptionKeys;
            }
            while (cursor.moveToNext()) {
                encryptionKeys.add(SqliteObjectMapper.constructEncryptionKeyFromCursor(cursor));
            }
            return encryptionKeys;
        } catch (SQLException e) {
            sLogger.e(e, "Failed to get all encryption keys from DB.");
        }
        return encryptionKeys;
    }

    @Override
    public boolean insert(EncryptionKey encryptionKey) {
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        if (!isEncryptionKeyValid(encryptionKey)) {
            sLogger.e("Encryption key is invalid, can't insert into DB.");
            return false;
        }
        EncryptionKey existKey =
                getEncryptionKeyFromEnrollmentIdAndKeyCommitmentId(
                        encryptionKey.getEnrollmentId(), encryptionKey.getKeyCommitmentId());
        if (existKey != null) {
            delete(existKey.getId());
        }
        try {
            insertToDb(encryptionKey, db);
        } catch (SQLException e) {
            sLogger.e(e, "Failed to insert EncryptionKey into DB.");
            return false;
        }
        return true;
    }

    private static boolean isEncryptionKeyValid(EncryptionKey encryptionKey) {
        return encryptionKey.getEnrollmentId() != null
                && encryptionKey.getKeyType() != null
                && encryptionKey.getEncryptionKeyUrl() != null
                && encryptionKey.getProtocolType() != null
                && encryptionKey.getBody() != null
                && encryptionKey.getExpiration() != 0L
                && encryptionKey.getLastFetchTime() != 0L;
    }

    private static void insertToDb(EncryptionKey encryptionKey, SQLiteDatabase db)
            throws SQLException {
        ContentValues values = new ContentValues();
        values.put(EncryptionKeyTables.EncryptionKeyContract.ID, encryptionKey.getId());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.KEY_TYPE,
                encryptionKey.getKeyType().name());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.ENROLLMENT_ID,
                encryptionKey.getEnrollmentId());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.REPORTING_ORIGIN,
                encryptionKey.getReportingOrigin().toString());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.ENCRYPTION_KEY_URL,
                encryptionKey.getEncryptionKeyUrl());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.PROTOCOL_TYPE,
                encryptionKey.getProtocolType().name());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.KEY_COMMITMENT_ID,
                encryptionKey.getKeyCommitmentId());
        values.put(EncryptionKeyTables.EncryptionKeyContract.BODY, encryptionKey.getBody());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.EXPIRATION,
                encryptionKey.getExpiration());
        values.put(
                EncryptionKeyTables.EncryptionKeyContract.LAST_FETCH_TIME,
                encryptionKey.getLastFetchTime());
        try {
            db.insertWithOnConflict(
                    EncryptionKeyTables.EncryptionKeyContract.TABLE,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException e) {
            sLogger.e("Failed to insert EncryptionKey into DB. Exception : " + e.getMessage());
        }
    }

    @Override
    public boolean delete(String id) {
        Objects.requireNonNull(id);
        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return false;
        }
        try {
            db.delete(
                    EncryptionKeyTables.EncryptionKeyContract.TABLE,
                    EncryptionKeyTables.EncryptionKeyContract.ID + " = ?",
                    new String[] {id});
        } catch (SQLException e) {
            sLogger.e(
                    "Failed to delete EncryptionKey in DB with id %s, error : ",
                    id, e.getMessage());
            return false;
        }
        return true;
    }
}
