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

package com.android.adservices.data.common;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.room.TypeConverter;

import java.time.Instant;
import java.util.Optional;

/**
 * Room DB type converters for FLEDGE.
 *
 * <p>Register custom type converters here.
 */
public class FledgeRoomConverters {
    private FledgeRoomConverters() {}

    /** Serialize {@link Instant} to Long. */
    @TypeConverter
    @Nullable
    public static Long serializeInstant(@Nullable Instant instant) {
        return Optional.ofNullable(instant).map(Instant::toEpochMilli).orElse(null);
    }

    /** Deserialize {@link Instant} from long. */
    @TypeConverter
    @Nullable
    public static Instant deserializeInstant(@Nullable Long epochMilli) {
        return Optional.ofNullable(epochMilli).map(Instant::ofEpochMilli).orElse(null);
    }

    /** Deserialize {@link Uri} from String. */
    @TypeConverter
    @Nullable
    public static Uri deserializeUrl(@Nullable String uri) {
        return Optional.ofNullable(uri).map(Uri::parse).orElse(null);
    }

    /** Serialize {@link Uri} to String. */
    @TypeConverter
    @Nullable
    public static String serializeUrl(@Nullable Uri uri) {
        return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
    }

    /** Serialize an {@link AdTechIdentifier} to String. */
    @TypeConverter
    @Nullable
    public static String serializeAdTechIdentifier(@Nullable AdTechIdentifier appIdentifier) {
        return Optional.ofNullable(appIdentifier).map(AdTechIdentifier::toString).orElse(null);
    }

    /** Deserialize an {@link AdTechIdentifier} from a String. */
    @TypeConverter
    @Nullable
    public static AdTechIdentifier deserializeAdTechIdentifier(@Nullable String packageName) {
        return Optional.ofNullable(packageName).map(AdTechIdentifier::fromString).orElse(null);
    }

    /** Serialize an {@link AdSelectionSignals} to String. */
    @TypeConverter
    @Nullable
    public static String serializeAdSelectionSignals(@Nullable AdSelectionSignals appIdentifier) {
        return Optional.ofNullable(appIdentifier).map(AdSelectionSignals::toString).orElse(null);
    }

    /** Deserialize an {@link AdSelectionSignals} from a String. */
    @TypeConverter
    @Nullable
    public static AdSelectionSignals deserializeAdSelectionSignals(@Nullable String packageName) {
        return Optional.ofNullable(packageName).map(AdSelectionSignals::fromString).orElse(null);
    }
}
