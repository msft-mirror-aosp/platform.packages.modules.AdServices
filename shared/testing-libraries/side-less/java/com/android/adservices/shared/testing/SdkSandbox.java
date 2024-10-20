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
package com.android.adservices.shared.testing;

/** Side-agnostic abstraction to interact with some {@code SdkSandbox} features. */
public interface SdkSandbox {

    /** Gets the current state. */
    State getState();

    /**
     * Sets the state, or no-op if not supported..
     *
     * @throws IllegalArgumentException if state is not {@code ENABLED} or {@code DISABLED}.
     */
    void setState(State state);

    /* State of the {@code SdkSandbox}. */
    enum State {
        /** Failed to parse state. */
        UNKNOWN(false),
        /** Device doesn't support it. */
        UNSUPPORTED(false),
        /** Enabled! */
        ENABLED(true),
        /* Disabled :-( */
        DISABLED(true);

        private final boolean mSettable;

        State(boolean settable) {
            mSettable = settable;
        }

        /** Whether it can be used on methods that sets the state. */
        public boolean isSettable() {
            return mSettable;
        }
    }
}
