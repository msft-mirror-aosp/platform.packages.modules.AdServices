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

package com.android.adservices.data.topics;

import android.annotation.NonNull;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbHelper;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Data Access Object for the Topics API.
 */
public final class TopicsDao {
    private static TopicsDao sSingleton;

    @SuppressWarnings("unused")
    private final DbHelper mDbHelper; // Used in tests.

    private TopicsDao(DbHelper dbHelper) {
        mDbHelper = dbHelper;
    }

    /** Returns an instance of the TopicsDAO given a context. */
    @NonNull
    public static TopicsDao getInstance(@NonNull Context ctx) {
        synchronized (TopicsDao.class) {
            if (sSingleton == null) {
                sSingleton = new TopicsDao(DbHelper.getInstance(ctx));
            }
            return sSingleton;
        }
    }

    /** Returns an instance of the TopicsDao given a context. This is used for testing only. */
    @VisibleForTesting
    @NonNull
    static TopicsDao getInstanceForTest(@NonNull Context ctx) {
        synchronized (TopicsDao.class) {
            if (sSingleton == null) {
                sSingleton = new TopicsDao(DbHelper.getInstanceForTest(ctx));
            }
            return sSingleton;
        }
    }

    // Persist the list of Top Topics to DB.
    @VisibleForTesting
    void persistTopTopics(long epochId, @NonNull List<String> topTopics) {
        // topTopics the Top Topics: a list of 5 top topics and the 6th topic
        // which was selected randomly. We can refer this 6th topic as the random-topic.
        Objects.requireNonNull(topTopics);
        Preconditions.checkArgument(topTopics.size() == 6);

        SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
        if (db == null) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put(TopicsTables.TopTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.TopTopicsContract.TOPIC1, topTopics.get(0));
        values.put(TopicsTables.TopTopicsContract.TOPIC2, topTopics.get(1));
        values.put(TopicsTables.TopTopicsContract.TOPIC3, topTopics.get(2));
        values.put(TopicsTables.TopTopicsContract.TOPIC4, topTopics.get(3));
        values.put(TopicsTables.TopTopicsContract.TOPIC5, topTopics.get(4));
        values.put(TopicsTables.TopTopicsContract.RANDOM_TOPIC, topTopics.get(5));

        try {
            db.insert(TopicsTables.TopTopicsContract.TABLE, null, values);
        } catch (SQLException e) {
            LogUtil.e("Failed to persist Top Topics. Exception : " + e.getMessage());
        }

    }

    // Return the Top Topics. This will return a list of 5 top topics and the 6th topic
    // which was selected randomly. We can refer this 6th topic as the random-topic.
    @VisibleForTesting
    @NonNull
    List<String> getTopTopics(long epochId) {
        SQLiteDatabase db = mDbHelper.safeGetReadableDatabase();
        if (db == null) {
            return new ArrayList<>();
        }

        String[] projection = {
                TopicsTables.TopTopicsContract.TOPIC1,
                TopicsTables.TopTopicsContract.TOPIC2,
                TopicsTables.TopTopicsContract.TOPIC3,
                TopicsTables.TopTopicsContract.TOPIC4,
                TopicsTables.TopTopicsContract.TOPIC5,
                TopicsTables.TopTopicsContract.RANDOM_TOPIC,
        };

        String selection = TopicsTables.AppClassificationTopicsContract.EPOCH_ID + " = ?";
        String[] selectionArgs = { String.valueOf(epochId) };

        try (
                Cursor cursor = db.query(
                        TopicsTables.TopTopicsContract.TABLE,   // The table to query
                        projection,        // The array of columns to return (pass null to get all)
                        selection,         // The columns for the WHERE clause
                        selectionArgs,     // The values for the WHERE clause
                        null,      // don't group the rows
                        null,       // don't filter by row groups
                        null       // The sort order
                )) {
            if (cursor.moveToNext()) {
                String topic1 = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.TopTopicsContract.TOPIC1));
                String topic2 = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.TopTopicsContract.TOPIC2));
                String topic3 = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.TopTopicsContract.TOPIC3));
                String topic4 = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.TopTopicsContract.TOPIC4));
                String topic5 = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.TopTopicsContract.TOPIC5));
                String randomTopic = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.TopTopicsContract.RANDOM_TOPIC));
                return Arrays.asList(topic1, topic2, topic3, topic4, topic5, randomTopic);
            }
        }

        return new ArrayList<>();
    }
}
