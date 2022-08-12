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

package com.android.adservices.service.measurement;

import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Unit tests for {@link MeasurementServiceImpl} */
@SmallTest
public final class MeasurementServiceImplTest {
    // This rule is used for configuring P/H flags
    @Rule
    public final TestableDeviceConfig.TestableDeviceConfigRule mDeviceConfigRule =
            new TestableDeviceConfig.TestableDeviceConfigRule();

    @Mock private ConsentManager mConsentManager;
    @Mock private PackageManager mPackageManager;

    private static final String PACKAGE_NAME = "test.package.name";
    private static final String ALLOW_LIST_WITHOUT_TEST_PACKAGE =
            "test1.package.name,test1.package.name";
    private static final Uri REGISTRATION_URI = Uri.parse("https://registration-uri.com");
    private static final Uri WEB_DESTINATION = Uri.parse("https://web-destination-uri.com");
    private static final Uri APP_DESTINATION = Uri.parse("android-app://com.app-destination");
    private static final int TIMEOUT = 5_000;
    private static final String ALLOW_ALL_PACKAGES = "*";
    private static final WebSourceParams SOURCE_REGISTRATION =
            new WebSourceParams.Builder(REGISTRATION_URI).setDebugKeyAllowed(true).build();
    private static final WebTriggerParams TRIGGER_REGISTRATION =
            new WebTriggerParams.Builder(REGISTRATION_URI).setDebugKeyAllowed(true).build();
    @Mock private MeasurementImpl mMockMeasurementImpl;
    @Mock private Throttler mMockThrottler;
    @Mock private Context mMockContext;
    @Mock private Flags mMockFlags;

