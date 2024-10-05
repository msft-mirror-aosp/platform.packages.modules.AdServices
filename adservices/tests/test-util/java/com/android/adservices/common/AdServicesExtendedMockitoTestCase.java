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

import com.android.adservices.common.AdServicesExtendedMockitoTestCase.Mocker;
import com.android.adservices.common.logging.AdServicesLoggingUsageRule;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.StaticClassChecker;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;

import com.google.common.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Base class for all unit tests that use {@code ExtendedMockito} - for "regular Mockito" use {@link
 * AdServicesMockitoTestCase} instead).
 *
 * <p><b>NOTE:</b> subclasses MUST use {@link
 * com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic} and/or {@link
 * com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic} to set which static classes are
 * mocked ad/or spied.
 *
 * <p><b>NOTE:</b>if the subclass wants to provide its own {@code mocker}, it should extend {@link
 * AdServicesMockerLessExtendedMockitoTestCase} instead - see {@link AdServicesJobServiceTestCase}
 * as an example.
 *
 * <p>TODO(b/355699778) - Add linter to ensure ErrorLogUtil invocation is not mocked in subclasses.
 * {@link ErrorLogUtil} is automatically spied for all subclasses to ensure {@link
 * AdServicesLoggingUsageRule} is enforced for logging verification. Subclasses should not mock
 * {@link ErrorLogUtil} to avoid interference with mocking behavior needed for the rule.
 */
public abstract class AdServicesExtendedMockitoTestCase
        extends AdServicesMockerLessExtendedMockitoTestCase<Mocker> {

    @Override
    protected final Mocker newMocker(
            AdServicesExtendedMockitoRule rule, Flags mockFlags, DebugFlags mockDebugFlags) {
        return new Mocker(rule, mockFlags, mockDebugFlags);
    }

    public static final class Mocker
            extends AdServicesMockerLessExtendedMockitoTestCase.InternalMocker {
        private Mocker(StaticClassChecker checker, Flags flags, DebugFlags mockDebugFlags) {
            super(checker, flags, mockDebugFlags);
        }

        // Factory methods below are used by AdServicesExtendedMockitoTestCaseXYZMockerTest - there
        // is one method for each of these tests, so it creates a Mocker object that implements
        // the mockier interface being tested

        @VisibleForTesting
        static Mocker forSharedMockerTests() {
            return new Mocker(/* checker= */ null, /* flags= */ null, /* mockDebugFlags= */ null);
        }

        @VisibleForTesting
        static Mocker forAndroidMockerTests() {
            return new Mocker(/* checker= */ null, /* flags= */ null, /* mockDebugFlags= */ null);
        }

        @VisibleForTesting
        static Mocker forAndroidStaticMockerTests(StaticClassChecker checker) {
            return new Mocker(
                    Objects.requireNonNull(checker, "checker cannot be null"),
                    /* flags= */ null,
                    /* mockDebugFlags= */ null);
        }

        @VisibleForTesting
        static Mocker forAdServicesPragmaticMockerTests() {
            return new Mocker(/* checker= */ null, /* flags= */ null, /* mockDebugFlags= */ null);
        }

        @VisibleForTesting
        static Mocker forAdServicesFlagsMockerTests(Flags flags) {
            return new Mocker(
                    /* checker= */ null,
                    Objects.requireNonNull(flags, "flags cannot be null"),
                    /* mockDebugFlags= */ null);
        }

        @VisibleForTesting
        static Mocker forAdServicesDebugFlagsMockerTests(DebugFlags debugFlags) {
            return new Mocker(
                    /* checker= */ null,
                    /* flags= */ null,
                    Objects.requireNonNull(debugFlags, "DebugFlags cannot be null"));
        }

        @VisibleForTesting
        static Mocker forAdServicesStaticMockerTests(StaticClassChecker checker) {
            return new Mocker(
                    Objects.requireNonNull(checker, "checker cannot be null"),
                    /* flags= */ null,
                    /* mockDebugFlags= */ null);
        }
    }
}
