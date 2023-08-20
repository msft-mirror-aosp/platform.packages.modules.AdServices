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
package com.android.adservices.common;

import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.compatibility.common.util.ShellUtils;

// TODO(b/294423183): add unit tests
/**
 * Helper class to set {@link android.os.SystemProperties} and properly reset then to their original
 * values.
 *
 * <p><b>Note: </b>this class is not thread safe.
 */
public final class SystemPropertiesHelper {

    private static final String TAG = SystemPropertiesHelper.class.getSimpleName();

    private final String mPrefix;
    private final ArrayMap<String, String> mPropsToBeReset = new ArrayMap<>();

    public SystemPropertiesHelper(String prefix) {
        Log.v(TAG, "Constructor for " + prefix);
        mPrefix = prefix;
    }

    public void set(String name, String value) {
        savePreviousValue(name);
        setOnly(name, value);
    }

    public void reset() {
        int size = mPropsToBeReset.size();
        if (size == 0) {
            Log.d(TAG, "reset(): not needed");
            return;
        }
        Log.v(TAG, "reset(): restoring " + size + " flags");
        try {
            mPropsToBeReset.forEach((name, value) -> setOnly(name, value));
        } finally {
            mPropsToBeReset.clear();
        }
    }

    public void dump(StringBuilder dump) {
        String properties = ShellUtils.runShellCommand("getprop");
        addProperties(dump, properties, mPrefix);
    }

    private String get(String name) {
        return SystemProperties.get(getPropertyName(name));
    }

    private void savePreviousValue(String name) {
        if (mPropsToBeReset.containsKey(name)) {
            Log.v(
                    TAG,
                    "Value of "
                            + name
                            + "("
                            + mPropsToBeReset.get(name)
                            + ") already saved for reset()");
            return;
        }
        String oldValue = get(name);
        Log.v(TAG, "Saving " + name + "=" + oldValue + " for reset");
        mPropsToBeReset.put(name, oldValue);
    }

    private void setOnly(String name, String value) {
        String prop = getPropertyName(name);
        Log.v(TAG, "set(" + prop + ", " + value + ")");

        if (!TextUtils.isEmpty(value)) {
            ShellUtils.runShellCommand("setprop %s %s", prop, value);
        } else {
            // TODO(b/293132368): UIAutomation doesn't support passing a "" or '' - it will quote
            // them, which would cause the property value to be "" or '', not the empty String.
            // Another approach would be calling SystemProperties.set(), but that method is hidden
            // (b/294414609)
            Log.w(
                    TAG,
                    "NOT resetting property "
                            + name
                            + " to empty String as it's not supported by"
                            + " runShellCommand(), but setting it as null");
            ShellUtils.runShellCommand("setprop %s null", prop);
        }
    }

    private String getPropertyName(String name) {
        return mPrefix + name;
    }

    private static void addProperties(StringBuilder builder, String properties, String prefix) {
        String realPrefix = "[" + prefix;
        String[] lines = properties.split("\n");
        boolean foundAtLeastOne = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(realPrefix)) {
                foundAtLeastOne = true;
                builder.append(line).append('\n');
            }
        }
        if (!foundAtLeastOne) {
            builder.append("(no properties with prefix ").append(prefix).append(')');
        }
    }
}
