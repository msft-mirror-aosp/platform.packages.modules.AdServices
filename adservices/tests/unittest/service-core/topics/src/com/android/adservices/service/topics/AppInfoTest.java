/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adservices.service.topics;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** Unit tests for {@link AppInfo} */
public final class AppInfoTest extends AdServicesUnitTestCase {
    private static final String APP_NAME = "appName";
    private static final String APP_DESCRIPTION = "appDescription";

    @Test
    public void testCreation() throws Exception {
        AppInfo appInfo = new AppInfo(APP_NAME, APP_DESCRIPTION);
        expect.withMessage("AppName").that(appInfo.getAppName()).isEqualTo(APP_NAME);
        expect.withMessage("AppDescription")
                .that(appInfo.getAppDescription())
                .isEqualTo(APP_DESCRIPTION);
    }

    @Test
    public void testNullInput() {
        assertThrows(
                NullPointerException.class,
                () -> new AppInfo(/* appName= */ null, APP_DESCRIPTION));

        assertThrows(
                NullPointerException.class,
                () -> new AppInfo(APP_NAME, /* appDescription= */ null));

        assertThrows(
                NullPointerException.class,
                () -> new AppInfo(/* appName= */ null, /* appDescription= */ null));
    }
}
