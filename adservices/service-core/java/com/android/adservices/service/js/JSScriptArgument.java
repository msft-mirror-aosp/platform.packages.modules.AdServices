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

import static java.util.Arrays.asList;

import org.json.JSONException;
import org.json.JSONObject;

/** Represent an argument to supply to an JS script. */
public abstract class JSScriptArgument {
    private final String mName;

    protected JSScriptArgument(String name) {
        mName = name;
    }

    /**
     * @return an argument with the given {@code name} and the given string value {@code value}
     */
    static JSScriptStringArgument stringArg(String name, String value) {
        return new JSScriptStringArgument(name, value);
    }

    /**
     * @return a JS object with the given {@code name} and value obtained parsing the given {@code
     *     value}.
     * @throws JSONException if {@code value} doesn't represent a valid JSON object
     */
    static JSScriptJsonArgument jsonArg(String name, String value) throws JSONException {
        // Creating the JSONObject just to parse value and cause a JSONException if invalid.
        new JSONObject(value);
        return new JSScriptJsonArgument(name, value);
    }

    /**
     * @return a JS array argument with the given {@code name} initialized with the values specified
     *     with {@code items}.
     */
    static <T extends JSScriptArgument> JSScriptArrayArgument<T> arrayArg(String name, T... items) {
        return new JSScriptArrayArgument<>(name, asList(items));
    }

    /**
     * @return a JS object with the given {@code name} and {@code fields} as fields values.
     */
    static JSScriptRecordArgument recordArg(String name, JSScriptArgument... fields) {
        return new JSScriptRecordArgument(name, asList(fields));
    }

    /**
     * @return a numeric variable with the given {@code name} and {@code value}.
     */
    static <T extends Number> JSScriptNumericArgument<T> numericArg(String name, T value) {
        return new JSScriptNumericArgument<>(name, value);
    }

    /**
     * @return the JS code to use to initialize the variable.
     */
    public String variableDeclaration() {
        return String.format("const %s = %s;", name(), initializationValue());
    }

    /**
     * @return name of the argument as referred in the call to the auction script function.
     */
    protected String name() {
        return mName;
    }

    /**
     * @return the JS code to use to initialize the newly declared variable.
     */
    abstract String initializationValue();
}
