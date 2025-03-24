/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.data.measurement.migration;

import static com.android.adservices.common.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.common.DbTestUtil.getDbHelperForTest;
import static com.android.adservices.data.measurement.migration.MigrationTestHelper.populateDb;

import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;
import com.android.adservices.service.measurement.CountUniqueReport;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV46Test extends MeasurementDbMigratorTestBase {

    @Test
    public void performMigration_v45ToV46WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        45,
                        getDbHelperForTest());

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        getTestSubject().performMigration(db, /* oldVersion= */ 45, /* newVersion= */ 46);

        // Populate Table with Data
        CountUniqueReport.Builder builder = new CountUniqueReport.Builder();
        builder.setReportId("report_id");
        builder.setPayload("{bucket: 5678n, value: 16, filteringId: 33n}");
        builder.setReportingOrigin(Uri.parse("https://test.bar/count-unique-reporting"));
        builder.setStatus(CountUniqueReport.ReportDeliveryStatus.PENDING);
        builder.setScheduledReportTime(1726874188156L);
        builder.setApiVersion("1.0");
        builder.setDebugKey("dfsdadsadsa");
        builder.setContextId("testContextId");
        CountUniqueReport report = builder.build();
        Map<String, List<ContentValues>> fakeData = createFakeDataV46(report);
        populateDb(db, fakeData);

        // Check that new columns are initialized
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueReportingContract.TABLE,
                MeasurementTables.CountUniqueReportingContract.REPORT_ID,
                report.getReportId());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueReportingContract.TABLE,
                MeasurementTables.CountUniqueReportingContract.PAYLOAD,
                report.getPayload());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueReportingContract.TABLE,
                MeasurementTables.CountUniqueReportingContract.REPORTING_ORIGIN,
                report.getReportingOrigin().toString());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueReportingContract.TABLE,
                MeasurementTables.CountUniqueReportingContract.STATUS,
                String.valueOf(report.getStatus()));
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueReportingContract.TABLE,
                MeasurementTables.CountUniqueReportingContract.SCHEDULED_REPORT_TIME,
                report.getScheduledReportTime().toString());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueReportingContract.TABLE,
                MeasurementTables.CountUniqueReportingContract.API_VERSION,
                report.getApiVersion());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueReportingContract.TABLE,
                MeasurementTables.CountUniqueReportingContract.DEBUG_KEY,
                report.getDebugKey());
        validateNewTextColumn(
                db,
                MeasurementTables.CountUniqueReportingContract.TABLE,
                MeasurementTables.CountUniqueReportingContract.CONTEXT_ID,
                report.getContextId());

        // Validation
        assertThat(
                        doesTableExistAndColumnCountMatch(
                                db,
                                MeasurementTables.CountUniqueReportingContract.TABLE,
                                /* columnCount= */ 8))
                .isTrue();
    }

    private Map<String, List<ContentValues>> createFakeDataV46(CountUniqueReport report) {
        Map<String, List<ContentValues>> tableRowsMap = new LinkedHashMap<>();

        List<ContentValues> countUniqueReportRows = getRows(report);
        tableRowsMap.put(
                MeasurementTables.CountUniqueReportingContract.TABLE, countUniqueReportRows);

        return tableRowsMap;
    }

    @NotNull
    private static List<ContentValues> getRows(CountUniqueReport reportObj) {
        List<ContentValues> countUniqueReportRows = new ArrayList<>();
        ContentValues report = new ContentValues();
        report.put(
                MeasurementTables.CountUniqueReportingContract.REPORT_ID, reportObj.getReportId());
        report.put(MeasurementTables.CountUniqueReportingContract.PAYLOAD, reportObj.getPayload());
        report.put(
                MeasurementTables.CountUniqueReportingContract.REPORTING_ORIGIN,
                reportObj.getReportingOrigin().toString());
        report.put(MeasurementTables.CountUniqueReportingContract.STATUS, reportObj.getStatus());
        report.put(
                MeasurementTables.CountUniqueReportingContract.SCHEDULED_REPORT_TIME,
                reportObj.getScheduledReportTime());
        report.put(
                MeasurementTables.CountUniqueReportingContract.API_VERSION,
                reportObj.getApiVersion());
        report.put(
                MeasurementTables.CountUniqueReportingContract.DEBUG_KEY, reportObj.getDebugKey());
        report.put(
                MeasurementTables.CountUniqueReportingContract.CONTEXT_ID,
                reportObj.getContextId());
        countUniqueReportRows.add(report);
        return countUniqueReportRows;
    }

    private static void validateNewTextColumn(
            SQLiteDatabase db, String table, String column, String expected) {
        try (Cursor cursor = db.query(table, new String[] {column}, null, null, null, null, null)) {
            assertThat(cursor.getCount()).isEqualTo(1);
            while (cursor.moveToNext()) {
                String actual = cursor.getString(cursor.getColumnIndex(column));
                assertThat(expected).isEqualTo(actual);
            }
        }
    }

    @Override
    int getTargetVersion() {
        return 46;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV46();
    }
}
