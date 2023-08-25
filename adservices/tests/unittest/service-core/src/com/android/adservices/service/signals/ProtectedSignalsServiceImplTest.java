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

package com.android.adservices.service.signals;

import static com.android.adservices.service.common.Throttler.ApiKey.PROTECTED_SIGNAL_API_FETCH_SIGNAL_UPDATES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.signals.FetchSignalUpdatesCallback;
import android.adservices.signals.FetchSignalUpdatesInput;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.Flags;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.net.URI;
import java.util.concurrent.ExecutorService;

public class ProtectedSignalsServiceImplTest {

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    // TODO(b/296586554) Add API id
    private static final int API_NAME = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
    private static final int UID = 42;
    private static final AdTechIdentifier ADTECH = AdTechIdentifier.fromString("example.com");
    private static final Uri URI = Uri.parse("https://example.com");
    private static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    private static final String EXCEPTION_MESSAGE = "message";

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();
    @Mock private FetchOrchestrator mFetchOrchestratorMock;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    @Mock private Flags mFlagsMock;
    @Mock private CallingAppUidSupplier mCallingAppUidSupplierMock;
    @Mock private CustomAudienceServiceFilter mCustomAudienceServiceFilterMock;
    @Mock private FetchSignalUpdatesCallback mFetchSignalUpdatesCallbackMock;

    @Captor ArgumentCaptor<FledgeErrorResponse> mErrorCaptor;

    private ProtectedSignalsServiceImpl mProtectedSignalsService;
    private DevContext mDevContext;
    private FetchSignalUpdatesInput mInput;

    @Before
    public void setup() {
        mProtectedSignalsService =
                new ProtectedSignalsServiceImpl(
                        CONTEXT,
                        mFetchOrchestratorMock,
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        DIRECT_EXECUTOR,
                        mAdServicesLoggerMock,
                        mFlagsMock,
                        mCallingAppUidSupplierMock,
                        mCustomAudienceServiceFilterMock);
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(false)
                        .setCallingAppPackageName(PACKAGE)
                        .build();
        mInput = new FetchSignalUpdatesInput.Builder(URI, PACKAGE).build();

        // Set up the mocks for a success flow -- indivual tests that want a failure can overwrite
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenReturn(UID);
        when(mFlagsMock.getDisableFledgeEnrollmentCheck()).thenReturn(false);
        when(mDevContextFilterMock.createDevContext()).thenReturn(mDevContext);
        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_FETCH_SIGNAL_UPDATES),
                        eq(mDevContext)))
                .thenReturn(ADTECH);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(false);
        SettableFuture<Object> emptyReturn = SettableFuture.create();
        emptyReturn.set(new Object());
        when(mFetchOrchestratorMock.orchestrateFetch(eq(URI), eq(ADTECH), eq(PACKAGE)))
                .thenReturn(FluentFuture.from(emptyReturn));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testFetchSignalUpdatesSuccess() throws Exception {
        mProtectedSignalsService.fetchSignalUpdates(mInput, mFetchSignalUpdatesCallbackMock);

        verify(mFledgeAuthorizationFilterMock)
                .assertAppDeclaredPermission(eq(CONTEXT), eq(PACKAGE), eq(API_NAME));
        verify(mCallingAppUidSupplierMock).getCallingAppUid();
        verify(mDevContextFilterMock).createDevContext();
        verify(mFlagsMock).getDisableFledgeEnrollmentCheck();
        verify(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_FETCH_SIGNAL_UPDATES),
                        eq(mDevContext));
        verify(mConsentManagerMock).isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE));
        verify(mFetchOrchestratorMock).orchestrateFetch(eq(URI), eq(ADTECH), eq(PACKAGE));
        verify(mFetchSignalUpdatesCallbackMock).onSuccess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_SUCCESS), eq(0));
    }

    @Test
    public void testFetchSignalUpdatesNullInput() throws Exception {
        assertThrows(
                NullPointerException.class,
                () ->
                        mProtectedSignalsService.fetchSignalUpdates(
                                null, mFetchSignalUpdatesCallbackMock));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(API_NAME, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, 0);
    }

    @Test
    public void testFetchSignalUpdatesNullCallback() throws Exception {
        assertThrows(
                NullPointerException.class,
                () -> mProtectedSignalsService.fetchSignalUpdates(mInput, null));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(API_NAME, AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, 0);
    }

    @Test
    public void testFetchSignalUpdatesExceptionGettingUid() throws Exception {
        when(mCallingAppUidSupplierMock.getCallingAppUid()).thenThrow(new IllegalStateException());
        assertThrows(
                IllegalStateException.class,
                () ->
                        mProtectedSignalsService.fetchSignalUpdates(
                                mInput, mFetchSignalUpdatesCallbackMock));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_INTERNAL_ERROR), eq(0));
    }

    @Test
    public void testFetchSignalUpdatesFilterException() throws Exception {
        when(mCustomAudienceServiceFilterMock.filterRequestAndExtractIdentifier(
                        eq(URI),
                        eq(PACKAGE),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(UID),
                        eq(API_NAME),
                        eq(PROTECTED_SIGNAL_API_FETCH_SIGNAL_UPDATES),
                        eq(mDevContext)))
                .thenThrow(new FilterException(new LimitExceededException(EXCEPTION_MESSAGE)));
        mProtectedSignalsService.fetchSignalUpdates(mInput, mFetchSignalUpdatesCallbackMock);

        verify(mFetchSignalUpdatesCallbackMock).onFailure(mErrorCaptor.capture());
        FledgeErrorResponse actual = mErrorCaptor.getValue();
        assertEquals(AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED, actual.getStatusCode());
        assertEquals(EXCEPTION_MESSAGE, actual.getErrorMessage());
        verifyZeroInteractions(mAdServicesLoggerMock);
    }

    @Test
    public void testFetchSignalUpdatesIllegalArgumentException() throws Exception {
        when(mFetchOrchestratorMock.orchestrateFetch(eq(URI), eq(ADTECH), eq(PACKAGE)))
                .thenThrow(new IllegalArgumentException(EXCEPTION_MESSAGE));
        mProtectedSignalsService.fetchSignalUpdates(mInput, mFetchSignalUpdatesCallbackMock);

        verify(mFetchSignalUpdatesCallbackMock).onFailure(mErrorCaptor.capture());
        FledgeErrorResponse actual = mErrorCaptor.getValue();
        assertEquals(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, actual.getStatusCode());
        assertEquals(EXCEPTION_MESSAGE, actual.getErrorMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT), eq(0));
    }

    @Test
    public void testFetchSignalUpdatesNoConsent() throws Exception {
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(eq(PACKAGE)))
                .thenReturn(true);
        mProtectedSignalsService.fetchSignalUpdates(mInput, mFetchSignalUpdatesCallbackMock);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED), eq(0));
        verify(mFetchSignalUpdatesCallbackMock).onSuccess();
    }

    @Test
    public void testFetchSignalUpdatesCallbackException() throws Exception {
        doThrow(new RuntimeException()).when(mFetchSignalUpdatesCallbackMock).onSuccess();
        mProtectedSignalsService.fetchSignalUpdates(mInput, mFetchSignalUpdatesCallbackMock);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(AdServicesStatusUtils.STATUS_INTERNAL_ERROR), eq(0));
    }
}
