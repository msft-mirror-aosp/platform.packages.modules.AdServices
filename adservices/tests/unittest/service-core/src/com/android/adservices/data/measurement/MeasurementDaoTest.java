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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.service.measurement.AdtechUrl;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;
import com.android.adservices.service.measurement.enrollment.EnrollmentData;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

public class MeasurementDaoTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TAG = "MeasurementDaoTest";
    private static final Uri APP_TWO_SOURCES = Uri.parse("android-app://com.example1.two-sources");
    private static final Uri APP_ONE_SOURCE = Uri.parse("android-app://com.example2.one-source");
    private static final Uri APP_NO_SOURCE = Uri.parse("android-app://com.example3.no-sources");
    private static final Uri APP_TWO_TRIGGERS =
            Uri.parse("android-app://com.example1.two-triggers");
    private static final Uri APP_ONE_TRIGGER = Uri.parse("android-app://com.example1.one-trigger");
    private static final Uri APP_NO_TRIGGERS = Uri.parse("android-app://com.example1.no-triggers");
    private static final Uri INSTALLED_PACKAGE = Uri.parse("android-app://com.example.installed");

    @After
    public void cleanup() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            db.delete(table, null, null);
        }
    }

    @Test
    public void testInsertSource() {
        Source validSource = SourceFixture.getValidSource();
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertSource(validSource));

        try (Cursor sourceCursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.SourceContract.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(sourceCursor.moveToNext());
            Source source = SqliteObjectMapper.constructSourceFromCursor(sourceCursor);
            Assert.assertNotNull(source);
            Assert.assertNotNull(source.getId());
            assertEquals(validSource.getPublisher(), source.getPublisher());
            assertEquals(validSource.getAppDestination(), source.getAppDestination());
            assertEquals(validSource.getWebDestination(), source.getWebDestination());
            assertEquals(validSource.getAdTechDomain(), source.getAdTechDomain());
            assertEquals(validSource.getRegistrant(), source.getRegistrant());
            assertEquals(validSource.getEventTime(), source.getEventTime());
            assertEquals(validSource.getExpiryTime(), source.getExpiryTime());
            assertEquals(validSource.getPriority(), source.getPriority());
            assertEquals(validSource.getSourceType(), source.getSourceType());
            assertEquals(validSource.getInstallAttributionWindow(),
                    source.getInstallAttributionWindow());
            assertEquals(validSource.getInstallCooldownWindow(), source.getInstallCooldownWindow());
            assertEquals(validSource.getAttributionMode(), source.getAttributionMode());
            assertEquals(validSource.getAggregateSource(), source.getAggregateSource());
            assertEquals(validSource.getAggregateFilterData(), source.getAggregateFilterData());
            assertEquals(validSource.getAggregateContributions(),
                    source.getAggregateContributions());
        }
    }

    @Test
    public void testInsertTrigger() {
        Trigger validTrigger = TriggerFixture.getValidTrigger();
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertTrigger(validTrigger));

        try (Cursor triggerCursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.TriggerContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(triggerCursor.moveToNext());
            Trigger trigger = SqliteObjectMapper.constructTriggerFromCursor(triggerCursor);
            Assert.assertNotNull(trigger);
            Assert.assertNotNull(trigger.getId());
            assertEquals(
                    validTrigger.getAttributionDestination(), trigger.getAttributionDestination());
            assertEquals(validTrigger.getAdTechDomain(), trigger.getAdTechDomain());
            assertEquals(validTrigger.getRegistrant(), trigger.getRegistrant());
            assertEquals(validTrigger.getTriggerTime(), trigger.getTriggerTime());
            assertEquals(validTrigger.getEventTriggers(), trigger.getEventTriggers());
        }
    }

    @Test
    public void testGetNumSourcesPerRegistrant() {
        setupSourceAndTriggerData();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(2, measurementDao.getNumSourcesPerRegistrant(APP_TWO_SOURCES));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(1, measurementDao.getNumSourcesPerRegistrant(APP_ONE_SOURCE));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(0, measurementDao.getNumSourcesPerRegistrant(APP_NO_SOURCE));
                });
    }

    @Test
    public void testGetNumTriggersPerRegistrant() {
        setupSourceAndTriggerData();
        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(2, measurementDao.getNumTriggersPerRegistrant(APP_TWO_TRIGGERS));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(1, measurementDao.getNumTriggersPerRegistrant(APP_ONE_TRIGGER));
                });
        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(0, measurementDao.getNumTriggersPerRegistrant(APP_NO_TRIGGERS));
                });
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteMeasurementData_requiredRegistrantAsNull() {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            dao.deleteMeasurementData(
                                    null /* registrant */,
                                    null /* start */,
                                    null /* end */,
                                    Collections.emptyList(),
                                    Collections.emptyList(),
                                    0,
                                    0);
                        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeNoStartDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            dao.deleteMeasurementData(
                                    APP_ONE_SOURCE,
                                    null /* start */,
                                    Instant.now(),
                                    Collections.emptyList(),
                                    Collections.emptyList(),
                                    0,
                                    0);
                        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeNoEndDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            dao.deleteMeasurementData(
                                    APP_ONE_SOURCE,
                                    Instant.now(),
                                    null /* end */,
                                    Collections.emptyList(),
                                    Collections.emptyList(),
                                    0,
                                    0);
                        });
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteMeasurementData_invalidRangeStartAfterEndDate() {
        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        (dao) -> {
                            dao.deleteMeasurementData(
                                    APP_ONE_SOURCE,
                                    Instant.now().plusMillis(1),
                                    Instant.now(),
                                    Collections.emptyList(),
                                    Collections.emptyList(),
                                    0,
                                    0);
                        });
    }

    @Test
    public void testInstallAttribution_selectHighestPriority() {
        long currentTimestamp = System.currentTimeMillis();

        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, 100, -1, false),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, 50, -1, false),
                db);
        // Should select id IA1 because it has higher priority
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE, currentTimestamp);
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
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, -1, 10, false),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, -1, 5, false),
                db);
        // Should select id=IA2 as it is latest
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE, currentTimestamp);
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
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, -1, 10, false),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, -1, 5, false),
                db);
        // Should select id=IA1 as it is the only valid choice.
        // id=IA2 is newer than the evenTimestamp of install event.
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao -> {
                                    measurementDao.doInstallAttribution(
                                            INSTALLED_PACKAGE,
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
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA1", currentTimestamp, 10, 10, true),
                db);
        AbstractDbIntegrationTest.insertToDb(
                createSourceForIATest("IA2", currentTimestamp, 10, 11, true),
                db);
        // Should not update any sources.
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.doInstallAttribution(
                                                INSTALLED_PACKAGE, currentTimestamp)));
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
        AbstractDbIntegrationTest.insertToDb(source, db);
        Assert.assertTrue(
                DatastoreManagerFactory.getDatastoreManager(sContext)
                        .runInTransaction(
                                measurementDao ->
                                        measurementDao.undoInstallAttribution(INSTALLED_PACKAGE)));
        // Should set installAttributed = false for id=IA1
        Assert.assertFalse(getInstallAttributionStatus("IA1", db));
    }

    @Test
    public void testGetSourceEventReports() {
        List<Source> sourceList = new ArrayList<>();
        sourceList.add(SourceFixture.getValidSourceBuilder()
                .setId("1").setEventId(3).build());
        sourceList.add(SourceFixture.getValidSourceBuilder()
                .setId("2").setEventId(4).build());

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

    @Test
    public void testUpdateSourceStatus() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);

        List<Source> sourceList = new ArrayList<>();
        sourceList.add(SourceFixture.getValidSourceBuilder().setId("1").build());
        sourceList.add(SourceFixture.getValidSourceBuilder().setId("2").build());
        sourceList.add(SourceFixture.getValidSourceBuilder().setId("3").build());
        sourceList.forEach(source -> {
            ContentValues values = new ContentValues();
            values.put(MeasurementTables.SourceContract.ID, source.getId());
            values.put(MeasurementTables.SourceContract.STATUS, 1);
            db.insert(MeasurementTables.SourceContract.TABLE, null, values);
        });

        // Multiple Elements
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        measurementDao -> measurementDao.updateSourceStatus(
                                sourceList, Source.Status.IGNORED)
                ));

        // Single Element
        Assert.assertTrue(DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction(
                        measurementDao -> measurementDao.updateSourceStatus(
                                sourceList.subList(0, 1), Source.Status.IGNORED)
                ));
    }

    @Test
    public void testGetMatchingActiveSources() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Objects.requireNonNull(db);
        Uri adTechDomain = Uri.parse("https://www.example.xyz");
        Uri appDestination = Uri.parse("android-app://com.example.abc");
        Uri webDestination = Uri.parse("https://com.example.abc");
        Source sApp1 =
                SourceFixture.getValidSourceBuilder()
                        .setId("1")
                        .setEventTime(10)
                        .setExpiryTime(20)
                        .setAppDestination(appDestination)
                        .setAdTechDomain(adTechDomain)
                        .build();
        Source sApp2 =
                SourceFixture.getValidSourceBuilder()
                        .setId("2")
                        .setEventTime(10)
                        .setExpiryTime(50)
                        .setAppDestination(appDestination)
                        .setAdTechDomain(adTechDomain)
                        .build();
        Source sApp3 =
                SourceFixture.getValidSourceBuilder()
                        .setId("3")
                        .setEventTime(20)
                        .setExpiryTime(50)
                        .setAppDestination(appDestination)
                        .setAdTechDomain(adTechDomain)
                        .build();
        Source sApp4 =
                SourceFixture.getValidSourceBuilder()
                        .setId("4")
                        .setEventTime(30)
                        .setExpiryTime(50)
                        .setAppDestination(appDestination)
                        .setAdTechDomain(adTechDomain)
                        .build();
        Source sWeb5 =
                SourceFixture.getValidSourceBuilder()
                        .setId("5")
                        .setEventTime(10)
                        .setExpiryTime(20)
                        .setWebDestination(webDestination)
                        .setAdTechDomain(adTechDomain)
                        .build();
        Source sWeb6 =
                SourceFixture.getValidSourceBuilder()
                        .setId("6")
                        .setEventTime(10)
                        .setExpiryTime(50)
                        .setWebDestination(webDestination)
                        .setAdTechDomain(adTechDomain)
                        .build();
        Source sAppWeb7 =
                SourceFixture.getValidSourceBuilder()
                        .setId("7")
                        .setEventTime(10)
                        .setExpiryTime(20)
                        .setAppDestination(appDestination)
                        .setWebDestination(webDestination)
                        .setAdTechDomain(adTechDomain)
                        .build();

        List<Source> sources = Arrays.asList(sApp1, sApp2, sApp3, sApp4, sWeb5, sWeb6, sAppWeb7);
        sources.forEach(source -> insertInDb(db, source));

        Function<Trigger, List<Source>> runFunc = trigger -> {
            List<Source> result = DatastoreManagerFactory.getDatastoreManager(sContext)
                    .runInTransactionWithResult(
                            measurementDao -> measurementDao.getMatchingActiveSources(trigger)
                    ).orElseThrow();
            result.sort(Comparator.comparing(Source::getId));
            return result;
        };

        // Trigger Time > sApp1's eventTime and < sApp1's expiryTime
        // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
        // Trigger Time < sApp3's eventTime
        // Trigger Time < sApp4's eventTime
        // sApp5 and sApp6 don't have app destination
        // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
        // Expected: Match with sApp1, sApp2, sAppWeb7
        Trigger trigger1MatchSource1And2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(12)
                        .setAdTechDomain(adTechDomain)
                        .setAttributionDestination(appDestination)
                        .build();
        List<Source> result1 = runFunc.apply(trigger1MatchSource1And2);
        Assert.assertEquals(3, result1.size());
        Assert.assertEquals(sApp1.getId(), result1.get(0).getId());
        Assert.assertEquals(sApp2.getId(), result1.get(1).getId());
        Assert.assertEquals(sAppWeb7.getId(), result1.get(2).getId());

        // Trigger Time > sApp1's eventTime and = sApp1's expiryTime
        // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
        // Trigger Time = sApp3's eventTime
        // Trigger Time < sApp4's eventTime
        // sApp5 and sApp6 don't have app destination
        // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
        // Expected: Match with sApp1, sApp2, sAppWeb7
        Trigger trigger2MatchSource127 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(20)
                        .setAdTechDomain(adTechDomain)
                        .setAttributionDestination(appDestination)
                        .build();

        List<Source> result2 = runFunc.apply(trigger2MatchSource127);
        Assert.assertEquals(3, result2.size());
        Assert.assertEquals(sApp1.getId(), result2.get(0).getId());
        Assert.assertEquals(sApp2.getId(), result2.get(1).getId());
        Assert.assertEquals(sAppWeb7.getId(), result2.get(2).getId());

        // Trigger Time > sApp1's expiryTime
        // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
        // Trigger Time > sApp3's eventTime and < sApp3's expiryTime
        // Trigger Time < sApp4's eventTime
        // sApp5 and sApp6 don't have app destination
        // Trigger Time > sAppWeb7's expiryTime
        // Expected: Match with sApp2, sApp3
        Trigger trigger3MatchSource237 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(21)
                        .setAdTechDomain(adTechDomain)
                        .setAttributionDestination(appDestination)
                        .build();

        List<Source> result3 = runFunc.apply(trigger3MatchSource237);
        Assert.assertEquals(2, result3.size());
        Assert.assertEquals(sApp2.getId(), result3.get(0).getId());
        Assert.assertEquals(sApp3.getId(), result3.get(1).getId());

        // Trigger Time > sApp1's expiryTime
        // Trigger Time > sApp2's eventTime and < sApp2's expiryTime
        // Trigger Time > sApp3's eventTime and < sApp3's expiryTime
        // Trigger Time > sApp4's eventTime and < sApp4's expiryTime
        // sApp5 and sApp6 don't have app destination
        // Trigger Time > sAppWeb7's expiryTime
        // Expected: Match with sApp2, sApp3 and sApp4
        Trigger trigger4MatchSource1And2And3 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(31)
                        .setAdTechDomain(adTechDomain)
                        .setAttributionDestination(appDestination)
                        .build();

        List<Source> result4 = runFunc.apply(trigger4MatchSource1And2And3);
        Assert.assertEquals(3, result4.size());
        Assert.assertEquals(sApp2.getId(), result4.get(0).getId());
        Assert.assertEquals(sApp3.getId(), result4.get(1).getId());
        Assert.assertEquals(sApp4.getId(), result4.get(2).getId());

        // sApp1, sApp2, sApp3, sApp4 don't have web destination
        // Trigger Time > sWeb5's eventTime and < sApp5's expiryTime
        // Trigger Time > sWeb6's eventTime and < sApp6's expiryTime
        // Trigger Time > sAppWeb7's eventTime and < sAppWeb7's expiryTime
        // Expected: Match with sApp5, sApp6, sAppWeb7
        Trigger trigger5MatchSource567 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(12)
                        .setAdTechDomain(adTechDomain)
                        .setAttributionDestination(webDestination)
                        .build();
        List<Source> result5 = runFunc.apply(trigger5MatchSource567);
        Assert.assertEquals(3, result1.size());
        Assert.assertEquals(sWeb5.getId(), result5.get(0).getId());
        Assert.assertEquals(sWeb6.getId(), result5.get(1).getId());
        Assert.assertEquals(sAppWeb7.getId(), result5.get(2).getId());

        // sApp1, sApp2, sApp3, sApp4 don't have web destination
        // Trigger Time > sWeb5's expiryTime
        // Trigger Time > sWeb6's eventTime and < sApp6's expiryTime
        // Trigger Time > sWeb7's expiryTime
        // Expected: Match with sApp6 only
        Trigger trigger6MatchSource67 =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(21)
                        .setAdTechDomain(adTechDomain)
                        .setAttributionDestination(webDestination)
                        .build();

        List<Source> result6 = runFunc.apply(trigger6MatchSource67);
        Assert.assertEquals(1, result6.size());
        Assert.assertEquals(sWeb6.getId(), result6.get(0).getId());
    }

    private void insertInDb(SQLiteDatabase db, Source source) {
        ContentValues values = new ContentValues();
        values.put(MeasurementTables.SourceContract.ID, source.getId());
        values.put(MeasurementTables.SourceContract.STATUS, Source.Status.ACTIVE);
        values.put(MeasurementTables.SourceContract.EVENT_TIME, source.getEventTime());
        values.put(MeasurementTables.SourceContract.EXPIRY_TIME, source.getExpiryTime());
        values.put(
                MeasurementTables.SourceContract.AD_TECH_DOMAIN,
                source.getAdTechDomain().toString());
        if (source.getAppDestination() != null) {
            values.put(
                    MeasurementTables.SourceContract.APP_DESTINATION,
                    source.getAppDestination().toString());
        }
        if (source.getWebDestination() != null) {
            values.put(
                    MeasurementTables.SourceContract.WEB_DESTINATION,
                    source.getWebDestination().toString());
        }
        values.put(MeasurementTables.SourceContract.PUBLISHER, source.getPublisher().toString());
        values.put(MeasurementTables.SourceContract.REGISTRANT, source.getRegistrant().toString());

        db.insert(MeasurementTables.SourceContract.TABLE, null, values);
    }

    @Test
    public void testInsertAggregateEncryptionKey() {
        String keyId = "38b1d571-f924-4dc0-abe1-e2bac9b6a6be";
        String publicKey = "/amqBgfDOvHAIuatDyoHxhfHaMoYA4BDxZxwtWBRQhc=";
        long expiry = 1653620135831L;

        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertAggregateEncryptionKey(
                        new AggregateEncryptionKey.Builder()
                                .setKeyId(keyId)
                                .setPublicKey(publicKey)
                                .setExpiry(expiry).build())
        );

        try (Cursor cursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.AggregateEncryptionKey.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            AggregateEncryptionKey aggregateEncryptionKey =
                    SqliteObjectMapper.constructAggregateEncryptionKeyFromCursor(cursor);
            Assert.assertNotNull(aggregateEncryptionKey);
            Assert.assertNotNull(aggregateEncryptionKey.getId());
            assertEquals(keyId, aggregateEncryptionKey.getKeyId());
            assertEquals(publicKey, aggregateEncryptionKey.getPublicKey());
            assertEquals(expiry, aggregateEncryptionKey.getExpiry());
        }
    }

    @Test
    public void testInsertAggregateReport() {
        AggregateReport validAggregateReport = AggregateReportFixture.getValidAggregateReport();
        DatastoreManagerFactory.getDatastoreManager(sContext).runInTransaction((dao) ->
                dao.insertAggregateReport(validAggregateReport));

        try (Cursor cursor =
                     DbHelper.getInstance(sContext).getReadableDatabase()
                             .query(MeasurementTables.AggregateReport.TABLE,
                                     null, null, null, null, null, null)) {
            Assert.assertTrue(cursor.moveToNext());
            AggregateReport aggregateReport =
                    SqliteObjectMapper.constructAggregateReport(cursor);
            Assert.assertNotNull(aggregateReport);
            Assert.assertNotNull(aggregateReport.getId());
            Assert.assertTrue(Objects.equals(validAggregateReport, aggregateReport));
        }
    }

    @Test
    public void testInsertAndGetAndDeleteEnrollmentData() {
        List<String> sdkNames = Arrays.asList("Admob", "Firebase");
        List<String> sourceRegistrationUrls =
                Arrays.asList("https://source.example1.com", "https://source.example2.com");
        List<String> triggerRegistrationUrls = Arrays.asList("https://trigger.example1.com");
        List<String> reportingUrls = Arrays.asList("https://reporting.example1.com");
        List<String> remarketingRegistrationUrls =
                Arrays.asList("https://remarketing.example1.com");
        List<String> encryptionUrls = Arrays.asList("https://encryption.example1.com");
        EnrollmentData enrollmentData =
                new EnrollmentData.Builder()
                        .setEnrollmentId("1")
                        .setCompanyId("1001")
                        .setSdkNames(sdkNames)
                        .setAttributionSourceRegistrationUrl(sourceRegistrationUrls)
                        .setAttributionTriggerRegistrationUrl(triggerRegistrationUrls)
                        .setAttributionReportingUrl(reportingUrls)
                        .setRemarketingResponseBasedRegistrationUrl(remarketingRegistrationUrls)
                        .setEncryptionKeyUrl(encryptionUrls)
                        .build();

        EnrollmentData enrollmentData1 =
                new EnrollmentData.Builder()
                        .setEnrollmentId("2")
                        .setCompanyId("1002")
                        .setSdkNames(sdkNames)
                        .build();

        DatastoreManager dm = DatastoreManagerFactory.getDatastoreManager(sContext);
        dm.runInTransaction((dao) -> dao.insertEnrollmentData(enrollmentData));
        dm.runInTransaction((dao) -> dao.insertEnrollmentData(enrollmentData1));

        try (Cursor cursor =
                DbHelper.getInstance(sContext)
                        .getReadableDatabase()
                        .query(
                                MeasurementTables.EnrollmentDataContract.TABLE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null)) {
            Assert.assertTrue(cursor.moveToNext());
            EnrollmentData data = SqliteObjectMapper.constructEnrollmentDataFromCursor(cursor);
            Assert.assertNotNull(data);
            assertEquals(data.getEnrollmentId(), "1");
            assertEquals(data.getCompanyId(), "1001");
            assertEquals(data.getSdkNames(), sdkNames);
            assertEquals(data.getAttributionSourceRegistrationUrl(), sourceRegistrationUrls);
            assertEquals(data.getAttributionTriggerRegistrationUrl(), triggerRegistrationUrls);
            assertEquals(data.getAttributionReportingUrl(), reportingUrls);
            assertEquals(
                    data.getRemarketingResponseBasedRegistrationUrl(), remarketingRegistrationUrls);
            assertEquals(data.getEncryptionKeyUrl(), encryptionUrls);
        }

        dm.runInTransaction(
                measurementDao -> {
                    assertNotNull(measurementDao.getEnrollmentData("1"));
                });

        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Objects.requireNonNull(
                                            measurementDao.getEnrollmentDataGivenUrl(
                                                    "https://source.example1.com"))
                                    .getEnrollmentId(),
                            "1");
                });

        dm.runInTransaction(
                measurementDao -> {
                    assertEquals(
                            Objects.requireNonNull(
                                            measurementDao.getEnrollmentDataGivenSdkName("Admob"))
                                    .getEnrollmentId(),
                            "1");
                });

        dm.runInTransaction(
                measurementDao -> {
                    assertNull(measurementDao.getEnrollmentDataGivenSdkName("null"));
                });

        dm.runInTransaction((dao) -> dao.deleteEnrollmentData("1"));
        dm.runInTransaction(
                measurementDao -> {
                    assertNull(measurementDao.getEnrollmentData("1"));
                });
    }

    @Test
    public void testDeleteAllMeasurementDataWithEmptyList() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();

        Source source = SourceFixture.getValidSourceBuilder().setId("S1").build();
        ContentValues sourceValue = new ContentValues();
        sourceValue.put("_id", source.getId());
        db.insert(MeasurementTables.SourceContract.TABLE, null, sourceValue);

        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setId("T1").build();
        ContentValues triggerValue = new ContentValues();
        triggerValue.put("_id", trigger.getId());
        db.insert(MeasurementTables.TriggerContract.TABLE, null, triggerValue);

        EventReport eventReport = new EventReport.Builder().setId("E1").build();
        ContentValues eventReportValue = new ContentValues();
        eventReportValue.put("_id", eventReport.getId());
        db.insert(MeasurementTables.EventReportContract.TABLE, null, eventReportValue);

        AggregateReport aggregateReport = new AggregateReport.Builder().setId("A1").build();
        ContentValues aggregateReportValue = new ContentValues();
        aggregateReportValue.put("_id", aggregateReport.getId());
        db.insert(MeasurementTables.AggregateReport.TABLE, null, aggregateReportValue);

        AttributionRateLimit rateLimit = new AttributionRateLimit.Builder().setId("ARL1").build();
        ContentValues rateLimitValue = new ContentValues();
        rateLimitValue.put("_id", rateLimit.getId());
        db.insert(MeasurementTables.AttributionRateLimitContract.TABLE, null, rateLimitValue);

        AdtechUrl adTechUrl =
                new AdtechUrl.Builder()
                        .setPostbackUrl("https://example.com")
                        .setAdtechId("AD1")
                        .build();
        ContentValues adTechUrlValues = new ContentValues();
        adTechUrlValues.put("postback_url", adTechUrl.getPostbackUrl());
        adTechUrlValues.put("ad_tech_id", adTechUrl.getAdtechId());
        db.insert(MeasurementTables.AdTechUrlsContract.TABLE, null, adTechUrlValues);

        AggregateEncryptionKey key =
                new AggregateEncryptionKey.Builder()
                        .setId("K1")
                        .setKeyId("keyId")
                        .setPublicKey("publicKey")
                        .setExpiry(1)
                        .build();
        ContentValues keyValues = new ContentValues();
        keyValues.put("_id", key.getId());

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.deleteAllMeasurementData(Collections.emptyList()));

        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            assertThat(
                            db.query(
                                            /* table */ table,
                                            /* columns */ null,
                                            /* selection */ null,
                                            /* selectionArgs */ null,
                                            /* groupBy */ null,
                                            /* having */ null,
                                            /* orderedBy */ null)
                                    .getCount())
                    .isEqualTo(0);
        }
    }

    @Test
    public void testDeleteAllMeasurementDataWithNonEmptyList() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();

        Source source = SourceFixture.getValidSourceBuilder().setId("S1").build();
        ContentValues sourceValue = new ContentValues();
        sourceValue.put("_id", source.getId());
        db.insert(MeasurementTables.SourceContract.TABLE, null, sourceValue);

        Trigger trigger = TriggerFixture.getValidTriggerBuilder().setId("T1").build();
        ContentValues triggerValue = new ContentValues();
        triggerValue.put("_id", trigger.getId());
        db.insert(MeasurementTables.TriggerContract.TABLE, null, triggerValue);

        EventReport eventReport = new EventReport.Builder().setId("E1").build();
        ContentValues eventReportValue = new ContentValues();
        eventReportValue.put("_id", eventReport.getId());
        db.insert(MeasurementTables.EventReportContract.TABLE, null, eventReportValue);

        AggregateReport aggregateReport = new AggregateReport.Builder().setId("A1").build();
        ContentValues aggregateReportValue = new ContentValues();
        aggregateReportValue.put("_id", aggregateReport.getId());
        db.insert(MeasurementTables.AggregateReport.TABLE, null, aggregateReportValue);

        AttributionRateLimit rateLimit = new AttributionRateLimit.Builder().setId("ARL1").build();
        ContentValues rateLimitValue = new ContentValues();
        rateLimitValue.put("_id", rateLimit.getId());
        db.insert(MeasurementTables.AttributionRateLimitContract.TABLE, null, rateLimitValue);

        AdtechUrl adTechUrl =
                new AdtechUrl.Builder()
                        .setPostbackUrl("https://example.com")
                        .setAdtechId("AD1")
                        .build();
        ContentValues adTechUrlValues = new ContentValues();
        adTechUrlValues.put("postback_url", adTechUrl.getPostbackUrl());
        adTechUrlValues.put("ad_tech_id", adTechUrl.getAdtechId());
        db.insert(MeasurementTables.AdTechUrlsContract.TABLE, null, adTechUrlValues);

        AggregateEncryptionKey key =
                new AggregateEncryptionKey.Builder()
                        .setId("K1")
                        .setKeyId("keyId")
                        .setPublicKey("publicKey")
                        .setExpiry(1)
                        .build();
        ContentValues keyValues = new ContentValues();
        keyValues.put("_id", key.getId());

        List<String> excludedTables = List.of(MeasurementTables.SourceContract.TABLE);

        DatastoreManagerFactory.getDatastoreManager(sContext)
                .runInTransaction((dao) -> dao.deleteAllMeasurementData(excludedTables));

        for (String table : MeasurementTables.ALL_MSMT_TABLES) {
            if (!excludedTables.contains(table)) {
                assertThat(
                                db.query(
                                                /* table */ table,
                                                /* columns */ null,
                                                /* selection */ null,
                                                /* selectionArgs */ null,
                                                /* groupBy */ null,
                                                /* having */ null,
                                                /* orderedBy */ null)
                                        .getCount())
                        .isEqualTo(0);
            } else {
                assertThat(
                                db.query(
                                                /* table */ table,
                                                /* columns */ null,
                                                /* selection */ null,
                                                /* selectionArgs */ null,
                                                /* groupBy */ null,
                                                /* having */ null,
                                                /* orderedBy */ null)
                                        .getCount())
                        .isNotEqualTo(0);
            }
        }
    }

    /** Test that the variable ALL_MSMT_TABLES actually has all the measurement related tables. */
    @Test
    public void testAllMsmtTables() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        Cursor cursor =
                db.query(
                        "sqlite_master",
                        /* columns */ null,
                        /* selection */ "type = ? AND name like ?",
                        /* selectionArgs*/ new String[] {
                            "table", MeasurementTables.MSMT_TABLE_PREFIX + "%"
                        },
                        /* groupBy */ null,
                        /* having */ null,
                        /* orderBy */ null);

        List<String> tableNames = new ArrayList<>();
        while (cursor.moveToNext()) {
            String tableName = cursor.getString(cursor.getColumnIndex("name"));
            if (!tableName.equals(MeasurementTables.AdTechUrlsContract.TABLE)) {
                // The AdTechUrls table is not included in the Measurement tables because it will be
                // used for a more general purpose.
                tableNames.add(tableName);
            }
        }
        assertThat(tableNames.size()).isEqualTo(MeasurementTables.ALL_MSMT_TABLES.length);
        for (String tableName : tableNames) {
            assertThat(MeasurementTables.ALL_MSMT_TABLES).asList().contains(tableName);
        }
    }

    private void setupSourceAndTriggerData() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).safeGetWritableDatabase();
        List<Source> sourcesList = new ArrayList<>();
        sourcesList.add(SourceFixture.getValidSourceBuilder()
                .setId("S1").setRegistrant(APP_TWO_SOURCES).build());
        sourcesList.add(SourceFixture.getValidSourceBuilder()
                .setId("S2").setRegistrant(APP_TWO_SOURCES).build());
        sourcesList.add(SourceFixture.getValidSourceBuilder()
                .setId("S3").setRegistrant(APP_ONE_SOURCE).build());
        for (Source source : sourcesList) {
            ContentValues values = new ContentValues();
            values.put("_id", source.getId());
            values.put("registrant", source.getRegistrant().toString());
            long row = db.insert("msmt_source", null, values);
            Assert.assertNotEquals("Source insertion failed", -1, row);
        }
        List<Trigger> triggersList = new ArrayList<>();
        triggersList.add(TriggerFixture.getValidTriggerBuilder()
                .setId("T1").setRegistrant(APP_TWO_TRIGGERS).build());
        triggersList.add(TriggerFixture.getValidTriggerBuilder()
                .setId("T2").setRegistrant(APP_TWO_TRIGGERS).build());
        triggersList.add(TriggerFixture.getValidTriggerBuilder()
                .setId("T3").setRegistrant(APP_ONE_TRIGGER).build());
        for (Trigger trigger : triggersList) {
            ContentValues values = new ContentValues();
            values.put("_id", trigger.getId());
            values.put("registrant", trigger.getRegistrant().toString());
            long row = db.insert("msmt_trigger", null, values);
            Assert.assertNotEquals("Trigger insertion failed", -1, row);
        }
    }

    private Source createSourceForIATest(String id, long currentTime, long priority,
            int eventTimePastDays, boolean expiredIAWindow) {
        return new Source.Builder()
                .setId(id)
                .setPublisher(Uri.parse("android-app://com.example.sample"))
                .setRegistrant(Uri.parse("android-app://com.example.sample"))
                .setAdTechDomain(Uri.parse("https://example.com"))
                .setExpiryTime(currentTime + TimeUnit.DAYS.toMillis(30))
                .setInstallAttributionWindow(TimeUnit.DAYS.toMillis(expiredIAWindow ? 0 : 30))
                .setAppDestination(INSTALLED_PACKAGE)
                .setEventTime(
                        currentTime
                                - TimeUnit.DAYS.toMillis(
                                        eventTimePastDays == -1 ? 10 : eventTimePastDays))
                .setPriority(priority == -1 ? 100 : priority)
                .build();
    }

    private boolean getInstallAttributionStatus(String sourceDbId, SQLiteDatabase db) {
        Cursor cursor = db.query(MeasurementTables.SourceContract.TABLE,
                new String[]{MeasurementTables.SourceContract.IS_INSTALL_ATTRIBUTED},
                MeasurementTables.SourceContract.ID + " = ? ", new String[]{sourceDbId},
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
}
