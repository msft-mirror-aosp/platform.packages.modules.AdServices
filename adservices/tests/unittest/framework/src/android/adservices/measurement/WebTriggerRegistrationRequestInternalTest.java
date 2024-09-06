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
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class WebTriggerRegistrationRequestInternalTest extends AdServicesUnitTestCase {
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");

    private static final WebTriggerParams TRIGGER_REGISTRATION_1 =
            new WebTriggerParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebTriggerParams TRIGGER_REGISTRATION_2 =
            new WebTriggerParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final List<WebTriggerParams> TRIGGER_REGISTRATIONS =
            Arrays.asList(TRIGGER_REGISTRATION_1, TRIGGER_REGISTRATION_2);

    private static final WebTriggerRegistrationRequest EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST =
            new WebTriggerRegistrationRequest.Builder(TRIGGER_REGISTRATIONS, TOP_ORIGIN_URI)
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
    public void build_nullTriggerRegistrationRequest_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebTriggerRegistrationRequestInternal.Builder(
                                null, mPackageName, SDK_PACKAGE_NAME));
    }

    @Test
    public void build_nullAppPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebTriggerRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST,
                                /* appPackageName */ null,
                                SDK_PACKAGE_NAME));
    }

    @Test
    public void build_nullSdkPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebTriggerRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST,
                                mPackageName,
                                /* sdkPackageName= */ null));
    }

    @Test
    public void testDescribeContents() {
        expect.that(createExampleRegistrationRequest().describeContents()).isEqualTo(0);
    }

    @Test
    public void testEquals() {
        EqualsTester et = new EqualsTester(expect);
        WebTriggerRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        WebTriggerRegistrationRequestInternal request2 = createExampleRegistrationRequest();
        et.expectObjectsAreEqual(request1, request2);
    }

    @Test
    public void testNotEquals() {
        EqualsTester et = new EqualsTester(expect);
        WebTriggerRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        WebTriggerRegistrationRequestInternal request2 =
                new WebTriggerRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST, "com.foo", SDK_PACKAGE_NAME)
                        .build();
        et.expectObjectsAreNotEqual(request1, request2);
    }

    private WebTriggerRegistrationRequestInternal createExampleRegistrationRequest() {
        return new WebTriggerRegistrationRequestInternal.Builder(
                        EXAMPLE_EXTERNAL_TRIGGER_REG_REQUEST, mPackageName, SDK_PACKAGE_NAME)
                .setAdIdPermissionGranted(true)
                .build();
    }

    private void verifyExampleRegistrationInternal(WebTriggerRegistrationRequestInternal request) {
        verifyExampleRegistration(request.getTriggerRegistrationRequest());
        expect.that(request.getAppPackageName()).isEqualTo(mPackageName);
        expect.that(request.isAdIdPermissionGranted()).isTrue();
    }

    private void verifyExampleRegistration(WebTriggerRegistrationRequest request) {
        expect.that(request.getTriggerParams()).isEqualTo(TRIGGER_REGISTRATIONS);
        expect.that(request.getDestination()).isEqualTo(TOP_ORIGIN_URI);
    }
}
