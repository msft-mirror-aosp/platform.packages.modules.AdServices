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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.database.Cursor;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.service.enrollment.EnrollmentData;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class EnrollmentDaoTest {

    private final DbHelper mDbHelper = DbTestUtil.getDbHelperForTest();
    private final EnrollmentDao mEnrollmentDao = new EnrollmentDao(mDbHelper);

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

        mEnrollmentDao.insertEnrollmentData(enrollmentData);
        mEnrollmentDao.insertEnrollmentData(enrollmentData1);

        try (Cursor cursor =
                mDbHelper
                        .getReadableDatabase()
                        .query(
                                EnrollmentTables.EnrollmentDataContract.TABLE,
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

        assertNotNull(mEnrollmentDao.getEnrollmentData("1"));
        assertEquals(
                Objects.requireNonNull(
                                mEnrollmentDao.getEnrollmentDataGivenUrl(
                                        "https://source.example1.com"))
                        .getEnrollmentId(),
                "1");
        assertEquals(
                Objects.requireNonNull(mEnrollmentDao.getEnrollmentDataGivenSdkName("Admob"))
                        .getEnrollmentId(),
                "1");
        assertNull(mEnrollmentDao.getEnrollmentDataGivenSdkName("null"));
        mEnrollmentDao.deleteEnrollmentData("1");
        assertNull(mEnrollmentDao.getEnrollmentData("1"));
    }
}
