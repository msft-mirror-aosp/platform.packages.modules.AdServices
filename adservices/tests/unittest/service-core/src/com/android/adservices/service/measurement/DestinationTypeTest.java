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

package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;

import android.net.Uri;

import org.junit.Test;

public class DestinationTypeTest {

    private static final Uri APP_URI = Uri.parse("android-app://com.example.app");
    private static final Uri WEB_URI = Uri.parse("https://app.example.com");
    private static final Uri UNKNOWN_URI = Uri.parse("some_random_uri");

    @Test
    public void getDestinationType_success() {
        assertEquals(DestinationType.APP, DestinationType.getDestinationType(APP_URI));
        assertEquals(DestinationType.WEB, DestinationType.getDestinationType(WEB_URI));
        assertEquals(DestinationType.WEB, DestinationType.getDestinationType(UNKNOWN_URI));
    }
}
