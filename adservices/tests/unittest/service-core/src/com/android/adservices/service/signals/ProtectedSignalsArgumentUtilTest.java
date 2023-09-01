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

package com.android.adservices.service.signals;

import static com.android.adservices.service.signals.ProtectedSignalsArgumentUtil.SIGNAL_FIELD_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.adservices.common.CommonFixture;

import com.android.adservices.service.js.JSScriptArgument;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtectedSignalsArgumentUtilTest {

    public static final String PACKAGE = CommonFixture.TEST_PACKAGE_NAME_1;
    public static final Instant FIXED_NOW = CommonFixture.FIXED_NOW;
    private Map<String, List<ProtectedSignal>> mSignals;

    @Before
    public void setup() {
        mSignals = new HashMap<>();
    }

    @Test
    public void testJSArgument() {
        ProtectedSignal signal = generateSignal("signal1");
        mSignals.put("test_key", List.of(signal));

        JSScriptArgument argument = ProtectedSignalsArgumentUtil.asScriptArgument(mSignals);

        assertEquals(SIGNAL_FIELD_NAME, argument.name());

        String expectedVariable =
                "const signals = \"[{\"test_key\":[{\"val\":\"signal1\","
                        + "\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]}]\";";

        String actualVariable = argument.variableDeclaration();
        assertEquals(expectedVariable, actualVariable);
    }

    @Test
    public void testEmptySignals() {
        String expectedEmpty = "[]";
        assertEquals(expectedEmpty, ProtectedSignalsArgumentUtil.marshalToJson(mSignals));
    }

    @Test
    public void testSingleSignal() {
        ProtectedSignal signal = generateSignal("signal1");
        mSignals.put("test_key", List.of(signal));

        String expectedJSON =
                "[{\"test_key\":"
                        + "[{\"val\":\"signal1\",\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]}]";
        String actualJSON = ProtectedSignalsArgumentUtil.marshalToJson(mSignals);

        assertEquals(expectedJSON, actualJSON);
        assertTrue(isValidJson(actualJSON));
    }

    @Test
    public void testMultipleSignals() {
        ProtectedSignal signalA1 = generateSignal("signalA1");
        ProtectedSignal signalA2 = generateSignal("signalA1");
        ProtectedSignal signalB1 = generateSignal("signalB1");

        mSignals.put("test_key_A", List.of(signalA1, signalA2));
        mSignals.put("test_key_B", List.of(signalB1));

        String expectedJSON =
                "["
                        + "{\"test_key_B\":"
                        + "[{\"val\":\"signalB1\",\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]},"
                        + "{\"test_key_A\":"
                        + "[{\"val\":\"signalA1\",\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"},"
                        + "{\"val\":\"signalA1\",\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]}"
                        + "]";
        String actualJSON = ProtectedSignalsArgumentUtil.marshalToJson(mSignals);

        assertEquals(expectedJSON, actualJSON);
        assertTrue(isValidJson(actualJSON));
    }

    @Test
    public void handleEmptyValue() {
        ProtectedSignal signal =
                ProtectedSignal.builder()
                        .setValue("")
                        .setCreationTime(FIXED_NOW)
                        .setPackageName(PACKAGE)
                        .build();
        mSignals.put("test_key", List.of(signal));
        String expectedJSON =
                "[{\"test_key\":[{\"val\":\"\",\"time\":"
                        + FIXED_NOW.getEpochSecond()
                        + ",\"app\":\"android.adservices.tests1\"}]}]";
        String actualJSON = ProtectedSignalsArgumentUtil.marshalToJson(mSignals);

        assertEquals(expectedJSON, actualJSON);
        assertTrue(isValidJson(actualJSON));
    }

    private ProtectedSignal generateSignal(String value) {
        return ProtectedSignal.builder()
                .setValue(value)
                .setCreationTime(FIXED_NOW)
                .setPackageName(PACKAGE)
                .build();
    }

    private boolean isValidJson(String jsonString) {
        try {
            JsonParser parser = new JsonParser();
            parser.parse(jsonString);
            return true;
        } catch (JsonSyntaxException e) {
            return false;
        }
    }
}
