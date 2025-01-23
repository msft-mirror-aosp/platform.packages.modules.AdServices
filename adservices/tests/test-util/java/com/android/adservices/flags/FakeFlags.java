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
package com.android.adservices.flags;

import com.android.adservices.shared.testing.Identifiable;
import com.android.adservices.shared.testing.flags.FakeFlagsBackend;

import com.google.common.annotations.VisibleForTesting;

import java.util.Map;
import java.util.stream.Collectors;

// TODO(b/384798806): make it package protected once FakeFlagsFactory is moved to this package
public final class FakeFlags extends RawFlagsForTests<FakeFlagsBackend> implements Identifiable {

    private static final String TAG = FakeFlags.class.getSimpleName();

    private static int sNextId;

    private final String mId = String.valueOf(++sNextId);

    private FakeFlags(FakeFlagsBackend backend) {
        super(backend);
    }

    static FakeFlags createFakeFlagsForFlagSetterRulePurposesOnly() {
        return new FakeFlags(new FakeFlagsBackend(TAG));
    }

    @VisibleForTesting
    static FakeFlags createFakeFlagsForFakeFlagsTestPurposesOnly() {
        // In theory test could call createFakeFlagsForFlagSetterRulePurposesOnly() directly, but it
        // doesn't hurt to offer a proper method...
        return createFakeFlagsForFlagSetterRulePurposesOnly();
    }

    // TODO(b/384798806): make it package protected once FakeFlagsFactory is moved to this package
    public static FakeFlags createFakeFlagsForFakeFlagsFactoryPurposesOnly() {
        var backend = new FakeFlagsBackend(TAG);
        AdServicesFlagsSetterRuleForUnitTests.setFakeFlagsFactoryFlags(
                (name, value) -> backend.setFlag(name, value));
        return new FakeFlags(backend.cloneForSnapshot());
    }

    FakeFlagsBackend getBackend() {
        return mBackend;
    }

    FakeFlags getSnapshot() {
        return new FakeFlags(mBackend.cloneForSnapshot());
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String toString() {
        var prefix = "FakeFlags#" + mId + "{";
        var flags = mBackend.getFlags();
        if (flags.isEmpty()) {
            return prefix + "empty}";
        }
        return flags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // sort by key
                .map(entry -> entry.getValue().toString())
                .collect(Collectors.joining(", ", prefix, "}"));
    }
}
