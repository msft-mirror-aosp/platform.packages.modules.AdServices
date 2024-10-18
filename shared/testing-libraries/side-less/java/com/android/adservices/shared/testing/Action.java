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

/** Represents an action executed by a rule, like setting the value of a flag. */
public interface Action {

    /**
     * Executes the action, returning whether it should be reverted at the end of the test.
     *
     * <p>For example, if the action is used to set the value of a flag but it does nothing because
     * the desired value was already set, then it should return {@code false}.
     *
     * @throws IllegalStateException if already executed (unless {@link #reset()} is called after
     *     it)
     */
    boolean execute() throws Exception;

    /**
     * Returns whether the action was executed, either initially or after it was {@link #reset()
     * reset}.
     */
    boolean isExecuted();

    /**
     * Reverts the previously executed action.
     *
     * @throws IllegalStateException if not executed yet or if called more than once after executed.
     */
    void revert() throws Exception;

    /**
     * Returns whether the action was reverted, either initially or after it was {@link #reset()
     * reset}.
     */
    boolean isReverted();

    /**
     * Resets the action so it can be executed again.
     *
     * @throws IllegalStateException if not executed and reverted yet.
     */
    void reset();
}
