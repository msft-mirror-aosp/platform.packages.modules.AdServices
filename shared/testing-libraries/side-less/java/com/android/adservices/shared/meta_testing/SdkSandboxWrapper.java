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
package com.android.adservices.shared.meta_testing;

import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.SdkSandbox;

/** Wrapper for real {@link SdkSandbox} implementations. */
public class SdkSandboxWrapper extends Wrapper<SdkSandbox> implements SdkSandbox {

    public SdkSandboxWrapper() {
        super();
    }

    public SdkSandboxWrapper(Logger logger) {
        super(logger);
    }

    @Override
    public SdkSandboxWrapper setState(State state) {
        mLog.v("setState(%s)", state);
        getWrapped().setState(state);
        return this;
    }

    @Override
    public State getState() {
        var state = getWrapped().getState();
        mLog.v("getState(): returning %s", state);
        return state;
    }
}
