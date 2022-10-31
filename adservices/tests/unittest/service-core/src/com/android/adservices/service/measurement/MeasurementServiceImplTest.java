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

import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_PERMISSION_NOT_REQUESTED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.CallerMetadata;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementApiStatusCallback;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.MeasurementErrorResponse;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.StatusParam;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.SystemClock;
import android.provider.DeviceConfig;
import android.test.mock.MockContext;

import androidx.test.filters.SmallTest;

import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.enrollment.EnrollmentFixture;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.Clock;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.time.Instant;
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
    private static final StatusParam STATUS_PARAM = new StatusParam.Builder(PACKAGE_NAME).build();

    @Mock private AppImportanceFilter mMockAppImportanceFilter;
    @Mock private PackageManager mPackageManager;
    @Mock private ConsentManager mConsentManager;
    @Mock private MeasurementImpl mMockMeasurementImpl;
    @Mock private Throttler mMockThrottler;
    @Mock private Flags mMockFlags;
    @Mock private AdServicesLogger mMockAdServicesLogger;
    @Mock private EnrollmentDao mEnrollmentDao;

    private CallerMetadata mCallerMetadata;
    private Clock mClock;
    private MockitoSession mMockitoSession;
    private final MockContext mMockContext =
            new MockContext() {
                @Override
                public int checkCallingOrSelfPermission(String permission) {
                    return PackageManager.PERMISSION_GRANTED;
                }

                @Override
                public PackageManager getPackageManager() {
                    return mPackageManager;
                }
            };

    private MeasurementServiceImpl mMeasurementServiceImpl;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        toggleAllowAllPpApi(true);
        toggleEnforceForegroundAll(true);
        when(mMockMeasurementImpl.register(any(RegistrationRequest.class), anyLong()))
                .thenReturn(STATUS_SUCCESS);
        when(mMockMeasurementImpl.registerWebSource(
                        any(WebSourceRegistrationRequestInternal.class), anyLong()))
                .thenReturn(STATUS_SUCCESS);
        when(mMockMeasurementImpl.registerWebSource(
                        any(WebSourceRegistrationRequestInternal.class), anyLong()))
                .thenReturn(STATUS_SUCCESS);
        when(mConsentManager.getConsent()).thenReturn(AdServicesApiConsent.GIVEN);
        when(mMockFlags.getWebContextClientAppAllowList()).thenReturn("*");
        when(mMockThrottler.tryAcquire(any(), any())).thenReturn(true).thenReturn(false);
        when(mMockFlags.getWebContextClientAppAllowList()).thenReturn(ALLOW_ALL_PACKAGES);
        when(mMockFlags.getMeasurementApiRegisterSourceKillSwitch()).thenReturn(false);
        when(mMockFlags.getMeasurementApiRegisterTriggerKillSwitch()).thenReturn(false);
        when(mMockFlags.getMeasurementApiRegisterWebSourceKillSwitch()).thenReturn(false);
        when(mMockFlags.getMeasurementApiRegisterWebTriggerKillSwitch()).thenReturn(false);
        when(mMockFlags.getMeasurementApiDeleteRegistrationsKillSwitch()).thenReturn(false);
        when(mMockFlags.getMeasurementApiStatusKillSwitch()).thenReturn(false);
        when(mMockFlags.getPpapiAppAllowList()).thenReturn("*");
        when(mMockFlags.getEnforceForegroundStatusForMeasurementStatus()).thenReturn(false);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementDeleteRegistrations())
                .thenReturn(false);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterSource()).thenReturn(false);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterTrigger())
                .thenReturn(false);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebSource())
                .thenReturn(false);
        when(mMockFlags.getEnforceForegroundStatusForMeasurementRegisterWebTrigger())
                .thenReturn(false);
        mCallerMetadata =
                new CallerMetadata.Builder()
                        .setBinderElapsedTimestamp(SystemClock.elapsedRealtime())
                        .build();
        mClock = Clock.SYSTEM_CLOCK;
        mMeasurementServiceImpl =
                new MeasurementServiceImpl(
                        mMockMeasurementImpl,
                        mMockContext,
                        mClock,
                        mConsentManager,
                        mEnrollmentDao,
                        mMockThrottler,
                        mMockFlags,
                        mMockAdServicesLogger,
                        mMockAppImportanceFilter);
    }

    @Test
    public void testRegister_success() throws Exception {
        try {
            mockAccessControl(true, true, true);

            CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<Integer> list = new ArrayList<>();

            mMeasurementServiceImpl.register(
                    getDefaultRegistrationSourceRequest(),
                    mCallerMetadata,
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
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);

        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterSource_enforceForeground_runInForegroundAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            doNothing()
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<Integer> list = new ArrayList<>();

            mMeasurementServiceImpl.register(
                    getDefaultRegistrationSourceRequest(),
                    mCallerMetadata,
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
            assertThat(list.size()).isEqualTo(1);
            assertThat(list.get(0)).isEqualTo(STATUS_SUCCESS);
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterSource_enforceForeground_runInBackgroundNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationSourceRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).register(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterSource_killSwitchOff() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterSourceKillSwitch(false);

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationSourceRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    countDownLatchAny.countDown();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse errorResponse) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, times(1)).register(any(), anyLong());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterSource_killSwitchOn() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterSourceKillSwitch(true);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationSourceRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).register(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterSource_ppApiNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterSourceKillSwitch(false);

            toggleAllowAllPpApi(false);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationSourceRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).register(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterSource_successfulThrottled() throws Exception {
        try {
            mockAccessControl(true, true, true);
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

            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(getDefaultRegistrationSourceRequest(), mCallerMetadata, callback);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(getDefaultRegistrationSourceRequest(), mCallerMetadata, callback);

            assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
            assertThat(statusCodes.size()).isEqualTo(1);
            assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
            assertThat(errors.size()).isEqualTo(1);
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterTrigger_successfulThrottled() throws Exception {
        try {
            mockAccessControl(true, true, true);
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

            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(getDefaultRegistrationTriggerRequest(), mCallerMetadata, callback);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(getDefaultRegistrationTriggerRequest(), mCallerMetadata, callback);

            assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
            assertThat(statusCodes.size()).isEqualTo(1);
            assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
            assertThat(errors.size()).isEqualTo(1);
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterTrigger_enforceForeground_runInForegroundAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            doNothing()
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationTriggerRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    countDownLatchAny.countDown();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse errorResponse) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();

            verify(mMockMeasurementImpl, times(1)).register(any(), anyLong());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterTrigger_enforceForeground_runInBackgroundNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationTriggerRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).register(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterTrigger_killSwitchOff() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterTriggerKillSwitch(false);

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationTriggerRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    countDownLatchAny.countDown();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse errorResponse) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();

            verify(mMockMeasurementImpl, times(1)).register(any(), anyLong());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterTrigger_killSwitchOn() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterTriggerKillSwitch(true);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationTriggerRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).register(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterTrigger_ppApiNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterTriggerKillSwitch(false);

            toggleAllowAllPpApi(false);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .register(
                            getDefaultRegistrationTriggerRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).register(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(STATUS_CALLER_NOT_ALLOWED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testRegister_invalidRequest() {
        try {
            mockAccessControl(true, true, true);
            mMeasurementServiceImpl.register(
                    null,
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {}

                        @Override
                        public void onFailure(MeasurementErrorResponse responseParcel) {}
                    });
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testRegister_invalidCallback() {
        try {
            mockAccessControl(true, true, true);
            mMeasurementServiceImpl.register(
                    getDefaultRegistrationSourceRequest(), mCallerMetadata, null);
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testDeleteRegistrations_success() throws Exception {
        try {
            mockAccessControl(true, true, true);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<Integer> list = new ArrayList<>();

            mMeasurementServiceImpl.deleteRegistrations(
                    getDefaultDeletionRequest(),
                    mCallerMetadata,
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
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testDeleteRegistrations_enforceForeground_runInForegroundAllowed()
            throws Exception {
        try {
            mockAccessControl(true, true, true);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            doNothing()
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<Integer> list = new ArrayList<>();

            mMeasurementServiceImpl.deleteRegistrations(
                    getDefaultDeletionRequest(),
                    mCallerMetadata,
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
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testDeleteRegistrations_enforceForeground_runInBackgroundNotAllowed()
            throws Exception {
        try {
            mockAccessControl(true, true, true);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .deleteRegistrations(
                            getDefaultDeletionRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    fail("Failure callback expected.");
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse errorResponse) {
                                    errors.add(errorResponse);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).deleteRegistrations(any());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testDeleteRegistrations_killSwitchOffAndPackageAllowListed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleDeleteRegistrationsKillSwitch(false);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .deleteRegistrations(
                            getDefaultDeletionRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    countDownLatchAny.countDown();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse errorResponse) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, times(1)).deleteRegistrations(any());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testDeleteRegistrations_killSwitchOffAndPackageNotAllowListed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleDeleteRegistrationsKillSwitch(false);

            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .deleteRegistrations(
                            getDefaultDeletionRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    fail("Failure callback expected.");
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse errorResponse) {
                                    errors.add(errorResponse);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).deleteRegistrations(any());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testDeleteRegistrations_killSwitchOnAndPackageAllowListed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleDeleteRegistrationsKillSwitch(true);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .deleteRegistrations(
                            getDefaultDeletionRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).deleteRegistrations(any());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testDeleteRegistrations_ppApiNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleDeleteRegistrationsKillSwitch(false);

            toggleAllowAllPpApi(false);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .deleteRegistrations(
                            getDefaultDeletionRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).deleteRegistrations(any());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(STATUS_CALLER_NOT_ALLOWED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testDeleteRegistrations_successfulThrottled() throws Exception {
        try {
            mockAccessControl(true, true, true);
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

            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .deleteRegistrations(getDefaultDeletionRequest(), mCallerMetadata, callback);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .deleteRegistrations(getDefaultDeletionRequest(), mCallerMetadata, callback);

            assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
            assertThat(statusCodes.size()).isEqualTo(1);
            assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
            assertThat(errors.size()).isEqualTo(1);
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteRegistrations_invalidRequest() {
        try {
            mockAccessControl(true, true, true);
            mMeasurementServiceImpl.deleteRegistrations(
                    null,
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {}

                        @Override
                        public void onFailure(MeasurementErrorResponse responseParcel) {}
                    });
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteRegistrations_invalidCallback() {
        mMeasurementServiceImpl.deleteRegistrations(
                getDefaultDeletionRequest(), mCallerMetadata, null);
    }

    @Test
    public void testGetMeasurementApiStatus_success() throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(Binder.class)
                        .spyStatic(ConsentManager.class)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(1).when(Binder::getCallingUidOrThrow);

            ExtendedMockito.doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
            ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any()));
            MeasurementImpl measurementImpl =
                    new MeasurementImpl(mMockContext, null, null, null, null);
            when(mMockFlags.getPpapiAppAllowList()).thenReturn("*");
            CountDownLatch countDownLatch = new CountDownLatch(1);
            final AtomicInteger resultWrapper = new AtomicInteger();

            mMeasurementServiceImpl =
                    new MeasurementServiceImpl(
                            measurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter);
            mMeasurementServiceImpl.getMeasurementApiStatus(
                    STATUS_PARAM,
                    mCallerMetadata,
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

    @Test
    public void testGetMeasurementApiStatus_enforceForeground_runInForegroundAllowed()
            throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(Binder.class)
                        .spyStatic(ConsentManager.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(1).when(Binder::getCallingUidOrThrow);

            ExtendedMockito.doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();

            doNothing()
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            ExtendedMockito.doReturn(mConsentManager).when(() -> ConsentManager.getInstance(any()));
            MeasurementImpl measurementImpl =
                    new MeasurementImpl(mMockContext, null, null, null, null);
            when(mMockFlags.getPpapiAppAllowList()).thenReturn("*");
            CountDownLatch countDownLatch = new CountDownLatch(1);
            final AtomicInteger resultWrapper = new AtomicInteger();

            mMeasurementServiceImpl =
                    new MeasurementServiceImpl(
                            measurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter);
            mMeasurementServiceImpl.getMeasurementApiStatus(
                    STATUS_PARAM,
                    mCallerMetadata,
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

    @Test
    public void testGetMeasurementApiStatus_enforceForeground_runInBackgroundNotAllowed()
            throws Exception {
        MockitoSession session =
                ExtendedMockito.mockitoSession()
                        .spyStatic(Binder.class)
                        .spyStatic(ConsentManager.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        try {
            ExtendedMockito.doReturn(1).when(Binder::getCallingUidOrThrow);

            ExtendedMockito.doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();

            doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .getMeasurementApiStatus(
                            STATUS_PARAM,
                            mCallerMetadata,
                            new IMeasurementApiStatusCallback.Stub() {
                                @Override
                                public void onResult(int result) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).getMeasurementApiStatus();
        } finally {
            session.finishMocking();
        }
    }

    @Test
    public void testGetMeasurementApiStatus_killSwitchOff() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleApiStatusKillSwitch(false);

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .getMeasurementApiStatus(
                            STATUS_PARAM,
                            mCallerMetadata,
                            new IMeasurementApiStatusCallback.Stub() {
                                @Override
                                public void onResult(int result) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, times(1)).getMeasurementApiStatus();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testGetMeasurementApiStatus_killSwitchOn() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleApiStatusKillSwitch(true);

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .getMeasurementApiStatus(
                            STATUS_PARAM,
                            mCallerMetadata,
                            new IMeasurementApiStatusCallback.Stub() {
                                @Override
                                public void onResult(int result) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).getMeasurementApiStatus();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testGetMeasurementApiStatus_ppApiNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleApiStatusKillSwitch(false);

            toggleAllowAllPpApi(false);

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .getMeasurementApiStatus(
                            STATUS_PARAM,
                            mCallerMetadata,
                            new IMeasurementApiStatusCallback.Stub() {
                                @Override
                                public void onResult(int result) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).getMeasurementApiStatus();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void testGetMeasurementApiStatus_invalidCallback() {
        mMeasurementServiceImpl.getMeasurementApiStatus(STATUS_PARAM, mCallerMetadata, null);
    }

    @Test
    public void registerWebSource_success() throws Exception {
        try {
            mockAccessControl(true, true, true);
            CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<Integer> list = new ArrayList<>();

            mMeasurementServiceImpl.registerWebSource(
                    createWebSourceRegistrationRequest(),
                    mCallerMetadata,
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
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void registerWebSource_enforceForeground_runInForegroundAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            doNothing()
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<Integer> list = new ArrayList<>();

            mMeasurementServiceImpl.registerWebSource(
                    createWebSourceRegistrationRequest(),
                    mCallerMetadata,
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
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void registerWebSource_enforceForeground_runInBackgroundNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebSource(
                            createWebSourceRegistrationRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).registerWebSource(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebSource_killSwitchOff() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterWebSourceKillSwitch(false);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebSource(
                            createWebSourceRegistrationRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    countDownLatchAny.countDown();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse errorResponse) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, times(1)).registerWebSource(any(), anyLong());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebSource_killSwitchOn() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterWebSourceKillSwitch(true);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebSource(
                            createWebSourceRegistrationRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).registerWebSource(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebSource_ppApiNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterWebSourceKillSwitch(false);

            toggleAllowAllPpApi(false);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebSource(
                            createWebSourceRegistrationRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).registerWebSource(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(STATUS_CALLER_NOT_ALLOWED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void registerWebSource_successfulThrottled() throws Exception {
        try {
            mockAccessControl(true, true, true);
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

            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebSource(
                            createWebSourceRegistrationRequest(), mCallerMetadata, callback);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebSource(
                            createWebSourceRegistrationRequest(), mCallerMetadata, callback);

            assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
            assertThat(statusCodes.size()).isEqualTo(1);
            assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
            assertThat(errors.size()).isEqualTo(1);
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void registerWebSource_invalidRequest() {
        try {
            mockAccessControl(true, true, true);
            mMeasurementServiceImpl.registerWebSource(
                    null,
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {}

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                    });
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void registerWebSource_invalidCallback() {
        try {
            mockAccessControl(true, true, true);
            mMeasurementServiceImpl.registerWebSource(
                    createWebSourceRegistrationRequest(), mCallerMetadata, null);
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void registerWebTrigger_success() throws Exception {
        try {
            mockAccessControl(true, true, true);
            CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<Integer> list = new ArrayList<>();

            mMeasurementServiceImpl.registerWebTrigger(
                    createWebTriggerRegistrationRequest(),
                    mCallerMetadata,
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
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void registerWebTrigger_enforceForeground_runInForegroundAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            doNothing()
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<Integer> list = new ArrayList<>();

            mMeasurementServiceImpl.registerWebTrigger(
                    createWebTriggerRegistrationRequest(),
                    mCallerMetadata,
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
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void registerWebTrigger_enforceForeground_runInBackgroundNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                    .when(mMockAppImportanceFilter)
                    .assertCallerIsInForeground(anyInt(), anyInt(), any());

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebTrigger(
                            createWebTriggerRegistrationRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).registerWebTrigger(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_BACKGROUND_CALLER, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebTrigger_killSwitchOff() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterWebTriggerKillSwitch(false);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatchAny = new CountDownLatch(1);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebTrigger(
                            createWebTriggerRegistrationRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    countDownLatchAny.countDown();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse errorResponse) {
                                    countDownLatchAny.countDown();
                                }
                            });

            assertThat(countDownLatchAny.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, times(1)).registerWebTrigger(any(), anyLong());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebTrigger_killSwitchOn() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterWebTriggerKillSwitch(true);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebTrigger(
                            createWebTriggerRegistrationRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).registerWebTrigger(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(
                    AdServicesStatusUtils.STATUS_KILLSWITCH_ENABLED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebTrigger_ppApiNotAllowed() throws Exception {
        try {
            mockAccessControl(true, true, true);

            toggleRegisterWebTriggerKillSwitch(false);

            toggleAllowAllPpApi(false);

            // Allow client to call API
            toggleAllowAllWebContext(true);

            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final List<MeasurementErrorResponse> errors = new ArrayList<>();
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            FlagsFactory.getFlags(),
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebTrigger(
                            createWebTriggerRegistrationRequest(),
                            mCallerMetadata,
                            new IMeasurementCallback.Stub() {
                                @Override
                                public void onResult() {
                                    Assert.fail();
                                }

                                @Override
                                public void onFailure(MeasurementErrorResponse responseParcel) {
                                    errors.add(responseParcel);
                                    countDownLatch.countDown();
                                }
                            });

            assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            verify(mMockMeasurementImpl, never()).registerWebTrigger(any(), anyLong());
            Assert.assertEquals(1, errors.size());
            Assert.assertEquals(STATUS_CALLER_NOT_ALLOWED, errors.get(0).getStatusCode());
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void registerWebTrigger_successfulThrottled() throws Exception {
        try {
            mockAccessControl(true, true, true);
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

            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebTrigger(
                            createWebTriggerRegistrationRequest(), mCallerMetadata, callback);
            new MeasurementServiceImpl(
                            mMockMeasurementImpl,
                            mMockContext,
                            mClock,
                            mConsentManager,
                            mEnrollmentDao,
                            mMockThrottler,
                            mMockFlags,
                            mMockAdServicesLogger,
                            mMockAppImportanceFilter)
                    .registerWebTrigger(
                            createWebTriggerRegistrationRequest(), mCallerMetadata, callback);

            assertThat(countDownLatchSuccess.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(countDownLatchFailed.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
            assertThat(statusCodes.get(0)).isEqualTo(STATUS_SUCCESS);
            assertThat(statusCodes.size()).isEqualTo(1);
            assertThat(errors.get(0).getStatusCode()).isEqualTo(STATUS_RATE_LIMIT_REACHED);
            assertThat(errors.size()).isEqualTo(1);
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test(expected = NullPointerException.class)
    public void registerWebTrigger_invalidRequest() {
        mMeasurementServiceImpl.registerWebSource(
                null,
                mCallerMetadata,
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult() {}

                    @Override
                    public void onFailure(MeasurementErrorResponse measurementErrorResponse) {}
                });
    }

    @Test(expected = NullPointerException.class)
    public void registerWebTrigger_invalidCallback() {
        mMeasurementServiceImpl.registerWebTrigger(
                createWebTriggerRegistrationRequest(), mCallerMetadata, null);
    }

    @Test
    public void testRegister_userRevokedConsent() {
        try {
            mockAccessControl(true, true, true);
            when(mConsentManager.getConsent()).thenReturn(AdServicesApiConsent.REVOKED);
            mMeasurementServiceImpl.register(
                    getDefaultRegistrationSourceRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_USER_CONSENT_REVOKED);
                        }
                    });
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebSource_userRevokedConsent() {
        try {
            mockAccessControl(true, true, true);
            doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent();

            mMeasurementServiceImpl.registerWebSource(
                    createWebSourceRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_USER_CONSENT_REVOKED);
                        }
                    });
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebSource_packageNotAllowListed() throws InterruptedException {
        try {
            mockAccessControl(true, true, true);
            doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
            doReturn(ALLOW_LIST_WITHOUT_TEST_PACKAGE)
                    .when(mMockFlags)
                    .getWebContextClientAppAllowList();
            CountDownLatch callbackCountDown = new CountDownLatch(1);

            // Execution
            mMeasurementServiceImpl.registerWebSource(
                    createWebSourceRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail("Failure callback expected.");
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_CALLER_NOT_ALLOWED);
                        }
                    });

            // Assertion
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebTrigger_userRevokedConsent() {
        try {
            mockAccessControl(true, true, true);
            doReturn(AdServicesApiConsent.REVOKED).when(mConsentManager).getConsent();

            mMeasurementServiceImpl.registerWebTrigger(
                    createWebTriggerRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_USER_CONSENT_REVOKED);
                        }
                    });
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebTrigger_packageNotAllowListed_doesNotBlock()
            throws InterruptedException {
        try {
            // Setup
            mockAccessControl(true, true, true);
            doReturn(AdServicesApiConsent.GIVEN).when(mConsentManager).getConsent();
            doReturn(ALLOW_LIST_WITHOUT_TEST_PACKAGE)
                    .when(mMockFlags)
                    .getWebContextClientAppAllowList();
            CountDownLatch callbackCountDown = new CountDownLatch(1);

            // Execution
            mMeasurementServiceImpl.registerWebTrigger(
                    createWebTriggerRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            callbackCountDown.countDown();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            fail("Success expected as allowlist does not apply to web trigger.");
                        }
                    });
            // Assertion
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegister_noPermission() throws InterruptedException {
        try {
            mockAccessControl(true, true, false);
            CountDownLatch callbackCountDown = new CountDownLatch(1);

            mMeasurementServiceImpl.register(
                    getDefaultRegistrationSourceRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_PERMISSION_NOT_REQUESTED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebSource_noPermission() throws InterruptedException {
        try {
            mockAccessControl(true, true, false);
            CountDownLatch callbackCountDown = new CountDownLatch(1);

            mMeasurementServiceImpl.registerWebSource(
                    createWebSourceRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_PERMISSION_NOT_REQUESTED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebTrigger_noPermission() throws InterruptedException {
        try {
            mockAccessControl(true, true, false);
            CountDownLatch callbackCountDown = new CountDownLatch(1);

            mMeasurementServiceImpl.registerWebTrigger(
                    createWebTriggerRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_PERMISSION_NOT_REQUESTED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegister_notEnrolled() throws InterruptedException {
        try {
            mockAccessControl(false, true, true);
            CountDownLatch callbackCountDown = new CountDownLatch(1);
            doReturn(null).when(mEnrollmentDao).getEnrollmentDataFromMeasurementUrl(any());

            mMeasurementServiceImpl.register(
                    getDefaultRegistrationSourceRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_CALLER_NOT_ALLOWED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebSource_notEnrolled() throws InterruptedException {
        try {
            mockAccessControl(false, true, true);
            CountDownLatch callbackCountDown = new CountDownLatch(1);
            doReturn(null).when(mEnrollmentDao).getEnrollmentDataFromMeasurementUrl(any());

            mMeasurementServiceImpl.registerWebSource(
                    createWebSourceRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_CALLER_NOT_ALLOWED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebTrigger_notEnrolled() throws InterruptedException {
        try {
            mockAccessControl(false, true, true);
            CountDownLatch callbackCountDown = new CountDownLatch(1);
            doReturn(null).when(mEnrollmentDao).getEnrollmentDataFromMeasurementUrl(any());

            mMeasurementServiceImpl.registerWebTrigger(
                    createWebTriggerRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_CALLER_NOT_ALLOWED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegister_notInManifestAllowlist() throws InterruptedException {
        try {
            mockAccessControl(true, false, true);
            CountDownLatch callbackCountDown = new CountDownLatch(1);

            mMeasurementServiceImpl.register(
                    getDefaultRegistrationSourceRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_CALLER_NOT_ALLOWED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebSource_notInManifestAllowlist() throws InterruptedException {
        try {
            mockAccessControl(true, false, true);
            CountDownLatch callbackCountDown = new CountDownLatch(1);

            mMeasurementServiceImpl.registerWebSource(
                    createWebSourceRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_CALLER_NOT_ALLOWED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    @Test
    public void testRegisterWebTrigger_notInManifestAllowlist() throws InterruptedException {
        try {
            mockAccessControl(true, false, true);
            CountDownLatch callbackCountDown = new CountDownLatch(1);

            mMeasurementServiceImpl.registerWebTrigger(
                    createWebTriggerRegistrationRequest(),
                    mCallerMetadata,
                    new IMeasurementCallback.Stub() {
                        @Override
                        public void onResult() {
                            Assert.fail();
                        }

                        @Override
                        public void onFailure(MeasurementErrorResponse measurementErrorResponse) {
                            callbackCountDown.countDown();
                            assertThat(measurementErrorResponse.getStatusCode())
                                    .isEqualTo(STATUS_CALLER_NOT_ALLOWED);
                        }
                    });
            assertThat(callbackCountDown.await(TIMEOUT, TimeUnit.SECONDS)).isTrue();
        } finally {
            mMockitoSession.finishMocking();
        }
    }

    private RegistrationRequest getDefaultRegistrationSourceRequest() {
        return new RegistrationRequest.Builder()
                .setPackageName(PACKAGE_NAME)
                .setRegistrationUri(Uri.parse("https://registration-uri.com"))
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .build();
    }

    private RegistrationRequest getDefaultRegistrationTriggerRequest() {
        return new RegistrationRequest.Builder()
                .setPackageName(PACKAGE_NAME)
                .setRegistrationUri(Uri.parse("https://registration-uri.com"))
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
                .setStart(Instant.MIN)
                .setEnd(Instant.MAX)
                .build();
    }

    private void toggleRegisterSourceKillSwitch(boolean enabled) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_api_register_source_kill_switch",
                Boolean.toString(enabled),
                /* makeDefault */ false);
    }

    private void toggleRegisterTriggerKillSwitch(boolean enabled) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_api_register_trigger_kill_switch",
                Boolean.toString(enabled),
                /* makeDefault */ false);
    }

    private void toggleRegisterWebSourceKillSwitch(boolean enabled) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_api_register_web_source_kill_switch",
                Boolean.toString(enabled),
                /* makeDefault */ false);
    }

    private void toggleRegisterWebTriggerKillSwitch(boolean enabled) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_api_register_web_trigger_kill_switch",
                Boolean.toString(enabled),
                /* makeDefault */ false);
    }

    private void toggleDeleteRegistrationsKillSwitch(boolean enabled) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_api_delete_registrations_kill_switch",
                Boolean.toString(enabled),
                /* makeDefault */ false);
    }

    private void toggleApiStatusKillSwitch(boolean enabled) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_api_status_kill_switch",
                Boolean.toString(enabled),
                /* makeDefault */ false);
    }

    private void toggleAllowAllPpApi(boolean enabledAll) {
        final String value = enabledAll ? "*" : "";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "ppapi_app_allow_list",
                value,
                /* makeDefault */ false);
    }

    private void toggleAllowAllWebContext(boolean enabledAll) {
        final String value = enabledAll ? "*" : "";
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "web_context_client_allow_list",
                value,
                /* makeDefault */ false);
    }

    private void toggleEnforceForegroundAll(boolean enforce) {
        toggleEnforceForegroundRegisterSource(enforce);
        toggleEnforceForegroundRegisterTrigger(enforce);
        toggleEnforceForegroundRegisterWebSource(enforce);
        toggleEnforceForegroundRegisterWebTrigger(enforce);
        toggleEnforceForegroundDeleteRegistrations(enforce);
        toggleEnforceForegroundApiStatus(enforce);
    }

    private void toggleEnforceForegroundRegisterSource(boolean enforce) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_foreground_status_register_source",
                Boolean.toString(enforce),
                /* makeDefault */ false);
    }

    private void toggleEnforceForegroundRegisterTrigger(boolean enforce) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_foreground_status_register_trigger",
                Boolean.toString(enforce),
                /* makeDefault */ false);
    }

    private void toggleEnforceForegroundRegisterWebSource(boolean enforce) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_foreground_status_register_web_source",
                Boolean.toString(enforce),
                /* makeDefault */ false);
    }

    private void toggleEnforceForegroundRegisterWebTrigger(boolean enforce) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_foreground_status_register_web_trigger",
                Boolean.toString(enforce),
                /* makeDefault */ false);
    }

    private void toggleEnforceForegroundDeleteRegistrations(boolean enforce) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_foreground_status_delete_registrations",
                Boolean.toString(enforce),
                /* makeDefault */ false);
    }

    private void toggleEnforceForegroundApiStatus(boolean enforce) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "measurement_enforce_foreground_status_get_status",
                Boolean.toString(enforce),
                /* makeDefault */ false);
    }

    private void mockAccessControl(
            boolean isEnrolled, boolean isAllowedByAppManifest, boolean isPermissionGranted) {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(Binder.class)
                        .mockStatic(PermissionHelper.class)
                        .mockStatic(AppManifestConfigHelper.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        ExtendedMockito.doReturn(1).when(Binder::getCallingUidOrThrow);
        ExtendedMockito.doReturn(isPermissionGranted)
                .when(() -> PermissionHelper.hasAttributionPermission(any(Context.class)));
        ExtendedMockito.doReturn(isAllowedByAppManifest)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedAttributionAccess(
                                        any(), any(), any()));
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ADSERVICES,
                "disable_measurement_enrollment_check",
                Boolean.toString(false),
                /* makeDefault */ false);
        if (isEnrolled) {
            doReturn(EnrollmentFixture.getValidEnrollment())
                    .when(mEnrollmentDao)
                    .getEnrollmentDataFromMeasurementUrl(any());
        } else {
            doReturn(null).when(mEnrollmentDao).getEnrollmentDataFromMeasurementUrl(any());
        }
    }
}
