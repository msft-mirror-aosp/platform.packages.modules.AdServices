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

package com.android.adservices.data.enrollment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdTechIdentifier;
import android.content.Context;
import android.database.DatabaseUtils;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.service.enrollment.EnrollmentData;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

public class EnrollmentDaoTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private DbHelper mDbHelper;
    private EnrollmentDao mEnrollmentDao;

    private static final EnrollmentData ENROLLMENT_DATA1 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("1")
                    .setCompanyId("1001")
                    .setSdkNames("1sdk")
                    .setAttributionSourceRegistrationUrl(Arrays.asList("https://1test.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://1test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://1test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://1test.com"))
                    .setEncryptionKeyUrl(Arrays.asList("https://1test.com/keys"))
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA2 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("2")
                    .setCompanyId("1002")
                    .setSdkNames(Arrays.asList("2sdk", "anotherSdk"))
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://2test.com/source", "https://2test2.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList("https://2test.com/trigger"))
                    .setAttributionReportingUrl(Arrays.asList("https://2test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(Arrays.asList("https://2test.com"))
                    .setEncryptionKeyUrl(Arrays.asList("https://2test.com/keys"))
                    .build();

    @Before
    public void setup() {
        mDbHelper = DbHelper.getInstance(sContext);
        mEnrollmentDao = new EnrollmentDao(sContext, mDbHelper);
    }

    @After
    public void cleanup() {
        for (String table : EnrollmentTables.ENROLLMENT_TABLES) {
            mDbHelper.safeGetWritableDatabase().delete(table, null, null);
        }
    }

    @Test
    public void testInitialization() {
        // Check seeded
        assertTrue(mEnrollmentDao.isSeeded());
        long count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertNotEquals(count, 0);

        // Check that seeded enrollments are in the table.
        EnrollmentData e = mEnrollmentDao.getEnrollmentData("E1");
        assertNotNull(e);
        assertEquals(e.getSdkNames().get(0), "sdk1");
        EnrollmentData e2 = mEnrollmentDao.getEnrollmentData("E2");
        assertNotNull(e2);
        assertEquals(e2.getSdkNames().get(0), "sdk2");
        EnrollmentData e3 = mEnrollmentDao.getEnrollmentData("E3");
        assertNotNull(e3);
        assertEquals(e3.getSdkNames().get(0), "sdk3");
    }

    @Test
    public void testDelete() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        EnrollmentData e = mEnrollmentDao.getEnrollmentData("1");
        assertNotNull(e);
        assertEquals(e, ENROLLMENT_DATA1);

        mEnrollmentDao.delete("1");
        EnrollmentData e2 = mEnrollmentDao.getEnrollmentData("1");
        assertNull(e2);
    }

    @Test
    public void testDeleteAll() {
        // Insert a record
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        long count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertNotEquals(count, 0);

        // Delete the whole table
        assertTrue(mEnrollmentDao.deleteAll());

        // Check seeded enrollments are deleted.
        count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertEquals(count, 0);

        // Check unseeded.
        assertFalse(mEnrollmentDao.isSeeded());
    }

    @Test
    public void testGetEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        EnrollmentData e = mEnrollmentDao.getEnrollmentData("1");
        assertNotNull(e);
        assertEquals(e, ENROLLMENT_DATA1);
    }

    @Test
    public void testGetEnrollmentDataFromMeasurementUrl() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        EnrollmentData e = mEnrollmentDao.getEnrollmentDataFromMeasurementUrl("2test.com/source");
        assertNotNull(e);
        assertEquals(e, ENROLLMENT_DATA2);
        EnrollmentData e2 = mEnrollmentDao.getEnrollmentDataFromMeasurementUrl("2test2.com/source");
        assertNotNull(e2);
        assertEquals(e, e2);
    }

    @Test
    public void testGetEnrollmentDataForFledgeByAdTechIdentifier() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        AdTechIdentifier adtechIdentifier = AdTechIdentifier.fromString("2test.com", false);
        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adtechIdentifier);
        assertNotNull(e);
        assertEquals(e, ENROLLMENT_DATA2);
    }

    @Test
    public void testGetEnrollmentDataFromSdkName() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        EnrollmentData e = mEnrollmentDao.getEnrollmentDataFromSdkName("2sdk");
        assertNotNull(e);
        assertEquals(e, ENROLLMENT_DATA2);

        EnrollmentData e2 = mEnrollmentDao.getEnrollmentDataFromSdkName("anotherSdk");
        assertNotNull(e2);
        assertEquals(e2, ENROLLMENT_DATA2);
    }
}
