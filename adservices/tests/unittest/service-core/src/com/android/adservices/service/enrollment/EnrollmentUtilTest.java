/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.enrollment;

import static com.android.adservices.service.FlagsConstants.KEY_CONFIG_DELIVERY__ENABLE_ENROLLMENT_CONFIG_V3_DATA_DOWNLOAD;
import static com.android.adservices.service.FlagsConstants.KEY_CONFIG_DELIVERY__USE_ARGON_CONFIG_MANAGER_TO_QUERY_ENROLLMENT;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.data.configdelivery.ArgonConfigurationManager;
import com.android.adservices.data.configdelivery.Configuration;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.proto.PrivacySandboxApi;
import com.android.adservices.service.proto.RbEnrollment;
import com.android.adservices.service.stats.AdServicesEnrollmentTransactionStats;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.testing.annotations.SetFlagFalse;
import com.android.adservices.shared.testing.annotations.SetFlagTrue;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/** Unit tests for {@link EnrollmentUtil} */
@ExtendedMockitoRule.SpyStatic(EnrollmentUtil.class)
@ExtendedMockitoRule.SpyStatic(EnrollmentDao.class)
@ExtendedMockitoRule.MockStatic(ArgonConfigurationManager.class)
@ExtendedMockitoRule.SpyStatic(ApplicationContextSingleton.class)
@ExtendedMockitoRule.MockStatic(FlagsFactory.class)
public final class EnrollmentUtilTest extends AdServicesExtendedMockitoTestCase {
    @Mock private AdServicesLogger mLogger;
    @Mock private SharedPreferences mMockSharedPreferences;
    @Mock private EnrollmentDao mMockEnrollmentDao;
    @Mock private ArgonConfigurationManager mMockArgonConfigurationManager;

    @Before
    public void setUp() throws Exception {
        resetEnrollmentUtilSingleton();
        doReturn(mFakeFlags).when(FlagsFactory::getFlags);
    }

    @Test
    public void logEnrollmentFileDownloadStats_nullInput_defaultValuesUsed() {
        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance();
        boolean isSuccessful = true;
        String buildId = null;
        int defaultBuildId = -1;
        enrollmentUtil.logEnrollmentFileDownloadStats(mLogger, isSuccessful, buildId);
        verify(mLogger).logEnrollmentFileDownloadStats(eq(isSuccessful), eq(defaultBuildId));
    }

    @Test
    public void logEnrollmentFailedStats_nullInput_defaultValuesUsed() {
        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance();
        int buildId = -1;
        int fileGroupStatus = 0;
        int enrollmentRecordCount = 2;
        int errorCause = EnrollmentStatus.ErrorCause.ENROLLMENT_BLOCKLISTED_ERROR_CAUSE.getValue();
        String queryParameter = null;
        String defaultQueryParameter = "";
        enrollmentUtil.logEnrollmentFailedStats(
                mLogger,
                buildId,
                fileGroupStatus,
                enrollmentRecordCount,
                queryParameter,
                errorCause);
        verify(mLogger)
                .logEnrollmentFailedStats(
                        eq(buildId),
                        eq(fileGroupStatus),
                        eq(enrollmentRecordCount),
                        eq(defaultQueryParameter),
                        eq(errorCause));
    }

    @Test
    @SetFlagFalse(KEY_CONFIG_DELIVERY__ENABLE_ENROLLMENT_CONFIG_V3_DATA_DOWNLOAD)
    @SetFlagFalse(KEY_CONFIG_DELIVERY__USE_ARGON_CONFIG_MANAGER_TO_QUERY_ENROLLMENT)
    public void getBuildId_enrollmentV3Disabled_usesSharedPreferences() {
        doReturn(mMockContext).when(ApplicationContextSingleton::get);
        when(mMockContext.getSharedPreferences(
                        eq(EnrollmentUtil.ENROLLMENT_SHARED_PREF), eq(Context.MODE_PRIVATE)))
                .thenReturn(mMockSharedPreferences);
        when(mMockSharedPreferences.getInt(EnrollmentUtil.BUILD_ID, -1)).thenReturn(123);

        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance();

        assertEquals(123, enrollmentUtil.getBuildId());
    }

