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
    protected int mTimes;

    public LogCall(int times) {
        mTimes = times;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Evaluates the equality of two LogCall objects. Two log calls are considered equal if the
     * invocation is the same along with number of log calls made. Subclasses are not expected to
     * change this implementation.
     *
     * @return true if two log calls are equal; false otherwise.
     */
    @Override
    public final boolean equals(Object o) {
        if (o == this) {
            return true;
        }

        if (!(o instanceof LogCall other)) {
            return false;
        }

        return mTimes == other.mTimes && isIdenticalInvocation(other);
    }

    /**
     * Determines whether two log call invocations are identical, irrespective of number of times
     * they have been invoked. Subclasses are expected to implement this method without taking into
     * account mTimes. NOTE: If two log calls represent the same invocation, their hash codes must
     * be the same.
     *
     * @param other other log call to compare against.
     * @return true if two log calls represent the same invocation; false otherwise.
     */
    public abstract boolean isIdenticalInvocation(LogCall other);

    /**
     * {@inheritDoc}
     *
     * <p>Subclass must define a unique hashcode using the logging metadata it stores without taking
     * into account mTimes. NOTE: If two log calls represent the same invocation, their hash codes
     * must be the same.
     *
     * @return hashcode of log call object.
     */
    @Override
    public abstract int hashCode();
}
