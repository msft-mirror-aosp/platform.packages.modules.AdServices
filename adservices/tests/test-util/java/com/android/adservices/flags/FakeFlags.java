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

import com.android.adservices.service.Flags;
import com.android.adservices.shared.flags.FlagsBackend;
import com.android.adservices.shared.testing.Identifiable;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePairSetter;
import com.android.adservices.shared.testing.flags.FakeFlagsBackend;
import com.android.adservices.shared.testing.flags.MissingFlagBehavior;

import com.google.common.annotations.VisibleForTesting;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

// TODO(b/338067482): there's still a lot of duplication here (like methods that delegate to
// getFakeFlagsBackend(), once AdServicesFakeDebugFlagsSetterRule is fully implemented (and tested),
// we should refactor them.
// TODO(b/384798806): make it package protected once FakeFlagsFactory is moved to this package
public final class FakeFlags extends RawFlags implements Identifiable {

    private static final String TAG = FakeFlags.class.getSimpleName();

    private static int sNextId;

    private final String mId = String.valueOf(++sNextId);
    private final boolean mImmutable;

    private FakeFlags(boolean immutable) {
        this(new FakeFlagsBackend(TAG), immutable);
    }

    private FakeFlags(FlagsBackend backend, boolean immutable) {
        super(backend);
        mImmutable = immutable;
    }

    private FakeFlagsBackend getFakeFlagsBackend() {
        return (FakeFlagsBackend) mBackend;
    }

    static FakeFlags createFakeFlagsForFlagSetterRulePurposesOnly() {
        return new FakeFlags(/* immutable= */ false);
    }

    // TODO(b/384798806): make it package protected once FakeFlagsFactory is moved to this package
    public static FakeFlags createFakeFlagsForFakeFlagsFactoryPurposesOnly() {
        return new FakeFlags(/* immutable= */ true).setFakeFlagsFactoryFlags();
    }

    NameValuePairSetter getFlagsSetter() {
        return getFakeFlagsBackend();
    }

    @VisibleForTesting
    void setFlag(String name, String value) {
        if (mImmutable) {
            throw new UnsupportedOperationException(
                    "setFlag(" + name + ", " + value + "): not supported on immutable Flags");
        }
        getFakeFlagsBackend().setFlag(name, value);
    }

    void setMissingFlagBehavior(MissingFlagBehavior behavior) {
        getFakeFlagsBackend().setMissingFlagBehavior(behavior);
    }

    MissingFlagBehavior getMissingFlagBehavior() {
        return getFakeFlagsBackend().getMissingFlagBehavior();
    }

    Flags getSnapshot() {
        Map<String, NameValuePair> flags = getFakeFlagsBackend().getSnapshot();
        return new FakeFlags(
                new FakeFlagsBackend(TAG, new HashMap<>(flags)), /* immutable= */ true);
    }

    private FakeFlags setFakeFlagsFactoryFlags() {
        var backend = getFakeFlagsBackend();
        AdServicesFlagsSetterRuleForUnitTests.setFakeFlagsFactoryFlags(
                (name, value) -> backend.setFlag(name, value));
        return this;
    }

    @Override
    public String getId() {
        return mId;
    }

    @Override
    public String toString() {
        var prefix = "FakeFlags#" + mId + "{";
        var flags = getFakeFlagsBackend().getSnapshot();
        if (flags.isEmpty()) {
            return prefix + "empty}";
        }
        return flags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()) // sort by key
                .map(entry -> entry.getValue().toString())
                .collect(Collectors.joining(", ", prefix, "}"));
    }
}
