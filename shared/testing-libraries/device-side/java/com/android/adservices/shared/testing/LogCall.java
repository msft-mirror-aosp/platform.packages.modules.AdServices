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

/** Defines a log call type to track expected and actual log calls. */
public abstract class LogCall {
    private static final int DEFAULT_TIMES = 1;
    public int mTimes;

    public LogCall() {
        this(DEFAULT_TIMES);
    }

    public LogCall(int times) {
        mTimes = times;
    }

    @Override
    public String toString() {
        return logInvocationToString() + ", times = " + mTimes;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Two LogCalls should be equal if the API and arguments that are being logged are identical,
     * regardless of mTimes. This definition is critical in that it serves two purposes:
     *
     * <p>1. Expected log calls may be modeled with a wild card argument. As a result, when matching
     * expected and actual log calls the verification happens in two scans where the first scan
     * identifies matching LogCalls using this definition. Ideally, matching priority should be
     * given to exactly matching LogCalls before wild card matching.
     *
     * <p>2. This definition may be used to identify duplicate expected LogCalls.
     *
     * @param o log call to compare against.
     * @return {@code true} if two log calls are identical; {@code false} otherwise.
     */
    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    /**
     * Expected log calls may be modeled with a wild card argument. In which case, override this to
     * provide an equality definition for two LogCall objects taking into account wild-card
     * modeling.
     *
     * <p>If LogCall does not support wild card matching of parameters, then the default
     * implementation is the same as equals. No need to override.
     *
     * @param other other log call to compare against.
     * @return {@code true} if two log calls represent the same invocation wrt to wildcard matching;
     *     {code false} otherwise.
     */
    public boolean isEquivalentInvocation(LogCall other) {
        return equals(other);
    }

    /**
     * Subclass must represent the log call invocation in readable format with any parameters, if
     * appropriate.
     *
     * <p>For example, "ErrorLogUtil.e(25, 26)", "ErrorLogUtil.e(IllegalStateException, 10, 20)".
     * The parent class will handle formatting the invocation call string along with expected number
     * of times. This will primarily help with providing helpful error messages in case of
     * mismatches.
     */
    public abstract String logInvocationToString();
}
