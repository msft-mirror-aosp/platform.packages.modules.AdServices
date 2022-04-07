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

import static org.junit.Assert.assertEquals;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class MeasurementDaoTest {

    private static final String TAG = "MeasurementDaoTest";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private final Uri mAppTwoSources = Uri.parse("android-app://com.example1.two-sources");
    private final Uri mAppOneSource = Uri.parse("android-app://com.example2.one-source");
    private final Uri mAppNoSources = Uri.parse("android-app://com.example3.no-sources");
    private final Uri mAppTwoTriggers = Uri.parse("android-app://com.example1.two-triggers");
    private final Uri mAppOneTrigger = Uri.parse("android-app://com.example1.one-trigger");
    private final Uri mAppNoTriggers = Uri.parse("android-app://com.example1.no-triggers");

    @Before
    public void before() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<Source> sourcesList = new ArrayList<>();
        sourcesList.add(new Source.Builder()
                .setId("S1")
                .setRegistrant(mAppTwoSources)
                .build());
        sourcesList.add(new Source.Builder()
                .setId("S2")
                .setRegistrant(mAppTwoSources)
                .build());
        sourcesList.add(new Source.Builder()
                .setId("S3")
                .setRegistrant(mAppOneSource)
                .build());
        for (Source source : sourcesList) {
            ContentValues values = new ContentValues();
            values.put("_id", source.getId());
            values.put("registrant", source.getRegistrant().toString());
            long row = db.insert("msmt_source", null, values);
            Assert.assertNotEquals("Source insertion failed", -1, row);
        }
        List<Trigger> triggersList = new ArrayList<>();
        triggersList.add(new Trigger.Builder()
                .setId("T1")
                .setRegistrant(mAppTwoTriggers)
                .build());
        triggersList.add(new Trigger.Builder()
                .setId("T2")
                .setRegistrant(mAppTwoTriggers)
                .build());
        triggersList.add(new Trigger.Builder()
                .setId("T3")
                .setRegistrant(mAppOneTrigger)
                .build());
        for (Trigger trigger : triggersList) {
            ContentValues values = new ContentValues();
            values.put("_id", trigger.getId());
            values.put("registrant", trigger.getRegistrant().toString());
            long row = db.insert("msmt_trigger", null, values);
            Assert.assertNotEquals("Trigger insertion failed", -1, row);
        }
    }

    @After
    public void after() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        db.delete("msmt_source", null, null);
        db.delete("msmt_trigger", null, null);
    }

    @Test
    public void testGetNumSourcesPerRegistrant() {
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(measurementDao -> {
            assertEquals(2, measurementDao
                    .getNumSourcesPerRegistrant(mAppTwoSources));
        });
        dm.runInTransaction(measurementDao -> {
            assertEquals(1, measurementDao
                    .getNumSourcesPerRegistrant(mAppOneSource));
        });
        dm.runInTransaction(measurementDao -> {
            assertEquals(0, measurementDao
                    .getNumSourcesPerRegistrant(mAppNoSources));
        });
    }

    @Test
    public void testGetNumTriggersPerRegistrant() {
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(measurementDao -> {
            assertEquals(2, measurementDao
                    .getNumTriggersPerRegistrant(mAppTwoTriggers));
        });
        dm.runInTransaction(measurementDao -> {
            assertEquals(1, measurementDao
                    .getNumTriggersPerRegistrant(mAppOneTrigger));
        });
        dm.runInTransaction(measurementDao -> {
            assertEquals(0, measurementDao
                    .getNumTriggersPerRegistrant(mAppNoTriggers));
        });
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteMeasurementData_requiredRegistrantAsNull() {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
            dao.deleteMeasurementData(
                    null /* registrant */, null /* origin */,
                    null /* start */, null /* end */);
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeNoStartDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
            dao.deleteMeasurementData(
                    mAppOneSource, null /* origin */, null /* start */, Instant.now());
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeNoEndDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
            dao.deleteMeasurementData(
                    mAppOneSource, null /* origin */, Instant.now(), null /* end */);
        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeStartAfterEndDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
            dao.deleteMeasurementData(
                    mAppOneSource, null /* origin */, Instant.now().plusMillis(1), Instant.now());
        });
    }
}
