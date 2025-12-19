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

package com.android.adservices.service.topics;

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_TOPICS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_ADSERVICES_DISABLED;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.enrollment.EnrollmentUtil.BUILD_ID;
import static com.android.adservices.service.enrollment.EnrollmentUtil.ENROLLMENT_SHARED_PREF;
import static com.android.adservices.service.enrollment.EnrollmentUtil.FILE_GROUP_STATUS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.adservices.common.CallerMetadata;
import android.adservices.topics.GetTopicsParam;
import android.adservices.topics.GetTopicsResult;
import android.adservices.topics.IGetTopicsCallback;
import android.app.adservices.AdServicesManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.RemoteException;

import com.android.adservices.cobalt.TopicsCobaltLogger;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.DbTestUtil;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.DbHelper;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.topics.TopicsDao;
import com.android.adservices.data.topics.TopicsTables;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.appsearch.AppSearchConsentManager;
import com.android.adservices.service.common.AllowLists;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppManifestConfigMetricsLogger;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.shared.testing.IntFailureSyncCallback;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.adservices.shared.util.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Random;

/** Unit test for {@link com.android.adservices.service.topics.TopicsServiceImpl}. */
@SpyStatic(Binder.class)
@SpyStatic(AllowLists.class)
@SpyStatic(FlagsFactory.class)
@SpyStatic(AppManifestConfigMetricsLogger.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__TOPICS)
public final class TopicsServiceImplTest extends AdServicesExtendedMockitoTestCase {
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.adservices.servicecore.topics.unittest";
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 10_000;
    private static final String SDK_PACKAGE_NAME = "test_package_name";
    private static final String ALLOWED_SDK_ID = "1234567";
    private static final int MY_UID = Process.myUid();

    private final AdServicesLogger mAdServicesLogger =
            Mockito.spy(AdServicesLoggerImpl.getInstance());

    private CallerMetadata mCallerMetadata;
    private TopicsWorker mTopicsWorker;
    private TopicsWorker mSpyTopicsWorker;
    private BlockedTopicsManager mBlockedTopicsManager;
    private TopicsDao mTopicsDao;
    private GetTopicsParam mRequest;
    private TopicsServiceImpl mTopicsServiceImpl;

    @Mock private EpochManager mMockEpochManager;
    @Mock private ConsentManager mConsentManager;
    @Mock private PackageManager mPackageManager;
    @Mock private Clock mClock;
    @Mock private Context mMockSdkContext;
    @Mock private Context mMockAppContext;
    @Mock private Throttler mMockThrottler;
    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;
    @Mock private AdServicesLogger mLogger;
    @Mock private AdServicesManager mMockAdServicesManager;
    @Mock private AppSearchConsentManager mAppSearchConsentManager;
    @Mock private TopicsCobaltLogger mTopicsCobaltLogger;
    @Mock private SharedPreferences mEnrollmentSharedPreferences;
    @Mock private IGetTopicsCallback mMockGetTopicsCallback;

