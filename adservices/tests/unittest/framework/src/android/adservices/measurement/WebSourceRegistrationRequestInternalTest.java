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
import android.view.KeyEvent;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.EqualsTester;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public final class WebSourceRegistrationRequestInternalTest extends AdServicesUnitTestCase {
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://foo1.com");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo2.com");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final Uri TOP_ORIGIN_URI = Uri.parse("https://top-origin.com");
    private static final Uri OS_DESTINATION_URI = Uri.parse("android-app://com.os-destination");
    private static final Uri WEB_DESTINATION_URI = Uri.parse("https://web-destination.com");
    private static final Uri VERIFIED_DESTINATION = Uri.parse("https://verified-dest.com");
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);
    private static final long REQUEST_TIME = 10000L;

    private static final WebSourceParams SOURCE_REGISTRATION_1 =
            new WebSourceParams.Builder(REGISTRATION_URI_1).setDebugKeyAllowed(true).build();

    private static final WebSourceParams SOURCE_REGISTRATION_2 =
            new WebSourceParams.Builder(REGISTRATION_URI_2).setDebugKeyAllowed(false).build();

    private static final List<WebSourceParams> SOURCE_REGISTRATIONS =
            Arrays.asList(SOURCE_REGISTRATION_1, SOURCE_REGISTRATION_2);

    private static final WebSourceRegistrationRequest EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST =
            new WebSourceRegistrationRequest.Builder(SOURCE_REGISTRATIONS, TOP_ORIGIN_URI)
                    .setAppDestination(OS_DESTINATION_URI)
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
    public void build_nullSourceRegistrationRequest_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequestInternal.Builder(
                                null, mPackageName, SDK_PACKAGE_NAME, REQUEST_TIME));
    }

    @Test
    public void build_nullAppPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                /* appPackageName= */ null,
                                SDK_PACKAGE_NAME,
                                REQUEST_TIME));
    }

    @Test
    public void build_nullSdkPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new WebSourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                mPackageName,
                                /* sdkPackageName= */ null,
                                REQUEST_TIME));
    }

    @Test
    public void testDescribeContents() {
        expect.that(createExampleRegistrationRequest().describeContents()).isEqualTo(0);
    }

    @Test
    public void testEquals() {
        EqualsTester et = new EqualsTester(expect);
        WebSourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        WebSourceRegistrationRequestInternal request2 = createExampleRegistrationRequest();
        et.expectObjectsAreEqual(request1, request2);
    }

    @Test
    public void testNotEquals() {
        EqualsTester et = new EqualsTester(expect);
        WebSourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        WebSourceRegistrationRequestInternal request2 =
                new WebSourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                "com.foo",
                                SDK_PACKAGE_NAME,
                                REQUEST_TIME)
                        .build();

        et.expectObjectsAreNotEqual(request1, request2);
    }

    private WebSourceRegistrationRequestInternal createExampleRegistrationRequest() {
        return new WebSourceRegistrationRequestInternal.Builder(
                        EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                        mPackageName,
                        SDK_PACKAGE_NAME,
                        REQUEST_TIME)
                .setAdIdPermissionGranted(true)
                .build();
    }

    private void verifyExampleRegistrationInternal(WebSourceRegistrationRequestInternal request) {
        verifyExampleRegistration(request.getSourceRegistrationRequest());
        expect.that(request.getAppPackageName()).isEqualTo(mPackageName);
        expect.that(request.getSdkPackageName()).isEqualTo(SDK_PACKAGE_NAME);
        expect.that(request.isAdIdPermissionGranted()).isTrue();
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
}
