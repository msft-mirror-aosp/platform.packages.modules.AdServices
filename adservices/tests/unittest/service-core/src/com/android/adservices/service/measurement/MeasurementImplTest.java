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

import static com.android.adservices.service.measurement.attribution.TriggerContentProvider.TRIGGER_URI;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementCallback;
import android.adservices.measurement.RegistrationRequest;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.service.measurement.registration.SourceFetcher;
import com.android.adservices.service.measurement.registration.TriggerFetcher;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.time.Instant;

/** Unit tests for {@link MeasurementImpl} */
@SmallTest
public final class MeasurementImplTest {

    private static final Context DEFAULT_CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Uri DEFAULT_URI = Uri.parse("android-app://com.example.abc");
    private static final Uri DEFAULT_REGISTRATION_URI = Uri.parse("https://foo.com/bar?ad=134");
    private static final Uri DEFAULT_REFERRER_URI = Uri.parse("https://example.com");
    private ContentResolver mContentResolver;
    private ContentProviderClient mMockContentProviderClient;

    @Before
    public void before() throws RemoteException {
        mContentResolver = Mockito.mock(ContentResolver.class);
        mMockContentProviderClient = Mockito.mock(ContentProviderClient.class);
        when(mContentResolver.acquireContentProviderClient(TRIGGER_URI))
                .thenReturn(mMockContentProviderClient);
        when(mMockContentProviderClient.insert(any(), any())).thenReturn(TRIGGER_URI);
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchSuccess() throws RemoteException {
        DatastoreManager mockDatastoreManager = Mockito.mock(DatastoreManager.class);
        SourceFetcher mockSourceFetcher = Mockito.mock(SourceFetcher.class);
        TriggerFetcher mockTriggerFetcher = Mockito.mock(TriggerFetcher.class);
        when(mockSourceFetcher.fetchSource(any(), any())).thenReturn(true);
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mockDatastoreManager, mockSourceFetcher, mockTriggerFetcher);
        final int result = measurement.register(
                new RegistrationRequest.Builder()
                        .setRegistrationUri(DEFAULT_REGISTRATION_URI)
                        .setReferrerUri(DEFAULT_REFERRER_URI)
                        .setTopOriginUri(DEFAULT_URI)
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .build(),
                System.currentTimeMillis()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mockSourceFetcher, times(1)).fetchSource(any(), any());
        verify(mockTriggerFetcher, never()).fetchTrigger(ArgumentMatchers.any(), any());
    }

    @Test
    public void testRegister_registrationTypeSource_sourceFetchFailure() {
        DatastoreManager mockDatastoreManager = Mockito.mock(DatastoreManager.class);
        SourceFetcher mockSourceFetcher = Mockito.mock(SourceFetcher.class);
        TriggerFetcher mockTriggerFetcher = Mockito.mock(TriggerFetcher.class);
        when(mockSourceFetcher.fetchSource(any(), any())).thenReturn(false);
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mockDatastoreManager, mockSourceFetcher, mockTriggerFetcher);
        final int result = measurement.register(
                new RegistrationRequest.Builder()
                        .setRegistrationUri(DEFAULT_REGISTRATION_URI)
                        .setReferrerUri(DEFAULT_REFERRER_URI)
                        .setTopOriginUri(DEFAULT_URI)
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setRegistrationType(RegistrationRequest.REGISTER_SOURCE)
                        .build(),
                System.currentTimeMillis()
        );
        assertEquals(IMeasurementCallback.RESULT_IO_ERROR, result);
        verify(mockSourceFetcher, times(1)).fetchSource(any(), any());
        verify(mockTriggerFetcher, never()).fetchTrigger(ArgumentMatchers.any(), any());
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchSuccess() throws RemoteException {
        DatastoreManager mockDatastoreManager = Mockito.mock(DatastoreManager.class);
        SourceFetcher mockSourceFetcher = Mockito.mock(SourceFetcher.class);
        TriggerFetcher mockTriggerFetcher = Mockito.mock(TriggerFetcher.class);
        when(mockTriggerFetcher.fetchTrigger(any(), any())).thenReturn(true);
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mockDatastoreManager, mockSourceFetcher, mockTriggerFetcher);
        final int result = measurement.register(
                new RegistrationRequest.Builder()
                        .setRegistrationUri(DEFAULT_REGISTRATION_URI)
                        .setReferrerUri(DEFAULT_REFERRER_URI)
                        .setTopOriginUri(DEFAULT_URI)
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                        .build(),
                System.currentTimeMillis()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
        verify(mMockContentProviderClient).insert(any(), any());
        verify(mockSourceFetcher, never()).fetchSource(any(), any());
        verify(mockTriggerFetcher, times(1)).fetchTrigger(ArgumentMatchers.any(), any());
    }