    @Test
    @SetFlagTrue(KEY_CONFIG_DELIVERY__ENABLE_ENROLLMENT_CONFIG_V3_DATA_DOWNLOAD)
    @SetFlagTrue(KEY_CONFIG_DELIVERY__USE_ARGON_CONFIG_MANAGER_TO_QUERY_ENROLLMENT)
    public void getBuildId_enrollmentV3Enabled_usesArgonConfigManager() {
        doReturn(mMockContext).when(ApplicationContextSingleton::get);
        when(mMockContext.getSharedPreferences(
                        eq(EnrollmentUtil.ENROLLMENT_SHARED_PREF), eq(Context.MODE_PRIVATE)))
                .thenReturn(mMockSharedPreferences);
        doReturn(mMockArgonConfigurationManager)
                .when(() -> ArgonConfigurationManager.getInstance(any(), any()));
        when(mMockArgonConfigurationManager.getLatestVersion()).thenReturn(456L);

        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance();

        assertEquals(456, enrollmentUtil.getBuildId());
    }

    @Test
    public void toEnrollmentData_validConfiguration_mapsCorrectly() {
        String configId = "test-config-id";
        List<PrivacySandboxApi> enrolledApis =
                Arrays.asList(
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_ATTRIBUTION_REPORTING,
                        PrivacySandboxApi.PRIVACY_SANDBOX_API_PROTECTED_AUDIENCE);
        List<String> sdkNames = Arrays.asList("sdk1.com", "sdk2.org");
        String site = "https://example.test";

        RbEnrollment rbEnrollmentProto =
                RbEnrollment.newBuilder()
                        .addAllEnrolledApis(enrolledApis)
                        .addAllSdkNames(sdkNames)
                        .setEnrolledSite(site)
                        .build();

        Configuration mockConfiguration = mock(Configuration.class);
        when(mockConfiguration.getId()).thenReturn(configId);
        when(mockConfiguration.getValue(RbEnrollment.getDefaultInstance()))
                .thenReturn(rbEnrollmentProto);

        EnrollmentData result = EnrollmentUtil.toEnrollmentData(mockConfiguration);

        assertNotNull(result);
        assertEquals(configId, result.getEnrollmentId());
        assertEquals(enrolledApis, result.getEnrolledAPIs());
        assertEquals(sdkNames, result.getSdkNames());
        assertEquals(site, result.getEnrolledSite());
        assertEquals(site, result.getAttributionSourceRegistrationUrl().get(0));
        assertEquals(site, result.getAttributionTriggerRegistrationUrl().get(0));
        assertEquals(site, result.getAttributionReportingUrl().get(0));
        assertEquals(site, result.getRemarketingResponseBasedRegistrationUrl().get(0));
        assertEquals(site, result.getEncryptionKeyUrl());
    }

    @Test
    public void toEnrollmentData_nullConfiguration_returnsNull() {
        assertNull(EnrollmentUtil.toEnrollmentData(null));
    }

    @Test
    public void toEnrollmentData_nullRbEnrollmentInConfiguration_returnsNull() {
        Configuration mockConfiguration = mock(Configuration.class);
        when(mockConfiguration.getId()).thenReturn("some-id");
        when(mockConfiguration.getValue(RbEnrollment.getDefaultInstance())).thenReturn(null);

        assertNull(EnrollmentUtil.toEnrollmentData(mockConfiguration));
    }

