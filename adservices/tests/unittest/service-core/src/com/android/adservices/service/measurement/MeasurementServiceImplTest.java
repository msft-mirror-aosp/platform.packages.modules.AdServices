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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.RegistrationRequest;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Unit tests for {@link MeasurementServiceImpl} */
@SmallTest
public final class MeasurementServiceImplTest {

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final int TIMEOUT = 5_000;
    private MeasurementImpl mMockMeasurementImpl;

    @Before
    public void setUp() {
        mMockMeasurementImpl = Mockito.mock(MeasurementImpl.class);
        when(mMockMeasurementImpl.register(any(RegistrationRequest.class), anyLong()))
                .thenReturn(IMeasurementCallback.RESULT_OK);
    }

    @Test
    public void testRegister_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        new MeasurementServiceImpl(mMockMeasurementImpl).register(
                getDefaultRegistrationRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult(int result) throws RemoteException {
                        list.add(result);
                        countDownLatch.countDown();
                    }
                }
        );

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(IMeasurementCallback.RESULT_OK);
    }

    @Test(expected = NullPointerException.class)
    public void testRegister_invalidRequest() throws Exception {
        new MeasurementServiceImpl(mMockMeasurementImpl).register(
                null,
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult(int result) throws RemoteException {
                    }
                }
        );
    }

    @Test(expected = NullPointerException.class)
    public void testRegister_invalidCallback() throws Exception {
        new MeasurementServiceImpl(mMockMeasurementImpl).register(
                getDefaultRegistrationRequest(),
                null
        );
    }

    @Test
    public void testDeleteRegistrations_success() throws Exception {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final List<Integer> list = new ArrayList<>();

        new MeasurementServiceImpl(mMockMeasurementImpl).deleteRegistrations(
                getDefaultDeletionRequest(),
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult(int result) throws RemoteException {
                        list.add(result);
                        countDownLatch.countDown();
                    }
                }
        );

        assertThat(countDownLatch.await(TIMEOUT, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(list.get(0)).isEqualTo(IMeasurementCallback.RESULT_OK);
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteRegistrations_invalidRequest() throws Exception {
        new MeasurementServiceImpl(mMockMeasurementImpl).deleteRegistrations(
                null,
                new IMeasurementCallback.Stub() {
                    @Override
                    public void onResult(int result) throws RemoteException {
                    }
                }
        );
    }

    @Test(expected = NullPointerException.class)
    public void testDeleteRegistrations_invalidCallback() throws Exception {
        new MeasurementServiceImpl(mMockMeasurementImpl).deleteRegistrations(
                getDefaultDeletionRequest(),
                null
        );
    }

    private RegistrationRequest getDefaultRegistrationRequest() {
        return new RegistrationRequest.Builder()
                .setAttributionSource(sContext.getAttributionSource())
                .setRegistrationUri(Uri.parse("https://registration-uri.com"))
                .setTopOriginUri(Uri.parse("android-app//com.example"))
                .setReferrerUri(Uri.parse("android-app//com.example"))
                .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                .build();
    }

    private DeletionRequest getDefaultDeletionRequest() {
        return new DeletionRequest.Builder()
                .setAttributionSource(sContext.getAttributionSource())
                .build();
    }
}
