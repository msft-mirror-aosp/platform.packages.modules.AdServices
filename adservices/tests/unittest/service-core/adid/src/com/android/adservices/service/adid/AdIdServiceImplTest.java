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

package com.android.adservices.service.adid;

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_AD_ID;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetFlags;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_CLASS__ADID;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__GET_ADID;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adid.AdId;
import android.adservices.adid.GetAdIdParam;
import android.adservices.adid.GetAdIdResult;
import android.adservices.adid.IGetAdIdCallback;
import android.adservices.common.CallerMetadata;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.Clock;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit test for {@link com.android.adservices.service.adid.AdIdServiceImpl}. */
public class AdIdServiceImplTest {
    @Rule
    public final AdServicesExtendedMockitoRule mExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .mockStatic(Binder.class)
                    .spyStatic(FlagsFactory.class)
                    .setStrictness(Strictness.WARN)
                    .build();

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TEST_APP_PACKAGE_NAME = "com.android.adservices.servicecoretest";
    private static final String INVALID_PACKAGE_NAME = "com.do_not_exists";
    private static final String SOME_SDK_NAME = "SomeSdkName";
    private static final int BINDER_CONNECTION_TIMEOUT_MS = 5_000;
    private static final int LOGGER_EVENT_TIMEOUT_MS = 5_000;
    private static final String SDK_PACKAGE_NAME = "test_package_name";
    private static final String ADID_API_ALLOW_LIST = "com.android.adservices.servicecoretest";
    private static final int SANDBOX_UID = 25000;

    private final Context mSpyContext = spy(sContext);
    private final AdServicesLogger mSpyAdServicesLogger = spy(AdServicesLoggerImpl.getInstance());

    private CountDownLatch mGetAdIdCallbackLatch;
    private CallerMetadata mCallerMetadata;
    private AdIdWorker mAdIdWorker;
    private GetAdIdParam mRequest;

    @Mock private PackageManager mMockPackageManager;
    @Mock private Flags mMockFlags;
    @Mock private Clock mClock;
    @Mock private Context mMockSdkContext;
    @Mock private Throttler mMockThrottler;
    @Mock private AdIdServiceImpl mAdIdServiceImpl;
    @Mock private AppImportanceFilter mMockAppImportanceFilter;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        AdIdCacheManager adIdCacheManager = spy(new AdIdCacheManager(sContext));
        mAdIdWorker = new AdIdWorker(adIdCacheManager);
        Mockito.doReturn(null).when(adIdCacheManager).getService();

        when(mClock.elapsedRealtime()).thenReturn(150L, 200L);
        mCallerMetadata = new CallerMetadata.Builder().setBinderElapsedTimestamp(100L).build();
        mRequest =
                new GetAdIdParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkPackageName(SDK_PACKAGE_NAME)
                        .build();

