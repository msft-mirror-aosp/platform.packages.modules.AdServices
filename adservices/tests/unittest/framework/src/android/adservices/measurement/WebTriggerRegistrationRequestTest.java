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

import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class WebTriggerRegistrationRequestTest {
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

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExampleRegistrationRequest().describeContents());
    }

    @Test
    public void testHashCode_equals() {
        final WebTriggerRegistrationRequest request1 = createExampleRegistrationRequest();
        final WebTriggerRegistrationRequest request2 = createExampleRegistrationRequest();
        final Set<WebTriggerRegistrationRequest> requestSet1 = Set.of(request1);
        final Set<WebTriggerRegistrationRequest> requestSet2 = Set.of(request2);
        assertEquals(request1.hashCode(), request2.hashCode());
        assertEquals(request1, request2);
        assertEquals(requestSet1, requestSet2);
    }

    @Test
    public void testHashCode_notEquals() {
        final WebTriggerRegistrationRequest request1 = createExampleRegistrationRequest();
        final WebTriggerRegistrationRequest request2 =
                new WebTriggerRegistrationRequest.Builder()
                        .setTriggerParams(TRIGGER_REGISTRATIONS)
                        .setDestination(Uri.parse("https://notEqual"))
                        .build();
        final Set<WebTriggerRegistrationRequest> requestSet1 = Set.of(request1);
        final Set<WebTriggerRegistrationRequest> requestSet2 = Set.of(request2);
        assertNotEquals(request1.hashCode(), request2.hashCode());
        assertNotEquals(request1, request2);
        assertNotEquals(requestSet1, requestSet2);
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
