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

package com.android.adservices.data.measurement.migration;

import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;

import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV6Test extends AbstractMeasurementDbMigratorTestBase {

    @Test
    public void performMigration_success() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        new MeasurementDbMigratorV3().performMigration(db, 2, 3);
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 22));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 13));
        new MeasurementDbMigratorV6().performMigration(db, 5, 6);
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 23));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 14));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.DebugReportContract.TABLE, 4));
    }

    @Override
    int getTargetVersion() {
        return 6;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV6();
    }
}
