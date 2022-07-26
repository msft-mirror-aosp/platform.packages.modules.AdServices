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

package android.adservices.common;

import android.net.Uri;

import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.ValidatorUtil;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class CommonFixture {
    public static final Flags FLAGS_FOR_TEST = FlagsFactory.getFlagsForTest();

    public static final Instant FIXED_NOW = Instant.now();
    public static final Instant FIXED_NOW_TRUNCATED_TO_MILLI =
            FIXED_NOW.truncatedTo(ChronoUnit.MILLIS);
    public static final Clock FIXED_CLOCK_TRUNCATED_TO_MILLI =
            Clock.fixed(FIXED_NOW.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC);

    public static final AdTechIdentifier VALID_BUYER =
            AdTechIdentifier.fromString("validbuyer.example.com");

    public static Uri getUri(String authority, String path) {
        return Uri.parse(ValidatorUtil.HTTPS_SCHEME + "://" + authority + path);
    }
}
