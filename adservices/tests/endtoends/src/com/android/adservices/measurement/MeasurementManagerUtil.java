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

package com.android.adservices.measurement;

import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.net.Uri;

import java.util.Collections;
import java.util.List;

public final class MeasurementManagerUtil {

    // class should not be instantiated, static methods only
    private MeasurementManagerUtil() {}

    /** returns a WebSourceRegistrationRequest */
    public static WebSourceRegistrationRequest buildDefaultWebSourceRegistrationRequest() {
        WebSourceParams webSourceParams =
                new WebSourceParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();

        return new WebSourceRegistrationRequest.Builder(
                        Collections.singletonList(webSourceParams),
                        Uri.parse("https://example.com"))
                .setInputEvent(null)
                .setAppDestination(Uri.parse("android-app://com.example"))
                .setWebDestination(Uri.parse("https://example.com"))
                .setVerifiedDestination(null)
                .build();
    }

    /** returns a WebTriggerRegistrationRequest */
    public static WebTriggerRegistrationRequest buildDefaultWebTriggerRegistrationRequest() {
        WebTriggerParams webTriggerParams =
                new WebTriggerParams.Builder(Uri.parse("https://example.com"))
                        .setDebugKeyAllowed(false)
                        .build();
        return new WebTriggerRegistrationRequest.Builder(
                        Collections.singletonList(webTriggerParams),
                        Uri.parse("https://example.com"))
                .build();
    }

    /** returns a SourceRegistrationRequest */
    public static SourceRegistrationRequest buildDefaultAppSourcesRegistrationRequest() {
        return new SourceRegistrationRequest.Builder(
                        List.of(
                                Uri.parse("https://example1.com"),
                                Uri.parse("https://example2.com")))
                .setInputEvent(null)
                .build();
    }
}
