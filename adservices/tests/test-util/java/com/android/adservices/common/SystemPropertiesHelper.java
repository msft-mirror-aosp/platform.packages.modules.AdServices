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

import com.android.adservices.common.Logger.RealLogger;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

// TODO(b/294423183): add unit tests
/**
 * Helper class to set {@link android.os.SystemProperties} and properly reset then to their original
 * values.
 *
 * <p><b>NOTE:</b>this class should not have any dependency on Android classes as its used both on
 * device and host side tests.
 *
 * <p><b>NOTE: </b>this class is not thread safe.
 */
public final class SystemPropertiesHelper {

    private final Map<String, String> mPropsToBeReset = new HashMap<>();

    final Logger mLog;
    private final Interface mInterface;
    private final String mPrefix;

    public SystemPropertiesHelper(Interface helperInterface, RealLogger logger, String prefix) {
        mInterface = Objects.requireNonNull(helperInterface);
        mLog = new Logger(Objects.requireNonNull(logger), SystemPropertiesHelper.class);
        mPrefix = Objects.requireNonNull(prefix);
        mLog.v("Constructor: interface=%s, logger=%s, prefix=%s", helperInterface, logger, prefix);
    }

    public void set(String name, String value) throws Exception {
        savePreviousValue(name);
        setOnly(name, value);
    }

    public void reset() throws Exception {
        int size = mPropsToBeReset.size();
        if (size == 0) {
            mLog.d("reset(): not needed");
            return;
        }
        mLog.v("reset(): restoring %s flags", size);
        try {
            for (Entry<String, String> flag : mPropsToBeReset.entrySet()) {
                setOnly(flag.getKey(), flag.getValue());
            }
        } finally {
            mPropsToBeReset.clear();
        }
    }

    public void dumpSystemProperties(StringBuilder dump) throws Exception {
        String properties = mInterface.dumpSystemProperties();
        addProperties(dump, properties, mPrefix);
    }

    private String get(String name) throws Exception {
        return mInterface.get(getPropertyName(name));
    }

    private void savePreviousValue(String name) throws Exception {
        if (mPropsToBeReset.containsKey(name)) {
            mLog.v("Value of %s (%s) already saved for reset()", name, mPropsToBeReset.get(name));
            return;
        }
        String oldValue = get(name);
        mLog.v("Saving %s=%s for reset", name, oldValue);
        mPropsToBeReset.put(name, oldValue);
    }

    private void setOnly(String name, String value) throws Exception {
        mInterface.set(getPropertyName(name), value);
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

    /** Low-level interface for {@link android.os.SystemProperties}. */
    interface Interface {

        /** Gets the value of a property. */
        String get(String name) throws Exception;

        /** Sets the value of a property. */
        void set(String name, String value) throws Exception;

        /** Lists all properties (names and values). */
        String dumpSystemProperties() throws Exception;
    }
}
