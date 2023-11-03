/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.adservices.service.common;

import static com.android.adservices.mockito.MockitoExpectations.setApplicationContextSingleton;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;

import com.android.adservices.shared.common.ApplicationContextSingleton;

import org.junit.Test;

public final class AdServicesInternalProviderTest {

    private final AdServicesInternalProvider mProvider = new AdServicesInternalProvider();

    @Test
    public void testDump_appContextSingletonNotSet() throws Exception {
        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()")
                .that(dump)
                .contains(ApplicationContextSingleton.ERROR_MESSAGE_SET_NOT_CALLED);
    }

    @Test
    public void testDump_appContextSingletonSet() throws Exception {
        Context appContext = setApplicationContextSingleton();

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()")
                .that(dump)
                .contains("ApplicationContextSingleton: " + appContext);
    }
}
