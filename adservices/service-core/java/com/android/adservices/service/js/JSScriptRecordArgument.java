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

import java.util.List;

/**
 * A value object to be represented as a JSON structure in the auction script. If you already have a
 * JSON string you should use {@link JSScriptJsonArgument}.
 */
public class JSScriptRecordArgument extends JSScriptArgument {
    private final List<JSScriptArgument> mFields;

    JSScriptRecordArgument(String name, List<JSScriptArgument> fields) {
        super(name);
        this.mFields = fields;
    }

    @Override
    public String initializationValue() {
        StringBuilder result = new StringBuilder();
        result.append("{");
        boolean firstArg = true;
        for (JSScriptArgument field : mFields) {
            result.append(
                    String.format(
                            "%s\n\"%s\": %s",
                            firstArg ? "" : ",", field.name(), field.initializationValue()));
            firstArg = false;
        }
        result.append("\n}");
        return result.toString();
    }
}