    private MeasurementServiceImpl mMeasurementServiceImpl;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mMockMeasurementImpl.register(any(RegistrationRequest.class), anyLong()))
                .thenReturn(STATUS_SUCCESS);
        when(mMockMeasurementImpl.registerWebSource(
                        any(WebSourceRegistrationRequestInternal.class), anyLong()))
                .thenReturn(STATUS_SUCCESS);
        when(mMockMeasurementImpl.registerWebSource(
                        any(WebSourceRegistrationRequestInternal.class), anyLong()))
                .thenReturn(STATUS_SUCCESS);
        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockThrottler.tryAcquire(any(), any())).thenReturn(true);
        when(mMockContext.getPackageManager()).thenReturn(mPackageManager);
        when(mMockFlags.getWebContextRegistrationClientAppAllowList())
                .thenReturn(ALLOW_ALL_PACKAGES);
        mMeasurementServiceImpl =
                new MeasurementServiceImpl(
                        mMockMeasurementImpl,
                        mMockContext,
                        mConsentManager,
                        mMockThrottler,
                        mMockFlags);
    }

    @Test
    public void testRegister_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.register(
                getDefaultRegistrationSourceRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    public void testRegisterSource_successfulThrottled() throws Exception {
        final CountDownLatch countDownLatchSuccess = new CountDownLatch(1);
        final CountDownLatch countDownLatchFailed = new CountDownLatch(1);
        final List<Integer> statusCodes = new ArrayList<>();
        final List<MeasurementErrorResponse> errors = new ArrayList<>();
        final IMeasurementCallback callback =
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        statusCodes.add(STATUS_SUCCESS);
                        countDownLatchSuccess.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errors.add(responseParcel);
                        countDownLatchFailed.countDown();
                    }
                };

        final Throttler throttler = Throttler.getInstance(1);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .register(getDefaultRegistrationSourceRequest(), callback);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .register(getDefaultRegistrationSourceRequest(), callback);

        assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(statusCodes.size()).isEqualTo(1);
        assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
        assertThat(errors.size()).isEqualTo(1);
    }

    @Test
    public void testRegisterTrigger_successfulThrottled() throws Exception {
        final CountDownLatch countDownLatchSuccess = new CountDownLatch(1);
        final CountDownLatch countDownLatchFailed = new CountDownLatch(1);
        final List<Integer> statusCodes = new ArrayList<>();
        final List<MeasurementErrorResponse> errors = new ArrayList<>();
        final IMeasurementCallback callback =
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        statusCodes.add(STATUS_SUCCESS);
                        countDownLatchSuccess.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errors.add(responseParcel);
                        countDownLatchFailed.countDown();
                    }
                };

        final Throttler throttler = Throttler.getInstance(1);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .register(getDefaultRegistrationTriggerRequest(), callback);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .register(getDefaultRegistrationTriggerRequest(), callback);

        assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(statusCodes.size()).isEqualTo(1);
        assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
        assertThat(errors.size()).isEqualTo(1);
    }

    @Test(expected = NullPointerException.class)
    public void testRegister_invalidRequest() {
        mMeasurementServiceImpl.register(
                null,
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {}
                });
    }

    @Test(expected = NullPointerException.class)
    public void testRegister_invalidCallback() {
        mMeasurementServiceImpl.register(getDefaultRegistrationSourceRequest(), null);
    }

    @Test
    public void testDeleteRegistrations_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.deleteRegistrations(
                getDefaultDeletionRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    public void testDeleteRegistrations_successfulThrottled() throws Exception {
        final CountDownLatch countDownLatchSuccess = new CountDownLatch(1);
        final CountDownLatch countDownLatchFailed = new CountDownLatch(1);
        final List<Integer> statusCodes = new ArrayList<>();
        final List<MeasurementErrorResponse> errors = new ArrayList<>();
        final IMeasurementCallback callback =
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        statusCodes.add(STATUS_SUCCESS);
                        countDownLatchSuccess.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errors.add(responseParcel);
                        countDownLatchFailed.countDown();
                    }
                };

        final Throttler throttler = Throttler.getInstance(1);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .deleteRegistrations(getDefaultDeletionRequest(), callback);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .deleteRegistrations(getDefaultDeletionRequest(), callback);

        assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(statusCodes.size()).isEqualTo(1);
        assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
        assertThat(errors.size()).isEqualTo(1);
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteRegistrations_invalidRequest() {
        mMeasurementServiceImpl.deleteRegistrations(
                null,
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {}
                });
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteRegistrations_invalidCallback() {
        mMeasurementServiceImpl.deleteRegistrations(getDefaultDeletionRequest(), null);
    }

    @Test
    public void testGetMeasurementApiStatus_success() throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(ConsentManager.class)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(AdServicesApiConsent.GIVEN)
                    .when(mConsentManager)
                    .getConsent(any());
            ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any()));
            MeasurementImpl measurementImpl =
                    new MeasurementImpl(mMockContext, null, null, null, null, null);
            CountDownLatch countDownLatch = new CountDownLatch(1);
            final AtomicInteger resultWrapper = new AtomicInteger();

            mMeasurementServiceImpl =
                    new MeasurementServiceImpl(
                            measurementImpl,
                            mMockContext,
                            mConsentManager,
                            mMockThrottler,
                            mMockFlags);
            mMeasurementServiceImpl.getMeasurementApiStatus(
                    new IMeasurementApiStatusCallback.Stub() {
                        @Override
                        public void onResult(int result) {
                            resultWrapper.set(result);
                            countDownLatch.countDown();
                        }
                    });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(resultWrapper.get())
                    .isEqualTo(MeasurementManager.MEASUREMENT_API_STATE_ENABLED);
        } finally {
            session.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testGetMeasurementApiStatus_invalidCallback() {
        mMeasurementServiceImpl.getMeasurementApiStatus(null);
    }

    @Test
    public void registerWebSource_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.registerWebSource(
                createWebSourceRegistrationRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    public void registerWebSource_successfulThrottled() throws Exception {
        final CountDownLatch countDownLatchSuccess = new CountDownLatch(1);
        final CountDownLatch countDownLatchFailed = new CountDownLatch(1);
        final List<Integer> statusCodes = new ArrayList<>();
        final List<MeasurementErrorResponse> errors = new ArrayList<>();
        final IMeasurementCallback callback =
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        statusCodes.add(STATUS_SUCCESS);
                        countDownLatchSuccess.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errors.add(responseParcel);
                        countDownLatchFailed.countDown();
                    }
                };

        final Throttler throttler = Throttler.getInstance(1);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .registerWebSource(createWebSourceRegistrationRequest(), callback);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .registerWebSource(createWebSourceRegistrationRequest(), callback);

        assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(statusCodes.size()).isEqualTo(1);
        assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
        assertThat(errors.size()).isEqualTo(1);
    }

    @Test(expected = NullPointerException.class)
    public void registerWebSource_invalidRequest() {
        mMeasurementServiceImpl.registerWebSource(
                null,
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                });
    }

    @Test(expected = NullPointerException.class)
    public void registerWebSource_invalidCallback() {
        mMeasurementServiceImpl.registerWebSource(createWebSourceRegistrationRequest(), null);
    }

    @Test
    public void registerWebTrigger_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        mMeasurementServiceImpl.registerWebTrigger(
                createWebTriggerRegistrationRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        list.add(STATUS_SUCCESS);
                        countDownLatch.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    public void registerWebTrigger_successfulThrottled() throws Exception {
        final CountDownLatch countDownLatchSuccess = new CountDownLatch(1);
        final CountDownLatch countDownLatchFailed = new CountDownLatch(1);
        final List<Integer> statusCodes = new ArrayList<>();
        final List<MeasurementErrorResponse> errors = new ArrayList<>();
        final IMeasurementCallback callback =
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        statusCodes.add(STATUS_SUCCESS);
                        countDownLatchSuccess.countDown();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse responseParcel) {
                        errors.add(responseParcel);
                        countDownLatchFailed.countDown();
                    }
                };

        final Throttler throttler = Throttler.getInstance(1);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .registerWebTrigger(createWebTriggerRegistrationRequest(), callback);
        new MeasurementServiceImpl(
                        mMockMeasurementImpl, mMockContext, mConsentManager, throttler, mMockFlags)
                .registerWebTrigger(createWebTriggerRegistrationRequest(), callback);

        assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
        assertThat(statusCodes.size()).isEqualTo(1);
        assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
        assertThat(errors.size()).isEqualTo(1);
    }

    @Test(expected = NullPointerException.class)
    public void registerWebTrigger_invalidRequest() {
        mMeasurementServiceImpl.registerWebSource(
                null,
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                });
    }

    @Test(expected = NullPointerException.class)
    public void registerWebTrigger_invalidCallback() {
        mMeasurementServiceImpl.registerWebTrigger(createWebTriggerRegistrationRequest(), null);
    }

    @Test
    public void testRegister_userRevokedConsent() {
        when(mConsentManager.getConsent(any(PackageManager.class)))
                .thenReturn(AdServicesApiConsent.REVOKED);
        doReturn(mPackageManager).when(mMockContext).getPackageManager();

        mMeasurementServiceImpl.register(
                getDefaultRegistrationSourceRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        Assert.fail();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                        assertThat(measurementErrorResponse.getStatusCode())
                                .isEqualTo(STATUS_USER_CONSENT_REVOKED);
                    }
                });
    }

    @Test
    public void testRegisterWebSource_userRevokedConsent() {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent(mPackageManager);

        mMeasurementServiceImpl.registerWebSource(
                createWebSourceRegistrationRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        Assert.fail();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                        assertThat(measurementErrorResponse.getStatusCode())
                                .isEqualTo(STATUS_USER_CONSENT_REVOKED);
                    }
                });
    }

    @Test
    public void testRegisterWebSource_packageNotAllowListed() throws InterruptedException {
        // Setup
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent(mPackageManager);
        doReturn(ALLOW_LIST_WITHOUT_TEST_PACKAGE)
                .when(mMockFlags)
                .getWebContextRegistrationClientAppAllowList();
        CountDownLatch callbackCountDown = new CountDownLatch(1);

        // Execution
        mMeasurementServiceImpl.registerWebSource(
                createWebSourceRegistrationRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        Assert.fail();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                        callbackCountDown.countDown();
                        assertThat(measurementErrorResponse.getStatusCode())
                                .isEqualTo(STATUS_USER_CONSENT_REVOKED);
                    }
                });

        // Assertion
        assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    public void testRegisterWebTrigger_userRevokedConsent() {
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent(mPackageManager);

        mMeasurementServiceImpl.registerWebTrigger(
                createWebTriggerRegistrationRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        Assert.fail();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                        assertThat(measurementErrorResponse.getStatusCode())
                                .isEqualTo(STATUS_USER_CONSENT_REVOKED);
                    }
                });
    }

    @Test
    public void testRegisterWebTrigger_packageNotAllowListed() throws InterruptedException {
        // Setup
        doReturn(mPackageManager).when(mMockContext).getPackageManager();
        doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent(mPackageManager);
        doReturn(ALLOW_LIST_WITHOUT_TEST_PACKAGE)
                .when(mMockFlags)
                .getWebContextRegistrationClientAppAllowList();
        CountDownLatch callbackCountDown = new CountDownLatch(1);

        // Execution
        mMeasurementServiceImpl.registerWebTrigger(
                createWebTriggerRegistrationRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {
                        Assert.fail();
                    }

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                        callbackCountDown.countDown();
                        assertThat(measurementErrorResponse.getStatusCode())
                                .isEqualTo(STATUS_USER_CONSENT_REVOKED);
                    }
                });

        // Assertion
        assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
    }

    private RegistrationRequest getDefaultRegistrationSourceRequest() {
        return new RegistrationRequest.Builder()
                .setPackageName(PACKAGE_NAME)
                .setRegistrationUri(Uri.parse("https://registration-uri.com"))
                .setTopOriginUri(Uri.parse("android-app://com.example"))
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .build();
    }

    private RegistrationRequest getDefaultRegistrationTriggerRequest() {
        return new RegistrationRequest.Builder()
                .setPackageName(PACKAGE_NAME)
                .setRegistrationUri(Uri.parse("https://registration-uri.com"))
                .setTopOriginUri(Uri.parse("android-app://com.example"))
                .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                .build();
    }

    private WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest() {
        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(SOURCE_REGISTRATION),
                                Uri.parse("android-app//com.example"))
                        .setWebDestination(WEB_DESTINATION)
                        .setAppDestination(APP_DESTINATION)
                        .build();
        return new WebSourceRegistrationRequestInternal.Builder(
                        sourceRegistrationRequest, PACKAGE_NAME, 10000L)
                .build();
    }

    private WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest() {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(TRIGGER_REGISTRATION),
                                Uri.parse("android-app://com.example"))
                        .build();
        return new WebTriggerRegistrationRequestInternal.Builder(
                        webTriggerRegistrationRequest, PACKAGE_NAME)
                .build();
    }

    private DeletionParam getDefaultDeletionRequest() {
        return new DeletionParam.Builder()
                .setPackageName(PACKAGE_NAME)
                .setDomainUris(Collections.emptyList())
                .setOriginUris(Collections.emptyList())
                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                .build();
    }
}
