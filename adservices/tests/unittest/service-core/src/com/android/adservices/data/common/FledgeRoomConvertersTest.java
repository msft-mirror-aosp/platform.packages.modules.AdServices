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

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;

import com.google.common.collect.ImmutableSet;

import org.json.JSONArray;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

public final class FledgeRoomConvertersTest extends AdServicesUnitTestCase {
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);

    @Test
    public void testSerializeDeserializeInstant() {
        Instant instant = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);

        Long fromInstant = FledgeRoomConverters.serializeInstant(instant);
        Instant fromLong = FledgeRoomConverters.deserializeInstant(fromInstant);

        assertThat(fromLong).isEqualTo(instant);
    }

    @Test
    public void testConvertersNullInputs() {
        expect.that(FledgeRoomConverters.serializeInstant(null)).isNull();
        expect.that(FledgeRoomConverters.deserializeInstant(null)).isNull();

        expect.that(FledgeRoomConverters.serializeUri(null)).isNull();
        expect.that(FledgeRoomConverters.deserializeUri(null)).isNull();

        expect.that(FledgeRoomConverters.serializeAdTechIdentifier(null)).isNull();
        expect.that(FledgeRoomConverters.deserializeAdTechIdentifier(null)).isNull();

        expect.that(FledgeRoomConverters.serializeAdSelectionSignals(null)).isNull();
        expect.that(FledgeRoomConverters.deserializeAdSelectionSignals(null)).isNull();
    }

    @Test
    public void testSerializeDeserializeUri() {
        Uri uri = Uri.parse("http://www.domain.com/adverts/123");

        String fromUri = FledgeRoomConverters.serializeUri(uri);
        Uri fromString = FledgeRoomConverters.deserializeUri(fromUri);

        assertThat(fromString).isEqualTo(uri);
    }

    @Test
    public void testSerializeDeserializeAdTechIdentifier() {
        AdTechIdentifier adTechIdentifier = AdTechIdentifier.fromString("test.identifier");

        String serializedIdentifier =
                FledgeRoomConverters.serializeAdTechIdentifier(adTechIdentifier);
        AdTechIdentifier deserializedIdentifier =
                FledgeRoomConverters.deserializeAdTechIdentifier(serializedIdentifier);

        assertThat(deserializedIdentifier).isEqualTo(adTechIdentifier);
    }

    @Test
    public void testSerializeDeserializeAdSelectionSignals() {
        AdSelectionSignals adSelectionSignals = AdSelectionSignals.fromString("{\"test\":1}");

        String serializedIdentifier =
                FledgeRoomConverters.serializeAdSelectionSignals(adSelectionSignals);
        AdSelectionSignals deserializedIdentifier =
                FledgeRoomConverters.deserializeAdSelectionSignals(serializedIdentifier);

        assertThat(deserializedIdentifier).isEqualTo(adSelectionSignals);
    }

    @Test
    public void testSerializeNullStringSet() {
        assertThat(FledgeRoomConverters.serializeStringSet(null)).isNull();
    }

    @Test
    public void testDeserializeNullStringSet() {
        assertThat(FledgeRoomConverters.deserializeStringSet(null)).isNull();
    }

    @Test
    public void testDeserializeMangledStringSet() {
        assertThat(FledgeRoomConverters.deserializeStringSet("This is not a JSON string")).isNull();
    }

    @Test
    public void testSerializeDeserializeStringSet() {
        ImmutableSet<String> originalSet = ImmutableSet.of("one", "two", "three");

        String serializedStringSet = FledgeRoomConverters.serializeStringSet(originalSet);
        Set<String> deserializeStringSet =
                FledgeRoomConverters.deserializeStringSet(serializedStringSet);

        assertThat(deserializeStringSet).containsExactlyElementsIn(originalSet);
    }

    @Test
    public void testSerializeNullIntegerSet() {
        assertThat(FledgeRoomConverters.serializeIntegerSet(null)).isNull();
    }

    @Test
    public void testDeserializeNullIntegerSet() {
        assertThat(FledgeRoomConverters.deserializeIntegerSet(null)).isNull();
    }

    @Test
    public void testDeserializeMangledIntegerSet() {
        assertThat(FledgeRoomConverters.deserializeIntegerSet("This is not a JSON string"))
                .isNull();
    }

    @Test
    public void testSerializeDeserializeIntegerSet() {
        String serializedIntegerSet =
                FledgeRoomConverters.serializeIntegerSet(AdDataFixture.getAdCounterKeys());
        Set<Integer> deserializeIntegerSet =
                FledgeRoomConverters.deserializeIntegerSet(serializedIntegerSet);

        assertThat(deserializeIntegerSet)
                .containsExactlyElementsIn(AdDataFixture.getAdCounterKeys());
    }

    @Test
    public void testSerializeDeserializeIntegerSet_invalidIntegerSkipped() {
        String serializedIntegerSet =
                new JSONArray(AdDataFixture.getAdCounterKeys()).put("invalid").toString();
        Set<Integer> deserializeIntegerSet =
                FledgeRoomConverters.deserializeIntegerSet(serializedIntegerSet);

        assertThat(deserializeIntegerSet)
                .containsExactlyElementsIn(AdDataFixture.getAdCounterKeys());
    }

    @Test
    public void testUriListToString_nullInput_returnsNull() {
        expect.that(FledgeRoomConverters.uriListToString(null)).isNull();
    }

    @Test
    public void testUriListToString_emptyList_returnsEmptyString() {
        expect.that(FledgeRoomConverters.uriListToString(List.of())).isEqualTo("");
    }

    @Test
    public void testUriListToString_singleUri_returnsString() {
        Uri uri = Uri.parse("https://example.com/test");
        List<Uri> uriList = List.of(uri);
        String expected = "https://example.com/test";
        expect.that(FledgeRoomConverters.uriListToString(uriList)).isEqualTo(expected);
    }

    @Test
    public void testUriListToString_multipleUris_returnsCommaSeparatedString() {
        Uri uri1 = Uri.parse("https://example.com/test1");
        Uri uri2 = Uri.parse("https://example.com/test2");
        Uri uri3 = Uri.parse("https://example.com/test3");
        List<Uri> uriList = List.of(uri1, uri2, uri3);

        String expected =
                "https://example.com/test1,https://example.com/test2,https://example.com/test3";
        expect.that(FledgeRoomConverters.uriListToString(uriList)).isEqualTo(expected);
    }

    @Test
    public void testUriListToString_uriWithComma() {
        // Test to ensure that a comma in the URI is handled.
        Uri uri1 = Uri.parse("https://example.com/test1,abc");
        List<Uri> uriList = List.of(uri1);

        String expected = "https://example.com/test1,abc";
        expect.that(FledgeRoomConverters.uriListToString(uriList)).isEqualTo(expected);
    }

    @Test
    public void testStringToUriList_nullInput_returnsNull() {
        expect.that(FledgeRoomConverters.stringToUriList(null)).isNull();
    }

    @Test
    public void testStringToUriList_emptyString_returnsEmptyList() {
        expect.that(FledgeRoomConverters.stringToUriList("")).isEmpty();
    }

    @Test
    public void testStringToUriList_emptyStringWithCommas_returnsEmptyList() {
        expect.that(FledgeRoomConverters.stringToUriList(",,,")).isEmpty();
    }

    @Test
    public void testStringToUriList_singleUriString_returnsList() {
        String uriString = "https://example.com/test";
        List<Uri> expected = List.of(Uri.parse(uriString));
        expect.that(FledgeRoomConverters.stringToUriList(uriString))
                .containsExactlyElementsIn(expected)
                .inOrder();
    }

    @Test
    public void testStringToUriList_multipleUriStrings_returnsList() {
        String uriString =
                "https://example.com/test1,https://example.com/test2,https://example.com/test3";
        List<Uri> expected =
                List.of(
                        Uri.parse("https://example.com/test1"),
                        Uri.parse("https://example.com/test2"),
                        Uri.parse("https://example.com/test3"));
        expect.that(FledgeRoomConverters.stringToUriList(uriString))
                .containsExactlyElementsIn(expected)
                .inOrder();
    }

    @Test
    public void testUriListToStringAndStringToUriList_roundTrip() {
        Uri uri1 = Uri.parse("https://example.com/test1");
        Uri uri2 = Uri.parse("https://example.com/test2");
        Uri uri3 = Uri.parse("https://example.com/test3");
        List<Uri> originalUris = List.of(uri1, uri2, uri3);

        String serialized = FledgeRoomConverters.uriListToString(originalUris);
        List<Uri> deserialized = FledgeRoomConverters.stringToUriList(serialized);

        expect.that(deserialized).containsExactlyElementsIn(originalUris).inOrder(); // Changed
    }

    @Test
    public void testUriListToStringAndStringToUriList_emptyRoundTrip() {
        List<Uri> originalUris = List.of();
        String serialized = FledgeRoomConverters.uriListToString(originalUris);
        List<Uri> deserialized = FledgeRoomConverters.stringToUriList(serialized);
        expect.that(deserialized).isEmpty();
    }
}
