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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.view.KeyEvent;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class WebSourceRegistrationRequestInternalTest {
    private static final Context CONTEXT =
            InstrumentationRegistry.getInstrumentation().getContext();
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");
    private static final Uri OS_DESTINATION_URI = Uri.parse("android-app://com.os-destination");
    private static final Uri WEB_DESTINATION_URI = Uri.parse("https://web-destination.com");
    private static final Uri VERIFIED_DESTINATION = Uri.parse("https://verified-dest.com");
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);

    private static final WebSourceParams SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_1)
                    .setAllowDebugKey(true)
                    .build();

    private static final WebSourceParams SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder()
                    .setRegistrationUri(REGISTRATION_URI_2)
                    .setAllowDebugKey(false)
                    .build();

    private static final List<WebSourceParams> SOURCE_REGISTRATIONS =
            Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2);

    private static final WebSourceRegistrationRequest EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST =
            new WebSourceRegistrationRequest.Builder()
                    .setSourceParams(SOURCE_REGISTRATIONS)
                    .setTopOriginUri(TOP_ORIGIN_URI)
                    .setOsDestination(OS_DESTINATION_URI)
                    .setWebDestination(WEB_DESTINATION_URI)
                    .setVerifiedDestination(VERIFIED_DESTINATION)
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
                WebSourceRegistrationRequestInternal.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_nullParameters_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequestInternal.Builder()
                                .setSourceRegistrationRequest(null)
                                .setPackageName(CONTEXT.getAttributionSource().getPackageName())
                                .build());

        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequestInternal.Builder()
                                .setSourceRegistrationRequest(EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST)
                                .setPackageName(null)
                                .build());
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExampleRegistrationRequest().describeContents());
    }

    @Test
    public void testHashCode_equals() {
        final WebSourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final WebSourceRegistrationRequestInternal request2 = createExampleRegistrationRequest();
        final Set<WebSourceRegistrationRequestInternal> requestSet1 = Set.of(request1);
        final Set<WebSourceRegistrationRequestInternal> requestSet2 = Set.of(request2);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertEquals(request1, request2);
        assertEquals(requestSet1, requestSet2);
    }

    @Test
    public void testHashCode_notEquals() {
        final WebSourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        final WebSourceRegistrationRequestInternal request2 =
                new WebSourceRegistrationRequestInternal.Builder()
                        .setSourceRegistrationRequest(EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST)
                        .setPackageName("com.foo")
                        .build();

        final Set<WebSourceRegistrationRequestInternal> requestSet1 = Set.of(request1);
        final Set<WebSourceRegistrationRequestInternal> requestSet2 = Set.of(request2);
        assertNotEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1, request2);
        assertNotEquals(requestSet1, requestSet2);
    }

    private WebSourceRegistrationRequestInternal createExampleRegistrationRequest() {
        return new WebSourceRegistrationRequestInternal.Builder()
                .setSourceRegistrationRequest(EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST)
                .setPackageName(CONTEXT.getAttributionSource().getPackageName())
                .build();
    }

    private void verifyExampleRegistrationInternal(WebSourceRegistrationRequestInternal request) {
        verifyExampleRegistration(request.getSourceRegistrationRequest());
        assertEquals(CONTEXT.getAttributionSource().getPackageName(), request.getPackageName());
    }

    private void verifyExampleRegistration(WebSourceRegistrationRequest request) {
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
