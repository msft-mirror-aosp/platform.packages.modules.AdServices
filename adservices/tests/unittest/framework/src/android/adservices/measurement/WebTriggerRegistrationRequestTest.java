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

import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class WebTriggerRegistrationRequestTest {
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");

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

    @Test
    public void testDefaults() throws Exception {
        WebTriggerRegistrationRequest request =
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(TRIGGER_REGISTRATIONS)
                        .setDestination(TOP_ORIGIN_URI)
                        .build();

        assertEquals(TRIGGER_REGISTRATIONS, request.getTriggerParams());
        assertEquals(TOP_ORIGIN_URI, request.getDestination());
    }

    @Test
    public void testCreationAttribution() {
        verifyExampleRegistration(createExampleRegistrationRequest());
    }

    @Test
    public void testParcelingAttribution() {
        Parcel p = Parcel.obtain();
        createExampleRegistrationRequest().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleRegistration(WebTriggerRegistrationRequest.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_withInvalidParams_fail() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WebTriggerRegistrationRequest.Builder()
                                .setTriggerParams(null)
                                .setDestination(TOP_ORIGIN_URI)
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WebTriggerRegistrationRequest.Builder()
                                .setTriggerParams(Collections.emptyList())
                                .setDestination(TOP_ORIGIN_URI)
                                .build());
    }

    private WebTriggerRegistrationRequest createExampleRegistrationRequest() {
        return new WebTriggerRegistrationRequest.Builder()
                .setTriggerParams(TRIGGER_REGISTRATIONS)
                .setDestination(TOP_ORIGIN_URI)
                .build();
    }

    private void verifyExampleRegistration(WebTriggerRegistrationRequest request) {
        assertEquals(TRIGGER_REGISTRATIONS, request.getTriggerParams());
        assertEquals(TOP_ORIGIN_URI, request.getDestination());
    }
}
