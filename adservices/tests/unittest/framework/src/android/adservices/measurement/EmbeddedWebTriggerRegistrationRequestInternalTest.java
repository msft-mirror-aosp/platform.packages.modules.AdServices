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
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class EmbeddedWebTriggerRegistrationRequestInternalTest {
    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);

    private static final TriggerParams TRIGGER_REGISTRATION_1 =
            new TriggerParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_1)
                    .setDebugEnabled(true)
                    .build();

    private static final TriggerParams TRIGGER_REGISTRATION_2 =
            new TriggerParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_2)
                    .setDebugEnabled(false)
                    .build();

    private static final List<TriggerParams> TRIGGER_REGISTRATIONS =
            Arrays.asList(TRIGGER_REGISTRATION_1, TRIGGER_REGISTRATION_2);

    private static final EmbeddedWebTriggerRegistrationRequest
            EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST =
                    new EmbeddedWebTriggerRegistrationRequest.Builder()
                            .setTriggerParams(TRIGGER_REGISTRATIONS)
                            .setTopOriginUri(TOP_ORIGIN_URI)
                            .setInputEvent(INPUT_KEY_EVENT)
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
                EmbeddedWebTriggerRegistrationRequestInternal.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_nullParameters_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new EmbeddedWebTriggerRegistrationRequestInternal.Builder()
                                .setTriggerRegistrationRequest(null)
                                .setAttributionSource(CONTEXT.getAttributionSource())
                                .build());

        assertThrows(
                NullPointerException.class,
                () ->
                        new EmbeddedWebTriggerRegistrationRequestInternal.Builder()
                                .setTriggerRegistrationRequest(EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST)
                                .setAttributionSource(null)
                                .build());
    }

    private EmbeddedWebTriggerRegistrationRequestInternal createExampleRegistrationRequest() {
        return new EmbeddedWebTriggerRegistrationRequestInternal.Builder()
                .setTriggerRegistrationRequest(EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST)
                .setAttributionSource(CONTEXT.getAttributionSource())
                .build();
    }

    private void verifyExampleRegistrationInternal(
            EmbeddedWebTriggerRegistrationRequestInternal request) {
        verifyExampleRegistration(request.getTriggerRegistrationRequest());
        assertEquals(CONTEXT.getAttributionSource(), request.getAttributionSource());
    }

    private void verifyExampleRegistration(EmbeddedWebTriggerRegistrationRequest request) {
        assertEquals(TRIGGER_REGISTRATIONS, request.getTriggerParams());
        assertEquals(TOP_ORIGIN_URI, request.getTopOriginUri());
        assertEquals(INPUT_KEY_EVENT.getAction(), ((KeyEvent) request.getInputEvent()).getAction());
        assertEquals(
                INPUT_KEY_EVENT.getKeyCode(), ((KeyEvent) request.getInputEvent()).getKeyCode());
    }
}
