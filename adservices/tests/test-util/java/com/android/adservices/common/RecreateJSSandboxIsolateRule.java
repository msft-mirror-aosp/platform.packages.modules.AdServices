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

package com.android.adservices.common;

import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.shared.testing.AbstractRule;
import com.android.adservices.shared.testing.AndroidLogger;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Rule used to recreate the JavaScript sandbox isolate before each test case. */
public final class RecreateJSSandboxIsolateRule extends AbstractRule {
    private static final long JS_SANDBOX_TIMEOUT_MS = 5_000L;

    // TODO (b/370750734): Add unit tests verifying test rule behavior.
    public RecreateJSSandboxIsolateRule() {
        super(AndroidLogger.getInstance());
    }

    @Override
    protected void evaluate(Statement base, Description description) throws Throwable {
        try {
            mLog.v("%s: Shutting down JavaScript sandbox isolate before test.", getTestName());
            shutdownJSSandboxIsolate();
            if (JSScriptEngine.hasActiveInstance()) {
                throw new IllegalStateException(
                        getTestName()
                                + ": Failed to shut down JavaScript sandbox isolate before test.");
            }

            mLog.v("%s: Recreating JavaScript sandbox isolate before test.", getTestName());
            JSScriptEngine.getInstance();

            base.evaluate();
        } finally {
            mLog.v("%s: Shutting down JavaScript sandbox isolate after test.", getTestName());
            shutdownJSSandboxIsolate();
        }
    }

    private void shutdownJSSandboxIsolate()
            throws ExecutionException, InterruptedException, TimeoutException {
        JSScriptEngine.getInstance().shutdown().get(JS_SANDBOX_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }
}
