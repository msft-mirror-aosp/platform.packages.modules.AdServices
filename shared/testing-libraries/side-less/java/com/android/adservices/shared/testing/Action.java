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
     */
    boolean execute() throws Exception;

    /** Reverts the previously executed action. */
    void revert() throws Exception;
}
