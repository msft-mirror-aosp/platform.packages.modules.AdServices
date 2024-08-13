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
package com.android.adservices.mockito;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;

/**
 * Provides Mockito expectation for common calls.
 *
 * <p><b>NOTE: </b> most expectations require {@code spyStatic()} or {@code mockStatic()} in the
 * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession session} ahead of time - this
 * helper doesn't check that such calls were made, it's up to the caller to do so.
 *
 * @deprecated - use {@code mocker} reference provided by test superclasses (or {@link
 *     AdServicesExtendedMockitoMocker} when they're not available).
 */
@Deprecated // TODO(b/314969513): remove when not used anymore
public final class ExtendedMockitoExpectations {

    private static final String TAG = ExtendedMockitoExpectations.class.getSimpleName();

    // NOTE: not really "Generated code", but we're using mocker (instead of sMocker or MOCKER) as
    // that's the name of the reference provided by the superclasses - once tests are refactored
    // to use the superclasses, they wouldn't need to change the variable name.

    // CHECKSTYLE:OFF Generated code
    public static final AdServicesExtendedMockitoTestCase.Mocker mocker =
            new AdServicesExtendedMockitoTestCase.Mocker(new StaticClassChecker() {});

    // CHECKSTYLE:ON

    private ExtendedMockitoExpectations() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
