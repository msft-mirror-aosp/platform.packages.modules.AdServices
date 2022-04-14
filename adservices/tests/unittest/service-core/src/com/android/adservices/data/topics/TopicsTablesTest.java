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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/** Unit tests for {@link com.android.adservices.data.topics.TopicsTables} */
public class TopicsTablesTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final DbHelper mDbHelper = DbHelper.getInstanceForTest(mContext);
    private final SQLiteDatabase mDb = mDbHelper.safeGetWritableDatabase();

    @Before
    public void setUp() throws Exception {
        if (mDb == null) throw new SQLException();
        // Erase all existing data.
        DbTestUtil.deleteTable(TopicsTables.AppClassificationTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.CallerCanLearnTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.TopTopicsContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.UsageHistoryContract.TABLE);
        DbTestUtil.deleteTable(TopicsTables.AppUsageHistoryContract.TABLE);
    }

    @Test
    public void testPersistAndGetAppClassificationTopicsContract() {
        final long epochId = 1L;
        final long taxonomyVersion = 1L;
        final int modelVersion = 1;
        final String app = "app";
        final String topic = "topic1";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.AppClassificationTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.AppClassificationTopicsContract.APP, app);
        values.put(TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION, taxonomyVersion);
        values.put(TopicsTables.AppClassificationTopicsContract.MODEL_VERSION, modelVersion);
        values.put(TopicsTables.AppClassificationTopicsContract.TOPIC, topic);

        try {
            mDb.insert(TopicsTables.AppClassificationTopicsContract.TABLE, null, values);
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to make DB transaction");
        }

        String[] projection = {
                TopicsTables.AppClassificationTopicsContract.APP,
                TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION,
                TopicsTables.AppClassificationTopicsContract.MODEL_VERSION,
                TopicsTables.AppClassificationTopicsContract.TOPIC,
        };

        String selection = TopicsTables.UsageHistoryContract.EPOCH_ID + " = ?";
        String[] selectionArgs = { String.valueOf(epochId) };

        try (
            Cursor cursor =
                    mDb.query(/* distinct = */true,
                            TopicsTables.AppClassificationTopicsContract.TABLE, projection,
                            selection,
                            selectionArgs, null, null,
                            null, null)
        )  {
            assertThat(cursor.moveToNext()).isTrue();
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.AppClassificationTopicsContract.APP))).isEqualTo(app);
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow(
                    TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION)))
                    .isEqualTo(taxonomyVersion);
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow(
                    TopicsTables.AppClassificationTopicsContract.MODEL_VERSION)))
                    .isEqualTo(modelVersion);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.AppClassificationTopicsContract.TOPIC))).isEqualTo(topic);
            assertThat(cursor.moveToNext()).isFalse();
        }
    }

    @Test
    public void testAppClassificationTopicsContractSchema_notNull() {
        final long epochId = 1L;
        final long taxonomyVersion = 1L;
        final int modelVersion = 1;
        final String app = "app";
        final String topic = "topic1";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.AppClassificationTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.AppClassificationTopicsContract.APP, app);
        values.put(TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION, taxonomyVersion);
        values.put(TopicsTables.AppClassificationTopicsContract.MODEL_VERSION, modelVersion);
        values.put(TopicsTables.AppClassificationTopicsContract.TOPIC, topic);

        values.put(TopicsTables.AppClassificationTopicsContract.EPOCH_ID, (Long) null);
        assertThat(mDb.insert(
                TopicsTables.AppClassificationTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.AppClassificationTopicsContract.EPOCH_ID, epochId);

        values.put(TopicsTables.AppClassificationTopicsContract.APP, (String) null);
        assertThat(mDb.insert(
                TopicsTables.AppClassificationTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.AppClassificationTopicsContract.APP, app);

        values.put(TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION, (Long) null);
        assertThat(mDb.insert(
                TopicsTables.AppClassificationTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.AppClassificationTopicsContract.TAXONOMY_VERSION,
                taxonomyVersion);

        values.put(TopicsTables.AppClassificationTopicsContract.MODEL_VERSION, (Integer) null);
        assertThat(mDb.insert(
                TopicsTables.AppClassificationTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.AppClassificationTopicsContract.MODEL_VERSION,
                modelVersion);

        values.put(TopicsTables.AppClassificationTopicsContract.TOPIC, (String) null);
        assertThat(mDb.insert(
                TopicsTables.AppClassificationTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.AppClassificationTopicsContract.TOPIC, topic);
    }

    @Test
    public void testPersistAndGetCallerCanLearnTopicsContract() {
        final long epochId = 1L;
        final String caller = "caller";
        final String topic = "topic1";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.CallerCanLearnTopicsContract.CALLER, caller);
        values.put(TopicsTables.CallerCanLearnTopicsContract.TOPIC, topic);

        try {
            mDb.insert(TopicsTables.CallerCanLearnTopicsContract.TABLE, null, values);
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to make DB transaction");
        }

        String[] projection = {
                TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID,
                TopicsTables.CallerCanLearnTopicsContract.CALLER,
                TopicsTables.CallerCanLearnTopicsContract.TOPIC,
        };

        String selection = TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID + " = ?";
        String[] selectionArgs = { String.valueOf(epochId) };

        try (
                Cursor cursor =
                        mDb.query(/* distinct = */true,
                                TopicsTables.CallerCanLearnTopicsContract.TABLE, projection,
                                selection,
                                selectionArgs, null, null,
                                null, null)
        )  {
            assertThat(cursor.moveToNext()).isTrue();
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.CallerCanLearnTopicsContract.CALLER))).isEqualTo(caller);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.CallerCanLearnTopicsContract.TOPIC))).isEqualTo(topic);
            assertThat(cursor.moveToNext()).isFalse();
        }
    }

    @Test
    public void testCallerCanLearnTopicsContractSchema_notNull() {
        final long epochId = 1L;
        final String caller = "caller";
        final String topic = "topic1";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.CallerCanLearnTopicsContract.CALLER, caller);
        values.put(TopicsTables.CallerCanLearnTopicsContract.TOPIC, topic);

        values.put(TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID, (Long) null);
        assertThat(mDb.insert(
                TopicsTables.CallerCanLearnTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID, epochId);

        values.put(TopicsTables.CallerCanLearnTopicsContract.CALLER, (String) null);
        assertThat(mDb.insert(
                TopicsTables.CallerCanLearnTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.CallerCanLearnTopicsContract.CALLER, caller);

        values.put(TopicsTables.CallerCanLearnTopicsContract.TOPIC, (String) null);
        assertThat(mDb.insert(
                TopicsTables.CallerCanLearnTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.CallerCanLearnTopicsContract.TOPIC, topic);
    }

    @Test
    public void testPersistAndGetTopTopicsContract() {
        final long epochId = 1L;
        final String topic1 = "topic1";
        final String topic2 = "topic2";
        final String topic3 = "topic3";
        final String topic4 = "topic4";
        final String topic5 = "topic5";
        final String randomTopic = "random_topic";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.TopTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.TopTopicsContract.TOPIC1, topic1);
        values.put(TopicsTables.TopTopicsContract.TOPIC2, topic2);
        values.put(TopicsTables.TopTopicsContract.TOPIC3, topic3);
        values.put(TopicsTables.TopTopicsContract.TOPIC4, topic4);
        values.put(TopicsTables.TopTopicsContract.TOPIC5, topic5);
        values.put(TopicsTables.TopTopicsContract.RANDOM_TOPIC, randomTopic);

        try {
            mDb.insert(TopicsTables.TopTopicsContract.TABLE, null, values);
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to make DB transaction");
        }

        String[] projection = {
                TopicsTables.TopTopicsContract.EPOCH_ID,
                TopicsTables.TopTopicsContract.TOPIC1,
                TopicsTables.TopTopicsContract.TOPIC2,
                TopicsTables.TopTopicsContract.TOPIC3,
                TopicsTables.TopTopicsContract.TOPIC4,
                TopicsTables.TopTopicsContract.TOPIC5,
                TopicsTables.TopTopicsContract.RANDOM_TOPIC
        };

        String selection = TopicsTables.CallerCanLearnTopicsContract.EPOCH_ID + " = ?";
        String[] selectionArgs = { String.valueOf(epochId) };

        try (
                Cursor cursor =
                        mDb.query(/* distinct = */true,
                                TopicsTables.TopTopicsContract.TABLE, projection,
                                selection,
                                selectionArgs, null, null,
                                null, null)
        )  {
            assertThat(cursor.moveToNext()).isTrue();
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.TopTopicsContract.TOPIC1))).isEqualTo(topic1);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.TopTopicsContract.TOPIC2))).isEqualTo(topic2);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.TopTopicsContract.TOPIC3))).isEqualTo(topic3);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.TopTopicsContract.TOPIC4))).isEqualTo(topic4);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.TopTopicsContract.TOPIC5))).isEqualTo(topic5);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.TopTopicsContract.RANDOM_TOPIC))).isEqualTo(randomTopic);
            assertThat(cursor.moveToNext()).isFalse();
        }
    }

    @Test
    public void testTopTopicsContractContractSchema_notNull() {
        final long epochId = 1L;
        final String topic1 = "topic1";
        final String topic2 = "topic2";
        final String topic3 = "topic3";
        final String topic4 = "topic4";
        final String topic5 = "topic5";
        final String randomTopic = "random_topic";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.TopTopicsContract.EPOCH_ID, epochId);
        values.put(TopicsTables.TopTopicsContract.TOPIC1, topic1);
        values.put(TopicsTables.TopTopicsContract.TOPIC2, topic2);
        values.put(TopicsTables.TopTopicsContract.TOPIC3, topic3);
        values.put(TopicsTables.TopTopicsContract.TOPIC4, topic4);
        values.put(TopicsTables.TopTopicsContract.TOPIC5, topic5);
        values.put(TopicsTables.TopTopicsContract.RANDOM_TOPIC, randomTopic);

        values.put(TopicsTables.TopTopicsContract.TOPIC1, (String) null);
        assertThat(mDb.insert(
                TopicsTables.TopTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.TopTopicsContract.TOPIC1, topic1);

        values.put(TopicsTables.TopTopicsContract.TOPIC2, (String) null);
        assertThat(mDb.insert(
                TopicsTables.TopTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.TopTopicsContract.TOPIC2, topic2);

        values.put(TopicsTables.TopTopicsContract.TOPIC3, (String) null);
        assertThat(mDb.insert(
                TopicsTables.TopTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.TopTopicsContract.TOPIC3, topic3);

        values.put(TopicsTables.TopTopicsContract.TOPIC4, (String) null);
        assertThat(mDb.insert(
                TopicsTables.TopTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.TopTopicsContract.TOPIC4, topic4);

        values.put(TopicsTables.TopTopicsContract.TOPIC5, (String) null);
        assertThat(mDb.insert(
                TopicsTables.TopTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.TopTopicsContract.TOPIC5, topic5);

        values.put(TopicsTables.TopTopicsContract.RANDOM_TOPIC, (String) null);
        assertThat(mDb.insert(
                TopicsTables.TopTopicsContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.TopTopicsContract.RANDOM_TOPIC, randomTopic);
    }

    @Test
    public void testPersistAndGetReturnedTopicContract() {
        final long epochId = 1L;
        final long taxonomyVersion = 1L;
        final int modelVersion = 1;
        final String app = "app";
        final String sdk = "sdk";
        final String topic = "topic1";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.ReturnedTopicContract.EPOCH_ID, epochId);
        values.put(TopicsTables.ReturnedTopicContract.APP, app);
        values.put(TopicsTables.ReturnedTopicContract.SDK, sdk);
        values.put(TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION, taxonomyVersion);
        values.put(TopicsTables.ReturnedTopicContract.MODEL_VERSION, modelVersion);
        values.put(TopicsTables.ReturnedTopicContract.TOPIC, topic);

        try {
            mDb.insert(TopicsTables.ReturnedTopicContract.TABLE, null, values);
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to make DB transaction");
        }

        String[] projection = {
                TopicsTables.ReturnedTopicContract.APP,
                TopicsTables.ReturnedTopicContract.SDK,
                TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION,
                TopicsTables.ReturnedTopicContract.MODEL_VERSION,
                TopicsTables.ReturnedTopicContract.TOPIC,
        };

        String selection = TopicsTables.ReturnedTopicContract.EPOCH_ID + " = ?";
        String[] selectionArgs = { String.valueOf(epochId) };

        try (
                Cursor cursor =
                        mDb.query(/* distinct = */true,
                                TopicsTables.ReturnedTopicContract.TABLE, projection,
                                selection,
                                selectionArgs, null, null,
                                null, null)
        )  {
            assertThat(cursor.moveToNext()).isTrue();
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.ReturnedTopicContract.APP))).isEqualTo(app);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.ReturnedTopicContract.SDK))).isEqualTo(sdk);
            assertThat(cursor.getLong(cursor.getColumnIndexOrThrow(
                    TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION)))
                    .isEqualTo(taxonomyVersion);
            assertThat(cursor.getInt(cursor.getColumnIndexOrThrow(
                    TopicsTables.ReturnedTopicContract.MODEL_VERSION)))
                    .isEqualTo(modelVersion);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.ReturnedTopicContract.TOPIC))).isEqualTo(topic);
            assertThat(cursor.moveToNext()).isFalse();
        }
    }

    @Test
    public void testReturnedTopicContractSchema_notNull() {
        final long epochId = 1L;
        final long taxonomyVersion = 1L;
        final int modelVersion = 1;
        final String app = "app";
        final String sdk = "sdk";
        final String topic = "topic1";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.ReturnedTopicContract.EPOCH_ID, epochId);
        values.put(TopicsTables.ReturnedTopicContract.APP, app);
        values.put(TopicsTables.ReturnedTopicContract.SDK, sdk);
        values.put(TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION, taxonomyVersion);
        values.put(TopicsTables.ReturnedTopicContract.MODEL_VERSION, modelVersion);
        values.put(TopicsTables.ReturnedTopicContract.TOPIC, topic);

        values.put(TopicsTables.ReturnedTopicContract.EPOCH_ID, (Long) null);
        assertThat(mDb.insert(
                TopicsTables.ReturnedTopicContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.ReturnedTopicContract.EPOCH_ID, epochId);

        values.put(TopicsTables.ReturnedTopicContract.APP, (String) null);
        assertThat(mDb.insert(
                TopicsTables.ReturnedTopicContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.ReturnedTopicContract.APP, app);

        values.put(TopicsTables.ReturnedTopicContract.SDK, (String) null);
        assertThat(mDb.insert(
                TopicsTables.ReturnedTopicContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.ReturnedTopicContract.SDK, sdk);

        values.put(TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION, (Long) null);
        assertThat(mDb.insert(
                TopicsTables.ReturnedTopicContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.ReturnedTopicContract.TAXONOMY_VERSION,
                taxonomyVersion);

        values.put(TopicsTables.ReturnedTopicContract.MODEL_VERSION, (Integer) null);
        assertThat(mDb.insert(
                TopicsTables.ReturnedTopicContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.ReturnedTopicContract.MODEL_VERSION,
                modelVersion);

        values.put(TopicsTables.ReturnedTopicContract.TOPIC, (String) null);
        assertThat(mDb.insert(
                TopicsTables.ReturnedTopicContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.ReturnedTopicContract.TOPIC, topic);
    }

    @Test
    public void testPersistAndGetUsageHistoryContract() {
        final long epochId = 1L;
        final String app = "app";
        final String sdk = "sdk";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.UsageHistoryContract.EPOCH_ID, epochId);
        values.put(TopicsTables.UsageHistoryContract.SDK, sdk);
        values.put(TopicsTables.UsageHistoryContract.APP, app);

        try {
            mDb.insert(TopicsTables.UsageHistoryContract.TABLE, null, values);
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to make DB transaction");
        }

        String[] projection = {
                TopicsTables.UsageHistoryContract.EPOCH_ID,
                TopicsTables.UsageHistoryContract.APP,
                TopicsTables.UsageHistoryContract.SDK,
        };

        String selection = TopicsTables.UsageHistoryContract.EPOCH_ID + " = ?";
        String[] selectionArgs = { String.valueOf(epochId) };

        try (
                Cursor cursor =
                        mDb.query(/* distinct = */true,
                                TopicsTables.UsageHistoryContract.TABLE, projection,
                                selection,
                                selectionArgs, null, null,
                                null, null)
        )  {
            assertThat(cursor.moveToNext()).isTrue();
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.UsageHistoryContract.APP))).isEqualTo(app);
            assertThat(cursor.getString(cursor.getColumnIndexOrThrow(
                    TopicsTables.UsageHistoryContract.SDK))).isEqualTo(sdk);
            assertThat(cursor.moveToNext()).isFalse();
        }
    }

    @Test
    public void testUsageHistoryContractSchema() {
        final long epochId = 1L;
        final String app = "app";
        final String sdk = "sdk";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.UsageHistoryContract.EPOCH_ID, epochId);
        values.put(TopicsTables.UsageHistoryContract.APP, app);
        values.put(TopicsTables.UsageHistoryContract.SDK, sdk);

        values.put(TopicsTables.UsageHistoryContract.EPOCH_ID, (Long) null);
        assertThat(mDb.insert(
                TopicsTables.UsageHistoryContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.UsageHistoryContract.EPOCH_ID, epochId);

        values.put(TopicsTables.UsageHistoryContract.APP, (String) null);
        assertThat(mDb.insert(
                TopicsTables.UsageHistoryContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.UsageHistoryContract.APP, app);

        // SDK is nullable
        values.put(TopicsTables.UsageHistoryContract.SDK, (String) null);
        assertThat(mDb.insert(
                TopicsTables.UsageHistoryContract.TABLE, null, values))
                .isNotEqualTo(-1);
        values.put(TopicsTables.UsageHistoryContract.SDK, sdk);
    }

    @Test
    public void testPersistAndGetAppUsageHistoryContract() {
        final long epochId1 = 1L;
        final long epochId2 = 2L;
        final String app1 = "app1";
        final String app2 = "app2";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.AppUsageHistoryContract.EPOCH_ID, epochId1);
        values.put(TopicsTables.AppUsageHistoryContract.APP, app1);

        try {
            // Insert same app multiple times
            mDb.insert(TopicsTables.AppUsageHistoryContract.TABLE, null, values);
            mDb.insert(TopicsTables.AppUsageHistoryContract.TABLE, null, values);

            // Insert different app
            values.put(TopicsTables.AppUsageHistoryContract.APP, app2);
            mDb.insert(TopicsTables.AppUsageHistoryContract.TABLE, null, values);

            // Insert different epoch
            values.put(TopicsTables.AppUsageHistoryContract.EPOCH_ID, epochId2);
            mDb.insert(TopicsTables.AppUsageHistoryContract.EPOCH_ID, null, values);
        } catch (SQLException e) {
            LogUtil.e(e, "Failed to make DB transaction");
        }

        String[] projection = {
                TopicsTables.AppUsageHistoryContract.EPOCH_ID,
                TopicsTables.AppUsageHistoryContract.APP,
        };

        String selection = TopicsTables.AppUsageHistoryContract.EPOCH_ID + " = ?";
        String[] selectionArgs1 = { String.valueOf(epochId1) },
                selectionArgs2 = { String.valueOf(epochId2) };

        // Test Epoch 1
        Map<String, Integer> expectedAppUsageMap1 = new HashMap<>();
        Map<String, Integer> appUsageMapFromDB1 = new HashMap<>();
        expectedAppUsageMap1.put("app1", 2);
        expectedAppUsageMap1.put("app2", 1);
        try (
                Cursor cursor =
                        mDb.query(/* distinct = */false,
                                TopicsTables.AppUsageHistoryContract.TABLE, projection,
                                selection,
                                selectionArgs1, null, null,
                                null, null)
        )  {
            while (cursor.moveToNext()) {
                String app = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.AppUsageHistoryContract.APP));
                appUsageMapFromDB1.put(app, appUsageMapFromDB1.getOrDefault(app, 0) + 1);
            }
        }
        assertThat(appUsageMapFromDB1).isEqualTo(expectedAppUsageMap1);

        // Test Epoch 2
        Map<String, Integer> expectedAppUsageMap2 = new HashMap<>();
        Map<String, Integer> appUsageMapFromDB2 = new HashMap<>();
        expectedAppUsageMap1.put("app2", 1);
        try (
                Cursor cursor =
                        mDb.query(/* distinct = */false,
                                TopicsTables.AppUsageHistoryContract.TABLE, projection,
                                selection,
                                selectionArgs2, null, null,
                                null, null)
        )  {
            while (cursor.moveToNext()) {
                String app = cursor.getString(cursor.getColumnIndexOrThrow(
                        TopicsTables.AppUsageHistoryContract.APP));
                appUsageMapFromDB1.put(app, appUsageMapFromDB1.getOrDefault(app, 0) + 1);
            }
        }
        assertThat(appUsageMapFromDB2).isEqualTo(expectedAppUsageMap2);

    }

    @Test
    public void testUsageHistoryAppOnlyContractSchema_notNull() {
        final long epochId = 1L;
        final String app = "app";
        ContentValues values = new ContentValues();
        values.put(TopicsTables.AppUsageHistoryContract.EPOCH_ID, epochId);
        values.put(TopicsTables.AppUsageHistoryContract.APP, app);

        values.put(TopicsTables.AppUsageHistoryContract.EPOCH_ID, (Long) null);
        assertThat(mDb.insert(
                TopicsTables.AppUsageHistoryContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.AppUsageHistoryContract.EPOCH_ID, epochId);

        values.put(TopicsTables.AppUsageHistoryContract.APP, (String) null);
        assertThat(mDb.insert(
                TopicsTables.AppUsageHistoryContract.TABLE, null, values))
                .isEqualTo(-1);
        values.put(TopicsTables.AppUsageHistoryContract.APP, app);
    }
}
