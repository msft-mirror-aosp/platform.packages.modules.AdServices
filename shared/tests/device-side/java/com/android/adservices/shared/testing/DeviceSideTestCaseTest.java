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

import static android.platform.test.ravenwood.RavenwoodRule.isOnRavenwood;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

public final class DeviceSideTestCaseTest extends DeviceSideTestCase {

    @Test
    public void testStaticContext() {
        if (isOnRavenwood()) {
            expect.withMessage("sContext")
                    .that(sContext)
                    // Cannot call InstrumentationRegistry... again as it would return another
                    // context
                    .isNotNull();
            return;
        }
        Context expectedContext = InstrumentationRegistry.getInstrumentation().getContext();
        expect.withMessage("sContext").that(sContext).isSameInstanceAs(expectedContext);
    }

    @Test
    public void testStaticPackageName() {
        expect.withMessage("sPackageName").that(sPackageName).isEqualTo(getExpectedPackageName());
    }

    @Test
    public void testStaticTargetContext() {
        if (isOnRavenwood()) {
            expect.withMessage("sTargetContext")
                    .that(sTargetContext)
                    // Cannot call InstrumentationRegistry... again as it would return another
                    // context
                    .isNotNull();
            return;
        }
        Context expectedContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        expect.withMessage("sTargetContext").that(sTargetContext).isSameInstanceAs(expectedContext);
    }

    @Test
    public void testStaticTargetPackageName() {
        expect.withMessage("sTargetPackageName")
                .that(sTargetPackageName)
                .isEqualTo(getExpectedPackageName());
    }

    @Test
    public void testContext() {
        Context expectedContext =
                isOnRavenwood()
                        ? mContext
                        : InstrumentationRegistry.getInstrumentation().getContext();
        expect.withMessage("mContext").that(mContext).isSameInstanceAs(expectedContext);
    }

    @Test
    public void testPackageName() {
        expect.withMessage("mPackageName").that(mPackageName).isEqualTo(getExpectedPackageName());
    }

    @Test
    public void testTargetContext() {
        Context expectedContext =
                isOnRavenwood()
                        ? mTargetContext
                        : InstrumentationRegistry.getInstrumentation().getTargetContext();
        expect.withMessage("mTargetContext").that(mTargetContext).isSameInstanceAs(expectedContext);
    }

    @Test
    public void testTargetPackageName() {
        expect.withMessage("mTargetPackageName")
                .that(mTargetPackageName)
                .isEqualTo(getExpectedPackageName());
    }

    @Test
    public void testTag() {
        expect.withMessage("mTag").that(mTag).isEqualTo("DeviceSideTestCaseTest");
    }

    private String getExpectedPackageName() {
        return isOnRavenwood() ? RAVENWOOD_PACKAGE_NAME : "com.android.adservices.shared.tests";
    }
}
