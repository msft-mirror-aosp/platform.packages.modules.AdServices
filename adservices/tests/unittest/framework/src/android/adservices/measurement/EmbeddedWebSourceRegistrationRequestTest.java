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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Parcel;
import android.view.KeyEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class EmbeddedWebSourceRegistrationRequestTest {
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");
    private static final Uri OS_DESTINATION_URI = Uri.parse("https://os-destination.com");
    private static final Uri WEB_DESTINATION_URI = Uri.parse("https://web-destination.com");
    private static final Uri VERIFIED_DESTINATION = Uri.parse("https://verified-dest.com");
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);

    private static final SourceParams SOURCE_REGISTRATION_1 =
            new SourceParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_1)
                    .setDebugEnabled(true)
                    .build();

    private static final SourceParams SOURCE_REGISTRATION_2 =
            new SourceParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_2)
                    .setDebugEnabled(false)
                    .build();

    private static final List<SourceParams> SOURCE_REGISTRATIONS =
            Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2);

    @Test
    public void testDefaults() throws Exception {
        EmbeddedWebSourceRegistrationRequest request =
                new EmbeddedWebSourceRegistrationRequest.Builder()
                        .setSourceParams(SOURCE_REGISTRATIONS)
                        .setTopOriginUri(TOP_ORIGIN_URI)
                        .setOsDestination(OS_DESTINATION_URI)
                        .build();

        assertEquals(SOURCE_REGISTRATIONS, request.getSourceParams());
        assertEquals(TOP_ORIGIN_URI, request.getTopOriginUri());
        assertEquals(OS_DESTINATION_URI, request.getOsDestination());
        assertNull(request.getInputEvent());
        assertNull(request.getWebDestination());
        assertNull(request.getVerifiedDestination());
    }

    @Test
    public void testCreationAttribution() {
        verifyExampleRegistration(createExampleRegistrationRequest());
    }

    @Test
    public void build_withMissingOsAndWebDestination_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EmbeddedWebSourceRegistrationRequest.Builder()
                                .setSourceParams(SOURCE_REGISTRATIONS)
                                .setInputEvent(INPUT_KEY_EVENT)
                                .setVerifiedDestination(VERIFIED_DESTINATION)
                                .setTopOriginUri(TOP_ORIGIN_URI)
                                .setOsDestination(null)
                                .setWebDestination(null)
                                .build());
    }

    @Test
    public void testParcelingAttribution() {
        Parcel p = Parcel.obtain();
        createExampleRegistrationRequest().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleRegistration(EmbeddedWebSourceRegistrationRequest.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_withInvalidParams_fail() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EmbeddedWebSourceRegistrationRequest.Builder()
                                .setSourceParams(null)
                                .setInputEvent(INPUT_KEY_EVENT)
                                .setTopOriginUri(TOP_ORIGIN_URI)
                                .build());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new EmbeddedWebSourceRegistrationRequest.Builder()
                                .setSourceParams(Collections.emptyList())
                                .setInputEvent(INPUT_KEY_EVENT)
                                .setTopOriginUri(TOP_ORIGIN_URI)
                                .build());
    }

    private EmbeddedWebSourceRegistrationRequest createExampleRegistrationRequest() {
        return new EmbeddedWebSourceRegistrationRequest.Builder()
                .setSourceParams(SOURCE_REGISTRATIONS)
                .setInputEvent(INPUT_KEY_EVENT)
                .setVerifiedDestination(VERIFIED_DESTINATION)
                .setTopOriginUri(TOP_ORIGIN_URI)
                .setOsDestination(OS_DESTINATION_URI)
                .setWebDestination(WEB_DESTINATION_URI)
                .build();
    }

    private void verifyExampleRegistration(EmbeddedWebSourceRegistrationRequest request) {
        assertEquals(SOURCE_REGISTRATIONS, request.getSourceParams());
        assertEquals(TOP_ORIGIN_URI, request.getTopOriginUri());
        assertEquals(OS_DESTINATION_URI, request.getOsDestination());
        assertEquals(WEB_DESTINATION_URI, request.getWebDestination());
        assertEquals(INPUT_KEY_EVENT.getAction(), ((KeyEvent) request.getInputEvent()).getAction());
        assertEquals(
                INPUT_KEY_EVENT.getKeyCode(), ((KeyEvent) request.getInputEvent()).getKeyCode());
        assertEquals(VERIFIED_DESTINATION, request.getVerifiedDestination());
    }
}
