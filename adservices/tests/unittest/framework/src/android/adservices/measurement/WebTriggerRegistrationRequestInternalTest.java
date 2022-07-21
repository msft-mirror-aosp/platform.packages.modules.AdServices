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

package android.adservices.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class WebTriggerRegistrationRequestInternalTest {
    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");

    private static final WebTriggerParams TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_1)
                    .setAllowDebugKey(true)
                    .build();

    private static final WebTriggerParams TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_2)
                    .setAllowDebugKey(false)
                    .build();

    private static final List<WebTriggerParams> TRIGGER_REGISTRATIONS =
            Arrays.asList(TRIGGER_REGISTRATION_1, TRIGGER_REGISTRATION_2);

    private static final WebTriggerRegistrationRequest EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST =
            new WebTriggerRegistrationRequest.Builder()
                    .setTriggerParams(TRIGGER_REGISTRATIONS)
                    .setDestination(TOP_ORIGIN_URI)
                    .build();

    @Test
    public void build_exampleRequest_success() {
        verifyExampleRegistrationInternal(createExampleRegistrationRequest());
    }

    @Test
    public void createFromParcel_basic_success() {
        Parcel p = Parcel.obtain();
        createExampleRegistrationRequest().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleRegistrationInternal(
                WebTriggerRegistrationRequestInternal.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_nullParameters_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebTriggerRegistrationRequestInternal.Builder()
                                .setTriggerRegistrationRequest(null)
                                .setPackageName(CONTEXT.getAttributionSource().getPackageName())
                                .build());

        assertThrows(
                NullPointerException.class,
                () ->
                        new WebTriggerRegistrationRequestInternal.Builder()
                                .setTriggerRegistrationRequest(EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST)
                                .setPackageName(null)
                                .build());
    }

    private WebTriggerRegistrationRequestInternal createExampleRegistrationRequest() {
        return new WebTriggerRegistrationRequestInternal.Builder()
                .setTriggerRegistrationRequest(EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST)
                .setPackageName(CONTEXT.getAttributionSource().getPackageName())
                .build();
    }

    private void verifyExampleRegistrationInternal(WebTriggerRegistrationRequestInternal request) {
        verifyExampleRegistration(request.getTriggerRegistrationRequest());
        assertEquals(CONTEXT.getAttributionSource().getPackageName(), request.getPackageName());
    }

    private void verifyExampleRegistration(WebTriggerRegistrationRequest request) {
        assertEquals(TRIGGER_REGISTRATIONS, request.getTriggerParams());
        assertEquals(TOP_ORIGIN_URI, request.getDestination());
    }
}
