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
package com.android.adservices.common;

import android.util.Log;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;

import org.junit.AfterClass;
import org.junit.Rule;
import org.mockito.Mockito;

/**
 * Base class for all unit tests that use {@code ExtendedMockito} - for "regular Mockito" use {@link
 * AdServicesMockitoTestCase} instead).
 *
 * <p><b>NOTE:</b> subclasses MUST use
 * {@link com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic} and/or
 * (@link com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic} to set which static
 * classes are mocked ad/or spied.
 */
public abstract class AdServicesExtendedMockitoTestCase extends AdServicesUnitTestCase {

    private static final String TAG = AdServicesExtendedMockitoTestCase.class.getSimpleName();

    @Rule(order = 10)
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this)
                    // TODO(b/315196012): use annotations instead
                    .dontClearInlineMocks()
                    .build();

    /** Clears inline mocks to avoid memory leaks. */
    @AfterClass
    public static void clearInlineMethods() {
        // TODO(b/315196012): need to figure out which class it is (might need a custom @ClassRule
        Log.i(TAG, "Calling Mockito.framework().clearInlineMocks() @AfterClass of");
        Mockito.framework().clearInlineMocks();
    }
}
