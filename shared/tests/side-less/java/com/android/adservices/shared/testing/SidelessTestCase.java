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

import com.android.adservices.shared.meta_testing.StandardStreamsLogger;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.google.common.truth.Expect;

import java.util.Objects;

import org.junit.Rule;

/** Base class for all tests on shared testing infra. */
public abstract class SidelessTestCase {

    @Rule public final Expect expect = Expect.create();

    protected final Logger mLog;

    protected SidelessTestCase() {
        this(StandardStreamsLogger.getInstance());
    }

    protected SidelessTestCase(RealLogger realLogger) {
        mLog = new Logger(Objects.requireNonNull(realLogger), getClass());
    }
}
