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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Parcel;
import android.view.KeyEvent;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class WebSourceRegistrationRequestTest extends AdServicesUnitTestCase {
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");
    private static final Uri OS_DESTINATION_URI = Uri.parse("android-app://com.os-destination");
    private static final Uri WEB_DESTINATION_URI = Uri.parse("https://web-destination.com");
    private static final Uri VERIFIED_DESTINATION = Uri.parse("https://verified-dest.com");
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);

    private static final WebSourceParams SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebSourceParams SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final List<WebSourceParams> SOURCE_REGISTRATIONS =
            Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2);

    private static final WebSourceRegistrationRequest SOURCE_REGISTRATION_REQUEST =
            new WebSourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS, TOP_ORIGIN_URI)
                    .setInputEvent(INPUT_KEY_EVENT)
                    .setVerifiedDestination(VERIFIED_DESTINATION)
                    .setAppDestination(OS_DESTINATION_URI)
                    .setWebDestination(WEB_DESTINATION_URI)
                    .build();

    @Test
    public void testDefaults() throws Exception {
        WebSourceRegistrationRequest request =
                new WebSourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS, TOP_ORIGIN_URI)
                        .setAppDestination(OS_DESTINATION_URI)
                        .build();

        expect.that(request.getSourceParams()).isEqualTo(SOURCE_REGISTRATIONS);
        expect.that(request.getTopOriginUri()).isEqualTo(TOP_ORIGIN_URI);
        expect.that(request.getAppDestination()).isEqualTo(OS_DESTINATION_URI);
        expect.that(request.getInputEvent()).isNull();
        expect.that(request.getWebDestination()).isNull();
        expect.that(request.getVerifiedDestination()).isNull();
    }

    @Test
    public void testCreationAttribution() {
        verifyExampleRegistration(SOURCE_REGISTRATION_REQUEST);
    }

    @Test
    public void build_withMissingOsAndWebDestination_DoesNotThrowException() {
        WebSourceRegistrationRequest request =
                new WebSourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS, TOP_ORIGIN_URI)
                        .setInputEvent(INPUT_KEY_EVENT)
                        .setVerifiedDestination(VERIFIED_DESTINATION)
                        .setAppDestination(null)
                        .setWebDestination(null)
                        .build();
        assertThat(request).isNotNull();
    }

    @Test
    public void testParcelingAttribution() {
        Parcel p = Parcel.obtain();
        SOURCE_REGISTRATION_REQUEST.writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleRegistration(WebSourceRegistrationRequest.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_withInvalidParams_fail() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequest.Builder(null, TOP_ORIGIN_URI)
                                .setInputEvent(INPUT_KEY_EVENT));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WebSourceRegistrationRequest.Builder(
                                        Collections.emptyList(), TOP_ORIGIN_URI)
                                .setInputEvent(INPUT_KEY_EVENT));

        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS, null)
                                .setInputEvent(INPUT_KEY_EVENT));
    }

    @Test
    public void testMaxWebSourceParam_failsWhenExceeds() {
        assertThat(
                        new WebSourceRegistrationRequest.Builder(
                                        generateWebSourceParamsList(80), TOP_ORIGIN_URI)
                                .setInputEvent(INPUT_KEY_EVENT)
                                .build())
                .isNotNull();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WebSourceRegistrationRequest.Builder(
                                        generateWebSourceParamsList(81), TOP_ORIGIN_URI)
                                .setInputEvent(INPUT_KEY_EVENT));
    }

    @Test
    public void testDescribeContents() {
        expect.that(createExampleRegistrationRequest().describeContents()).isEqualTo(0);
    }

    @Test
    public void testEquals() throws Exception {
        EqualsTester et = new EqualsTester(expect);
        WebSourceRegistrationRequest request1 = createExampleRegistrationRequest();
        WebSourceRegistrationRequest request2 = createExampleRegistrationRequest();
        et.expectObjectsAreEqual(request1, request2);
    }

    @Test
    public void testNotEquals() {
        EqualsTester et = new EqualsTester(expect);
        WebSourceRegistrationRequest request1 = createExampleRegistrationRequest();
        WebSourceRegistrationRequest request2 =
                new WebSourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS, TOP_ORIGIN_URI)
                        .setInputEvent(null)
                        .setVerifiedDestination(VERIFIED_DESTINATION)
                        .setAppDestination(OS_DESTINATION_URI)
                        .setWebDestination(WEB_DESTINATION_URI)
                        .build();
        et.expectObjectsAreNotEqual(request1, request2);
    }

    private WebSourceRegistrationRequest createExampleRegistrationRequest() {
        return new WebSourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS, TOP_ORIGIN_URI)
                .setInputEvent(INPUT_KEY_EVENT)
                .setVerifiedDestination(VERIFIED_DESTINATION)
                .setAppDestination(OS_DESTINATION_URI)
                .setWebDestination(WEB_DESTINATION_URI)
                .build();
    }

    private void verifyExampleRegistration(WebSourceRegistrationRequest request) {
        expect.that(request.getSourceParams()).isEqualTo(SOURCE_REGISTRATIONS);
        expect.that(request.getTopOriginUri()).isEqualTo(TOP_ORIGIN_URI);
        expect.that(request.getAppDestination()).isEqualTo(OS_DESTINATION_URI);
        expect.that(request.getWebDestination()).isEqualTo(WEB_DESTINATION_URI);
        expect.that(((KeyEvent) request.getInputEvent()).getAction())
                .isEqualTo(INPUT_KEY_EVENT.getAction());
        expect.that(((KeyEvent) request.getInputEvent()).getKeyCode())
                .isEqualTo(INPUT_KEY_EVENT.getKeyCode());
        expect.that(request.getVerifiedDestination()).isEqualTo(VERIFIED_DESTINATION);
    }

    private static List<WebSourceParams> generateWebSourceParamsList(int count) {
        return IntStream.range(0, count)
                .mapToObj(
                        i ->
                                new WebSourceParams.Builder(REGISTRATION_URI_1)
                                        .setDebugKeyAllowed(true)
                                        .build())
                .collect(Collectors.toList());
    }
}
