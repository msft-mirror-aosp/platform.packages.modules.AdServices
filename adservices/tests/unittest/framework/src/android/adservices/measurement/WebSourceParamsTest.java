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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;
import android.os.Parcel;

import org.junit.Test;

/** Unit tests for {@link WebSourceParams}. */
public class WebSourceParamsTest {
    private static final Uri REGISTRATION_URI = Uri.parse("http://foo.com");

    private WebSourceParams createExampleRegistration() {
        return new WebSourceParams.Builder()
                .setRegistrationUri(REGISTRATION_URI)
                .setAllowDebugKey(true)
                .build();
    }

    private void verifyExampleRegistration(WebSourceParams request) {
        assertEquals(REGISTRATION_URI, request.getRegistrationUri());
        assertTrue(request.isAllowDebugKey());
    }

    @Test
    public void testDefaults() throws Exception {
        WebSourceParams sourceRegistration =
                new WebSourceParams.Builder().setRegistrationUri(REGISTRATION_URI).build();
        assertEquals(REGISTRATION_URI, sourceRegistration.getRegistrationUri());
        assertFalse(sourceRegistration.isAllowDebugKey());
    }

    @Test
    public void testCreationAttribution() {
        verifyExampleRegistration(createExampleRegistration());
    }

    @Test
    public void testParcelingAttribution() {
        Parcel p = Parcel.obtain();
        createExampleRegistration().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleRegistration(WebSourceParams.CREATOR.createFromParcel(p));
        p.recycle();
    }

    @Test
    public void testDescribeContents() {
        assertEquals(0, createExampleRegistration().describeContents());
    }
}
