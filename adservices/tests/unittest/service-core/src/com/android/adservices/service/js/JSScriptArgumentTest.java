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

package com.android.adservices.service.js;


import static com.android.adservices.service.js.JSScriptArgument.arrayArg;
import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.jsonArrayArg;
import static com.android.adservices.service.js.JSScriptArgument.jsonArrayArgNoValidation;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;
import static com.android.adservices.service.js.JSScriptArgument.stringMapToRecordArg;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.json.JSONException;
import org.junit.Test;

import java.util.Map;

public class JSScriptArgumentTest {
    private static final String VALID_TEST_OBJECT_JSON_ARRAY =
            "["
                    + "{"
                    + "\"name\":\"John\","
                    + "\"age\":30,"
                    + "\"city\":\"New York\""
                    + "},"
                    + "{"
                    + "\"name\":\"Alice\","
                    + "\"age\":25,"
                    + "\"city\":\"Los Angeles\""
                    + "},"
                    + "{"
                    + "\"name\":\"Bob\","
                    + "\"age\":35,"
                    + "\"city\":\"Chicago\""
                    + "}]";

    private static final ImmutableSet<TestObject> VALID_TEST_OBJECT_ARRAY =
            ImmutableSet.of(
                    new TestObject("John", 30, "New York"),
                    new TestObject("Alice", 25, "Los Angeles"),
                    new TestObject("Bob", 35, "Chicago"));

    @Test
    public void test_stringArg_validString_returnsSuccess() {
        JSScriptArgument arg = stringArg("stringArg", "value");
        assertThat(arg.variableDeclaration()).isEqualTo("const stringArg = \"value\";");
    }

    @Test
    public void test_numericArg_validInteger_returnsSuccess() {
        JSScriptArgument arg = numericArg("numericArg", 1);
        assertThat(arg.variableDeclaration()).isEqualTo("const numericArg = 1;");
    }

    @Test
    public void test_numericArg_validFloat_returnsSuccess() {
        JSScriptArgument arg = numericArg("numericArg", 1.001);
        assertThat(arg.variableDeclaration()).isEqualTo("const numericArg = 1.001;");
    }

    @Test
    public void test_jsonArg_validJson_returnsSuccess() throws JSONException {
        final String jsonValue = "{\"intField\": 123, \"stringField\": \"value\"}";
        JSScriptArgument arg = jsonArg("jsonArg", jsonValue);
        assertThat(arg.variableDeclaration())
                .isEqualTo(String.format("const jsonArg = %s;", jsonValue));
    }

    @Test
    public void test_jsonArrayArg_validJsonArray_returnsSuccess() throws JSONException {
        JSScriptArgument arg = jsonArrayArg("jsonArrayArg", VALID_TEST_OBJECT_JSON_ARRAY);
        assertThat(arg.variableDeclaration())
                .isEqualTo(String.format("const jsonArrayArg = %s;", VALID_TEST_OBJECT_JSON_ARRAY));
    }

    @Test
    public void test_jsonArrayArg_invalidJsonArray_throwsException() {
        final String jsonArrayValue = "this is an invalid json array";
        assertThrows(JSONException.class, () -> jsonArrayArg("jsonArrayArg", jsonArrayValue));
    }

    @Test
    public void test_jsonArrayArgNoValidation_emptyCollection_returnsEmptyJson() {
        JSScriptArgument arg =
                jsonArrayArgNoValidation(
                        "jsonArrayArg", ImmutableSet.of(), TestObject::serializeEntryToJson);
        assertThat(arg.variableDeclaration()).isEqualTo("const jsonArrayArg = [];");
    }

    @Test
    public void test_jsonArrayArgNoValidation_testObjectMarshaling_returnsMarshalledJson() {
        JSScriptArgument arg =
                jsonArrayArgNoValidation(
                        "jsonArrayArg", VALID_TEST_OBJECT_ARRAY, TestObject::serializeEntryToJson);
        assertThat(arg.variableDeclaration())
                .isEqualTo(String.format("const jsonArrayArg = %s;", VALID_TEST_OBJECT_JSON_ARRAY));
    }

    @Test
    public void test_jsonArg_invalidJson_throwsException() throws JSONException {
        // Missing closing }
        final String jsonValue = "{\"intField\": 123, \"stringField\": \"value\"";
        assertThrows(JSONException.class, () -> jsonArg("jsonArg", jsonValue));
    }

    @Test
    public void test_arrayArg_validArray_returnsSuccess() throws JSONException {
        JSScriptArgument arg =
                JSScriptArgument.arrayArg(
                        "arrayArg", stringArg("ignored", "value1"), stringArg("ignored", "value2"));

        assertThat(arg.variableDeclaration())
                .isEqualTo("const arrayArg = [\n\"value1\"," + "\n\"value2\"\n];");
    }

    @Test
    public void test_recordArg_validInput_returnsSuccess() throws JSONException {
        JSScriptArgument arg =
                recordArg(
                        "recordArg",
                        numericArg("intField", 123),
                        arrayArg(
                                "arrayField",
                                stringArg("ignored", "value1"),
                                stringArg("ignored", "value2")));
        assertThat(arg.variableDeclaration())
                .isEqualTo(
                        "const recordArg = {\n\"intField\": 123,\n\"arrayField\": [\n\"value1\","
                                + "\n\"value2\"\n]\n};");
    }

    @Test
    public void test_stringMapToRecordArg_validStringMap_returnsSuccess() throws JSONException {
        Map<String, String> signals =
                ImmutableMap.of(
                        "key1",
                        "{\"signals\":1}",
                        "key2",
                        "{\"signals\":2}",
                        "key3",
                        "{\"signals\":3}");
        JSScriptArgument arg = stringMapToRecordArg("stringMapToRecordArg", signals);
        assertThat(arg.variableDeclaration())
                .isEqualTo(
                        "const stringMapToRecordArg = {\n"
                                + "\"key1\": {\"signals\":1},\n"
                                + "\"key2\": {\"signals\":2},\n"
                                + "\"key3\": {\"signals\":3}\n"
                                + "};");
    }

    private static class TestObject {
        public String name;
        public int age;
        public String city;

        TestObject(String name, int age, String city) {
            this.name = name;
            this.age = age;
            this.city = city;
        }

        static void serializeEntryToJson(TestObject value, StringBuilder accumulator) {
            accumulator.append("{");
            accumulator.append("\"name\":\"").append(value.name).append("\",");
            accumulator.append("\"age\":").append(value.age).append(",");
            accumulator.append("\"city\":\"").append(value.city).append("\"");
            accumulator.append("}");
        }
    }
}
