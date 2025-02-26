/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.adservices.common.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.topics.migration.TopicsDbMigratorV7;
import com.android.adservices.data.topics.migration.TopicsDbMigratorV8;
import com.android.adservices.data.topics.migration.TopicsDbMigratorV9;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

@SpyStatic(FlagsFactory.class)
public final class DbHelperTopicsTest extends AdServicesExtendedMockitoTestCase {
    @Before
    public void setup() {
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testOnUpgrade_topicsV7Migration() {
        DbHelper dbHelper = spy(getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicsDbMigratorV7 topicsDbMigratorV7 = Mockito.spy(new TopicsDbMigratorV7());
        Mockito.doNothing().when(topicsDbMigratorV7).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(false).when(dbHelper).hasV1MeasurementTables(db);
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicsDbMigratorV7)).when(dbHelper).topicsGetOrderedDbMigrators();

        // Negative case - target version 5 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 5);
        Mockito.verify(topicsDbMigratorV7, Mockito.never()).performMigration(db);

        // Positive case - target version 5 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 7);
        Mockito.verify(topicsDbMigratorV7).performMigration(db);
    }

    @Test
    public void testOnUpgrade_topicsV8Migration() {
        DbHelper dbHelper = spy(getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicsDbMigratorV8 topicsDbMigratorV8 = Mockito.spy(new TopicsDbMigratorV8());
        Mockito.doNothing().when(topicsDbMigratorV8).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(false).when(dbHelper).hasV1MeasurementTables(db);
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicsDbMigratorV8)).when(dbHelper).topicsGetOrderedDbMigrators();

        // Negative case - target version 5 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 5);
        Mockito.verify(topicsDbMigratorV8, Mockito.never()).performMigration(db);

        // Positive case - target version 5 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 8);
        Mockito.verify(topicsDbMigratorV8).performMigration(db);
    }

    @Test
    public void testOnUpgrade_topicsV8Migration_loggedTopicColumnExist() {
        String dbName = FileCompatUtils.getAdservicesFilename("test_db");
        DbHelperV1 dbHelperV1 = new DbHelperV1(mContext, dbName, /* dbVersion */ 1);
        SQLiteDatabase db = dbHelperV1.safeGetWritableDatabase();

        assertEquals(1, db.getVersion());

        DbHelper dbHelper = new DbHelper(mContext, dbName, /* dbVersion */ 7);
        dbHelper.onUpgrade(db, /* oldDbVersion */ 7, /* newDbVersion */ 8);

        // ReturnTopics table should have 8 columns in version 8 database
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, "topics_returned_topics", /* columnCount */ 8));
    }

    @Test
    public void testOnUpgrade_topicsV9Migration() {
        DbHelper dbHelper = spy(getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicsDbMigratorV9 topicsDbMigratorV9 = Mockito.spy(new TopicsDbMigratorV9());
        Mockito.doNothing().when(topicsDbMigratorV9).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(false).when(dbHelper).hasV1MeasurementTables(db);
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicsDbMigratorV9)).when(dbHelper).topicsGetOrderedDbMigrators();

        // Negative case - target version 8 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 8);
        Mockito.verify(topicsDbMigratorV9, Mockito.never()).performMigration(db);

        // Positive case - target version 9 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV9, times(1)).performMigration(db);

        // Positive case - target version 9 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 8, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV9, times(2)).performMigration(db);

        // Don't expect interaction when we try upgrading from 9 to 9.
        dbHelper.onUpgrade(db, /* oldVersion */ 9, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV9, times(2)).performMigration(db);
    }

    @Test
    public void testOnUpgrade_topicsMigration_V7_V8_V9() {
        DbHelper dbHelper = spy(getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicsDbMigratorV7 topicsDbMigratorV7 = Mockito.spy(new TopicsDbMigratorV7());
        TopicsDbMigratorV8 topicsDbMigratorV8 = Mockito.spy(new TopicsDbMigratorV8());
        TopicsDbMigratorV9 topicsDbMigratorV9 = Mockito.spy(new TopicsDbMigratorV9());
        Mockito.doNothing().when(topicsDbMigratorV7).performMigration(db);
        Mockito.doNothing().when(topicsDbMigratorV8).performMigration(db);
        Mockito.doNothing().when(topicsDbMigratorV9).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(false).when(dbHelper).hasV1MeasurementTables(db);
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicsDbMigratorV7, topicsDbMigratorV8, topicsDbMigratorV9))
                .when(dbHelper)
                .topicsGetOrderedDbMigrators();

        // Negative case - target version 6 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 6);
        Mockito.verify(topicsDbMigratorV7, Mockito.never()).performMigration(db);
        Mockito.verify(topicsDbMigratorV8, Mockito.never()).performMigration(db);
        Mockito.verify(topicsDbMigratorV9, Mockito.never()).performMigration(db);

        // Positive case - 1 -> 9 should use all migrators
        dbHelper.onUpgrade(db, /* oldVersion */ 5, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV7, times(1)).performMigration(db);
        Mockito.verify(topicsDbMigratorV8, times(1)).performMigration(db);
        Mockito.verify(topicsDbMigratorV9, times(1)).performMigration(db);

        // Positive case - 6 -> 8 should use V7, V8 migrators only
        dbHelper.onUpgrade(db, /* oldVersion */ 6, /* new Version */ 8);
        Mockito.verify(topicsDbMigratorV7, times(2)).performMigration(db);
        Mockito.verify(topicsDbMigratorV8, times(2)).performMigration(db);
        Mockito.verify(topicsDbMigratorV9, times(1)).performMigration(db);

        // Positive case - 8 -> 9 should use V9 migrator only
        dbHelper.onUpgrade(db, /* oldVersion */ 8, /* new Version */ 9);
        Mockito.verify(topicsDbMigratorV7, times(2)).performMigration(db);
        Mockito.verify(topicsDbMigratorV8, times(2)).performMigration(db);
        Mockito.verify(topicsDbMigratorV9, times(2)).performMigration(db);
    }
}
