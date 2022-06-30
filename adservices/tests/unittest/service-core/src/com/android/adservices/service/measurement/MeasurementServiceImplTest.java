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

import static android.adservices.measurement.MeasurementManager.RESULT_OK;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link MeasurementServiceImpl} */
@SmallTest
public final class MeasurementServiceImplTest {

    private static final Uri REGISTRATION_URI = Uri.parse("https://registration-uri.com");
    private static final Uri WEB_DESTINATION = Uri.parse("https://web-destination-uri.com");
    private static final Uri OS_DESTINATION = Uri.parse("https://os-destination-uri.com");
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final int TIMEOUT = 5_000;
    private static final WebSourceParams SOURCE_REGISTRATION =
            new WebSourceParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI)
                    .setAllowDebugKey(true)
                    .build();
    private static final WebTriggerParams TRIGGER_REGISTRATION =
            new WebTriggerParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI)
                    .setAllowDebugKey(true)
                    .build();
    private MeasurementImpl mMockMeasurementImpl;

    @Before
    public void setUp() {
        mMockMeasurementImpl = Mockito.mock(MeasurementImpl.class);
        when(mMockMeasurementImpl.register(any(RegistrationRequest.class), anyLong()))
                .thenReturn(RESULT_OK);
        when(mMockMeasurementImpl.registerWebSource(
                        any(WebSourceRegistrationRequestInternal.class), anyLong()))
                .thenReturn(MeasurementManager.RESULT_OK);
        when(mMockMeasurementImpl.registerWebSource(
                        any(WebSourceRegistrationRequestInternal.class), anyLong()))
                .thenReturn(MeasurementManager.RESULT_OK);
    }

    @Test
    public void testRegister_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        new MeasurementServiceImpl(mMockMeasurementImpl)
                .register(
                        getDefaultRegistrationRequest(),
                        new IMeasurementCallback.Stub() {
                            @Override
                            public void onResult() {
                                list.add(RESULT_OK);
                                countDownLatch.countDown();
                            }

                            @Override
                            public void onFailure(MeasurementErrorResponse responseParcel) {}
                        });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(RESULT_OK);
    }

    @Test(expected = NullPointerException.class)
    public void testRegister_invalidRequest() {
        new MeasurementServiceImpl(mMockMeasurementImpl)
                .register(
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
        new MeasurementServiceImpl(mMockMeasurementImpl).register(
                getDefaultRegistrationRequest(),
                null
        );
    }

    @Test
    public void testDeleteRegistrations_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        new MeasurementServiceImpl(mMockMeasurementImpl)
                .deleteRegistrations(
                        getDefaultDeletionRequest(),
                        new IMeasurementCallback.Stub() {
                            @Override
                            public void onResult() {
                                list.add(RESULT_OK);
                                countDownLatch.countDown();
                            }

                            @Override
                            public void onFailure(MeasurementErrorResponse responseParcel) {}
                        });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(RESULT_OK);
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteRegistrations_invalidRequest() {
        new MeasurementServiceImpl(mMockMeasurementImpl)
                .deleteRegistrations(
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
        new MeasurementServiceImpl(mMockMeasurementImpl).deleteRegistrations(
                getDefaultDeletionRequest(),
                null
        );
    }

    @Test
    public void testGetMeasurementApiStatus_success() throws Exception {
        MeasurementImpl measurementImpl = MeasurementImpl.getInstance(sContext);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        new MeasurementServiceImpl(measurementImpl)
                .getMeasurementApiStatus(
                        new IMeasurementApiStatusCallback.Stub() {
                            @Override
                            public void onResult(int result) {
                                list.add(result);
                                countDownLatch.countDown();
                            }
                        });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(MeasurementManager.MEASUREMENT_API_STATE_ENABLED);
    }

    @Test(expected = NullPointerException.class)
    public void testGetMeasurementApiStatus_invalidCallback() {
        new MeasurementServiceImpl(mMockMeasurementImpl).getMeasurementApiStatus(null);
    }

    @Test
    public void registerWebSource_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        new MeasurementServiceImpl(mMockMeasurementImpl)
                .registerWebSource(
                        createWebSourceRegistrationRequest(),
                        new IMeasurementCallback.Stub() {
                            @Override
                            public void onResult() {
                                list.add(RESULT_OK);
                                countDownLatch.countDown();
                            }

                            @Override
                            public void onFailure(
                                    MeasurementErrorResponse measurementErrorResponse) {}
                        });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(MeasurementManager.RESULT_OK);
    }

    @Test(expected = NullPointerException.class)
    public void registerWebSource_invalidRequest() {
        new MeasurementServiceImpl(mMockMeasurementImpl)
                .registerWebSource(
                        null,
                        new IMeasurementCallback.Stub() {
                            @Override
                            public void onResult() {}

                            @Override
                            public void onFailure(
                                    MeasurementErrorResponse measurementErrorResponse) {}
                        });
    }

    @Test(expected = NullPointerException.class)
    public void registerWebSource_invalidCallback() {
        new MeasurementServiceImpl(mMockMeasurementImpl)
                .registerWebSource(createWebSourceRegistrationRequest(), null);
    }

    @Test
    public void registerWebTrigger_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        new MeasurementServiceImpl(mMockMeasurementImpl)
                .registerWebTrigger(
                        createWebTriggerRegistrationRequest(),
                        new IMeasurementCallback.Stub() {
                            @Override
                            public void onResult() {
                                list.add(RESULT_OK);
                                countDownLatch.countDown();
                            }

                            @Override
                            public void onFailure(
                                    MeasurementErrorResponse measurementErrorResponse) {}
                        });

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(MeasurementManager.RESULT_OK);
    }

    @Test(expected = NullPointerException.class)
    public void registerWebTrigger_invalidRequest() {
        new MeasurementServiceImpl(mMockMeasurementImpl)
                .registerWebSource(
                        null,
                        new IMeasurementCallback.Stub() {
                            @Override
                            public void onResult() {}

                            @Override
                            public void onFailure(
                                    MeasurementErrorResponse measurementErrorResponse) {}
                        });
    }

    @Test(expected = NullPointerException.class)
    public void registerWebTrigger_invalidCallback() {
        new MeasurementServiceImpl(mMockMeasurementImpl)
                .registerWebTrigger(createWebTriggerRegistrationRequest(), null);
    }

    private RegistrationRequest getDefaultRegistrationRequest() {
        return new RegistrationRequest.Builder()
                .setAttributionSource(sContext.getAttributionSource())
                .setRegistrationUri(Uri.parse("https://registration-uri.com"))
                .setTopOriginUri(Uri.parse("android-app//com.example"))
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .build();
    }

    private WebSourceRegistrationRequestInternal createWebSourceRegistrationRequest() {
        WebSourceRegistrationRequest sourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder()
                        .setSourceParams(Collections.singletonList(SOURCE_REGISTRATION))
                        .setWebDestination(WEB_DESTINATION)
                        .setOsDestination(OS_DESTINATION)
                        .setTopOriginUri(Uri.parse("android-app//com.example"))
                        .build();
        return new WebSourceRegistrationRequestInternal.Builder()
                .setSourceRegistrationRequest(sourceRegistrationRequest)
                .setAttributionSource(sContext.getAttributionSource())
                .build();
    }

    private WebTriggerRegistrationRequestInternal createWebTriggerRegistrationRequest() {
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(Collections.singletonList(TRIGGER_REGISTRATION))
                        .setDestination(Uri.parse("android-app//com.example"))
                        .build();
        return new WebTriggerRegistrationRequestInternal.Builder()
                .setTriggerRegistrationRequest(webTriggerRegistrationRequest)
                .setAttributionSource(sContext.getAttributionSource())
                .build();
    }

    private DeletionParam getDefaultDeletionRequest() {
        return new DeletionParam.Builder()
                .setAttributionSource(sContext.getAttributionSource())
                .setDomainUris(Collections.emptyList())
                .setOriginUris(Collections.emptyList())
                .setMatchBehavior(DeletionRequest.MATCH_BEHAVIOR_DELETE)
                .setDeletionMode(DeletionRequest.DELETION_MODE_ALL)
                .build();
    }
}
