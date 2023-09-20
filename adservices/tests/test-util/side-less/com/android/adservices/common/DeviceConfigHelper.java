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

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

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
        mLog = mInterface.mLog;
        mLog.v("Constructor: interface=%s, logger=%s, namespace=%s", mInterface, logger, namespace);
    }

    public void set(String name, String value) {
        savePreviousValue(name);
        setOnly(name, value);
    }

    public void setWithSeparator(String name, String value, String separator) {
        String oldValue = savePreviousValue(name);
        String newValue = oldValue == null ? value : oldValue + separator + value;
        setOnly(name, newValue);
    }

    public void reset() {
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

    public void setSyncDisabledMode(SyncDisabledModeForTest mode) {
        mInterface.setSyncDisabledModeForTest(mode);
    }

    public void dumpFlags(StringBuilder dump) {
        String flags = mInterface.dump();
        dump.append(flags.isEmpty() ? "(no flags on namespace " + mNamespace + ")" : flags);
    }

    // TODO(b/294423183): temporarily exposed as it's used by legacy helper methods on
    // AdServicesFlagsSetterRule
    String get(String name) {
        return mInterface.get(name, /* defaultValue= */ null);
    }

    private String savePreviousValue(String name) {
        String oldValue = get(name);
        if (mFlagsToBeReset.containsKey(name)) {
            mLog.v("Value of %s (%s) already saved for reset()", name, mFlagsToBeReset.get(name));
            return oldValue;
        }
        mLog.v("Saving %s=%s for reset", name, oldValue);
        mFlagsToBeReset.put(name, oldValue);
        return oldValue;
    }

    private void setOnly(String name, String value) {
        if (!mInterface.set(name, value)) {
            // TODO(b/294423183): throw exception instead, to make it clear why it failed
            mLog.e("Fail to set %s=%s", name, value);
        }
    }

    private void delete(String name) {
        if (!mInterface.delete(name)) {
            // TODO(b/294423183): throw exception instead, to make it clear why it failed
            mLog.e("Fail to delete %s", name);
        }
    }

    enum SyncDisabledModeForTest {
        NONE,
        PERSISTENT,
        UNTIL_REBOOT
    }

    // TODO(b/294423183); move to a separate file (and rename it?)?
    /**
     * Low-level interface for {@link android.provider.DeviceConfig}.
     *
     * <p>By default it uses {@code cmd device_config} to implement all methods, but subclasses
     * could override them (for example, device-side implementation could use {@code DeviceConfig}
     * instead.
     */
    protected abstract static class Interface {

        protected final Logger mLog;
        protected final String mNamespace;

        protected Interface(String namespace, RealLogger logger) {
            mNamespace = Objects.requireNonNull(namespace);
            mLog = new Logger(Objects.requireNonNull(logger), DeviceConfigHelper.class);
        }

        void setSyncDisabledModeForTest(SyncDisabledModeForTest mode) {
            String value = mode.name().toLowerCase();
            mLog.v("SyncDisabledModeForTest(%s)", value);
            runShellCommand("device_config set_sync_disabled_for_test %s", value);
        }

        public String get(String name, String defaultValue) {
            mLog.d("get(%s, %s): using runShellCommand", name, defaultValue);
            String value = runShellCommand("device_config get %s %s", mNamespace, name).trim();
            mLog.v(
                    "get(%s, %s): raw value is '%s' (is null: %b)",
                    name, defaultValue, value, value == null);
            if (!value.equals("null")) {
                return value;
            }
            // "null" could mean the value doesn't exist, or it's the string "null", so we need to
            // check
            // them
            String allFlags = runShellCommand("device_config list %s", mNamespace);
            for (String line : allFlags.split("\n")) {
                if (line.equals(name + "=null")) {
                    mLog.v("Value of flag %s is indeed \"%s\"", name, value);
                    return value;
                }
            }
            return defaultValue;
        }

        // TODO(b/294423183): throw exception instead, to make it clear why it failed
        public boolean set(String name, @Nullable String value) {
            mLog.d("set(%s, %s): using runShellCommand", name, value);
            runShellCommand("device_config put %s %s %s", mNamespace, name, value);
            // TODO(b/294423183): parse result
            return true;
        }

        public boolean delete(String name) {
            mLog.d("delete(%s): using runShellCommand", name);
            runShellCommand("device_config delete %s %s", mNamespace, name);
            // TODO(b/294423183): parse result
            return true;
        }

        public String dump() {
            return runShellCommand("device_config list %s", mNamespace).trim();
        }

        @FormatMethod
        protected String runShellCommand(@FormatString String cmdFmt, @Nullable Object... cmdArgs) {
            throw new UnsupportedOperationException(
                    "Subclass must either implement this or the methods that use it");
        }

        @Override
        public String toString() {
            return getClass().getSimpleName();
        }
    }

    /** Factory for {@link Interface} objects. */
    interface InterfaceFactory {

        /**
         * Gets an {@link Interface} for the given {@link android.provider.DeviceConfig} namespace.
         */
        Interface getInterface(String namespace);
    }
}
