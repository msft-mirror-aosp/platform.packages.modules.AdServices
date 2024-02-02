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
package com.android.adservices.measurement;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.ext.SdkExtensions;

import androidx.privacysandbox.ads.adservices.java.measurement.MeasurementManagerFutures;
import androidx.privacysandbox.ads.adservices.measurement.DeletionRequest;
import androidx.privacysandbox.ads.adservices.measurement.WebSourceParams;
import androidx.privacysandbox.ads.adservices.measurement.WebSourceRegistrationRequest;
import androidx.privacysandbox.ads.adservices.measurement.WebTriggerParams;
import androidx.privacysandbox.ads.adservices.measurement.WebTriggerRegistrationRequest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.TestUtil;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

public class MeasurementManagerJetpackTest {
    private static final String TAG = "MeasurementManagerJetpackTest";
    TestUtil mTestUtil = new TestUtil(InstrumentationRegistry.getInstrumentation(), TAG);

    private static final Uri SOURCE_REGISTRATION_URI = Uri.parse("https://test.com/source");
    private static final Uri TRIGGER_REGISTRATION_URI = Uri.parse("https://test.com/trigger");
    private static final Uri DESTINATION = Uri.parse("http://trigger-origin.com");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os.destination");
    private static final Uri WEB_DESTINATION = Uri.parse("http://web-destination.com");
    private static final Uri ORIGIN_URI = Uri.parse("https://sample.example1.com");
    private static final Uri DOMAIN_URI = Uri.parse("https://example2.com");

    private MeasurementManagerFutures mMeasurementManager;

    @Before
    public void setup() throws Exception {
        Assume.assumeTrue(SdkExtensions.getExtensionVersion(SdkExtensions.AD_SERVICES) >= 5);
        // To grant access to all pp api app
        mTestUtil.overrideAllowlists(true);
        // We need to turn the Consent Manager into debug mode
        mTestUtil.overrideConsentManagerDebugMode(true);
        mTestUtil.overrideMeasurementKillSwitches(true);
        mTestUtil.overrideAdIdKillSwitch(true);
        mTestUtil.overrideDisableMeasurementEnrollmentCheck("1");
        mMeasurementManager =
                MeasurementManagerFutures.from(ApplicationProvider.getApplicationContext());

        // Put in a short sleep to make sure the updated config propagates
        // before starting the tests
        Thread.sleep(100);
    }

    @After
    public void tearDown() throws Exception {
        mTestUtil.overrideAllowlists(false);
        mTestUtil.overrideConsentManagerDebugMode(false);
        mTestUtil.resetOverrideDisableMeasurementEnrollmentCheck();
        mTestUtil.overrideMeasurementKillSwitches(false);
        mTestUtil.overrideAdIdKillSwitch(false);
        mTestUtil.overrideDisableMeasurementEnrollmentCheck("0");
    }

    @Test
    public void testRegisterSource_NoServerSetup_NoErrors() throws Exception {
        assertThat(
                        mMeasurementManager
                                .registerSourceAsync(
                                        SOURCE_REGISTRATION_URI, /* inputEvent= */ null)
                                .get())
                .isNotNull();
    }

    @Test
    public void testRegisterTrigger_NoServerSetup_NoErrors() throws Exception {
        assertThat(mMeasurementManager.registerTriggerAsync(TRIGGER_REGISTRATION_URI).get())
                .isNotNull();
    }

    @Test
    public void registerWebSource_NoErrors() throws Exception {
        WebSourceParams webSourceParams = new WebSourceParams(SOURCE_REGISTRATION_URI, false);

        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest(
                        Collections.singletonList(webSourceParams),
                        SOURCE_REGISTRATION_URI,
                        /* inputEvent= */ null,
                        OS_DESTINATION,
                        WEB_DESTINATION,
                        /* verifiedDestination= */ null);

        assertThat(mMeasurementManager.registerWebSourceAsync(webSourceRegistrationRequest).get())
                .isNotNull();
    }

    @Test
    public void registerWebTrigger_NoErrors() throws Exception {
        WebTriggerParams webTriggerParams = new WebTriggerParams(TRIGGER_REGISTRATION_URI, false);
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest(
                        Collections.singletonList(webTriggerParams), DESTINATION);

        assertThat(mMeasurementManager.registerWebTriggerAsync(webTriggerRegistrationRequest).get())
                .isNotNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withNoRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder(
                                DeletionRequest.DELETION_MODE_ALL,
                                DeletionRequest.MATCH_BEHAVIOR_DELETE)
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .build();
        assertThat(mMeasurementManager.deleteRegistrationsAsync(deletionRequest).get()).isNotNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withEmptyLists_withRange_withCallback_NoErrors()
            throws Exception {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder(
                                DeletionRequest.DELETION_MODE_ALL,
                                DeletionRequest.MATCH_BEHAVIOR_DELETE)
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setStart(Instant.ofEpochMilli(0))
                        .setEnd(Instant.now())
                        .build();
        assertThat(mMeasurementManager.deleteRegistrationsAsync(deletionRequest).get()).isNotNull();
    }

    @Test
    public void testDeleteRegistrations_withRequest_withInvalidArguments_withCallback_hasError() {
        DeletionRequest deletionRequest =
                new DeletionRequest.Builder(
                                DeletionRequest.DELETION_MODE_ALL,
                                DeletionRequest.MATCH_BEHAVIOR_DELETE)
                        .setDomainUris(Collections.singletonList(DOMAIN_URI))
                        .setOriginUris(Collections.singletonList(ORIGIN_URI))
                        .setStart(Instant.now().plusMillis(1000))
                        .setEnd(Instant.now())
                        .build();
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> mMeasurementManager.deleteRegistrationsAsync(deletionRequest).get());
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testMeasurementApiStatus_returnResultStatus() throws Exception {
        int result = mMeasurementManager.getMeasurementApiStatusAsync().get();
        assertThat(result).isEqualTo(1);
    }
}