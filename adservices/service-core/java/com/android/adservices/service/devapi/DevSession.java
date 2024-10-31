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

    public DevSession() {
        // Constructor for AutoValue.
    }

    /** Returns the current state of the developer session. */
    public abstract DevSessionState getState();

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
        return builder().setState(DevSessionState.values()[proto.getState().getNumber()]).build();
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
                .build();
    }

    /** Returns a new builder for creating a {@link DevSession} instance. */
    public static Builder builder() {
        return new AutoValue_DevSession.Builder();
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

        /** Creates a new {@link DevSession} instance with the configured properties. */
        public abstract DevSession build();
    }
}
