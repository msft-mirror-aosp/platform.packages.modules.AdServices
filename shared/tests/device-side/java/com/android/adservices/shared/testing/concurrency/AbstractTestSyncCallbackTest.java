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
package com.android.adservices.shared.testing.concurrency;

import static com.android.adservices.shared.concurrency.AbstractSyncCallback.LOG_TAG;

import android.util.Log;

import com.android.adservices.mockito.LogInterceptor;
import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.adservices.shared.testing.LogEntry.Level;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

public final class AbstractTestSyncCallbackTest extends SharedExtendedMockitoTestCase {

    @Test
    @SpyStatic(Log.class)
    public void testLogV() {
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();
        String tag = LOG_TAG;
        LogInterceptor logInterceptor = mocker.interceptLogV(tag);

        callback.logV("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.v() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.VERBOSE))
                .containsExactly(callback + ": Answer=42");
    }

    @Test
    @SpyStatic(Log.class)
    public void testLogD() {
        ConcreteTestSyncCallback callback = new ConcreteTestSyncCallback();
        String tag = LOG_TAG;
        LogInterceptor logInterceptor = mocker.interceptLogD(tag);

        callback.logD("Answer=%d", 42);

        expect.withMessage("Log.*() calls to tag %s", tag)
                .that(logInterceptor.getAllEntries(tag))
                .hasSize(1);
        expect.withMessage("Log.d() calls to tag %s", tag)
                .that(logInterceptor.getPlainMessages(tag, Level.DEBUG))
                .containsExactly("[" + callback.getId() + "]: Answer=42");
    }

    private static final class ConcreteTestSyncCallback extends AbstractTestSyncCallback {}
}