    @Before
    public void setup() throws Exception {
        // TODO(b/310270746): Holly Hack, Batman! This class needs some serious refactoring :-(
        appContext.set(mMockAppContext);

        mocker.mockGetCallingUidOrThrow(); // expect to return test uid by default

        // Clean DB before each test
        DbTestUtil.deleteTable(TopicsTables.ReturnedTopicContract.TABLE);

        DbHelper dbHelper = DbTestUtil.getDbHelperForTest();
        mTopicsDao = new TopicsDao(dbHelper);
        mBlockedTopicsManager =
                new BlockedTopicsManager(
                        mTopicsDao,
                        mMockAdServicesManager,
                        mAppSearchConsentManager,
                        Flags.PPAPI_AND_SYSTEM_SERVER,
                        /* enableAppSearchConsent= */ false);
        CacheManager cacheManager =
                new CacheManager(
                        mTopicsDao,
                        mMockFlags,
                        mLogger,
                        mBlockedTopicsManager,
                        new GlobalBlockedTopicsManager(
                                /* globalBlockedTopicIds= */ new HashSet<>()),
                        mTopicsCobaltLogger,
                        mClock);

        AppUpdateManager appUpdateManager =
                new AppUpdateManager(dbHelper, mTopicsDao, new Random(), mMockFlags);
        mTopicsWorker =
                new TopicsWorker(
                        mMockEpochManager,
                        cacheManager,
                        mBlockedTopicsManager,
                        appUpdateManager,
                        mMockFlags);
        // Used for verifying recordUsage method invocations.
        mSpyTopicsWorker =
                Mockito.spy(
                        new TopicsWorker(
                                mMockEpochManager,
                                cacheManager,
                                mBlockedTopicsManager,
                                appUpdateManager,
                                mMockFlags));

        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        mCallerMetadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(100L).build();
        mRequest =
                new GetTopicsParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkName(SOME_SDK_NAME)
                        .setSdkPackageName(SDK_PACKAGE_NAME)
                        .build();

        DbTestUtil.deleteTable(TopicsTables.BlockedTopicsContract.TABLE);
        when(mConsentManager.getConsent(AdServicesApiType.TOPICS))
                .thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockSdkContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0)).thenReturn(MY_UID);

        // Grant Permission to access Topics API
        PackageManager pmWithPerm = spy(mSpyContext.getPackageManager());
        doReturn(MY_UID).when(pmWithPerm).getPackageUid(TEST_APP_PACKAGE_NAME, 0);
        PackageInfo packageInfoGrant = new PackageInfo();
        packageInfoGrant.requestedPermissions = new String[] {ACCESS_ADSERVICES_TOPICS};
        doReturn(packageInfoGrant)
                .when(pmWithPerm)
                .getPackageInfo(anyString(), eq(PackageManager.GET_PERMISSIONS));
        doReturn(packageInfoGrant)
                .when(mPackageManager)
                .getPackageInfo(anyString(), eq(PackageManager.GET_PERMISSIONS));
        doReturn(pmWithPerm).when(mSpyContext).getPackageManager();

        // Allow all for signature allow list check
        when(mMockFlags.getPpapiAppSignatureAllowList()).thenReturn(AllowLists.ALLOW_ALL);
        when(mMockFlags.getTopicsEpochJobPeriodMs()).thenReturn(Flags.TOPICS_EPOCH_JOB_PERIOD_MS);

        // Initialize enrollment data.
        EnrollmentData fakeEnrollmentData =
                new EnrollmentData.Builder().setEnrollmentId(ALLOWED_SDK_ID).build();
        when(mEnrollmentDao.getEnrollmentDataFromSdkName(SOME_SDK_NAME))
                .thenReturn(fakeEnrollmentData);

        // Rate Limit is not reached.
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.TOPICS_API_SDK_NAME), anyString()))
                .thenReturn(true);
        when(mMockThrottler.tryAcquire(
                        eq(Throttler.ApiKey.TOPICS_API_APP_PACKAGE_NAME), anyString()))
                .thenReturn(true);

        when(mMockFlags.isEnrollmentBlocklisted(Mockito.any())).thenReturn(false);

        // TODO(b/310270746): expectations below (and spying FlagsFactory,
        // AppManifestConfigMetricsLogger, and possibly ErrorLogUtil) wouldn't be needed if tests
        // mocked AppManifestConfigHelper.isAllowedTopicsAccess() directly (instead of mocking the
        // contents of the app manifests)

        // Topics must call AppManifestConfigHelper to check if topics is enabled, whose behavior is
        // currently guarded by a flag
        mocker.mockGetFlags(mMockFlags);

        // And AppManifestConfigHelper calls AppManifestConfigMetricsLogger, which in turn does
        // stuff in a bg thread - chances are the test is done by the time the thread runs,
        // which could cause test failures (like lack of permission when calling Flags)
        ExtendedMockito.doNothing().when(() -> AppManifestConfigMetricsLogger.logUsage(any()));

        // Mock shared preferences behavior for enrollment build id and file group status
        when(mMockAppContext.getSharedPreferences(
                        eq(ENROLLMENT_SHARED_PREF), eq(Context.MODE_PRIVATE)))
                .thenReturn(mEnrollmentSharedPreferences);
        when(mMockSdkContext.getSharedPreferences(
                        eq(ENROLLMENT_SHARED_PREF), eq(Context.MODE_PRIVATE)))
                .thenReturn(mEnrollmentSharedPreferences);
        when(mEnrollmentSharedPreferences.getInt(eq(BUILD_ID), eq(/* defaultValue */ -1)))
                .thenReturn(1000);
        when(mEnrollmentSharedPreferences.getInt(eq(FILE_GROUP_STATUS), eq(/* defaultValue */ 0)))
                .thenReturn(2);
    }

    @Test
    public void getTopics_onResultSucceeded_shouldReturnDisabledStatus() throws Exception {
        mTopicsServiceImpl =
                new TopicsServiceImpl(
                        mSpyContext,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler,
                        mEnrollmentDao,
                        mMockAppImportanceFilter);
        ResultSyncCallback<ApiCallStats> logApiCallStatsCallback =
                mocker.mockLogApiCallStats(mAdServicesLogger);
        SyncGetTopicsCallback callback = new SyncGetTopicsCallback();

        mTopicsServiceImpl.getTopics(mRequest, mCallerMetadata, callback);

        GetTopicsResult getTopicsResult = callback.assertSuccess();
        expect.withMessage("%s.getResultCode()", getTopicsResult)
                .that(getTopicsResult.getResultCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        ApiCallStats apiCallStats = logApiCallStatsCallback.assertResultReceived();
        expect.withMessage("%s.getResultCode()", apiCallStats)
                .that(apiCallStats.getResultCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        expect.withMessage("%s.getAppPackageName()", apiCallStats)
                .that(apiCallStats.getAppPackageName())
                .isEqualTo(TEST_APP_PACKAGE_NAME);
        expect.withMessage("%s.getSdkPackageName()", apiCallStats)
                .that(apiCallStats.getSdkPackageName())
                .isEqualTo(SOME_SDK_NAME);
        expect.withMessage("%s.getApiName()", apiCallStats)
                .that(apiCallStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__API_CALLBACK_ERROR)
    public void getTopics_onResultFailed_shouldLogCel() throws Exception {
        mTopicsServiceImpl =
                new TopicsServiceImpl(
                        mSpyContext,
                        mTopicsWorker,
                        mConsentManager,
                        mAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler,
                        mEnrollmentDao,
                        mMockAppImportanceFilter);
        ResultSyncCallback<ApiCallStats> logApiCallStatsCallback =
                mocker.mockLogApiCallStats(mAdServicesLogger);
        doThrow(new RemoteException()).when(mMockGetTopicsCallback).onResult(any());

        mTopicsServiceImpl.getTopics(mRequest, mCallerMetadata, mMockGetTopicsCallback);

        ApiCallStats apiCallStats = logApiCallStatsCallback.assertResultReceived();
        expect.withMessage("%s.getResultCode()", apiCallStats)
                .that(apiCallStats.getResultCode())
                .isEqualTo(STATUS_ADSERVICES_DISABLED);
        expect.withMessage("%s.getAppPackageName()", apiCallStats)
                .that(apiCallStats.getAppPackageName())
                .isEqualTo(TEST_APP_PACKAGE_NAME);
        expect.withMessage("%s.getSdkPackageName()", apiCallStats)
                .that(apiCallStats.getSdkPackageName())
                .isEqualTo(SOME_SDK_NAME);
        expect.withMessage("%s.getApiName()", apiCallStats)
                .that(apiCallStats.getApiName())
                .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_TOPICS);
    }

    private static final class SyncGetTopicsCallback extends IntFailureSyncCallback<GetTopicsResult>
            implements IGetTopicsCallback {

        SyncGetTopicsCallback() {
            super(BINDER_CONNECTION_TIMEOUT_MS);
        }
    }
}
