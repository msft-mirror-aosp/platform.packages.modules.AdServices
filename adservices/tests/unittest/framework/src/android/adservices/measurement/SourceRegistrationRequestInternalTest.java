/*
 * Copyright (C) 2023 The Android Open Source Project
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
import java.util.Set;

public final class SourceRegistrationRequestInternalTest extends AdServicesUnitTestCase {
    private static final Uri REGISTRATION_URI_1 = Uri.parse("https://bar.test");
    private static final Uri REGISTRATION_URI_2 = Uri.parse("https://foo.test");
    private static final String SDK_PACKAGE_NAME = "sdk.package.name";
    private static final KeyEvent INPUT_KEY_EVENT =
            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1);
    private static final long BOOT_RELATIVE_REQUEST_TIME = 10000L;
    private static final String AD_ID_VALUE = "ad_id_value";

    private static final List<Uri> EXAMPLE_REGISTRATION_URIS =
            Arrays.asList(REGISTRATION_URI_1, REGISTRATION_URI_2);
    private static final SourceRegistrationRequest EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST =
            new SourceRegistrationRequest.Builder(EXAMPLE_REGISTRATION_URIS)
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
                SourceRegistrationRequestInternal.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void build_nullSourceRegistrationRequest_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SourceRegistrationRequestInternal.Builder(
                                null, mPackageName, SDK_PACKAGE_NAME, BOOT_RELATIVE_REQUEST_TIME));
    }

    @Test
    public void build_nullAppPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                /* appPackageName= */ null,
                                SDK_PACKAGE_NAME,
                                BOOT_RELATIVE_REQUEST_TIME));
    }

    @Test
    public void build_nullSdkPackageName_throwsException() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                mPackageName,
                                /* sdkPackageName= */ null,
                                BOOT_RELATIVE_REQUEST_TIME));
    }

    @Test
    public void testDescribeContents() {
        expect.that(createExampleRegistrationRequest().describeContents()).isEqualTo(0);
    }

    @Test
    public void testHashCode_equals() {
        EqualsTester et = new EqualsTester(expect);
        SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        SourceRegistrationRequestInternal request2 = createExampleRegistrationRequest();
        et.expectObjectsAreEqual(request1, request2);

        Set<SourceRegistrationRequestInternal> requestSet1 = Set.of(request1);
        Set<SourceRegistrationRequestInternal> requestSet2 = Set.of(request2);
        expect.that(requestSet1).isEqualTo(requestSet2);
    }

    @Test
    public void testAppPackageNameMismatch_notEquals() {
        EqualsTester et = new EqualsTester(expect);

        SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                "com.foo",
                                SDK_PACKAGE_NAME,
                                BOOT_RELATIVE_REQUEST_TIME)
                        .setAdIdValue(AD_ID_VALUE)
                        .build();

        et.expectObjectsAreNotEqual(request1, request2);
    }

    @Test
    public void testSdkNameMismatch_notEquals() {
        EqualsTester et = new EqualsTester(expect);

        SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                mPackageName,
                                "com.foo",
                                BOOT_RELATIVE_REQUEST_TIME)
                        .setAdIdValue(AD_ID_VALUE)
                        .build();

        et.expectObjectsAreNotEqual(request1, request2);
    }

    @Test
    public void testRegistrationUrisMismatch_notEquals() {
        EqualsTester et = new EqualsTester(expect);

        SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        SourceRegistrationRequest diffSourceRegistrationRequest =
                new SourceRegistrationRequest.Builder(EXAMPLE_REGISTRATION_URIS)
                        .setInputEvent(null) // this is the difference
                        .build();
        SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                diffSourceRegistrationRequest,
                                mPackageName,
                                SDK_PACKAGE_NAME,
                                BOOT_RELATIVE_REQUEST_TIME)
                        .setAdIdValue(AD_ID_VALUE)
                        .build();

        et.expectObjectsAreNotEqual(request1, request2);
    }

    @Test
    public void testRequestTimeMismatch_notEquals() {
        EqualsTester et = new EqualsTester(expect);

        SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                mPackageName,
                                SDK_PACKAGE_NAME,
                                43534534653L)
                        .setAdIdValue(AD_ID_VALUE)
                        .build();

        et.expectObjectsAreNotEqual(request1, request2);
    }

    @Test
    public void testAdIdMismatch_notEquals() {
        EqualsTester et = new EqualsTester(expect);

        SourceRegistrationRequestInternal request1 = createExampleRegistrationRequest();
        SourceRegistrationRequestInternal request2 =
                new SourceRegistrationRequestInternal.Builder(
                                EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                                mPackageName,
                                SDK_PACKAGE_NAME,
                                BOOT_RELATIVE_REQUEST_TIME)
                        .setAdIdValue("different_ad_id")
                        .build();

        et.expectObjectsAreNotEqual(request1, request2);
    }

    private SourceRegistrationRequestInternal createExampleRegistrationRequest() {
        return new SourceRegistrationRequestInternal.Builder(
                        EXAMPLE_EXTERNAL_SOURCE_REG_REQUEST,
                        mPackageName,
                        SDK_PACKAGE_NAME,
                        BOOT_RELATIVE_REQUEST_TIME)
                .setAdIdValue(AD_ID_VALUE)
                .build();
    }

    private void verifyExampleRegistrationInternal(SourceRegistrationRequestInternal request) {
        verifyExampleRegistration(request.getSourceRegistrationRequest());
        expect.that(request.getAppPackageName()).isEqualTo(mPackageName);
        expect.that(request.getSdkPackageName()).isEqualTo(SDK_PACKAGE_NAME);
        expect.that(request.getAdIdValue()).isEqualTo(AD_ID_VALUE);
    }

    private void verifyExampleRegistration(SourceRegistrationRequest request) {
        expect.that(request.getRegistrationUris()).isEqualTo(EXAMPLE_REGISTRATION_URIS);
        expect.that(((KeyEvent) request.getInputEvent()).getAction())
                .isEqualTo(INPUT_KEY_EVENT.getAction());
        expect.that(((KeyEvent) request.getInputEvent()).getKeyCode())
                .isEqualTo(INPUT_KEY_EVENT.getKeyCode());
    }
}