        when(mMockSdkContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mSpyContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0))
                .thenReturn(Process.myUid());

        setupPermissions(TEST_APP_PACKAGE_NAME, ACCESS_ADSERVICES_AD_ID);

        // Put this test app into bypass list to bypass Allow-list check.
        when(mMockFlags.getPpapiAppAllowList()).thenReturn(ADID_API_ALLOW_LIST);

        // Rate Limit is not reached.
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.ADID_API_APP_PACKAGE_NAME), anyString()))
                .thenReturn(true);

        mockGetFlags(mMockFlags);
    }

    @Test
    public void checkAllowList_emptyAllowList() throws InterruptedException {
        // Empty allow list.
        when(mMockFlags.getPpapiAppAllowList()).thenReturn("");
        invokeGetAdIdAndVerifyError(
                mSpyContext, STATUS_CALLER_NOT_ALLOWED, /* checkLoggingStatus */ true);
    }

    @Test
    public void checkThrottler_rateLimitReached_forAppPackageName() throws InterruptedException {
        // App calls AdId API directly, not via an SDK.
        GetAdIdParam request =
                new GetAdIdParam.Builder()
                        .setAppPackageName(TEST_APP_PACKAGE_NAME)
                        .setSdkPackageName(SDK_PACKAGE_NAME)
                        .build();

        // Rate Limit Reached.
        when(mMockThrottler.tryAcquire(eq(Throttler.ApiKey.ADID_API_APP_PACKAGE_NAME), anyString()))
                .thenReturn(false);
        // We don't log STATUS_RATE_LIMIT_REACHED for getAdId API.
        invokeGetAdIdAndVerifyError(
                mSpyContext, STATUS_RATE_LIMIT_REACHED, request, /* checkLoggingStatus */ false);
    }

    @Test
    public void testEnforceForeground_sandboxCaller() throws Exception {
        // Sandbox is only applicable for T+
        Assume.assumeTrue(SdkLevel.isAtLeastT());

        // Mock AppImportanceFilter to throw Exception when invoked. This is to verify getAdId()
        // doesn't throw if caller is via Sandbox.
        doThrow(new WrongCallingApplicationStateException())
                .when(mMockAppImportanceFilter)
                .assertCallerIsInForeground(
                        SANDBOX_UID, AD_SERVICES_API_CALLED__API_NAME__GET_ADID, SOME_SDK_NAME);

        // Mock UID with SDK UID
        when(Binder.getCallingUidOrThrow()).thenReturn(SANDBOX_UID);

        // Mock Flags with true to enable enforcing foreground check.
        doReturn(true).when(mMockFlags).getEnforceForegroundStatusForAdId();

        // Mock to grant required permissions
        // Copied UID calculation from Process.getAppUidForSdkSandboxUid().
        final int appCallingUid = SANDBOX_UID - 10000;
        when(mMockPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0)).thenReturn(appCallingUid);

        // Verify getAdId() doesn't throw.
        mAdIdServiceImpl = createAdIdServiceImplInstance_SandboxContext();
        runGetAdId(mAdIdServiceImpl);

        verify(mMockAppImportanceFilter, never())
                .assertCallerIsInForeground(
                        SANDBOX_UID, AD_SERVICES_API_CALLED__API_NAME__GET_ADID, SOME_SDK_NAME);
    }

    @Test
    public void testEnforceForeground_disableEnforcing() throws Exception {
        final int uid = Process.myUid();
        // Mock AppImportanceFilter to throw Exception when invoked. This is to verify getAdId()
        // doesn't throw if enforcing foreground is disabled
        doThrow(new WrongCallingApplicationStateException())
                .when(mMockAppImportanceFilter)
                .assertCallerIsInForeground(
                        uid, AD_SERVICES_API_CALLED__API_NAME__GET_ADID, SOME_SDK_NAME);

        // Mock UID with Non-SDK UI
        when(Binder.getCallingUidOrThrow()).thenReturn(uid);

        // Mock Flags with false to disable enforcing foreground check.
        doReturn(false).when(mMockFlags).getEnforceForegroundStatusForAdId();

        // Mock to grant required permissions
        when(mMockPackageManager.getPackageUid(TEST_APP_PACKAGE_NAME, 0)).thenReturn(uid);

        // Verify getAdId() doesn't throw.
        mAdIdServiceImpl = createTestAdIdServiceImplInstance();
        runGetAdId(mAdIdServiceImpl);

        verify(mMockAppImportanceFilter, never())
                .assertCallerIsInForeground(
                        uid, AD_SERVICES_API_CALLED__API_NAME__GET_ADID, SOME_SDK_NAME);
    }

    @Test
    public void checkAppNoPermission() throws Exception {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        setupPermissions(TEST_APP_PACKAGE_NAME);
        invokeGetAdIdAndVerifyError(
                mSpyContext, STATUS_PERMISSION_NOT_REQUESTED, /* checkLoggingStatus */ true);
    }

    @Test
    public void checkSdkNoPermission() throws Exception {
        // Sdk Sandbox only exists in T+
        Assume.assumeTrue(SdkLevel.isAtLeastT());
        when(Binder.getCallingUidOrThrow()).thenReturn(SANDBOX_UID);

        setupPermissions(TEST_APP_PACKAGE_NAME, ACCESS_ADSERVICES_AD_ID);
        doReturn(PackageManager.PERMISSION_DENIED)
                .when(mMockPackageManager)
                .checkPermission(eq(ACCESS_ADSERVICES_AD_ID), any());
        invokeGetAdIdAndVerifyError(
                mMockSdkContext, STATUS_PERMISSION_NOT_REQUESTED, /* checkLoggingStatus */ true);
    }

    @Test
    public void getAdId() throws Exception {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        runGetAdId(createTestAdIdServiceImplInstance());
    }

    @Test
    public void testGetAdId_enforceCallingPackage_invalidPackage() throws Exception {
        AdIdServiceImpl adidService = createTestAdIdServiceImplInstance();

        // Invalid package has ad id permissions
        setupPermissions(INVALID_PACKAGE_NAME, ACCESS_ADSERVICES_AD_ID);

        // A request with an invalid package name.
        mRequest =
                new GetAdIdParam.Builder()
                        .setAppPackageName(INVALID_PACKAGE_NAME)
                        .setSdkPackageName(SOME_SDK_NAME)
                        .build();

        mGetAdIdCallbackLatch = new CountDownLatch(1);

        adidService.getAdId(
                mRequest,
                mCallerMetadata,
                new IGetAdIdCallback() {
                    @Override
                    public void onResult(GetAdIdResult responseParcel) {
                        Assert.fail();
                    }

                    @Override
                    public void onError(int resultCode) {
                        assertThat(resultCode).isEqualTo(STATUS_CALLER_NOT_ALLOWED);
                        mGetAdIdCallbackLatch.countDown();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });

        // This ensures that the callback was called.
        assertThat(mGetAdIdCallbackLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();
    }

    private void invokeGetAdIdAndVerifyError(
            Context context, int expectedResultCode, boolean checkLoggingStatus)
            throws InterruptedException {
        invokeGetAdIdAndVerifyError(context, expectedResultCode, mRequest, checkLoggingStatus);
    }

    private void invokeGetAdIdAndVerifyError(
            Context context,
            int expectedResultCode,
            GetAdIdParam request,
            boolean checkLoggingStatus)
            throws InterruptedException {
        CountDownLatch jobFinishedCountDown = new CountDownLatch(1);

        CountDownLatch logOperationCalledLatch = new CountDownLatch(1);
        mockLoggerEvent(logOperationCalledLatch);

        mAdIdServiceImpl =
                new AdIdServiceImpl(
                        context,
                        mAdIdWorker,
                        mSpyAdServicesLogger,
                        mClock,
                        mMockFlags,
                        mMockThrottler,
                        mMockAppImportanceFilter);
        mAdIdServiceImpl.getAdId(
                request,
                mCallerMetadata,
                new IGetAdIdCallback() {
                    @Override
                    public void onResult(GetAdIdResult responseParcel) {
                        Assert.fail();
                        jobFinishedCountDown.countDown();
                    }

                    @Override
                    public void onError(int resultCode) {
                        assertThat(resultCode).isEqualTo(expectedResultCode);
                        jobFinishedCountDown.countDown();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });
        jobFinishedCountDown.await();

        if (checkLoggingStatus) {
            // getAdId method finished executing.
            logOperationCalledLatch.await();

            ArgumentCaptor<ApiCallStats> argument = ArgumentCaptor.forClass(ApiCallStats.class);

            verify(mSpyAdServicesLogger).logApiCallStats(argument.capture());
            assertThat(argument.getValue().getCode()).isEqualTo(AD_SERVICES_API_CALLED);
            assertThat(argument.getValue().getApiClass())
                    .isEqualTo(AD_SERVICES_API_CALLED__API_CLASS__ADID);
            assertThat(argument.getValue().getApiName())
                    .isEqualTo(AD_SERVICES_API_CALLED__API_NAME__GET_ADID);
            assertThat(argument.getValue().getResultCode()).isEqualTo(expectedResultCode);
            assertThat(argument.getValue().getAppPackageName())
                    .isEqualTo(request.getAppPackageName());
            assertThat(argument.getValue().getSdkPackageName())
                    .isEqualTo(request.getSdkPackageName());
        }
    }

    private void runGetAdId(AdIdServiceImpl adIdServiceImpl) throws Exception {

        GetAdIdResult expectedGetAdIdResult =
                new GetAdIdResult.Builder().setAdId(AdId.ZERO_OUT).setLatEnabled(false).build();

        final GetAdIdResult[] capturedResponseParcel = getAdIdResults(adIdServiceImpl);
        CountDownLatch loggerCountDownLatch = new CountDownLatch(1);
        mockLoggerEvent(loggerCountDownLatch);

        assertThat(mGetAdIdCallbackLatch.await(BINDER_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        // Verify the logger event has occurred.
        assertWithMessage("Logger event:")
                .that(loggerCountDownLatch.await(LOGGER_EVENT_TIMEOUT_MS, TimeUnit.MILLISECONDS))
                .isTrue();

        GetAdIdResult getAdIdResult = capturedResponseParcel[0];
        assertThat(getAdIdResult.getAdId()).isEqualTo(expectedGetAdIdResult.getAdId());
    }

    @NonNull
    private GetAdIdResult[] getAdIdResults(AdIdServiceImpl adIdServiceImpl) {
        // To capture result in inner class, we have to declare final.
        final GetAdIdResult[] capturedResponseParcel = new GetAdIdResult[1];
        mGetAdIdCallbackLatch = new CountDownLatch(1);
        adIdServiceImpl.getAdId(
                mRequest,
                mCallerMetadata,
                new IGetAdIdCallback() {
                    @Override
                    public void onResult(GetAdIdResult responseParcel) {
                        capturedResponseParcel[0] = responseParcel;
                        mGetAdIdCallbackLatch.countDown();
                    }

                    @Override
                    public void onError(int resultCode) {
                        Assert.fail();
                    }

                    @Override
                    public IBinder asBinder() {
                        return null;
                    }
                });

        return capturedResponseParcel;
    }

    @NonNull
    private AdIdServiceImpl createTestAdIdServiceImplInstance() {
        return new AdIdServiceImpl(
                mSpyContext,
                mAdIdWorker,
                mSpyAdServicesLogger,
                mClock,
                mMockFlags,
                mMockThrottler,
                mMockAppImportanceFilter);
    }

    @NonNull
    private AdIdServiceImpl createAdIdServiceImplInstance_SandboxContext() {
        return new AdIdServiceImpl(
                mMockSdkContext,
                mAdIdWorker,
                mSpyAdServicesLogger,
                mClock,
                mMockFlags,
                mMockThrottler,
                mMockAppImportanceFilter);
    }

    private void setupPermissions(String packageName, String... permissions)
            throws PackageManager.NameNotFoundException {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.requestedPermissions = permissions;
        doReturn(packageInfo)
                .when(mMockPackageManager)
                .getPackageInfo(eq(packageName), eq(PackageManager.GET_PERMISSIONS));
    }

    private void mockLoggerEvent(CountDownLatch loggerCountDownLatch) {
        Mockito.doAnswer(
                        (Answer<Object>)
                                invocation -> {
                                    // The method logAPiCallStats is called.
                                    invocation.callRealMethod();
                                    loggerCountDownLatch.countDown();
                                    return null;
                                })
                .when(mSpyAdServicesLogger)
                .logApiCallStats(ArgumentMatchers.any(ApiCallStats.class));
    }
}
