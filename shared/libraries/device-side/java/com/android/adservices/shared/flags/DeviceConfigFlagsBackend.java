/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.shared.flags;

import android.provider.DeviceConfig;

import java.util.Objects;

/** Implementation of {@link FlagsBackend} using {@link DeviceConfig} */
public final class DeviceConfigFlagsBackend implements FlagsBackend {

    private final String mNamespace;

    /** Trivial javadoc to make checkstyle happy! */
    public DeviceConfigFlagsBackend(String namespace) {
        mNamespace = Objects.requireNonNull(namespace, "namespace cannot be null");
    }

    @Override
    public String getFlag(String name) {
        checkName(name);
        return DeviceConfig.getProperty(mNamespace, name);
    }

    @Override
    public boolean getFlag(String name, boolean defaultValue) {
        checkName(name);
        return DeviceConfig.getBoolean(mNamespace, name, defaultValue);
    }

    @Override
    public String getFlag(String name, String defaultValue) {
        checkName(name);
        return DeviceConfig.getString(mNamespace, name, defaultValue);
    }

    @Override
    public int getFlag(String name, int defaultValue) {
        checkName(name);
        return DeviceConfig.getInt(mNamespace, name, defaultValue);
    }

    @Override
    public long getFlag(String name, long defaultValue) {
        checkName(name);
        return DeviceConfig.getLong(mNamespace, name, defaultValue);
    }

    @Override
    public float getFlag(String name, float defaultValue) {
        checkName(name);
        return DeviceConfig.getFloat(mNamespace, name, defaultValue);
    }

    private void checkName(String name) {
        Objects.requireNonNull(name, "name cannot be null");
    }
}
