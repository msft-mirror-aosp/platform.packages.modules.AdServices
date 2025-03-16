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

package com.android.adservices.measurement;

import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_API_STATUS_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_MEASUREMENT_KILL_SWITCH;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.measurement.DeletionParam;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.IMeasurementService;
import android.adservices.measurement.MeasurementCompatibleManager;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.StatusParam;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebSourceRegistrationRequestInternal;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.adservices.measurement.WebTriggerRegistrationRequestInternal;
import android.net.Uri;
import android.os.OutcomeReceiver;

import com.android.adservices.AdServicesEndToEndTestCase;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.concurrent.Executor;

@DisableGlobalKillSwitch
@RequiresSdkLevelAtLeastT
@SetFlagDisabled(KEY_MEASUREMENT_API_DELETE_REGISTRATIONS_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_REGISTER_SOURCE_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_REGISTER_TRIGGER_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_REGISTER_WEB_SOURCE_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_REGISTER_WEB_TRIGGER_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_API_STATUS_KILL_SWITCH)
@SetFlagDisabled(KEY_MEASUREMENT_KILL_SWITCH)
@SuppressWarnings("NewApi")
public final class MeasurementCompatibleManagerSandboxTest extends AdServicesEndToEndTestCase {
    private Executor mMockCallbackExecutor;
    private IMeasurementService mMockMeasurementService;

    private MeasurementCompatibleManager mMeasurementManager;

    @Before
    public void setUp() {
        mMockCallbackExecutor = mock(Executor.class);
        mMockMeasurementService = mock(IMeasurementService.class);

        // The intention of spying on MeasurementManager and returning an IMeasurementService mock
        // is to avoid calling the process on the device. The goal of these tests are to verify the
        // same parameters are being sent as with a regular context. In these cases, the sdk package
        // name could be the only parameter that could differ, so package name would need to be
        // verified that it remains the same as the context package name on all the APIs.
        String adId = "35a4ac90-e4dc-4fe7-bbc6-95e804aa7dbc";
        AdIdManager adIdManager = mock(AdIdManager.class);
        mMeasurementManager = spy(MeasurementCompatibleManager.get(sContext, adIdManager));
        doReturn(mMockMeasurementService).when(mMeasurementManager).getService();
        doAnswer(
                        (invocation) -> {
                            ((AdServicesOutcomeReceiver) invocation.getArgument(1))
                                    .onResult(new AdId(adId, true));
                            return null;
                        })
                .when(adIdManager)
                .getAdId(any(), any(AdServicesOutcomeReceiver.class));
    }

    @Test
    public void testRegisterSource_verifySamePackageAsContext() throws Exception {
        OutcomeReceiver mockOutcomeReceiver = mock(OutcomeReceiver.class);
        // Execution
        mMeasurementManager.registerSource(
                Uri.parse("https://registration-source"),
                /* inputEvent= */ null,
                mMockCallbackExecutor,
                mockOutcomeReceiver);
        // Verification
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);

        verify(mMockMeasurementService, timeout(2000)).register(captor.capture(), any(), any());
        expect.that(captor.getValue()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(mPackageName);
    }

    @Test
    public void testRegisterTrigger_verifySamePackageAsContext() throws Exception {
        OutcomeReceiver mockOutcomeReceiver = mock(OutcomeReceiver.class);
        // Execution
        mMeasurementManager.registerTrigger(
                Uri.parse("https://registration-trigger"),
                mMockCallbackExecutor,
                mockOutcomeReceiver);

        // Verification
        ArgumentCaptor<RegistrationRequest> captor =
                ArgumentCaptor.forClass(RegistrationRequest.class);

        verify(mMockMeasurementService, timeout(2000)).register(captor.capture(), any(), any());
        expect.that(captor.getValue()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(mPackageName);
    }

    @Test
    public void testRegisterWebSource_verifySamePackageAsContext() throws Exception {
        OutcomeReceiver mockOutcomeReceiver = mock(OutcomeReceiver.class);
        // Setup
        final Uri source = Uri.parse("https://source");
        final Uri osDestination = Uri.parse("android-app://os.destination");
        final Uri webDestination = Uri.parse("https://web-destination");

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(source).setDebugKeyAllowed(false).build();

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams), source)
                        .setInputEvent(null)
                        .setAppDestination(osDestination)
                        .setWebDestination(webDestination)
                        .setVerifiedDestination(null)
                        .build();

        // Execution
        mMeasurementManager.registerWebSource(
                webSourceRegistrationRequest, mMockCallbackExecutor, mockOutcomeReceiver);

        // Verification
        ArgumentCaptor<WebSourceRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebSourceRegistrationRequestInternal.class);

        verify(mMockMeasurementService, timeout(2000))
                .registerWebSource(captor.capture(), any(), any());
        expect.that(captor.getValue()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(mPackageName);
    }

    @Test
    public void testRegisterWebTrigger_verifySamePackageAsContext() throws Exception {
        OutcomeReceiver mockOutcomeReceiver = mock(OutcomeReceiver.class);
        // Setup
        final Uri registrationUri = Uri.parse("https://registration-uri");
        final Uri destination = Uri.parse("https://destination");

        WebTriggerParams webTriggerParams = new WebTriggerParams.Builder(registrationUri).build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams), destination)
                        .build();

        // Execution
        mMeasurementManager.registerWebTrigger(
                webTriggerRegistrationRequest, mMockCallbackExecutor, mockOutcomeReceiver);

        // Verification
        ArgumentCaptor<WebTriggerRegistrationRequestInternal> captor =
                ArgumentCaptor.forClass(WebTriggerRegistrationRequestInternal.class);

        verify(mMockMeasurementService, timeout(2000))
                .registerWebTrigger(captor.capture(), any(), any());
        expect.that(captor.getValue()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(mPackageName);
    }

    @Test
    public void testDeleteRegistrations_verifySamePackageAsContext() throws Exception {
        OutcomeReceiver mockOutcomeReceiver = mock(OutcomeReceiver.class);
        // Setup
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder()
                        .setOriginUris(Collections.singletonList(Uri.parse("https://origin")))
                        .setDomainUris(Collections.singletonList(Uri.parse("https://domain")))
                        .build();

        // Execution
        mMeasurementManager.deleteRegistrations(
                deletionRequest, mMockCallbackExecutor, mockOutcomeReceiver);

        // Verification
        ArgumentCaptor<DeletionParam> captor = ArgumentCaptor.forClass(DeletionParam.class);

        verify(mMockMeasurementService, timeout(2000))
                .deleteRegistrations(captor.capture(), any(), any());
        expect.that(captor.getValue()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(mPackageName);
    }

    @Test
    public void testMeasurementApiStatus_verifySamePackageAsContext() throws Exception {
        OutcomeReceiver mockOutcomeReceiver = mock(OutcomeReceiver.class);
        // Execution
        mMeasurementManager.getMeasurementApiStatus(mMockCallbackExecutor, mockOutcomeReceiver);

        // Verification
        ArgumentCaptor<StatusParam> captor = ArgumentCaptor.forClass(StatusParam.class);

        verify(mMockMeasurementService, timeout(2000))
                .getMeasurementApiStatus(captor.capture(), any(), any());
        expect.that(captor.getValue()).isNotNull();
        expect.that(captor.getValue().getAppPackageName()).isEqualTo(mPackageName);
    }
}
