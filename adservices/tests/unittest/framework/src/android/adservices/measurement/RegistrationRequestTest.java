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

import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link android.adservices.measurement.RegistrationRequest} */
public final class RegistrationRequestTest extends AdServicesUnitTestCase {
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";

    private RegistrationRequest createExampleAttribution() {
        return new RegistrationRequest.Builder(
                        RegistrationRequest.REGISTER_SOURCE,
                        Uri.parse("https://baz.test"),
                        sContext.getPackageName(),
                        SDK_PACKAGE_NAME)
                .setRequestTime(1000L)
                .setAdIdPermissionGranted(true)
                .build();
    }

    void verifyExampleAttribution(RegistrationRequest request) {
        expect.that(request.getRegistrationUri().toString()).isEqualTo("https://baz.test");
        expect.that(request.getRegistrationType()).isEqualTo(RegistrationRequest.REGISTER_SOURCE);
        expect.that(request.getInputEvent()).isNull();
        expect.that(request.getAppPackageName()).isNotNull();
        expect.that(request.getSdkPackageName()).isEqualTo(SDK_PACKAGE_NAME);
        expect.that(request.getRequestTime()).isEqualTo(1000L);
        expect.that(request.isAdIdPermissionGranted()).isTrue();
    }

    @Test
    public void testNoRegistrationType_throwException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.INVALID,
                                        Uri.parse("https://foo.test"),
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testRegistrationUriWithoutScheme_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_SOURCE,
                                        Uri.parse("foo.test"),
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testRegistrationUriWithNonHttpsScheme_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_SOURCE,
                                        Uri.parse("http://foo.test"),
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testNoAttributionSource_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_TRIGGER,
                                        /* registrationUri = */ null,
                                        sContext.getPackageName(),
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testNoAppPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_TRIGGER,
                                        Uri.parse("https://foo.test"),
                                        /* appPackageName = */ null,
                                        SDK_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testNoSdkPackageName_throwException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new RegistrationRequest.Builder(
                                        RegistrationRequest.REGISTER_TRIGGER,
                                        /* registrationUri = */ Uri.parse("https://foo.test"),
                                        sContext.getPackageName(),
                                        /* sdkPackageName = */ null)
                                .build());
    }

    @Test
    public void testDefaults() throws Exception {
        RegistrationRequest request =
                new RegistrationRequest.Builder(
                                RegistrationRequest.REGISTER_TRIGGER,
                                Uri.parse("https://foo.test"),
                                sContext.getPackageName(),
                                SDK_PACKAGE_NAME)
                        .build();
        expect.that(request.getRegistrationUri().toString()).isEqualTo("https://foo.test");
        expect.that(request.getRegistrationType()).isEqualTo(RegistrationRequest.REGISTER_TRIGGER);
        expect.that(request.getInputEvent()).isNull();
        expect.that(request.getAppPackageName()).isNotNull();
        expect.that(request.getSdkPackageName()).isEqualTo(SDK_PACKAGE_NAME);
        expect.that(request.getRequestTime()).isEqualTo(0);
    }

    @Test
    public void testCreationAttribution() {
        verifyExampleAttribution(createExampleAttribution());
    }

    @Test
    public void testParcelingAttribution() {
        Parcel p = Parcel.obtain();
        createExampleAttribution().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleAttribution(
                RegistrationRequest.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testDescribeContents() {
        expect.that(createExampleAttribution().describeContents()).isEqualTo(0);
    }
}
