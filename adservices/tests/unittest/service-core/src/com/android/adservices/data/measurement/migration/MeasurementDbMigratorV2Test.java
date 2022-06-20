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

import static com.android.adservices.data.DbTestUtil.doesIndexExist;
import static com.android.adservices.data.DbTestUtil.doesTableExistAndColumnCountMatch;

import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.MeasurementTables;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDbMigratorV2Test extends AbstractMeasurementDbMigratorTestBase {

    @Test
    public void performMigration_success() {
        // Setup
        DbHelper dbHelper = getDbHelper(1);
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        // Execution
        new MeasurementDbMigratorV2().performMigration(db, 1, 2);
        // To mimic real onUpgrade behaviour. Without closing the db, changes don't reflect.
        db.close();

        // Verify
        db = dbHelper.getReadableDatabase();
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.SourceContract.TABLE, 18));
        assertTrue(
                doesTableExistAndColumnCountMatch(db, MeasurementTables.TriggerContract.TABLE, 11));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.EventReportContract.TABLE, 12));
        assertTrue(
                doesTableExistAndColumnCountMatch(
                        db, MeasurementTables.AttributionRateLimitContract.TABLE, 6));
        assertTrue(doesIndexExist(db, "idx_msmt_source_ad_rt_et"));
        assertTrue(doesIndexExist(db, "idx_msmt_trigger_ad_rt_tt"));
        assertTrue(doesIndexExist(db, "idx_msmt_attribution_rate_limit_ss_ds_tt"));
    }

    @Override
    int getTargetVersion() {
        return 2;
    }

    @Override
    AbstractMeasurementDbMigrator getTestSubject() {
        return new MeasurementDbMigratorV2();
    }
}
