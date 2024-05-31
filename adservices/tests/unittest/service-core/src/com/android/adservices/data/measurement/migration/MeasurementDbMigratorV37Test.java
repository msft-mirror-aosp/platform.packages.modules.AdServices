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
package com.android.adservices.data.measurement.migration;

import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;
import static com.android.adservices.data.DbTestUtil.getDbHelperForTest;

import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.measurement.MeasurementDbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV37Test extends MeasurementDbMigratorTestBase {

    @Test
    public void performMigration_v36ToV37WithData_maintainsDataIntegrity() {
        // Setup
        MeasurementDbHelper dbHelper =
                new MeasurementDbHelper(
                        sContext,
                        MEASUREMENT_DATABASE_NAME_FOR_MIGRATION,
                        36,
                        getDbHelperForTest());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        getTestSubject().performMigration(db, 36, 37);
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AppReportHistoryContract.TABLE, 3));
    }

    @Override
    int getTargetVersion() {
        return 37;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV37();
    }
}
