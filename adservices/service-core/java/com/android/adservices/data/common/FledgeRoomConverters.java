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

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.profiling.Tracing;

import org.json.JSONArray;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Room DB type converters for FLEDGE.
 *
 * <p>Register custom type converters here.
 */
public class FledgeRoomConverters {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private FledgeRoomConverters() {}

    /** Serialize {@link Instant} to Long. */
    @TypeConverter
    @Nullable
    public static Long serializeInstant(@Nullable Instant instant) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_INSTANT_TO_LONG);
        try {
            return Optional.ofNullable(instant).map(Instant::toEpochMilli).orElse(null);
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_INSTANT_TO_LONG, traceCookie);
        }
    }

    /** Deserialize {@link Instant} from long. */
    @TypeConverter
    @Nullable
    public static Instant deserializeInstant(@Nullable Long epochMilli) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_INSTANT_FROM_LONG);
        try {
            return Optional.ofNullable(epochMilli).map(Instant::ofEpochMilli).orElse(null);
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_INSTANT_FROM_LONG, traceCookie);
        }
    }

    /** Deserialize {@link Uri} from String. */
    @TypeConverter
    @Nullable
    public static Uri deserializeUri(@Nullable String uri) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_URI_FROM_STRING);
        try {
            return Optional.ofNullable(uri).map(Uri::parse).orElse(null);
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_URI_FROM_STRING, traceCookie);
        }
    }

    /** Serialize {@link Uri} to String. */
    @TypeConverter
    @Nullable
    public static String serializeUri(@Nullable Uri uri) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_URI_TO_STRING);
        try {
            return Optional.ofNullable(uri).map(Uri::toString).orElse(null);
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_URI_TO_STRING, traceCookie);
        }
    }

    /** Serialize an {@link AdTechIdentifier} to String. */
    @TypeConverter
    @Nullable
    public static String serializeAdTechIdentifier(@Nullable AdTechIdentifier adTechIdentifier) {
        int traceCookie =
                Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_ADTECHIDENTIFIER_TO_STRING);
        try {
            return Optional.ofNullable(adTechIdentifier)
                    .map(AdTechIdentifier::toString)
                    .orElse(null);
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_ADTECHIDENTIFIER_TO_STRING, traceCookie);
        }
    }

    /** Deserialize an {@link AdTechIdentifier} from a String. */
    @TypeConverter
    @Nullable
    public static AdTechIdentifier deserializeAdTechIdentifier(@Nullable String adTechIdentifier) {
        int traceCookie =
                Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_ADTECHIDENTIFIER_FROM_STRING);
        try {
            return Optional.ofNullable(adTechIdentifier)
                    .map(AdTechIdentifier::fromString)
                    .orElse(null);
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_ADTECHIDENTIFIER_FROM_STRING, traceCookie);
        }
    }

    /** Serialize an {@link AdSelectionSignals} to String. */
    @TypeConverter
    @Nullable
    public static String serializeAdSelectionSignals(@Nullable AdSelectionSignals signals) {
        int traceCookie =
                Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_ADSELECTIONSIGNALS_TO_STRING);
        try {
            return Optional.ofNullable(signals).map(AdSelectionSignals::toString).orElse(null);
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_ADSELECTIONSIGNALS_TO_STRING, traceCookie);
        }
    }

    /** Deserialize an {@link AdSelectionSignals} from a String. */
    @TypeConverter
    @Nullable
    public static AdSelectionSignals deserializeAdSelectionSignals(@Nullable String signals) {
        int traceCookie =
                Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_ADSELECTIONSIGNALS_FROM_STRING);
        try {
            return Optional.ofNullable(signals).map(AdSelectionSignals::fromString).orElse(null);
        } finally {
            Tracing.endAsyncSection(
                    Tracing.ROOM_CONVERT_ADSELECTIONSIGNALS_FROM_STRING, traceCookie);
        }
    }

    /** Serialize a {@link Set} of Strings into a JSON array as a String. */
    @TypeConverter
    @Nullable
    public static String serializeStringSet(@Nullable Set<String> stringSet) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_STRING_SET_TO_JSON);
        try {
            if (stringSet == null) {
                return null;
            }

            JSONArray jsonSet = new JSONArray(stringSet);
            return jsonSet.toString();
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_STRING_SET_TO_JSON, traceCookie);
        }
    }

    /** Deserialize a {@link Set} of Strings from a JSON array. */
    @TypeConverter
    @Nullable
    public static Set<String> deserializeStringSet(@Nullable String serializedSet) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_STRING_SET_FROM_JSON);
        try {
            if (serializedSet == null) {
                return null;
            }

            Set<String> outputSet = new HashSet<>();
            JSONArray jsonSet;
            try {
                jsonSet = new JSONArray(serializedSet);
            } catch (Exception exception) {
                sLogger.d(
                        exception,
                        "Error deserializing set of strings from DB; returning null set");
                return null;
            }

            for (int arrayIndex = 0; arrayIndex < jsonSet.length(); arrayIndex++) {
                String currentString;
                try {
                    currentString = jsonSet.getString(arrayIndex);
                } catch (Exception exception) {
                    // getString() coerces elements into Strings, so this should only happen if we
                    // get
                    // out of bounds
                    sLogger.d(
                            exception,
                            "Error deserializing set string #%d from DB; skipping any other"
                                + " elements",
                            arrayIndex);
                    break;
                }
                outputSet.add(currentString);
            }

            return outputSet;
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_STRING_SET_FROM_JSON, traceCookie);
        }
    }

    /** Serialize a {@link Set} of Integers into a JSON array as a String. */
    @TypeConverter
    @Nullable
    public static String serializeIntegerSet(@Nullable Set<Integer> integerSet) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_INTEGER_SET_TO_JSON);
        try {
            if (integerSet == null) {
                return null;
            }

            JSONArray jsonSet = new JSONArray(integerSet);
            return jsonSet.toString();
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_INTEGER_SET_TO_JSON, traceCookie);
        }
    }

    /** Deserialize a {@link Set} of Strings from a JSON array. */
    @TypeConverter
    @Nullable
    public static Set<Integer> deserializeIntegerSet(@Nullable String serializedSet) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.ROOM_CONVERT_INTEGER_SET_FROM_JSON);
        try {
            if (serializedSet == null) {
                return null;
            }

            Set<Integer> outputSet = new HashSet<>();
            JSONArray jsonSet;
            try {
                jsonSet = new JSONArray(serializedSet);
            } catch (Exception exception) {
                sLogger.d(exception, "Error deserializing set of ints from DB; returning null set");
                return null;
            }

            for (int arrayIndex = 0; arrayIndex < jsonSet.length(); arrayIndex++) {
                int currentInt;
                try {
                    currentInt = jsonSet.getInt(arrayIndex);
                } catch (Exception exception) {
                    sLogger.d(
                            exception,
                            "Error deserializing set int #%d from DB; skipping element from %s",
                            arrayIndex,
                            serializedSet);
                    continue;
                }
                outputSet.add(currentInt);
            }

            return outputSet;
        } finally {
            Tracing.endAsyncSection(Tracing.ROOM_CONVERT_INTEGER_SET_FROM_JSON, traceCookie);
        }
    }
}