    @Test
    public void testRegister_registrationTypeTrigger_triggerFetchFailure() throws RemoteException {
        DatastoreManager mockDatastoreManager = Mockito.mock(DatastoreManager.class);
        SourceFetcher mockSourceFetcher = Mockito.mock(SourceFetcher.class);
        TriggerFetcher mockTriggerFetcher = Mockito.mock(TriggerFetcher.class);
        when(mockTriggerFetcher.fetchTrigger(any(), any())).thenReturn(false);
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mockDatastoreManager, mockSourceFetcher, mockTriggerFetcher);
        final int result = measurement.register(
                new RegistrationRequest.Builder()
                        .setRegistrationUri(DEFAULT_REGISTRATION_URI)
                        .setReferrerUri(DEFAULT_REFERRER_URI)
                        .setTopOriginUri(DEFAULT_URI)
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setRegistrationType(RegistrationRequest.REGISTER_TRIGGER)
                        .build(),
                System.currentTimeMillis()
        );
        assertEquals(IMeasurementCallback.RESULT_IO_ERROR, result);
        verify(mMockContentProviderClient, never()).insert(any(), any());
        verify(mockSourceFetcher, never()).fetchSource(any(), any());
        verify(mockTriggerFetcher, times(1)).fetchTrigger(ArgumentMatchers.any(), any());
    }

    @Test
    public void testDeleteRegistrations_successfulNoOptionalParameters() throws Exception {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithRange() throws Exception {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setStart(Instant.now().minusSeconds(1))
                        .setEnd(Instant.now())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithOrigin() throws Exception {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setOriginUri(DEFAULT_URI)
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_successfulWithRangeAndOrigin() throws Exception {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setStart(Instant.now().minusSeconds(1))
                        .setEnd(Instant.now())
                        .setOriginUri(DEFAULT_URI)
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_OK, result);
    }

    @Test
    public void testDeleteRegistrations_invalidParameterStartButNoEnd() throws Exception {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setStart(Instant.now())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_INVALID_ARGUMENT, result);
    }

    @Test
    public void testDeleteRegistrations_invalidParameterEndButNoStart() throws Exception {
        MeasurementImpl measurement = MeasurementImpl.getInstance(DEFAULT_CONTEXT);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .setEnd(Instant.now())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_INVALID_ARGUMENT, result);
    }

    @Test
    public void testDeleteRegistrations_internalError() throws Exception {
        DatastoreManager mockDatastoreManager = Mockito.mock(DatastoreManager.class);
        MeasurementImpl measurement = new MeasurementImpl(
                mContentResolver, mockDatastoreManager, new SourceFetcher(), new TriggerFetcher());
        Mockito.when(mockDatastoreManager.runInTransaction(ArgumentMatchers.any()))
                .thenReturn(false);
        final int result = measurement.deleteRegistrations(
                new DeletionRequest.Builder()
                        .setAttributionSource(DEFAULT_CONTEXT.getAttributionSource())
                        .build()
        );
        assertEquals(IMeasurementCallback.RESULT_INTERNAL_ERROR, result);
    }
}
