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

package com.android.adservices.shared.testing;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.List;

/**
 * Generic logging usage rule that scans for supported logging calls and ensures those calls have
 * been verified through annotations. This class should not be dependent on any module specific
 * (e.g. AdServices) code.
 *
 * <p>IMPORTANT: The actual mocking of the log calls will be handled by the rule. DO NOT mock the
 * log call behavior in the test itself as it will likely override the required mocking behavior set
 * by the rule.
 */
// TODO (b/339709062): Consider extending AbstractRethrowerRule.
public abstract class AbstractLoggingUsageRule extends AbstractRule {

    public AbstractLoggingUsageRule() {
        super(AndroidLogger.getInstance());
    }

    /** Returns a list of {@link LogVerifier} objects for rule orchestration. */
    protected abstract List<LogVerifier> getLogVerifiers();

    @Override
    protected void evaluate(Statement base, Description description) throws Throwable {
        // Rule can only be applied to individual tests.
        TestHelper.throwIfNotTest(description);

        // Fetch log verifiers based on enabled log types for the rule.
        List<LogVerifier> logVerifiers = getLogVerifiers();

        // Setup work to scan expected calls and track actual calls only if the logging usage rule
        // is active for the test.
        SkipLoggingUsageRule annotation =
                TestHelper.getAnnotationFromAnywhere(description, SkipLoggingUsageRule.class);
        boolean shouldUseVerifiers = annotation == null;
        if (shouldUseVerifiers) {
            logVerifiers.forEach(LogVerifier::setup);
        }

        // Execute test
        try {
            base.evaluate();
        } catch (Throwable t) {
            mLog.v("Base evaluated, exception thrown. Skipping log verification.");
            // Skip log verification and rethrow the exception in case the issue caused by
            // the rule itself e.g. capturing and casting arguments from log calls.
            throw t;
        }

        // Skip verification of log usage if appropriate annotation is detected.
        if (!shouldUseVerifiers) {
            mLog.v("Skipping log usage rule verification, reason: %s", annotation.reason());
            return;
        }

        // Ensure all log calls have been verified. Fail fast if any of the log verifiers
        // result in an error.
        for (LogVerifier logVerifier : logVerifiers) {
            logVerifier.verify(description);
        }
    }
}
