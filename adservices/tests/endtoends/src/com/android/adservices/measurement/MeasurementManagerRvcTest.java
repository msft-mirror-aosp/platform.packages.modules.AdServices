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

package com.android.adservices.measurement;

import static com.android.adservices.measurement.MeasurementManagerUtil.buildDefaultAppSourcesRegistrationRequest;
import static com.android.adservices.measurement.MeasurementManagerUtil.buildDefaultWebSourceRegistrationRequest;
import static com.android.adservices.measurement.MeasurementManagerUtil.buildDefaultWebTriggerRegistrationRequest;
import static com.android.adservices.shared.testing.AndroidSdk.RVC;

import android.adservices.exceptions.AdServicesException;
import android.adservices.measurement.DeletionRequest;
import android.adservices.measurement.MeasurementManager;
import android.adservices.measurement.SourceRegistrationRequest;
import android.net.Uri;

import com.android.adservices.AdServicesEndToEndTestCase;
import com.android.adservices.common.AdServicesOutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;

import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@RequiresSdkRange(atMost = RVC)
public final class MeasurementManagerRvcTest extends AdServicesEndToEndTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private MeasurementManager getMeasurementManager() {
        return MeasurementManager.get(mContext);
    }

    @Test
    public void testRegisterSourceUri_onR_invokesCallbackOnError() throws Exception {
        MeasurementManager mm = getMeasurementManager();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mm.registerSource(
                Uri.parse("https://registration-source"),
                /* inputEvent= */ null,
                CALLBACK_EXECUTOR,
                callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testRegisterSourceRequest_onR_invokesCallbackOnError() throws Exception {
        MeasurementManager mm = getMeasurementManager();
        SourceRegistrationRequest request = buildDefaultAppSourcesRegistrationRequest();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mm.registerSource(request, CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testRegisterWebSource_onR_invokesCallbackOnError() throws Exception {
        MeasurementManager mm = getMeasurementManager();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mm.registerWebSource(
                buildDefaultWebSourceRegistrationRequest(), CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testRegisterWebTrigger_onR_invokesCallbackOnError() throws Exception {
        MeasurementManager mm = getMeasurementManager();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mm.registerWebTrigger(
                buildDefaultWebTriggerRegistrationRequest(), CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testRegisterTrigger_onR_invokesCallbackOnError() throws Exception {
        MeasurementManager mm = getMeasurementManager();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mm.registerTrigger(Uri.parse("https://registration-trigger"), CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testDeleteRegistrations_onR_invokesCallbackOnError() throws Exception {
        MeasurementManager mm = getMeasurementManager();
        DeletionRequest request = new DeletionRequest.Builder().build();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mm.deleteRegistrations(request, CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }

    @Test
    public void testGetMeasurementApiStatus_onR_invokesCallbackOnError() throws Exception {
        MeasurementManager mm = getMeasurementManager();
        AdServicesOutcomeReceiverForTests callback = new AdServicesOutcomeReceiverForTests();

        mm.getMeasurementApiStatus(CALLBACK_EXECUTOR, callback);

        callback.assertFailure(AdServicesException.class);
    }
}
