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

import android.content.Context;
import android.platform.test.annotations.DisabledOnRavenwood;

import org.junit.Test;

public final class SharedUnitTestCaseTest extends SharedUnitTestCase {

    @Test
    public void testName() {
        assertWithMessage("name").that(name).isNotNull();
    }

    // TODO(b/335935200): RavenwoodBaseContext.getApplicationContext() not supported
    @DisabledOnRavenwood(blockedBy = Context.class)
    @Test
    public void testAppContext() {
        assertWithMessage("appContext").that(appContext).isNotNull();

        expect.withMessage("appContext.get()")
                .that(appContext.get())
                .isSameInstanceAs(mContext.getApplicationContext());
    }
}
