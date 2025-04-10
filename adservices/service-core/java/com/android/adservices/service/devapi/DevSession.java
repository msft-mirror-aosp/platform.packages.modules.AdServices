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

package com.android.adservices.service.devapi;

import com.google.auto.value.AutoValue;

import java.util.regex.Pattern;

/**
 * Represents the current state of developer mode on the device.
 *
 * <p>This class is used to store and manage the developer mode session, including its expiry time
 * and current state.
 */
@AutoValue
public abstract class DevSession {

    /**
     * Constant representing an unknown state. This should match {@link
     * com.android.adservices.service.proto.DevSession#getDefaultInstance}.
     */
    public static final DevSession UNKNOWN =
            DevSession.builder().setState(DevSessionState.UNKNOWN).build();

    /** Default app allowlist pattern which matches empty input. */
    public static final String DEFAULT_EMPTY_APP_ALLOWLIST_PATTERN = "^$";

    public DevSession() {
        // Constructor for AutoValue.
    }

    /** Returns the current state of the developer session. */
    public abstract DevSessionState getState();

    /** Returns true if server auction test keys are enabled */
    public abstract boolean isServerAuctionTestKeysEnabled();

    /** Returns the non-debuggable app allowlist pattern string for the current dev session. */
    public abstract String getNonDebuggableAppAllowlistPatternString();

    /** Returns the non-debuggable app allowlist pattern for the current dev session. */
    public Pattern getNonDebuggableAppAllowlistPattern() {
        return Pattern.compile(getNonDebuggableAppAllowlistPatternString());
    }

    /**
     * Creates a new {@link DevSession} instance from the given proto.
     *
     * @param proto The proto to convert from.
     * @return A new {@link DevSession} instance.
     * @throws IllegalStateException If the {@link
     *     com.android.adservices.service.proto.DevSessionStorage} was not initialized.
     */
    public static DevSession fromProto(
            com.android.adservices.service.proto.DevSessionStorage proto) {
        if (!proto.getIsStorageInitialized()) {
            throw new IllegalStateException("Cannot read DevSessionStorage when not initialized");
        }
        return builder()
                .setState(DevSessionState.values()[proto.getState().getNumber()])
                .setServerAuctionTestKeysEnabled(proto.getServerAuctionTestKeysEnabled())
                .setNonDebuggableAppAllowlistPatternString(
                        proto.getNonDebuggableAppAllowlistPattern())
                .build();
    }

    /**
     * Converts this {@link DevSession} instance to a proto.
     *
     * @param devSession The {@link DevSession} instance to convert.
     * @return A new proto instance.
     */
    public static com.android.adservices.service.proto.DevSessionStorage toProto(
            DevSession devSession) {
        return com.android.adservices.service.proto.DevSessionStorage.newBuilder()
                .setState(
                        com.android.adservices.service.proto.DevSessionStorage.State.forNumber(
                                devSession.getState().ordinal()))
                .setIsStorageInitialized(true)
                .setServerAuctionTestKeysEnabled(devSession.isServerAuctionTestKeysEnabled())
                .setNonDebuggableAppAllowlistPattern(
                        devSession.getNonDebuggableAppAllowlistPatternString())
                .build();
    }

    /** Returns a new builder for creating a {@link DevSession} instance. */
    public static Builder builder() {
        return new AutoValue_DevSession.Builder()
                .setServerAuctionTestKeysEnabled(false)
                .setNonDebuggableAppAllowlistPatternString(DEFAULT_EMPTY_APP_ALLOWLIST_PATTERN);
    }

    /** Returns a {@link DevSession} for a newly initialized state, e.g. first read. */
    public static DevSession createForNewlyInitializedState() {
        return builder().setState(DevSessionState.IN_PROD).build();
    }

    /** Builder for creating a {@link DevSession} instance. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the state of the developer session. */
        public abstract Builder setState(DevSessionState state);

        /** Enables/disables server auction test keys. */
        public abstract Builder setServerAuctionTestKeysEnabled(boolean enabled);

        /** Sets the app allowlist pattern for the current dev session. */
        public abstract Builder setNonDebuggableAppAllowlistPatternString(String patternString);

        /** Creates a new {@link DevSession} instance with the configured properties. */
        public abstract DevSession build();
    }
}
