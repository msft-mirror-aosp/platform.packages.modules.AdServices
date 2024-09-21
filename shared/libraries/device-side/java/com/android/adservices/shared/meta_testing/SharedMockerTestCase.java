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

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;

import com.android.adservices.mockito.SharedMocker;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.testing.DeviceSideTestCase;

import org.junit.Before;
import org.junit.Test;

/**
 * Base class for all {@link SharedMocker} implementations.
 *
 * @param <T> mocker implementation
 */
public abstract class SharedMockerTestCase<T extends SharedMocker> extends DeviceSideTestCase {

    protected abstract T getMocker();

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker()).isNotNull();
    }

    // TODO(b/285300419): use ApplicationContextSingleton rule / helper instead?
    @SuppressWarnings("VisibleForTests")
    @Test
    public final void testSetApplicationContextSingleton() {
        Context contextBefore = ApplicationContextSingleton.getForTests();
        try {
            Context context = getMocker().setApplicationContextSingleton();

            assertWithMessage("context").that(context).isNotNull();
            expect.withMessage("context").that(context).isNotSameInstanceAs(contextBefore);
            expect.withMessage("context.getApplicationContext()")
                    .that(context.getApplicationContext())
                    .isSameInstanceAs(context);
            expect.withMessage("ApplicationContextSingleton.getForTests()")
                    .that(ApplicationContextSingleton.get())
                    .isSameInstanceAs(context);
        } finally {
            ApplicationContextSingleton.setForTests(contextBefore);
        }
    }
}
