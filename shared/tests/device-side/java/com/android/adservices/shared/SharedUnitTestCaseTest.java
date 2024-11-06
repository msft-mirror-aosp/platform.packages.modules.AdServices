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

import static com.google.common.truth.Truth.assertWithMessage;

import android.platform.test.ravenwood.RavenwoodRule;

import org.junit.Test;

public final class SharedUnitTestCaseTest extends SharedUnitTestCase {

    @Test
    public void testName() {
        assertWithMessage("name").that(name).isNotNull();
    }

    @Test
    public void testAppContext() {
        assertWithMessage("appContext").that(appContext).isNotNull();

        // TODO(b/355286824): remove once Ravenwood supports it
        if (RavenwoodRule.isOnRavenwood()) {
            expect.withMessage("appContext.get()").that(appContext.get()).isNotNull();
            expect.withMessage("appContext.get()")
                    .that(appContext.get())
                    .isSameInstanceAs(mContext);
            return;
        }

        expect.withMessage("appContext.get()")
                .that(appContext.get())
                .isSameInstanceAs(mContext.getApplicationContext());
    }
}
