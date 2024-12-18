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
package com.android.adservices.shared;

import android.platform.test.ravenwood.RavenwoodRule;

import com.android.adservices.shared.testing.CallSuper;
import com.android.adservices.shared.testing.DeviceSideTestCase;
import com.android.adservices.shared.testing.common.ApplicationContextSingletonRule;

import org.junit.Rule;
import org.junit.rules.TestName;

public abstract class SharedUnitTestCase extends DeviceSideTestCase {

    // TODO(b/342639109): make sure rules below have the right order

    // TODO(b/285014040): use custom rule
    @Rule(order = 2)
    public final TestName name = new TestName();

    @Rule(order = 5)
    public final ApplicationContextSingletonRule appContext = getApplicationContextSingletonRule();

    @Override
    public final String getTestName() {
        return name.getMethodName();
    }

    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        assertTestClassHasNoFieldsFromSuperclass(SharedUnitTestCase.class, "name", "appContext");
    }

    // TODO(b/355286824): Hac^H^H^Hworkaround to run on Ravenwood, as it doesn't support
    // Context.getApplicationContext() yet
    private ApplicationContextSingletonRule getApplicationContextSingletonRule() {
        var appContext =
                RavenwoodRule.isOnRavenwood() ? mContext : mContext.getApplicationContext();
        return new ApplicationContextSingletonRule(appContext, /* restoreAfter= */ false);
    }
}
