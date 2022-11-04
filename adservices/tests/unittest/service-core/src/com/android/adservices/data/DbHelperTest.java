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

import static com.android.adservices.data.DbHelper.CURRENT_DATABASE_VERSION;
import static com.android.adservices.data.DbHelper.DATABASE_VERSION_V3;
import static com.android.adservices.data.DbTestUtil.doesIndexExist;
import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.data.DbTestUtil.getDatabaseNameForTest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.topics.migration.TopicDbMigratorV3;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class DbHelperTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    private MockitoSession mStaticMockSession;

    @Mock private Flags mMockFlags;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.WARN)
                        .startMocking();
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testOnCreate() {
        SQLiteDatabase db = DbTestUtil.getDbHelperForTest().safeGetReadableDatabase();
        assertNotNull(db);
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_taxonomy", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_app_classification_topics", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_caller_can_learn_topic", 6));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_top_topics", 10));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_returned_topics", 7));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch(db, "topics_app_usage_history", 3));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_source", 22));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_trigger", 13));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_async_registration_contract", 16));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_event_report", 17));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_attribution", 10));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_report", 14));
        assertTrue(doesTableExistAndColumnCountMatch(db, "msmt_aggregate_encryption_key", 4));
        assertTrue(doesTableExistAndColumnCountMatch(db, "enrollment_data", 8));
        assertTrue(doesIndexExist(db, "idx_msmt_source_ad_ei_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_p_ad_wd_s_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_ad_ei_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_source_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_ss_so_ds_do_ei_tt"));
    }

    @Test
    public void testGetDbFileSize() {
        final String databaseName = "testsize.db";
        DbHelper dbHelper = new DbHelper(sContext, databaseName, 1);

        // Create database
        dbHelper.getReadableDatabase();

        // Verify size should be more than 0 bytes as database was created
        Assert.assertTrue(dbHelper.getDbFileSize() > 0);

        // Delete database file
        sContext.getDatabasePath(databaseName).delete();

        // Verify database does not exist anymore
        Assert.assertEquals(-1, dbHelper.getDbFileSize());
    }

    @Test
    public void onOpen_appliesForeignKeyConstraint() {
        // dbHelper.onOpen gets called implicitly
        SQLiteDatabase db = DbTestUtil.getDbHelperForTest().safeGetReadableDatabase();
        try (Cursor cursor = db.rawQuery("PRAGMA foreign_keys", null)) {
            cursor.moveToNext();
            assertEquals(1, cursor.getLong(0));
        }
    }

    @Test
    public void testOnUpgrade_topicsV3Migration() {
        DbHelper dbHelper = spy(DbTestUtil.getDbHelperForTest());
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        // Do not actually perform queries but verify the invocation.
        TopicDbMigratorV3 topicDbMigratorV3 = Mockito.spy(new TopicDbMigratorV3());
        Mockito.doNothing().when(topicDbMigratorV3).performMigration(db);

        // Ignore Measurement Migrators
        doReturn(List.of()).when(dbHelper).getOrderedDbMigrators();
        doReturn(List.of(topicDbMigratorV3)).when(dbHelper).topicsGetOrderedDbMigrators();

        // Negative case - target version 3 is not in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 2);
        Mockito.verify(topicDbMigratorV3, Mockito.never()).performMigration(db);

        // Positive case - target version 3 is in (oldVersion, newVersion]
        dbHelper.onUpgrade(db, /* oldVersion */ 1, /* new Version */ 3);
        Mockito.verify(topicDbMigratorV3).performMigration(db);
    }

    @Test
    public void testSupportsTopicContributorsTable() {
        DbHelper dbHelperV2 = new DbHelper(sContext, getDatabaseNameForTest(), /* dbVersion*/ 2);
        assertThat(dbHelperV2.supportsTopicContributorsTable()).isFalse();

        DbHelper dbHelperV3 = new DbHelper(sContext, getDatabaseNameForTest(), /* dbVersion*/ 3);
        assertThat(dbHelperV3.supportsTopicContributorsTable()).isTrue();
    }

    @Test
    public void testGetDatabaseVersionToCreate() {
        // Test feature flag is off
        when(mMockFlags.getEnableDatabaseSchemaVersion3()).thenReturn(false);
        assertThat(DbHelper.getDatabaseVersionToCreate()).isEqualTo(CURRENT_DATABASE_VERSION);

        // Test feature flag is on
        when(mMockFlags.getEnableDatabaseSchemaVersion3()).thenReturn(true);
        assertThat(DbHelper.getDatabaseVersionToCreate()).isEqualTo(DATABASE_VERSION_V3);
    }
}
