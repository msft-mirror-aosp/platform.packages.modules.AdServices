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

import com.android.adservices.shared.testing.AbstractProcessLifeguardRule;
import com.android.adservices.shared.testing.DynamicLogger;
import com.android.adservices.shared.testing.Logger;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.SidelessTestCase;

import org.junit.Rule;
import org.junit.Test;

/** Base class for all tests on shared testing infra. */
public abstract class SharedSidelessTestCase extends SidelessTestCase {

    // TODO(b/342639109): set order / move to superclass (which should rely on an abstract method
    // to get it, so it would be properly implemented by host/device-side)
    @Rule
    public final AbstractProcessLifeguardRule processLifeguard =
            new AbstractProcessLifeguardRule(
                    DynamicLogger.getInstance(), AbstractProcessLifeguardRule.Mode.IGNORE) {

                @Override
                protected boolean isMainThread() {
                    mLog.i("isMainThread(): undefined on sideless, returning false");
                    return false;
                }
            };

    protected final FakeRealLogger mFakeRealLogger = new FakeRealLogger();
    protected final Logger mFakeLogger = new Logger(mFakeRealLogger, getClass());

    protected SharedSidelessTestCase() {
        this(DynamicLogger.getInstance());
    }

    protected SharedSidelessTestCase(RealLogger realLogger) {
        super(realLogger);
    }

    @Test
    @Override
    public final void testValidTestCaseFixtures() throws Exception {
        assertValidTestCaseFixtures();
    }

    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        assertTestClassHasNoFieldsFromSuperclass(
                SharedSidelessTestCase.class, "processLifeguard", "mFakeRealLogger", "mFakeLogger");
    }
}
