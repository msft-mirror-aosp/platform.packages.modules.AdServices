/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.server.adservices;

import android.os.Binder;
import android.provider.DeviceConfig;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Utility class for reading {@link DeviceConfig} flags from the Binder thread in the system server.
 *
 * <p>To read a {@link DeviceConfig} flag in the system server, only system server itself is allowed
 * to perform the read. The caller bound to the system server will encounter a permission issue when
 * reading a {@link DeviceConfig} flag in the system server.
 *
 * <p>To allow the binder thread to read a {@link DeviceConfig} flag, the calling identity needs to
 * be temporarily cleared.
 */
final class BinderFlagReader {
    /**
     * Reads and returns the given flag from the {@link Binder} thread in the system server.
     *
     * <p>To allow the binder thread to read a {@link DeviceConfig} flag, the calling identity needs
     * to be temporarily cleared.
     *
     * @param <T> type returned by the {@code flagSupplier}
     */
    static <T> T readFlag(Supplier<T> flagSupplier) {
        Objects.requireNonNull(flagSupplier);

        final long token = Binder.clearCallingIdentity();

        try {
            return flagSupplier.get();
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }
}
