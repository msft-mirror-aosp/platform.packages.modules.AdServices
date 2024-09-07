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

/** Unit tests for {@link WebSourceParams}. */
public final class WebSourceParamsTest extends AdServicesUnitTestCase {
    private static final Uri REGISTRATION_URI = Uri.parse("https://foo.test");
    private static final WebSourceParams WEB_SOURCE_PARAMS =
            new WebSourceParams.Builder(REGISTRATION_URI).setDebugKeyAllowed(true).build();

    private WebSourceParams createExampleRegistration() {
        return new WebSourceParams.Builder(REGISTRATION_URI).setDebugKeyAllowed(true).build();
    }

    private void verifyExampleRegistration(WebSourceParams request) {
        expect.that(request.getRegistrationUri()).isEqualTo(REGISTRATION_URI);
        expect.that(request.isDebugKeyAllowed()).isTrue();
    }

    @Test
    public void testCreationAttribution() {
        verifyExampleRegistration(WEB_SOURCE_PARAMS);
    }

    @Test
    public void testRegistrationUriWithoutScheme_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebSourceParams.Builder(Uri.parse("foo.test")));
    }

    @Test
    public void testRegistrationUriWithNonHttpsScheme_throwsException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WebSourceParams.Builder(Uri.parse("http://foo.test")));
    }

    @Test
    public void testParcelingAttribution() {
        Parcel p = Parcel.obtain();
        WEB_SOURCE_PARAMS.writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleRegistration(WebSourceParams.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testDescribeContents() {
        expect.that(createExampleRegistration().describeContents()).isEqualTo(0);
    }
}
