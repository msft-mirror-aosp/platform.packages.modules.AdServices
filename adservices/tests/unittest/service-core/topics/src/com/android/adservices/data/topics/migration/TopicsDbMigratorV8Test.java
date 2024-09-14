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

package com.android.adservices.data.topics.migration;

import static com.google.common.truth.Truth.assertThat;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link TopicsDbMigratorV8} */
@MockStatic(FlagsFactory.class)
public final class TopicsDbMigratorV8Test extends AdServicesExtendedMockitoTestCase {

    // The database is created with V7 and will migrate to V8.
    private final TopicsDbHelperV7 mTopicsDbHelper = TopicsDbHelperV7.getInstance();

    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testDbMigrationFromV7ToV8() {
        // Enable DB Schema Flag
        SQLiteDatabase db = mTopicsDbHelper.getWritableDatabase();

        // ReturnedTopic table doesn't have logged_topic column in V7.
        assertThat(DbTestUtil.getTableColumns(db, TopicsTables.ReturnedTopicContract.TABLE))
                .doesNotContain(TopicsTables.ReturnedTopicContract.LOGGED_TOPIC);

        // Use transaction here so that the changes in the performMigration is committed.
        // In normal DB upgrade, the DB will commit the change automatically.
        db.beginTransaction();
        // Upgrade the db V8 by using TopicsDbMigratorV8
        new TopicsDbMigratorV8().performMigration(db);
        // Commit the schema change
        db.setTransactionSuccessful();
        db.endTransaction();

        // ReturnedTopic table has logged_topic column in V8.
        assertThat(DbTestUtil.getTableColumns(db, TopicsTables.ReturnedTopicContract.TABLE))
                .contains(TopicsTables.ReturnedTopicContract.LOGGED_TOPIC);
        assertThat(
                        DbTestUtil.doesTableExistAndColumnCountMatch(
                                db, TopicsTables.ReturnedTopicContract.TABLE, /* columnCount= */ 8))
                .isTrue();
        // Insert two logged_topic (10001 and 10005) into ReturnedTopic table.
        insertTestLoggedTopic(
                db,
                TopicsTables.ReturnedTopicContract.TABLE);
        // Verify that the data can be read correctly from the table.
        assertThat(
                getAllValuesFromSelectedColumn(
                        db,
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.LOGGED_TOPIC))
                .isEqualTo(Arrays.asList("10001", "10005"));
        assertThat(
                getAllValuesFromSelectedColumn(
                        db,
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.APP))
                .isEqualTo(Arrays.asList("app1", "app2"));

        // Test column logged_topic in ReturnedTopic table is cleared when upgrading.
        db.beginTransaction();
        new TopicsDbMigratorV8().performMigration(db);
        // Commit the schema change
        db.setTransactionSuccessful();
        db.endTransaction();
        assertThat(
                getAllValuesFromSelectedColumn(
                        db,
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.LOGGED_TOPIC))
                .isEqualTo(Arrays.asList(null, null));
        assertThat(
                getAllValuesFromSelectedColumn(
                        db,
                        TopicsTables.ReturnedTopicContract.TABLE,
                        TopicsTables.ReturnedTopicContract.APP))
                .isEqualTo(Arrays.asList("app1", "app2"));
    }

    private void insertTestLoggedTopic(SQLiteDatabase db, String tableName) {
        String returnedTopicTableColumns =
                "epoch_id, app, sdk, taxonomy_version, model_version, topic, logged_topic";
        String[] QUERIES_TO_PERFORM = {
                String.format(
                        "INSERT INTO %1$s (%2$s) VALUES (100, 'app1', 'sdk1', 2, 4, 10001, 10001);",
                        tableName, returnedTopicTableColumns),
                String.format(
                        "INSERT INTO %1$s (%2$s) VALUES (101, 'app2', 'sdk2', 2, 4, 10005, 10005);",
                        tableName, returnedTopicTableColumns),
        };
        for (String query : QUERIES_TO_PERFORM) {
            db.execSQL(query);
        }
    }

    private List<String> getAllValuesFromSelectedColumn(
            SQLiteDatabase db, String tableName, String columnName){
        List<String> allValues = new ArrayList<>();
        Cursor cursor = db.rawQuery(
                String.format("SELECT %1$s FROM %2$s", columnName, tableName), null);
        cursor.moveToFirst();

        for(int i = 0; i < cursor.getCount(); i++) {
            allValues.add(cursor.getString(cursor.getColumnIndex(columnName)));
            cursor.moveToNext();
        }
        return allValues;
    }
}
