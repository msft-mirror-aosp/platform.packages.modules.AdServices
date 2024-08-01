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
package com.android.adservices.service.devapi;

import static com.google.common.truth.Truth.assertWithMessage;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class DevContextTest extends AdServicesUnitTestCase {

    @Test
    public void testBuilder() {
        DevContext.Builder builder = DevContext.builder();

        assertWithMessage("builder").that(builder).isNotNull();
    }

    @Test
    public void testCreateForDevOptionsDisabled() {
        DevContext devContext = DevContext.createForDevOptionsDisabled();

        assertWithMessage("devContext").that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isFalse();
        // TODO(b/356709022): assert it's not null instead (and/or is a default value / constant)
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isNull();
    }
}
