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

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_GET_FLEDGE_ENROLLMENT_DATA_FROM_DB_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_GET_PAS_ENROLLMENT_DATA_FROM_DB_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_PRIVACY_API_INVALID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_URI_ENROLLMENT_MATCH_FAILED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_URI_INVALID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_DELETE_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_INSERT_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.util.Pair;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.data.shared.SharedDbHelper;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.enrollment.EnrollmentStatus;
import com.android.adservices.service.enrollment.EnrollmentUtil;
import com.android.adservices.service.proto.PrivacySandboxApi;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.collect.ImmutableList;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class EnrollmentDaoTest extends AdServicesExtendedMockitoTestCase {

    private SharedDbHelper mDbHelper;
    private EnrollmentDao mEnrollmentDao;

    @Mock private AdServicesLogger mLogger;
    @Mock private EnrollmentUtil mEnrollmentUtil;
    @Mock private SharedDbHelper mMockDbHelper;

    public static final EnrollmentData ENROLLMENT_DATA1 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("1")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING"
                                    + " PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS")
                    .setSdkNames("1sdk")
                    .setAttributionSourceRegistrationUrl(List.of("https://1test.com/source"))
                    .setAttributionTriggerRegistrationUrl(List.of("https://1test.com/trigger"))
                    .setAttributionReportingUrl(List.of("https://1test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(List.of("https://1test.com"))
                    .setEncryptionKeyUrl("https://1test.com/keys")
                    .build();

    public static final EnrollmentData ENROLLMENT_DATA1_API_BASED =
            new EnrollmentData.Builder()
                    .setEnrollmentId("1")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING"
                                    + " PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS")
                    .setSdkNames("1sdk")
                    .setEnrolledSite("https://1test.com/")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA2 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("2")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE")
                    .setSdkNames(Arrays.asList("2sdk", "anotherSdk"))
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList(
                                    "https://2test.com/source",
                                    "https://2test-middle.com/source",
                                    "https://2test2.com/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList(
                                    "https://2test.com/trigger",
                                    "https://2test.com/trigger/extra/path"))
                    .setAttributionReportingUrl(List.of("https://2test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(List.of("https://2test.com"))
                    .setEncryptionKeyUrl("https://2test.com/keys")
                    .build();

    public static final EnrollmentData ENROLLMENT_DATA2_API_BASED =
            new EnrollmentData.Builder()
                    .setEnrollmentId("2")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE")
                    .setSdkNames(Arrays.asList("2sdk", "anotherSdk"))
                    .setEnrolledSite("https://2test.com")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA3 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("3")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION")
                    .setSdkNames("3sdk 31sdk")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://2test.com/source", "https://2test2.com/source"))
                    .setAttributionTriggerRegistrationUrl(List.of("https://2test.com/trigger"))
                    .setAttributionReportingUrl(List.of("https://2test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(List.of("https://2test.com"))
                    .setEncryptionKeyUrl("https://2test.com/keys")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA3_API_BASED =
            new EnrollmentData.Builder()
                    .setEnrollmentId("3")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION")
                    .setSdkNames("3sdk 31sdk")
                    .setEncryptionKeyUrl("https://3test.com")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA4 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("4")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS")
                    .setSdkNames("4sdk 41sdk")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList("https://4test.com", "https://prefix.test-prefix.com"))
                    .setAttributionTriggerRegistrationUrl(List.of("https://4test.com"))
                    .setAttributionReportingUrl(List.of("https://4test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(List.of("https://4test.com"))
                    .setEncryptionKeyUrl("https://4test.com/keys")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA4_API_BASED =
            new EnrollmentData.Builder()
                    .setEnrollmentId("4")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS")
                    .setEnrolledSite("https://4test.com")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA_LOCAL_API_BASED =
            new EnrollmentData.Builder()
                    .setEnrollmentId("4")
                    .setEnrolledAPIs(
                            "PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE PRIVACY_SANDBOX_API_TOPICS"
                                    + " PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setEnrolledSite("https://localhost")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA5 =
            new EnrollmentData.Builder()
                    .setEnrollmentId("5")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("5sdk 51sdk")
                    .setAttributionSourceRegistrationUrl(
                            Arrays.asList(
                                    "https://us.5test.com/source",
                                    "https://us.5test2.com/source",
                                    "https://port-test.5test3.com:443/source"))
                    .setAttributionTriggerRegistrationUrl(
                            Arrays.asList(
                                    "https://us.5test.com/trigger",
                                    "https://port-test.5test3.com:443/trigger"))
                    .setAttributionReportingUrl(List.of("https://us.5test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(List.of("https://us.5test.com"))
                    .setEncryptionKeyUrl("https://us.5test.com/keys")
                    .build();

    private static final EnrollmentData DUPLICATE_ID_ENROLLMENT_DATA =
            new EnrollmentData.Builder()
                    .setEnrollmentId("1")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                    .setSdkNames("4sdk")
                    .setAttributionSourceRegistrationUrl(List.of("https://4test.com/source"))
                    .setAttributionTriggerRegistrationUrl(List.of("https://4test.com/trigger"))
                    .setAttributionReportingUrl(List.of("https://4test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(List.of("https://4test.com"))
                    .setEncryptionKeyUrl("https://4test.com/keys")
                    .build();

    private static final EnrollmentData ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR =
            new EnrollmentData.Builder()
                    .setEnrollmentId("6")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE")
                    .setSdkNames("6sdk")
                    .setAttributionSourceRegistrationUrl(List.of("https://6test.com/source"))
                    .setAttributionTriggerRegistrationUrl(List.of("https://6test.com/trigger"))
                    .setAttributionReportingUrl(List.of("https://6test.com"))
                    .setRemarketingResponseBasedRegistrationUrl(
                            Arrays.asList(
                                    CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "")
                                            .toString(),
                                    CommonFixture.getUri(CommonFixture.VALID_BUYER_2, "")
                                            .toString()))
                    .setEncryptionKeyUrl("https://6test.com/keys")
                    .build();

    @Before
    public void setup() {
        mDbHelper = DbTestUtil.getSharedDbHelperForTest();
        when(mMockFlags.isEnableEnrollmentTestSeed()).thenReturn(false);
        when(mEnrollmentUtil.getBuildId()).thenReturn(1);
        mEnrollmentDao =
                new EnrollmentDao(
                        mContext,
                        mDbHelper,
                        mMockFlags,
                        mMockFlags.isEnableEnrollmentTestSeed(),
                        mLogger,
                        mEnrollmentUtil);
        // We want to clear the shared pref boolean value before each test.
        mEnrollmentDao.unSeed();
    }

    @After
    public void cleanup() {
        clearAllTables();
    }

    private void clearAllTables() {
        for (String table : EnrollmentTables.ENROLLMENT_TABLES) {
            mDbHelper.safeGetWritableDatabase().delete(table, null, null);
        }
    }

    @Test
    public void testInitialization() {
        // Check seeded
        EnrollmentDao spyEnrollmentDao =
                Mockito.spy(
                        new EnrollmentDao(
                                mContext,
                                mDbHelper,
                                mMockFlags,
                                mMockFlags.isEnableEnrollmentTestSeed(),
                                mLogger,
                                mEnrollmentUtil));
        Mockito.doReturn(false).when(spyEnrollmentDao).isSeeded();

        spyEnrollmentDao.seed();
        long count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertThat(count).isNotEqualTo(0);

        // Check that seeded enrollments are in the table.
        EnrollmentData e = spyEnrollmentDao.getEnrollmentData("E1");
        assertThat(e).isNotNull();
        assertThat(e.getSdkNames().get(0)).isEqualTo("sdk1");
        EnrollmentData e2 = spyEnrollmentDao.getEnrollmentData("E2");
        assertThat(e2).isNotNull();
        assertThat(e2.getSdkNames().get(0)).isEqualTo("sdk2");
        EnrollmentData e3 = spyEnrollmentDao.getEnrollmentData("E3");
        assertThat(e3).isNotNull();
        assertThat(e3.getSdkNames().get(0)).isEqualTo("sdk3");
        spyEnrollmentDao.deleteAll();
    }

    @Test
    public void testDelete() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e = mEnrollmentDao.getEnrollmentData("1");
        assertThat(e).isEqualTo(ENROLLMENT_DATA1);

        mEnrollmentDao.delete("1");
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e2 = mEnrollmentDao.getEnrollmentData("1");
        assertThat(e2).isNull();
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
        assertThat(count).isNotEqualTo(0);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        // Delete the whole table
        assertThat(mEnrollmentDao.deleteAll()).isTrue();
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        // Check seeded enrollments are deleted.
        count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertThat(count).isEqualTo(0);

        // Check unseeded.
        assertThat(mEnrollmentDao.isSeeded()).isFalse();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = SQLiteException.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_DELETE_ERROR,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)
    public void testDeleteAllDoesNotThrowException() {
        SharedDbHelper helper = Mockito.mock(SharedDbHelper.class);
        SQLiteDatabase readDb = mock(SQLiteDatabase.class);
        SQLiteDatabase db = mock(SQLiteDatabase.class);

        EnrollmentDao enrollmentDao =
                new EnrollmentDao(
                        mContext,
                        helper,
                        mMockFlags,
                        mMockFlags.isEnableEnrollmentTestSeed(),
                        mLogger,
                        mEnrollmentUtil);
        when(helper.safeGetWritableDatabase()).thenReturn(db);
        when(helper.safeGetReadableDatabase()).thenReturn(readDb);

        when(db.delete(eq(EnrollmentTables.EnrollmentDataContract.TABLE), eq(null), eq(null)))
                .thenThrow(SQLiteException.class);

        boolean result = enrollmentDao.deleteAll();
        assertThat(result).isFalse();
    }

    @Test
    public void testOverwriteEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        long count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertThat(count).isNotEqualTo(0);

        mEnrollmentDao.overwriteData(Arrays.asList(ENROLLMENT_DATA2, ENROLLMENT_DATA3));
        count =
                DatabaseUtils.queryNumEntries(
                        mDbHelper.getReadableDatabase(),
                        EnrollmentTables.EnrollmentDataContract.TABLE,
                        null);
        assertThat(count).isEqualTo(2);
        assertThat(mEnrollmentDao.getEnrollmentData(ENROLLMENT_DATA1.getEnrollmentId())).isNull();
        assertThat(mEnrollmentDao.getEnrollmentData(ENROLLMENT_DATA2.getEnrollmentId()))
                .isEqualTo(ENROLLMENT_DATA2);
        assertThat(mEnrollmentDao.getEnrollmentData(ENROLLMENT_DATA3.getEnrollmentId()))
                .isEqualTo(ENROLLMENT_DATA3);
    }

    @Test
    public void testGetEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e = mEnrollmentDao.getEnrollmentData("1");
        assertThat(e).isEqualTo(ENROLLMENT_DATA1);
    }

    @Test
    public void testGetAllEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);

        List<EnrollmentData> enrollmentDataList = mEnrollmentDao.getAllEnrollmentData();
        assertThat(enrollmentDataList).hasSize(2);
    }

    @Test
    public void initEnrollmentDao_ForEnrollmentSeedFlagOn_PerformsSeed() {
        when(mMockFlags.isEnableEnrollmentTestSeed()).thenReturn(true);
        EnrollmentDao enrollmentDao =
                new EnrollmentDao(
                        mContext,
                        mDbHelper,
                        mMockFlags,
                        mMockFlags.isEnableEnrollmentTestSeed(),
                        mLogger,
                        mEnrollmentUtil);

        for (EnrollmentData enrollmentData : PreEnrolledAdTechForTest.getList()) {
            EnrollmentData e = enrollmentDao.getEnrollmentData(enrollmentData.getEnrollmentId());
            assertThat(e).isEqualTo(enrollmentData);
        }
        enrollmentDao.deleteAll();
    }

    @Test
    public void initEnrollmentDao_ForEnrollmentSeedFlagOff_SkipsSeed() {
        when(mMockFlags.isEnableEnrollmentTestSeed()).thenReturn(false);
        for (EnrollmentData enrollmentData : PreEnrolledAdTechForTest.getList()) {
            EnrollmentData e = mEnrollmentDao.getEnrollmentData(enrollmentData.getEnrollmentId());
            assertThat(e).isNull();
        }
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndOriginData_isMatch() {
        // Site based matching should match if the DB has URLs with origins.
        mEnrollmentDao.insert(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test.com/source"));

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test.com"));

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test.com/anotherPath"));

        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test2.com/source"));

        EnrollmentData e5 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://us.5test.com/trigger"));

        assertThat(e1).isEqualTo(ENROLLMENT_DATA5);
        assertThat(e2).isEqualTo(ENROLLMENT_DATA5);
        assertThat(e3).isEqualTo(ENROLLMENT_DATA5);
        assertThat(e4).isEqualTo(ENROLLMENT_DATA5);
        assertThat(e5).isEqualTo(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(5))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(5)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndAnyPort_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com:443/source"));
        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com:8080/source"));
        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://port-test.5test3.com/source"));
        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://5test3.com/source"));

        assertThat(e1).isEqualTo(ENROLLMENT_DATA5);
        assertThat(e2).isEqualTo(ENROLLMENT_DATA5);
        assertThat(e3).isEqualTo(ENROLLMENT_DATA5);
        assertThat(e4).isEqualTo(ENROLLMENT_DATA5);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(4)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndSameSiteUri_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/source"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA2);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test2.com/source"));
        assertThat(e2).isEqualTo(ENROLLMENT_DATA2);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger"));
        assertThat(e3).isEqualTo(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndSameETLD_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.2test.com/source"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA2);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.2test2.com/source"));
        assertThat(e2).isEqualTo(ENROLLMENT_DATA2);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.2test.com/trigger"));
        assertThat(e3).isEqualTo(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndSamePath_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/source"));

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger"));

        assertThat(e1).isEqualTo(ENROLLMENT_DATA2);
        assertThat(e2).isEqualTo(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndIncompletePath_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/so"));
        assertThat(e).isEqualTo(ENROLLMENT_DATA2);
        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test2.com/so"));
        assertThat(e2).isEqualTo(ENROLLMENT_DATA2);
        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/tri"));
        assertThat(e3).isEqualTo(ENROLLMENT_DATA2);
        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger/extra"));
        assertThat(e4).isEqualTo(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(4)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndExtraPath_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/source/viewId/123"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA2);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test2.com/source/viewId/123"));
        assertThat(e2).isEqualTo(ENROLLMENT_DATA2);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger/clickId/123"));
        assertThat(e3).isEqualTo(ENROLLMENT_DATA2);

        EnrollmentData e4 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger/extra/path/clickId/123"));
        assertThat(e4).isEqualTo(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(4)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDifferentUri_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://4test.com/path"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA4);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.test-prefix.com/path"));
        assertThat(e2).isEqualTo(ENROLLMENT_DATA4);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://test-prefix.com/path"));
        assertThat(e3).isEqualTo(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndOneUrlInEnrollment_isMatch() {
        EnrollmentData data =
                new EnrollmentData.Builder()
                        .setEnrollmentId("5")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("5sdk 51sdk")
                        .setAttributionSourceRegistrationUrl(
                                List.of("https://prefix.test-prefix.com"))
                        .setAttributionTriggerRegistrationUrl(List.of("https://5test.com"))
                        .setAttributionReportingUrl(List.of("https://5test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(List.of("https://5test.com"))
                        .setEncryptionKeyUrl("https://5test.com/keys")
                        .build();
        mEnrollmentDao.insert(data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.test-prefix.com"));
        assertThat(e1).isEqualTo(data);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://another-prefix.prefix.test-prefix.com"));
        assertThat(e2).isEqualTo(data);

        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.test-prefix.com/path"));
        assertThat(e3).isEqualTo(data);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDiffSchemeUrl_matchesScheme() {
        EnrollmentData data =
                new EnrollmentData.Builder()
                        .setEnrollmentId("4")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("4sdk 41sdk")
                        .setAttributionSourceRegistrationUrl(
                                Arrays.asList("http://4test.com", "https://prefix.test-prefix.com"))
                        .setAttributionTriggerRegistrationUrl(List.of("https://4test.com"))
                        .setAttributionReportingUrl(List.of("https://4test.com"))
                        .setRemarketingResponseBasedRegistrationUrl(List.of("https://4test.com"))
                        .setEncryptionKeyUrl("https://4test.com/keys")
                        .build();
        mEnrollmentDao.insert(data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix.test-prefix.com"));
        assertThat(e1).isEqualTo(data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndSameSubdomainChild_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://test-prefix.com"));
        assertThat(e).isEqualTo(ENROLLMENT_DATA4);

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://other-prefix.test-prefix.com"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDifferentDomain_doesNotMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://abc2test.com/source"));
        assertThat(e).isNull();
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://abc2test.com/trigger"));
        assertThat(e1).isNull();
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDifferentScheme_doesNotMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("http://2test.com/source"));
        assertThat(e).isNull();
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("http://2test.com/trigger"));
        assertThat(e1).isNull();
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndDifferentETld_doesNotMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(Uri.parse("https://2test.co"));

        assertThat(e).isNull();
        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.co/source"));
        assertThat(e2).isNull();
        EnrollmentData e3 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.co/trigger"));
        assertThat(e3).isNull();
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_ForSiteMatchAndInvalidPublicSuffix_isNoMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://4test.invalid"));
        assertThat(e).isNull();
        verifyZeroInteractions(mLogger);

        EnrollmentData enrollmentData =
                new EnrollmentData.Builder()
                        .setEnrollmentId("4")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("4sdk 41sdk")
                        .setAttributionSourceRegistrationUrl(List.of("https://4test.invalid"))
                        .setAttributionTriggerRegistrationUrl(List.of("https://4test.invalid"))
                        .setAttributionReportingUrl(List.of("https://4test.invalid"))
                        .setRemarketingResponseBasedRegistrationUrl(
                                List.of("https://4test.invalid"))
                        .setEncryptionKeyUrl("https://4test.invalid/keys")
                        .build();
        mEnrollmentDao.insert(enrollmentData);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://4test.invalid"));
        assertThat(e1).isNull();
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testGetEnrollmentDataForFledgeByAdTechIdentifier() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        AdTechIdentifier adtechIdentifier = AdTechIdentifier.fromString("2test.com", false);
        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adtechIdentifier);
        assertWithMessage("Found enrollment").that(e).isEqualTo(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void testGetAllFledgeEnrolledAdTechs_noEntries() {
        // Delete any entries in the database
        clearAllTables();

        assertThat(mEnrollmentDao.getAllFledgeEnrolledAdTechs()).isEmpty();
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void testGetAllFledgeEnrolledAdTechs_multipleEntries() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Set<AdTechIdentifier> enrolledFledgeAdTechIdentifiers =
                mEnrollmentDao.getAllFledgeEnrolledAdTechs();

        assertThat(enrolledFledgeAdTechIdentifiers).hasSize(2);
        assertThat(enrolledFledgeAdTechIdentifiers)
                .containsExactly(
                        AdTechIdentifier.fromString("1test.com"),
                        AdTechIdentifier.fromString("2test.com"));
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_nullUri() {
        assertWithMessage("Returned enrollment pair")
                .that(mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(null))
                .isNull();
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_emptyUri() {
        assertWithMessage("Returned enrollment pair")
                .that(
                        mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                                Uri.EMPTY))
                .isNull();
        verifyZeroInteractions(mLogger);
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_noMatchFound() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Uri nonMatchingUri =
                CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(nonMatchingUri);

        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_matchesHostExactly() {
        mEnrollmentDao.insert(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Uri exactMatchUri = CommonFixture.getUri(CommonFixture.VALID_BUYER_1, "/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(exactMatchUri);

        assertThat(enrollmentResult).isNotNull();
        assertWithMessage("Returned EnrollmentData")
                .that(enrollmentResult.second)
                .isEqualTo(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        assertWithMessage("Returned AdTechIdentifier")
                .that(enrollmentResult.first)
                .isEqualTo(CommonFixture.VALID_BUYER_1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_matchesHostSubdomain() {
        mEnrollmentDao.insert(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Uri subdomainMatchUri =
                CommonFixture.getUriWithValidSubdomain(
                        CommonFixture.VALID_BUYER_2.toString(), "/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                        subdomainMatchUri);

        assertThat(enrollmentResult).isNotNull();
        assertWithMessage("Returned EnrollmentData")
                .that(enrollmentResult.second)
                .isEqualTo(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        assertWithMessage("Returned AdTechIdentifier")
                .that(enrollmentResult.first)
                .isEqualTo(CommonFixture.VALID_BUYER_2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_nonMatchingSubstring() {
        mEnrollmentDao.insert(ENROLLMENT_DATA_MULTIPLE_FLEDGE_RBR);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        // Note this URI is missing a "." separating the prefix from the expected host
        Uri nonMatchingSubstringUri =
                CommonFixture.getUri(
                        "prefixstring" + CommonFixture.VALID_BUYER_2, "/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                        nonMatchingSubstringUri);

        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_GET_FLEDGE_ENROLLMENT_DATA_FROM_DB_FAILED,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE,
            throwable = SQLiteException.class)
    public void
            testGetEnrollmentDataForFledgeByMatchingAdTechIdentifier_logCelIfFailedGettingEnrollment() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(false);
        Uri testUri = Uri.parse("https://1test.com' AND");

        expect.withMessage("enrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier")
                .that(mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(testUri))
                .isNull();
    }

    @Test
    public void testGetEnrollmentDataFromSdkName() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e = mEnrollmentDao.getEnrollmentDataFromSdkName("2sdk");
        assertThat(e).isEqualTo(ENROLLMENT_DATA2);
        EnrollmentData e2 = mEnrollmentDao.getEnrollmentDataFromSdkName("anotherSdk");
        assertThat(e2).isEqualTo(ENROLLMENT_DATA2);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));

        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e3 = mEnrollmentDao.getEnrollmentDataFromSdkName("31sdk");
        assertThat(e3).isEqualTo(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(3))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(3)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_GET_PAS_ENROLLMENT_DATA_FROM_DB_FAILED,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS,
            throwable = SQLiteException.class)
    public void testGetEnrollmentDataFromSdkNameLogCelIfSqlException() {
        expect.withMessage("enrollmentDao.getEnrollmentDataFromSdkName")
                .that(mEnrollmentDao.getEnrollmentDataFromSdkName("sdk'AND"))
                .isNull();
    }

    @Test
    public void testDuplicateEnrollmentData() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData e = mEnrollmentDao.getEnrollmentData("1");
        assertThat(e).isEqualTo(ENROLLMENT_DATA1);

        mEnrollmentDao.insert(DUPLICATE_ID_ENROLLMENT_DATA);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        e = mEnrollmentDao.getEnrollmentData("1");
        assertThat(e).isEqualTo(DUPLICATE_ID_ENROLLMENT_DATA);
    }

    @Test
    public void testGetEnrollmentRecordsCountForLogging_insertionsMatchCount() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        mEnrollmentDao.insert(DUPLICATE_ID_ENROLLMENT_DATA);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        int enrollmentRecordsCount = mEnrollmentDao.getEnrollmentRecordCountForLogging();
        assertThat(enrollmentRecordsCount).isEqualTo(3);
    }

    @Test
    public void testGetEnrollmentRecordsCountForLogging_limitedEnrollmentLoggingEnabled() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        when(mMockFlags.getEnrollmentEnableLimitedLogging()).thenReturn(true);
        int enrollmentRecordsCount = mEnrollmentDao.getEnrollmentRecordCountForLogging();
        assertThat(enrollmentRecordsCount).isEqualTo(-2);
    }

    @Test
    public void testGetEnrollmentRecordsCountForLogging_databaseError() {
        EnrollmentDao enrollmentDao =
                new EnrollmentDao(
                        mContext,
                        mMockDbHelper,
                        mMockFlags,
                        mMockFlags.isEnableEnrollmentTestSeed(),
                        mLogger,
                        mEnrollmentUtil);
        enrollmentDao.insert(ENROLLMENT_DATA1);
        enrollmentDao.insert(ENROLLMENT_DATA2);
        enrollmentDao.insert(ENROLLMENT_DATA3);
        when(mMockFlags.getEnrollmentEnableLimitedLogging()).thenReturn(false);
        when(mMockDbHelper.safeGetWritableDatabase()).thenReturn(null);
        int enrollmentRecordsCount = enrollmentDao.getEnrollmentRecordCountForLogging();
        assertThat(enrollmentRecordsCount).isEqualTo(-1);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            throwable = Any.class,
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DATA_INSERT_ERROR,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT)
    public void testInsert_throwsSQLException_logsCEL() {
        SQLiteDatabase db = mock(SQLiteDatabase.class);
        EnrollmentData enrollmentData = mock(EnrollmentData.class);

        when(mMockDbHelper.safeGetWritableDatabase()).thenReturn(db);
        when(db.insertWithOnConflict(
                        eq(EnrollmentTables.EnrollmentDataContract.TABLE), any(), any(), anyInt()))
                .thenThrow(new SQLException());

        assertThat(mEnrollmentDao.insert(enrollmentData)).isFalse();
    }

    @Test
    public void testGetEnrollmentData_enrollmentDataGetEnrolledAPIs() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        // Checking ENROLLMENT_DATA2
        EnrollmentData enrollmentData2 = mEnrollmentDao.getEnrollmentData("2");
        assertThat(enrollmentData2).isNotNull();

        String enrolledAPIsString2 =
                "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING"
                        + " PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE";
        assertThat(enrollmentData2.getEnrolledAPIsString()).isEqualTo(enrolledAPIsString2);

        List<PrivacySandboxApi> enrolledAPIs2 =
                ImmutableList.of(
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE,
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING);
        assertThat(enrollmentData2.getEnrolledAPIs()).hasSize(2);
        assertThat(enrollmentData2.getEnrolledAPIs()).containsExactlyElementsIn(enrolledAPIs2);

        // Checking ENROLLMENT_DATA3
        EnrollmentData enrollmentData3 = mEnrollmentDao.getEnrollmentData("3");
        assertThat(enrollmentData3).isNotNull();

        String enrolledAPIsString3 =
                "PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING PRIVACY_SANDBOX_API_TOPICS"
                        + " PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION";
        assertThat(enrollmentData3.getEnrolledAPIsString()).isEqualTo(enrolledAPIsString3);

        List<PrivacySandboxApi> enrolledAPIs3 =
                ImmutableList.of(
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_TOPICS,
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_PRIVATE_AGGREGATION,
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING);
        assertThat(enrollmentData3.getEnrolledAPIs()).hasSize(3);
        assertThat(enrollmentData3.getEnrolledAPIs()).containsExactlyElementsIn(enrolledAPIs3);
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Pair<AdTechIdentifier, EnrollmentData> paired =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        Uri.parse("https://4test.com/"));
        assertThat(paired).isNotNull();
        assertThat(paired.first).isEqualTo(AdTechIdentifier.fromString("4test.com"));
        assertThat(paired.second).isEqualTo(ENROLLMENT_DATA4);
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsNotMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        Uri.parse("https://2test.com/"));
        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsMatchWithSubdomain() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        Uri subdomainMatchUri = Uri.parse("https://example.abc.4test.com/path/for/resource");

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(subdomainMatchUri);
        assertThat(enrollmentResult).isNotNull();
        assertThat(enrollmentResult.first).isEqualTo(AdTechIdentifier.fromString("4test.com"));
        assertThat(enrollmentResult.second).isEqualTo(ENROLLMENT_DATA4);
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsMatchNoPASEnrollments() {
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        Uri.parse("https://2test.com"));
        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
    }

    @Test
    public void getEnrollmentDataForPASByMatchingAdTechIdentifier_uriIsNotMatchPASEnrollments() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByMatchingAdTechIdentifier(
                        Uri.parse("https://test2.com"));
        assertWithMessage("Returned enrollment result").that(enrollmentResult).isNull();
    }

    @Test
    public void getEnrollmentDataForPASByAdTechIdentifier_isMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        AdTechIdentifier adTechIdentifier = AdTechIdentifier.fromString("1test.com");
        EnrollmentData enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByAdTechIdentifier(adTechIdentifier);
        assertThat(enrollmentResult).isEqualTo(ENROLLMENT_DATA1);
    }

    @Test
    public void getEnrollmentDataForPASByAdTechIdentifier_isNotMatch() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        AdTechIdentifier adTechIdentifier = AdTechIdentifier.fromString("2test.com");
        EnrollmentData enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForPASByAdTechIdentifier(adTechIdentifier);
        assertThat(enrollmentResult).isNull();
    }

    @Test
    public void getAllPASEnrolledAdTechs_multipleEntries() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1);
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Set<AdTechIdentifier> enrolledPASAdTechIdentifiers =
                mEnrollmentDao.getAllPASEnrolledAdTechs();

        assertThat(enrolledPASAdTechIdentifiers).hasSize(2);
        assertThat(enrolledPASAdTechIdentifiers)
                .containsExactly(
                        AdTechIdentifier.fromString("1test.com"),
                        AdTechIdentifier.fromString("4test.com"));
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void getAllPASEnrolledAdTechs_noEntries() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);
        mEnrollmentDao.insert(ENROLLMENT_DATA3);
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Set<AdTechIdentifier> enrolledPASAdTechIdentifiers =
                mEnrollmentDao.getAllPASEnrolledAdTechs();

        assertThat(enrolledPASAdTechIdentifiers).isEmpty();
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void getAllPASEnrolledAdTechs_multipleEntries_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA1_API_BASED);
        mEnrollmentDao.insert(ENROLLMENT_DATA2_API_BASED);
        mEnrollmentDao.insert(ENROLLMENT_DATA3_API_BASED);
        mEnrollmentDao.insert(ENROLLMENT_DATA4_API_BASED);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        Set<AdTechIdentifier> enrolledPASAdTechIdentifiers =
                mEnrollmentDao.getAllPASEnrolledAdTechs();

        assertThat(enrolledPASAdTechIdentifiers).hasSize(2);
        assertThat(enrolledPASAdTechIdentifiers)
                .containsExactly(
                        AdTechIdentifier.fromString("1test.com"),
                        AdTechIdentifier.fromString("4test.com"));
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void getEnrollmentDataForAPIByUrl_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA1_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        EnrollmentData enrollmentData =
                mEnrollmentDao.getEnrollmentDataForAPIByUrl(
                        Uri.parse("https://abc.example.1test.com"),
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING);

        assertThat(enrollmentData).isEqualTo(ENROLLMENT_DATA1_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_URI_ENROLLMENT_MATCH_FAILED,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE)
    public void getEnrollmentDataForAPIByUrl_logCelIfFailedMatchingEnrollmentByUrl() {
        mEnrollmentDao.insert(ENROLLMENT_DATA1_API_BASED);

        expect.withMessage("mEnrollmentDao.getEnrollmentDataForAPIByUrl")
                .that(
                        mEnrollmentDao.getEnrollmentDataForAPIByUrl(
                                Uri.parse("https://abc.example.1test.com"),
                                PrivacySandboxApi.PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE))
                .isNull();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_PRIVACY_API_INVALID,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE)
    public void getEnrollmentDataForAPIByUrl_logCelIfInvalidPrivacyApi() {
        expect.withMessage("mEnrollmentDao.getEnrollmentDataForAPIByUrl")
                .that(mEnrollmentDao.getEnrollmentDataForAPIByUrl(Uri.parse("test"), null))
                .isNull();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_URI_INVALID,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
    public void getEnrollmentDataForAPIByUrl_invalidUriWithTopicApi_logCel() {
        expect.withMessage("mEnrollmentDao.getEnrollmentDataForAPIByUrl")
                .that(
                        mEnrollmentDao.getEnrollmentDataForAPIByUrl(
                                null, PrivacySandboxApi.PRIVACY_SANDBOX_API_TOPICS))
                .isNull();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_URI_INVALID,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
    public void getEnrollmentDataForAPIByUrl_invalidUriWithPASApi_logCel() {
        expect.withMessage("mEnrollmentDao.getEnrollmentDataForAPIByUrl")
                .that(
                        mEnrollmentDao.getEnrollmentDataForAPIByUrl(
                                null, PrivacySandboxApi.PRIVACY_SANDBOX_API_PROTECTED_APP_SIGNALS))
                .isNull();
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__ENROLLMENT_DAO_URI_INVALID,
            ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE)
    public void getEnrollmentDataForAPIByUrl_invalidUriWithPAApi_logCel() {
        expect.withMessage("mEnrollmentDao.getEnrollmentDataForAPIByUrl")
                .that(
                        mEnrollmentDao.getEnrollmentDataForAPIByUrl(
                                null, PrivacySandboxApi.PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE))
                .isNull();
    }

    @Test
    public void testGetEnrollmentDataForFledgeByAdTechIdentifier_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA2_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        AdTechIdentifier adtechIdentifier = AdTechIdentifier.fromString("2test.com", false);
        EnrollmentData e =
                mEnrollmentDao.getEnrollmentDataForFledgeByAdTechIdentifier(adtechIdentifier);
        assertThat(e).isEqualTo(ENROLLMENT_DATA2_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getAllEnrollmentDataByAPI_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA1_API_BASED);
        mEnrollmentDao.insert(ENROLLMENT_DATA2_API_BASED);
        mEnrollmentDao.insert(ENROLLMENT_DATA3_API_BASED);
        mEnrollmentDao.insert(ENROLLMENT_DATA4_API_BASED);
        verify(mEnrollmentUtil, times(4))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));

        List<EnrollmentData> enrollmentDataList =
                mEnrollmentDao.getAllEnrollmentDataByAPI(
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING);

        assertThat(enrollmentDataList).hasSize(3);
        assertThat(enrollmentDataList)
                .containsExactly(
                        ENROLLMENT_DATA1_API_BASED,
                        ENROLLMENT_DATA2_API_BASED,
                        ENROLLMENT_DATA3_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA2_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/source"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA2_API_BASED);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2test.com/trigger"));
        assertThat(e2).isEqualTo(ENROLLMENT_DATA2_API_BASED);

        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_fullOriginPath_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA2_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://example.2test.com/source"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA2_API_BASED);

        EnrollmentData e2 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://example.2test.com/trigger"));
        assertThat(e2).isEqualTo(ENROLLMENT_DATA2_API_BASED);

        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_noMatchFound_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA2_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://3test.com/source"));
        assertThat(e1).isNull();

        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(false), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_upperUri_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA2_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://2TEST.com/source"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA2_API_BASED);

        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_uriWithPort_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA_LOCAL_API_BASED);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://localhost:8383"));
        assertThat(e1).isEqualTo(ENROLLMENT_DATA_LOCAL_API_BASED);

        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(1)).logEnrollmentMatchStats(eq(mLogger), eq(true), eq(1));
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_urlPrefix_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        EnrollmentData data =
                new EnrollmentData.Builder()
                        .setEnrollmentId("5")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("5sdk 51sdk")
                        .setEnrolledSite("https://prefix_host.com")
                        .build();
        mEnrollmentDao.insert(data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(Uri.parse("https://prefix.com"));
        assertThat(e1).isNull();
    }

    @Test
    public void getEnrollmentDataFromMeasurementUrl_enrollmentPrefix_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        EnrollmentData data =
                new EnrollmentData.Builder()
                        .setEnrollmentId("5")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("5sdk 51sdk")
                        .setEnrolledSite("https://prefix.com")
                        .build();
        mEnrollmentDao.insert(data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://prefix_host.com"));
        assertThat(e1).isNull();
    }

    @Test
    public void
            getEnrollmentDataFromMeasurementUrl_uriWithSubdomainPrefix_enrollmentApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        EnrollmentData data =
                new EnrollmentData.Builder()
                        .setEnrollmentId("5")
                        .setEnrolledAPIs("PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING")
                        .setSdkNames("5sdk 51sdk")
                        .setEnrolledSite("https://my_prefix.com")
                        .build();
        mEnrollmentDao.insert(data);
        verify(mEnrollmentUtil, times(1))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        EnrollmentData e1 =
                mEnrollmentDao.getEnrollmentDataFromMeasurementUrl(
                        Uri.parse("https://my_prefix.site.com "));
        assertThat(e1).isNull();
    }

    @Test
    public void
            getEnrollmentDataForFledgeByMatchingAdTechIdentifier_disableToEnableApiBasedSchema() {
        mEnrollmentDao.insert(ENROLLMENT_DATA2);

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                        Uri.parse("https://example.2test.com/path/to/resource"));
        assertThat(enrollmentResult).isNotNull();
        assertWithMessage("Returned EnrollmentData")
                .that(enrollmentResult.second)
                .isEqualTo(ENROLLMENT_DATA2);
        assertWithMessage("Returned AdTechIdentifier")
                .that(enrollmentResult.first)
                .isEqualTo(AdTechIdentifier.fromString("2test.com"));

        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA2_API_BASED);

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResultAPIBased =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                        Uri.parse("https://example.2test.com/path/to/resource"));
        assertThat(enrollmentResultAPIBased).isNotNull();
        assertWithMessage("Returned EnrollmentData")
                .that(enrollmentResultAPIBased.second)
                .isEqualTo(ENROLLMENT_DATA2_API_BASED);
        assertWithMessage("Returned AdTechIdentifier")
                .that(enrollmentResultAPIBased.first)
                .isEqualTo(AdTechIdentifier.fromString("2test.com"));

        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }

    @Test
    public void
            getEnrollmentDataForFledgeByMatchingAdTechIdentifier_enableToDisableApiBasedSchema() {
        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(true);
        mEnrollmentDao.insert(ENROLLMENT_DATA4_API_BASED);

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResultAPIBased =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                        Uri.parse("https://example.4test.com/path/to/resource"));
        assertThat(enrollmentResultAPIBased).isNotNull();
        assertWithMessage("Returned EnrollmentData")
                .that(enrollmentResultAPIBased.second)
                .isEqualTo(ENROLLMENT_DATA4_API_BASED);
        assertWithMessage("Returned AdTechIdentifier")
                .that(enrollmentResultAPIBased.first)
                .isEqualTo(AdTechIdentifier.fromString("4test.com"));

        when(mMockFlags.getEnrollmentApiBasedSchemaEnabled()).thenReturn(false);
        mEnrollmentDao.insert(ENROLLMENT_DATA4);

        Pair<AdTechIdentifier, EnrollmentData> enrollmentResult =
                mEnrollmentDao.getEnrollmentDataForFledgeByMatchingAdTechIdentifier(
                        Uri.parse("https://example.4test.com/path/to/resource"));
        assertThat(enrollmentResult).isNotNull();
        assertWithMessage("Returned EnrollmentData")
                .that(enrollmentResult.second)
                .isEqualTo(ENROLLMENT_DATA4);
        assertWithMessage("Returned AdTechIdentifier")
                .that(enrollmentResult.first)
                .isEqualTo(AdTechIdentifier.fromString("4test.com"));

        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.WRITE_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
        verify(mEnrollmentUtil, times(2))
                .logEnrollmentDataStats(
                        eq(mLogger),
                        eq(EnrollmentStatus.TransactionType.READ_TRANSACTION_TYPE.getValue()),
                        eq(true),
                        eq(1));
    }
}
