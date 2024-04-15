/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.measurement.ondevicepersonalization;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.service.measurement.registration.AsyncRegistration;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OdpDelegationWrapperImplTest {
    private static final String ODP_PACKAGE_NAME = "com.adtech1";
    private static final String ODP_CLASS_NAME = "com.adtech1.AdTechIsolatedService";
    private static final String ODP_CERT_DIGEST = "AABBCCDD";
    private static final String ODP_EVENT_DATA = "123";
    private static final String ODP_INVALID_SERVICE_1 =
            "com.adtech1.com.adtech1.AdTechIsolatedService";
    private static final String ODP_INVALID_SERVICE_2 =
            "com.adtech1.com.adtech1.AdTechIsolatedService/";

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Test
    public void creation_nullParameters_fail() {
        assertThrows(NullPointerException.class, () -> new OdpDelegationWrapperImpl(null));
    }

    @Test
    public void registerOdpTrigger_nullParameters_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true);
        assertThrows(
                NullPointerException.class,
                () -> odpDelegationWrapperImpl.registerOdpTrigger(null, null));
    }

    @Test
    public void registerOdpTrigger_validParameters_success() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true);
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId("1")
                        .setTopOrigin(Uri.parse("android-app://com.somePackageName"))
                        .setRegistrant(Uri.parse("android-app://com.somePackageName"))
                        .build();
        Map<String, List<String>> header = new HashMap<>();
        header.put(
                "Odp-Register-Trigger",
                List.of(
                        "{"
                                + "\"service\":\""
                                + ODP_PACKAGE_NAME
                                + "/"
                                + ODP_CLASS_NAME
                                + "\","
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        boolean result = odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
        assertTrue(result);
    }

    @Test
    public void registerOdpTrigger_missingHeader_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true);
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId("1")
                        .setTopOrigin(Uri.parse("android-app://com.somePackageName"))
                        .setRegistrant(Uri.parse("android-app://com.somePackageName"))
                        .build();
        Map<String, List<String>> header = new HashMap<>();
        header.put(
                "Not-Odp-Delegated-Trigger",
                List.of(
                        "{"
                                + "\"service\":\""
                                + ODP_PACKAGE_NAME
                                + "/"
                                + ODP_CLASS_NAME
                                + "\","
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        boolean result = odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
        assertFalse(result);
    }

    @Test
    public void registerOdpTrigger_invalidHeaderFormat_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true);
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId("1")
                        .setTopOrigin(Uri.parse("android-app://com.somePackageName"))
                        .setRegistrant(Uri.parse("android-app://com.somePackageName"))
                        .build();
        Map<String, List<String>> header = new HashMap<>();
        header.put(
                "Odp-Register-Trigger",
                List.of(
                        "{"
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}",
                        "{"
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        boolean result = odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
        assertFalse(result);
    }

    @Test
    public void registerOdpTrigger_missingRequiredField_fail() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true);
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId("1")
                        .setTopOrigin(Uri.parse("android-app://com.somePackageName"))
                        .setRegistrant(Uri.parse("android-app://com.somePackageName"))
                        .build();
        Map<String, List<String>> header = new HashMap<>();
        header.put(
                "Odp-Register-Trigger",
                List.of(
                        "{"
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        boolean result = odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
        assertFalse(result);
    }

    @Test
    public void registerOdpTrigger_invalidServiceName_NoForwardSlash_success() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true);
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId("1")
                        .setTopOrigin(Uri.parse("android-app://com.somePackageName"))
                        .setRegistrant(Uri.parse("android-app://com.somePackageName"))
                        .build();
        Map<String, List<String>> header = new HashMap<>();
        header.put(
                "Odp-Register-Trigger",
                List.of(
                        "{"
                                + "\"service\":\"com.adtech1\""
                                + ODP_INVALID_SERVICE_1
                                + "\","
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        boolean result = odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
        assertFalse(result);
    }

    @Test
    public void registerOdpTrigger_invalidServiceName_forwardSlashEndingCharacter_success() {
        OdpDelegationWrapperImpl odpDelegationWrapperImpl =
                new OdpDelegationWrapperImpl(null, true);
        AsyncRegistration asyncRegistration =
                new AsyncRegistration.Builder()
                        .setRegistrationId("1")
                        .setTopOrigin(Uri.parse("android-app://com.somePackageName"))
                        .setRegistrant(Uri.parse("android-app://com.somePackageName"))
                        .build();
        Map<String, List<String>> header = new HashMap<>();
        header.put(
                "Odp-Register-Trigger",
                List.of(
                        "{"
                                + "\"service\":\"com.adtech1\""
                                + ODP_INVALID_SERVICE_2
                                + "\","
                                + "\"certDigest\":\""
                                + ODP_CERT_DIGEST
                                + "\","
                                + "\"data\":\""
                                + ODP_EVENT_DATA
                                + "\""
                                + "}"));
        boolean result = odpDelegationWrapperImpl.registerOdpTrigger(asyncRegistration, header);
        assertFalse(result);
    }
}
