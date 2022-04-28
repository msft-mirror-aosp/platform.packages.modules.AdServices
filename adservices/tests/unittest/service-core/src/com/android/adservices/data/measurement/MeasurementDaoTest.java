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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class MeasurementDaoTest {

    private static final String TAG = "MeasurementDaoTest";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private final Uri mAppTwoSources = Uri.parse("android-app://com.example1.two-sources");
    private final Uri mAppOneSource = Uri.parse("android-app://com.example2.one-source");
    private final Uri mAppNoSources = Uri.parse("android-app://com.example3.no-sources");
    private final Uri mAppTwoTriggers = Uri.parse("android-app://com.example1.two-triggers");
    private final Uri mAppOneTrigger = Uri.parse("android-app://com.example1.one-trigger");
    private final Uri mAppNoTriggers = Uri.parse("android-app://com.example1.no-triggers");
    private final Uri mInstalledPackage = Uri.parse("android-app://com.example.installed");

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
    public void cleanup() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        db.delete("msmt_source", null, null);
        db.delete("msmt_trigger", null, null);
    }

    @Test
    public void testInsertSource() {
        cleanup();

        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertSource(
                        ValidSourceParams.SOURCE_EVENT_ID,
                        ValidSourceParams.sAttributionSource,
                        ValidSourceParams.sAttributionDestination,
                        ValidSourceParams.sReportTo,
                        ValidSourceParams.sRegistrant,
                        ValidSourceParams.SOURCE_EVENT_TIME,
                        ValidSourceParams.EXPIRY_TIME,
                        ValidSourceParams.PRIORITY,
                        ValidSourceParams.sSourceType
                )
        );

        try (Cursor sourceCursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.SourceContract.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(sourceCursor.moveToNext());
            Source source = SqliteObjectMapper.constructSourceFromCursor(sourceCursor);
            Assert.assertNotNull(source);
            Assert.assertNotNull(source.getId());
            assertEquals(ValidSourceParams.sAttributionSource, source.getAttributionSource());
            assertEquals(ValidSourceParams.sAttributionDestination,
                    source.getAttributionDestination());
            assertEquals(ValidSourceParams.sReportTo, source.getReportTo());
            assertEquals(ValidSourceParams.sRegistrant, source.getRegistrant());
            assertEquals(ValidSourceParams.SOURCE_EVENT_TIME.longValue(), source.getEventTime());
            assertEquals(ValidSourceParams.EXPIRY_TIME.longValue(), source.getExpiryTime());
            assertEquals(ValidSourceParams.PRIORITY.longValue(), source.getPriority());
            assertEquals(ValidSourceParams.sSourceType, source.getSourceType());
        }
    }

    @Test
    public void testInsertSource_validateArgumentSourceEventId() {
        assertInvalidSourceArguments(
                null,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);
    }

    @Test
    public void testInsertSource_validateArgumentAttributionSource() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                null,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);

        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                Uri.parse("com.source"),
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);
    }

    @Test
    public void testInsertSource_validateArgumentAttributionDestination() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                null,
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);

        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                Uri.parse("com.destination"),
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);
    }

    @Test
    public void testInsertSource_validateArgumentReportTo() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                null,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);

        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                Uri.parse("com.reportTo"),
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);
    }

    @Test
    public void testInsertSource_validateArgumentRegistrant() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                null,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);

        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                Uri.parse("com.registrant"),
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);
    }

    @Test
    public void testInsertSource_validateArgumentSourceEventTime() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                null,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);
    }

    @Test
    public void testInsertSource_validateArgumentSourceExpiryTime() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                null,
                ValidSourceParams.PRIORITY,
                ValidSourceParams.sSourceType);
    }

    @Test
    public void testInsertSource_validateArgumentPriority() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                null,
                ValidSourceParams.sSourceType);
    }

    @Test
    public void testInsertSource_validateArgumentSourceType() {
        assertInvalidSourceArguments(
                ValidSourceParams.SOURCE_EVENT_ID,
                ValidSourceParams.sAttributionSource,
                ValidSourceParams.sAttributionDestination,
                ValidSourceParams.sReportTo,
                ValidSourceParams.sRegistrant,
                ValidSourceParams.SOURCE_EVENT_TIME,
                ValidSourceParams.EXPIRY_TIME,
                ValidSourceParams.PRIORITY,
                null);
    }

    private void assertInvalidSourceArguments(Long sourceEventId, Uri attributionSource,
            Uri attributionDestination, Uri reportTo, Uri registrant, Long sourceEventTime,
            Long expiryTime, Long priority, Source.SourceType sourceType) {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
                    try {
                        dao.insertSource(
                                sourceEventId,
                                attributionSource,
                                attributionDestination,
                                reportTo,
                                registrant,
                                sourceEventTime,
                                expiryTime,
                                priority,
                                sourceType
                        );
                        fail();
                    } catch (DatastoreException e) {
                        // Valid Exception
                    }
                }
        );

    }

    @Test
    public void testInsertTrigger() {
        cleanup();

        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertTrigger(
                        ValidTriggerParams.sAttributionDestination,
                        ValidTriggerParams.sReportTo,
                        ValidTriggerParams.sRegistrant,
                        ValidTriggerParams.TRIGGER_TIME,
                        ValidTriggerParams.TRIGGER_DATA,
                        ValidTriggerParams.DEDUP_KEY,
                        ValidTriggerParams.PRIORITY
                )
        );

        try (Cursor sourceCursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.TriggerContract.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(sourceCursor.moveToNext());
            Trigger trigger = SqliteObjectMapper.constructTriggerFromCursor(sourceCursor);
            Assert.assertNotNull(trigger);
            Assert.assertNotNull(trigger.getId());
            assertEquals(ValidTriggerParams.sAttributionDestination,
                    trigger.getAttributionDestination());
            assertEquals(ValidTriggerParams.sReportTo, trigger.getReportTo());
            assertEquals(ValidTriggerParams.sRegistrant, trigger.getRegistrant());
            assertEquals(ValidTriggerParams.TRIGGER_TIME.longValue(), trigger.getTriggerTime());
            assertEquals(ValidTriggerParams.TRIGGER_DATA.longValue(), trigger.getTriggerData());
            assertEquals(ValidTriggerParams.DEDUP_KEY, trigger.getDedupKey());
            assertEquals(ValidTriggerParams.PRIORITY.longValue(), trigger.getPriority());
        }
    }

    @Test
    public void testInsertTrigger_validateArgumentAttributionDestination() {
        assertInvalidTriggerArguments(
                null,
                ValidTriggerParams.sReportTo,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY);

        assertInvalidTriggerArguments(
                Uri.parse("com.destination"),
                ValidTriggerParams.sReportTo,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY);
    }

    @Test
    public void testInsertTrigger_validateArgumentReportTo() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                null,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY);

        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                Uri.parse("com.reportTo"),
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY);
    }

    @Test
    public void testInsertTrigger_validateArgumentRegistrant() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sReportTo,
                null,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY);

        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sReportTo,
                Uri.parse("com.registrant"),
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY);
    }

    @Test
    public void testInsertTrigger_validateArgumentTriggerTime() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sReportTo,
                ValidTriggerParams.sRegistrant,
                null,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY);
    }

    @Test
    public void testInsertTrigger_validateArgumentTriggerData() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sReportTo,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                null,
                ValidTriggerParams.DEDUP_KEY,
                ValidTriggerParams.PRIORITY);
    }

    @Test
    public void testInsertTrigger_validateArgumentDedupKey() {
        cleanup();

        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertTrigger(
                        ValidTriggerParams.sAttributionDestination,
                        ValidTriggerParams.sReportTo,
                        ValidTriggerParams.sRegistrant,
                        ValidTriggerParams.TRIGGER_TIME,
                        ValidTriggerParams.TRIGGER_DATA,
                        null,
                        ValidTriggerParams.PRIORITY
                )
        );

        try (Cursor sourceCursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.TriggerContract.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(sourceCursor.moveToNext());
            Trigger trigger = SqliteObjectMapper.constructTriggerFromCursor(sourceCursor);
            Assert.assertNotNull(trigger);
            Assert.assertNotNull(trigger.getId());
            assertEquals(ValidTriggerParams.sAttributionDestination,
                    trigger.getAttributionDestination());
            assertEquals(ValidTriggerParams.sReportTo, trigger.getReportTo());
            assertEquals(ValidTriggerParams.sRegistrant, trigger.getRegistrant());
            assertEquals(ValidTriggerParams.TRIGGER_TIME.longValue(), trigger.getTriggerTime());
            assertEquals(ValidTriggerParams.TRIGGER_DATA.longValue(), trigger.getTriggerData());
            assertNull(trigger.getDedupKey());
            assertEquals(ValidTriggerParams.PRIORITY.longValue(), trigger.getPriority());
        }
    }

    @Test
    public void testInsertTrigger_validateArgumentPriority() {
        assertInvalidTriggerArguments(
                ValidTriggerParams.sAttributionDestination,
                ValidTriggerParams.sReportTo,
                ValidTriggerParams.sRegistrant,
                ValidTriggerParams.TRIGGER_TIME,
                ValidTriggerParams.TRIGGER_DATA,
                ValidTriggerParams.DEDUP_KEY,
                null);
    }

    public void assertInvalidTriggerArguments(Uri attributionDestination, Uri reportTo,
            Uri registrant, Long triggerTime, Long triggerData, Long dedupKey, Long priority) {
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) -> {
                    try {
                        dao.insertTrigger(
                                attributionDestination,
                                reportTo,
                                registrant,
                                triggerTime,
                                triggerData,
                                dedupKey,
                                priority
                        );
                        fail();
                    } catch (DatastoreException e) {
                        // Valid Exception
                    }
                }
        );
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

    @Test
    public void testInstallAttribution_selectHighestPriority() {
        long currentTimestamp = System.currentTimeMillis();

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        DatabaseE2ETest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, 100, -1, false),
                db);
        DatabaseE2ETest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, 50, -1, false),
                db);
        // Should select id IA1 because it has higher priority
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction(
                measurementDao -> {
                    measurementDao.doInstallAttribution(mInstalledPackage, currentTimestamp);
                }));
        Assert.assertTrue(getInstallAttributionStatus("IA1", db));
        Assert.assertFalse(getInstallAttributionStatus("IA2", db));
        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_selectLatest() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        DatabaseE2ETest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, -1, 10, false),
                db);
        DatabaseE2ETest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, -1, 5, false),
                db);
        // Should select id=IA2 as it is latest
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction(
                measurementDao -> {
                    measurementDao.doInstallAttribution(mInstalledPackage, currentTimestamp);
                }));
        Assert.assertFalse(getInstallAttributionStatus("IA1", db));
        Assert.assertTrue(getInstallAttributionStatus("IA2", db));

        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_ignoreNewerSources() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        DatabaseE2ETest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, -1, 10, false),
                db);
        DatabaseE2ETest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, -1, 5, false),
                db);
        // Should select id=IA1 as it is the only valid choice.
        // id=IA2 is newer than the evenTimestamp of install event.
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction(
                measurementDao -> {
                    measurementDao.doInstallAttribution(mInstalledPackage,
                            currentTimestamp - TimeUnit.DAYS.toMillis(7));
                }));
        Assert.assertTrue(getInstallAttributionStatus("IA1", db));
        Assert.assertFalse(getInstallAttributionStatus("IA2", db));

        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testInstallAttribution_noValidSource() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        DatabaseE2ETest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, 10, 10, true),
                db);
        DatabaseE2ETest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, 10, 11, true),
                db);
        // Should not update any sources.
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction(
                measurementDao -> measurementDao.doInstallAttribution(mInstalledPackage,
                        currentTimestamp)));
        Assert.assertFalse(getInstallAttributionStatus("IA1", db));
        Assert.assertFalse(getInstallAttributionStatus("IA2", db));
        removeSources(Arrays.asList("IA1", "IA2"), db);
    }

    @Test
    public void testUndoInstallAttribution_noMarkedSource() {
        long currentTimestamp = System.currentTimeMillis();
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        Source source = createSourceForIATest("IA1", currentTimestamp, 10, 10, false);
        source.setInstallAttributed(true);
        DatabaseE2ETest.insertToDb(source, db);
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction(
                measurementDao -> measurementDao.undoInstallAttribution(mInstalledPackage)));
        // Should set installAttributed = false for id=IA1
        Assert.assertFalse(getInstallAttributionStatus("IA1", db));
    }

    private Source createSourceForIATest(String id, long currentTime, long priority,
            int eventTimePastDays, boolean expiredIAWindow) {
        return new Source.Builder()
                .setId(id)
                .setAttributionSource(Uri.parse("android-app://com.example.sample"))
                .setRegistrant(Uri.parse("android-app://com.example.sample"))
                .setReportTo(Uri.parse("https://example.com"))
                .setExpiryTime(currentTime + TimeUnit.DAYS.toMillis(30))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(expiredIAWindow ? 0 : 30))
                .setAttributionDestination(mInstalledPackage)
                .setEventTime(currentTime - TimeUnit.DAYS.toMillis(
                        eventTimePastDays == -1 ? 10 : eventTimePastDays))
                .setPriority(priority == -1 ? 100 : priority)
                .build();
    }

    private boolean getInstallAttributionStatus(String sourceDbId, SQLiteDatabase db) {
        Cursor cursor = db.query(MeasurementTables.SourceContract.TABLE,
                new String[]{ MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED },
                MeasurementTables.SourceContract.ID + " = ? ", new String[]{ sourceDbId },
                null, null,
                null, null);
        Assert.assertTrue(cursor.moveToFirst());
        return cursor.getInt(0) == 1;
    }

    private void removeSources(List<String> dbIds, SQLiteDatabase db) {
        db.delete(MeasurementTables.SourceContract.TABLE,
                MeasurementTables.SourceContract.ID + " IN ( ? )",
                new String[]{String.join(",", dbIds)});
    }

    @Test
    public void testGetSourceEventReports() {
        List<Source> sourceList = new ArrayList<>();
        sourceList.add(new Source.Builder().setId("1").setEventId(3).build());
        sourceList.add(new Source.Builder().setId("2").setEventId(4).build());

        // Should match with source 1
        List<EventReport> reportList1 = new ArrayList<>();
        reportList1.add(new EventReport.Builder().setId("1").setSourceId(3).build());
        reportList1.add(new EventReport.Builder().setId("7").setSourceId(3).build());

        // Should match with source 2
        List<EventReport> reportList2 = new ArrayList<>();
        reportList2.add(new EventReport.Builder().setId("3").setSourceId(4).build());
        reportList2.add(new EventReport.Builder().setId("8").setSourceId(4).build());

        List<EventReport> reportList3 = new ArrayList<>();
        // Should not match with any source
        reportList3.add(new EventReport.Builder().setId("2").setSourceId(5).build());
        reportList3.add(new EventReport.Builder().setId("4").setSourceId(6).build());
        reportList3.add(new EventReport.Builder().setId("5").setSourceId(1).build());
        reportList3.add(new EventReport.Builder().setId("6").setSourceId(2).build());

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        sourceList.forEach(source -> {
            ContentValues values = new ContentValues();
            values.put(MeasurementTables.SourceContract.ID, source.getId());
            values.put(MeasurementTables.SourceContract.EVENT_ID, source.getEventId());
            db.insert(MeasurementTables.SourceContract.TABLE, null, values);
        });
        Stream.of(reportList1, reportList2, reportList3)
                .flatMap(Collection::stream)
                .forEach(eventReport -> {
                    ContentValues values = new ContentValues();
                    values.put(MeasurementTables.EventReportContract.ID, eventReport.getId());
                    values.put(MeasurementTables.EventReportContract.SOURCE_ID,
                            eventReport.getSourceId());
                    db.insert(MeasurementTables.EventReportContract.TABLE, null, values);
                });

        Assert.assertEquals(
                reportList1,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao -> measurementDao.getSourceEventReports(
                                        sourceList.get(0)))
                        .orElseThrow());

        Assert.assertEquals(
                reportList2,
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransactionWithResult(
                                measurementDao -> measurementDao.getSourceEventReports(
                                        sourceList.get(1)))
                        .orElseThrow());
    }

    private static class ValidSourceParams {
        static final Long EXPIRY_TIME = 8640000010L;
        static final Long PRIORITY = 100L;
        static final Long SOURCE_EVENT_ID = 1L;
        static final Long SOURCE_EVENT_TIME = 8640000000L;
        static final Uri sAttributionDestination = Uri.parse("android-app://com.destination");
        static final Uri sAttributionSource = Uri.parse("android-app://com.source");
        static final Uri sRegistrant = Uri.parse("android-app://com.registrant");
        static final Uri sReportTo = Uri.parse("https://com.example");
        static final Source.SourceType sSourceType = Source.SourceType.EVENT;
    }

    private static class ValidTriggerParams {
        static final Long DEDUP_KEY = 200L;
        static final Long PRIORITY = 100L;
        static final Long TRIGGER_TIME = 8640000000L;
        static final Long TRIGGER_DATA = 3L;
        static final Uri sAttributionDestination = Uri.parse("android-app://com.destination");
        static final Uri sRegistrant = Uri.parse("android-app://com.registrant");
        static final Uri sReportTo = Uri.parse("https://com.example");
    }

}