    @Test
    public void logTransactionStats2_callsLoggerWithCorrectArgs() {
        AdServicesEnrollmentTransactionStats.Builder statsBuilder =
                AdServicesEnrollmentTransactionStats.builder()
                        .setTransactionType(
                                AdServicesEnrollmentTransactionStats.TransactionType.INSERT)
                        .setTransactionParameterCount(1)
                        .setDataSourceRecordCountPre(5);
        AdServicesEnrollmentTransactionStats.TransactionStatus status =
                AdServicesEnrollmentTransactionStats.TransactionStatus.SUCCESS;
        int queryResultCount = 3;
        int transactionResultCount = 1;
        int dataSourceRecordCountPost = 6;
        int latencyMs = 150;
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        when(mMockEnrollmentDao.useV3EnrollmentData()).thenReturn(false);
        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance();

        enrollmentUtil.logTransactionStats(
                mLogger,
                statsBuilder,
                queryResultCount,
                transactionResultCount,
                dataSourceRecordCountPost,
                latencyMs);

        ArgumentCaptor<AdServicesEnrollmentTransactionStats> captor =
                ArgumentCaptor.forClass(AdServicesEnrollmentTransactionStats.class);
        verify(mLogger).logEnrollmentTransactionStats(captor.capture());
        AdServicesEnrollmentTransactionStats loggedStats = captor.getValue();
        assertEquals(status, loggedStats.transactionStatus());
        assertEquals(queryResultCount, loggedStats.queryResultCount());
        assertEquals(transactionResultCount, loggedStats.transactionResultCount());
        assertEquals(dataSourceRecordCountPost, loggedStats.dataSourceRecordCountPost());
        assertEquals(latencyMs, loggedStats.latencyMs());
        assertEquals(
                AdServicesEnrollmentTransactionStats.TransactionType.INSERT,
                loggedStats.transactionType());
        assertEquals(1, loggedStats.transactionParameterCount());
        assertEquals(5, loggedStats.dataSourceRecordCountPre());
    }

    @Test
    public void logTransactionStats2_noRecord_callsLoggerWithCorrectArgs() {
        AdServicesEnrollmentTransactionStats.Builder statsBuilder =
                AdServicesEnrollmentTransactionStats.builder()
                        .setTransactionType(
                                AdServicesEnrollmentTransactionStats.TransactionType
                                        .GET_ENROLLMENT_DATA)
                        .setTransactionParameterCount(1)
                        .setDataSourceRecordCountPre(-1);
        int queryResultCount = 0;
        int transactionResultCountArg = 2;
        int dataSourceRecordCountArg = 3;
        int latencyMsArg = 4;
        doReturn(mMockEnrollmentDao).when(EnrollmentDao::getInstance);
        when(mMockEnrollmentDao.useV3EnrollmentData()).thenReturn(false);
        EnrollmentUtil enrollmentUtil = EnrollmentUtil.getInstance();

        enrollmentUtil.logTransactionStats(
                mLogger,
                statsBuilder,
                queryResultCount,
                transactionResultCountArg,
                dataSourceRecordCountArg,
                latencyMsArg);

        ArgumentCaptor<AdServicesEnrollmentTransactionStats> captor =
                ArgumentCaptor.forClass(AdServicesEnrollmentTransactionStats.class);
        verify(mLogger).logEnrollmentTransactionStats(captor.capture());
        AdServicesEnrollmentTransactionStats loggedStats = captor.getValue();
        assertEquals(
                AdServicesEnrollmentTransactionStats.TransactionStatus.MATCH_NOT_FOUND,
                loggedStats.transactionStatus());
        assertEquals(0, loggedStats.queryResultCount());
        assertEquals(0, loggedStats.transactionResultCount());
        assertEquals(dataSourceRecordCountArg, loggedStats.dataSourceRecordCountPost());
        assertEquals(latencyMsArg, loggedStats.latencyMs());
        assertEquals(
                AdServicesEnrollmentTransactionStats.TransactionType.GET_ENROLLMENT_DATA,
                loggedStats.transactionType());
        assertEquals(1, loggedStats.transactionParameterCount());
        assertEquals(-1, loggedStats.dataSourceRecordCountPre());
    }

    private static void resetEnrollmentUtilSingleton() {
        try {
            Field singletonField = EnrollmentUtil.class.getDeclaredField("sSingleton");
            singletonField.setAccessible(true);
            singletonField.set(null, null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(
                    "Failed to reset " + EnrollmentUtil.class.getSimpleName() + "." + "sSingleton",
                    e);
        }
    }
}
