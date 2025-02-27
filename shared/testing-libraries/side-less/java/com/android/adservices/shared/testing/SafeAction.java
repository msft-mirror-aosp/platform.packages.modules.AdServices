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

import java.util.Objects;

/** {@link Action} implementation that wraps another action so its methods don't throw. */
public final class SafeAction implements Action {

    private final Logger mLog;
    private final Action mAction;

    /** Default constructor. */
    public SafeAction(Logger logger, Action action) {
        mLog = Objects.requireNonNull(logger, "logger cannot be null");
        mAction = Objects.requireNonNull(action, "action cannot be null");
    }

    @Override
    public boolean execute() {
        try {
            return mAction.execute();
        } catch (Exception e) {
            mLog.e(e, "Failed to execute action %s", mAction);
            return false;
        }
    }

    @Override
    public boolean isExecuted() {
        return mAction.isExecuted();
    }

    @Override
    public void revert() {
        try {
            mAction.revert();
        } catch (Exception e) {
            mLog.e(e, "Failed to revert action %s", mAction);
        }
    }

    @Override
    public boolean isReverted() {
        return mAction.isReverted();
    }

    @Override
    public void reset() {
        try {
            mAction.reset();
        } catch (Exception e) {
            mLog.e(e, "Failed to revert action %s", mAction);
        }
    }

    @Override
    public String toString() {
        String originalString = mAction.toString();
        return originalString.contains("Action[")
                ? originalString.replace("Action[", "SafeAction[")
                : "SafeAction[" + originalString + ']';
    }
}
