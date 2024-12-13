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

import com.android.adservices.shared.testing.AbstractRethrowerRule;
import com.android.adservices.shared.testing.Logger.RealLogger;
import com.android.adservices.shared.testing.NameValuePair;
import com.android.adservices.shared.testing.NameValuePair.Matcher;
import com.android.adservices.shared.testing.Nullable;
import com.android.adservices.shared.testing.SystemPropertiesHelper;
import com.android.adservices.shared.testing.TestFailure;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// TODO(b/328064701): add unit tests
/** Rule used to dump some system properties when a test fails. */
abstract class AbstractSystemPropertiesDumperRule extends AbstractRethrowerRule {

    // TODO(b/328064701): support multiple prefixes
    private final String mPrefix;
    private final SystemPropertiesHelper mHelper;
    private final List<NameValuePair> mPreTestSystemProperties = new ArrayList<>();
    private final List<NameValuePair> mOnTestFailureSystemProperties = new ArrayList<>();
    private final Matcher mMatcher;

    protected AbstractSystemPropertiesDumperRule(
            RealLogger logger, String prefix, SystemPropertiesHelper.Interface helper) {
        super(logger);
        mPrefix = Objects.requireNonNull(prefix, "prefix cannot be null");
        mMatcher = (prop) -> prop.name.startsWith(prefix);
        mHelper = new SystemPropertiesHelper(helper, logger);
        mLog.v("Constructor: mPrefix=%s, mHelper=%s", mPrefix, mHelper);
    }

    @Override
    protected void preTest(Statement base, Description description, List<Throwable> cleanUpErrors) {
        runSafely(cleanUpErrors, () -> mPreTestSystemProperties.addAll(mHelper.getAll(mMatcher)));
    }

    @Override
    protected void onTestFailure(
            Statement base,
            Description description,
            List<Throwable> cleanUpErrors,
            Throwable testFailure) {
        runSafely(
                cleanUpErrors,
                () -> mOnTestFailureSystemProperties.addAll(mHelper.getAll(mMatcher)));
    }

    @Override
    protected void throwTestFailure(Throwable testError, List<Throwable> cleanUpErrors)
            throws Throwable {
        StringBuilder dump = new StringBuilder("*** System properties before / after test ***\n");
        logAndDumpAllProperties(dump);
        TestFailure.throwTestFailure(testError, dump.toString());
    }

    private void logAndDumpAllProperties(StringBuilder dump) {
        // TODO(b/328682831): save on disk as well (maybe even called from superclass)
        logAndDump(dump, "before test", mPreTestSystemProperties);
        logAndDump(dump, "on test failure", mOnTestFailureSystemProperties);
    }

    private void logAndDump(StringBuilder dump, String when, List<NameValuePair> values) {
        if (values.isEmpty()) {
            logAndDump(dump, "No SystemProperties %s", when);
            return;
        }
        logAndDump(dump, "%d system properties %s:", values.size(), when);
        values.forEach(value -> logAndDump(dump, "\t%s", value));
    }

    @FormatMethod
    private void logAndDump(
            StringBuilder dump, @FormatString String msgFmt, @Nullable Object... args) {
        String msg = String.format(msgFmt, args);
        mLog.e("%s", msg);
        dump.append(msg).append('\n');
    }
}
