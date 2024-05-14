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
package com.android.adservices.mockito;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.Context;

import com.android.adservices.shared.SharedUnitTestCase;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import org.junit.Test;

// NOTE: not extending SharedMockitoTestCase on purpose, so it has full control of mockito state
public final class SharedMockitoMockerTest extends SharedUnitTestCase {

    private final SharedMockitoMocker mMocker = new SharedMockitoMocker();

    @Test
    public void testSetApplicationContextSingleton() {
        Context contextBefore = ApplicationContextSingleton.getForTests();
        try {
            Context context = mMocker.setApplicationContextSingleton();

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
