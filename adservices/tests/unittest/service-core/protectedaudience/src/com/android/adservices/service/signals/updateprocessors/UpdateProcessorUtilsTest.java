/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals.updateprocessors;

import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.decodeKey;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.decodeValue;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.touchKey;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.validateAndCastToJSONArray;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.validateAndCastToJSONObject;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.validateAndCastToString;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import com.google.common.collect.Sets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Set;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class UpdateProcessorUtilsTest extends AdServicesUnitTestCase {

    private static final String COMMAND = "put";

    @Test
    public void testValidateAndCastToJSONArraySuccess() {
        JSONArray expected = new JSONArray();
        expected.put("arbitrary string");
        JSONArray actual = validateAndCastToJSONArray(COMMAND, expected);
        assertWithMessage("Validated JSON array").that(actual).isEqualTo(expected);
    }

    @Test
    public void testValidateAndCastToJSONArrayFailure() {
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> validateAndCastToJSONArray(COMMAND, new Object()));
    }

    @Test
    public void testValidateAndCastToJSONObjectSuccess() throws JSONException {
        JSONObject expected = new JSONObject();
        expected.put("arbitrary_string", "other_string");
        JSONObject actual = validateAndCastToJSONObject(COMMAND, expected);
        assertWithMessage("Validated JSON object").that(actual).isEqualTo(expected);
    }

    @Test
    public void testValidateAndCastToJSONObjectFailure() {
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> validateAndCastToJSONObject(COMMAND, new Object()));
    }

    @Test
    public void testValidateAndCastToStringSuccess() {
        String expected = "arbitrary string";
        String actual = validateAndCastToString(COMMAND, expected);
        assertWithMessage("Validated string").that(actual).isEqualTo(expected);
    }

    @Test
    public void testValidateAndCastToStringFailure() {
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> validateAndCastToString(COMMAND, new Object()));
    }

    @Test
    public void testTouchKeySuccess() {
        Set<ByteBuffer> set = Sets.newHashSet();
        touchKey(BB_KEY_1, set);
        assertWithMessage("Touched key").that(set).containsExactly(BB_KEY_1);
    }

    @Test
    public void testTouchKeyFailure() {
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> touchKey(BB_KEY_1, Sets.newHashSet(BB_KEY_1)));
    }

    @Test
    public void testDecodeKey() {
        String key = "AQIDBA==";
        ByteBuffer decoded = decodeKey(COMMAND, key);
        ByteBuffer expected = ByteBuffer.wrap(new byte[] {(byte) 1, (byte) 2, (byte) 3, (byte) 4});
        assertWithMessage("Decoded key").that(decoded).isEqualTo(expected);
    }

    @Test
    public void testDecodeKeyInvalidBase64() {
        String key = "*";
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> decodeKey(COMMAND, key));
    }

    @Test
    public void testDecodeKeyTooBig() {
        String key = "AAAAAAAAAAAAAAAAAAAAAAAA";
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> decodeKey(COMMAND, key));
    }

    @Test
    public void testDecodeValue() {
        String value = "KgUJ";
        byte[] decoded = decodeValue(COMMAND, value);
        byte[] expected = {(byte) 42, (byte) 5, (byte) 9};
        assertWithMessage("Decoded value").that(decoded).isEqualTo(expected);
    }

    @Test
    public void testDecodeValueInvalidBase64() {
        String value = "*";
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> decodeValue(COMMAND, value));
    }

    @Test
    public void testDecodeValueTooBig() {
        String value = "a".repeat(500);
        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () -> decodeValue(COMMAND, value));
    }
}
