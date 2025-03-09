/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.tests.cts.measurement;

import static com.android.adservices.shared.testing.AndroidSdk.RVC;

import static com.google.common.truth.Truth.assertWithMessage;

import android.adservices.exceptions.AdServicesException;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.net.Uri;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.WebUtil;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.concurrency.DeviceSideConcurrencyHelper;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RequiresSdkRange(atMost = RVC)
public final class MeasurementManagerRvcCtsTest extends CtsMeasurementEndToEndTestCase {

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    // Note: The source and trigger registration used here must match one of those in
    // {@link PreEnrolledAdTechForTest}.
    private static final Uri SOURCE_REGISTRATION_URI = WebUtil.validUri("https://test.test/source");
    private static final Uri TRIGGER_REGISTRATION_URI =
            WebUtil.validUri("https://test.test/trigger");
    private static final Uri DESTINATION = WebUtil.validUri("http://trigger-origin.test");
    private static final Uri OS_DESTINATION = Uri.parse("android-app://com.os.destination");
    private static final Uri WEB_DESTINATION = WebUtil.validUri("http://web-destination.test");
    private static final InputEvent INPUT_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);

    private MeasurementManager mMeasurementManager;

    @Before
    public void setup() throws Exception {
        // Kill adservices process to avoid interfering from other tests.
        AdservicesTestHelper.killAdservicesProcess(mContext);
        mMeasurementManager = MeasurementManager.get(mContext);
        assertWithMessage("MeasurementManager.get(%s)", mContext)
                .that(mMeasurementManager)
                .isNotNull();

        DeviceSideConcurrencyHelper.sleep(
                1000, "Cool-off rate limiter in case it was initialized by another test");
    }

    @Test
    public void testRegisterSourceUri_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mMeasurementManager.registerSource(
                Uri.parse("https://registration-source"),
                /* inputEvent= */ null,
                CALLBACK_EXECUTOR,
                callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testRegisterSourceRequest_onR_invokesCallbackOnError() throws Exception {
        SourceRegistrationRequest request = createSourceRegistrationRequest();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mMeasurementManager.registerSource(request, CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testRegisterWebSource_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(SOURCE_REGISTRATION_URI)
                        .setDebugKeyAllowed(false)
                        .build();
        WebSourceRegistrationRequest webSourceRegistrationRequest =
                new WebSourceRegistrationRequest.Builder(
                                Collections.singletonList(webSourceParams), SOURCE_REGISTRATION_URI)
                        .setInputEvent(null)
                        .setAppDestination(OS_DESTINATION)
                        .setWebDestination(WEB_DESTINATION)
                        .setVerifiedDestination(null)
                        .build();

        mMeasurementManager.registerWebSource(
                webSourceRegistrationRequest, CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testRegisterWebTrigger_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(TRIGGER_REGISTRATION_URI).build();
        WebTriggerRegistrationRequest webTriggerRegistrationRequest =
                new WebTriggerRegistrationRequest.Builder(
                                Collections.singletonList(webTriggerParams), DESTINATION)
                        .build();

        mMeasurementManager.registerWebTrigger(
                webTriggerRegistrationRequest, CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testRegisterTrigger_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mMeasurementManager.registerTrigger(
                Uri.parse("https://registration-trigger"), CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testDeleteRegistrations_onR_invokesCallbackOnError() throws Exception {
        DeletionRequest request = new DeletionRequest.Builder().build();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mMeasurementManager.deleteRegistrations(request, CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testGetMeasurementApiStatus_onR_invokesCallbackOnError() throws Exception {
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mMeasurementManager.getMeasurementApiStatus(CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    private SourceRegistrationRequest createSourceRegistrationRequest() {
        return new SourceRegistrationRequest.Builder(
                        Collections.singletonList(SOURCE_REGISTRATION_URI))
                .setInputEvent(INPUT_EVENT)
                .build();
    }
}
