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

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class DevContextTest extends AdServicesUnitTestCase {

    private static final String PKG_NAME = "pkg.I.am";

    @Test
    public void testBuilder_legacy() {
        DevContext.Builder builder = DevContext.builder();
        assertWithMessage("builder").that(builder).isNotNull();

        DevContext devContext =
                builder.setCallingAppPackageName(PKG_NAME).setDevOptionsEnabled(true).build();

        assertWithMessage("builder.build()").that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isTrue();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(PKG_NAME);
    }

    @Test
    public void testBuilder_nullPackageName() {
        assertThrows(NullPointerException.class, () -> DevContext.builder(null));
    }

    @Test
    public void testBuilder() {
        DevContext.Builder builder = DevContext.builder(PKG_NAME);
        assertWithMessage("builder(%s)", PKG_NAME).that(builder).isNotNull();

        DevContext devContext = builder.setDevOptionsEnabled(true).build();

        assertWithMessage("builder.build()").that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isTrue();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo(PKG_NAME);
    }

    @Test
    public void testBuilder_multipleCallsToSetters() {
        DevContext.Builder builder = DevContext.builder(PKG_NAME);
        assertWithMessage("builder").that(builder).isNotNull();

        DevContext devContext =
                builder.setCallingAppPackageName(PKG_NAME + ".NOT")
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName("not.not." + PKG_NAME)
                        .setDevOptionsEnabled(false)
                        .build();

        assertWithMessage("builder.build()").that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isFalse();
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isEqualTo("not.not." + PKG_NAME);
    }

    @Test
    public void testBuilder_buildWithoutDevOptions() {
        DevContext.Builder builder = DevContext.builder(PKG_NAME);

        assertThrows(IllegalStateException.class, () -> builder.build());
    }

    @Test
    public void testCreateForDevOptionsDisabled() {
        DevContext devContext = DevContext.createForDevOptionsDisabled();

        assertWithMessage("devContext").that(devContext).isNotNull();
        expect.withMessage("devContext.getDevOptionsEnabled()")
                .that(devContext.getDevOptionsEnabled())
                .isFalse();
        // TODO(b/356709022): check it's not null
        expect.withMessage("devContext.getCallingAppPackageName()")
                .that(devContext.getCallingAppPackageName())
                .isNull();
    }
}
