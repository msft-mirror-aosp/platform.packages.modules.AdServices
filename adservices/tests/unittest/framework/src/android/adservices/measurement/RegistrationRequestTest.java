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

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import org.junit.Test;


/**
 * Unit tests for {@link android.adservices.measurement.RegistrationRequest}
 */
@SmallTest
public final class RegistrationRequestTest {
    private static final String TAG = "RegistrationRequestTest";

    private static final Context sContext = InstrumentationRegistry.getTargetContext();

    private RegistrationRequest createExampleAttribution() {
        return new RegistrationRequest.Builder()
            .setRegistrationType(RegistrationRequest.REGISTER_ATTRIBUTION_SOURCE)
            .setTopOriginUri(Uri.parse("http://foo.com"))
            .setReferrerUri(Uri.parse("http://bar.com"))
            .setRegistrationUri(Uri.parse("http://baz.com"))
            .setAttributionSource(sContext.getAttributionSource())
            .build();
    }

    void verifyExampleAttribution(RegistrationRequest request) {
        assertEquals("http://foo.com", request.getTopOriginUri().toString());
        assertEquals("http://bar.com", request.getReferrerUri().toString());
        assertEquals("http://baz.com", request.getRegistrationUri().toString());
        assertEquals(RegistrationRequest.REGISTER_ATTRIBUTION_SOURCE,
                request.getRegistrationType());
        assertNull(request.getInputEvent());
        assertNotNull(request.getAttributionSource());
    }

    @Test
    public void testNoRegistrationType() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new RegistrationRequest.Builder()
                        .setAttributionSource(sContext.getAttributionSource())
                        .build();
                });
    }

    @Test
    public void testNoAttributionSource() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new RegistrationRequest.Builder()
                        .setRegistrationType(
                                RegistrationRequest.TRIGGER_ATTRIBUTION)
                        .build();
                });
    }

    @Test
    public void testDefaults() throws Exception {
        RegistrationRequest request = new RegistrationRequest.Builder()
                .setAttributionSource(sContext.getAttributionSource())
                .setRegistrationType(
                        RegistrationRequest.TRIGGER_ATTRIBUTION)
                .build();
        assertEquals("", request.getTopOriginUri().toString());
        assertEquals("", request.getReferrerUri().toString());
        assertEquals("", request.getRegistrationUri().toString());
        assertEquals(RegistrationRequest.TRIGGER_ATTRIBUTION,
                request.getRegistrationType());
        assertNull(request.getInputEvent());
        assertNotNull(request.getAttributionSource());
    }

    @Test
    public void testCreationAttribution() throws Exception {
        verifyExampleAttribution(createExampleAttribution());
    }

    @Test
    public void testParcelingAttribution() throws Exception {
        Parcel p = Parcel.obtain();
        createExampleAttribution().writeToParcel(p, 0);
        p.setDataPosition(0);
        verifyExampleAttribution(
                RegistrationRequest.CREATOR.createFromParcel(p));
        p.recycle();
    }
}
