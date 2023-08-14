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
// TODO(b/294423183): use an existing class like DeviceConfigStateManager or DeviceConfigStateHelper
/**
 * Helper class to set {@link android.provider.DeviceConfig} flags and properly reset then to their
 * original values.
 *
 * <p><b>NOTE:</b>this class should not have any dependency on Android classes as its used both on
 * device and host side tests.
 *
 * <p><b>NOTE: </b>this class is not thread safe.
 */
final class DeviceConfigHelper {

    private final String mNamespace;
    private final Interface mInterface;
    private final Map<String, String> mFlagsToBeReset = new HashMap<>();

    final Logger mLog;

    DeviceConfigHelper(InterfaceFactory interfaceFactory, String namespace, RealLogger logger) {
        mNamespace = Objects.requireNonNull(namespace);
        mInterface = Objects.requireNonNull(interfaceFactory).getInterface(mNamespace);
        if (mInterface == null) {
            throw new IllegalArgumentException(
                    "factory " + interfaceFactory + " returned null interface");
        }
        mLog = new Logger(Objects.requireNonNull(logger), DeviceConfigHelper.class);
        mLog.v("Constructor: interface=%s, logger=%s, namespace=%s", mInterface, logger, namespace);
    }

    public void set(String name, String value) throws Exception {
        savePreviousValue(name);
        setOnly(name, value);
    }

    public void setWithSeparator(String name, String value, String separator) throws Exception {
        String oldValue = savePreviousValue(name);
        String newValue = oldValue == null ? value : oldValue + separator + value;
        setOnly(name, newValue);
    }

    public void reset() throws Exception {
        int size = mFlagsToBeReset.size();
        if (size == 0) {
            mLog.d("reset(): not needed");
            return;
        }
        mLog.v("reset(): restoring %d flags", size);
        try {
            for (Entry<String, String> flag : mFlagsToBeReset.entrySet()) {
                String name = flag.getKey();
                String value = flag.getValue();
                if (value == null) {
                    delete(name);
                } else {
                    setOnly(name, value);
                }
            }
        } finally {
            mFlagsToBeReset.clear();
        }
    }

    public void setSyncDisabledMode(SyncDisabledModeForTest mode) throws Exception {
        mInterface.setSyncDisabledModeForTest(mode);
    }

    public void dumpFlags(StringBuilder dump) throws Exception {
        String flags = mInterface.dump();
        dump.append(flags.isEmpty() ? "(no flags on namespace " + mNamespace + ")" : flags);
    }

    // TODO(b/294423183): temporarily exposed as it's used by legacy helper methods on
    // AdServicesFlagsSetterRule
    String get(String name) throws Exception {
        return mInterface.get(name, /* defaultValue= */ null);
    }

    private String savePreviousValue(String name) throws Exception {
        String oldValue = get(name);
        if (mFlagsToBeReset.containsKey(name)) {
            mLog.v("Value of %s (%s) already saved for reset()", name, mFlagsToBeReset.get(name));
            return oldValue;
        }
        mLog.v("Saving %s=%s for reset", name, oldValue);
        mFlagsToBeReset.put(name, oldValue);
        return oldValue;
    }

    private void setOnly(String name, String value) throws Exception {
        mInterface.set(name, value);
    }

    private void delete(String name) throws Exception {
        mInterface.delete(name);
    }

    enum SyncDisabledModeForTest {
        NONE,
        PERSISTENT,
        UNTIL_REBOOT
    }

    /** Low-level interface for {@link android.provider.DeviceConfig}. */
    interface Interface {

        void setSyncDisabledModeForTest(SyncDisabledModeForTest mode) throws Exception;

        String get(String name, @Nullable String defaultValue) throws Exception;

        void set(String name, @Nullable String value) throws Exception;

        void delete(String name) throws Exception;

        String dump() throws Exception;
    }

    /** Factory for {@link Interface} objects. */
    interface InterfaceFactory {

        /**
         * Gets an {@link Interface} for the given {@link android.provider.DeviceConfig} namespace.
         */
        Interface getInterface(String namespace);
    }
}
