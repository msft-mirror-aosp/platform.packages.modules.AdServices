/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import java.util.Collection;
import java.util.List;

public final class ValidatorTestUtil {
    public static void assertViolationContainsOnly(
            Collection<String> errors, String... expectedErrors) {
        assertWithMessage("errors").that(errors).containsExactlyElementsIn(expectedErrors);
    }

    public static void assertValidationFailuresMatch(
            IllegalArgumentException actualException,
            String expectedViolationsPrefix,
            List<String> expectedViolations) {
        assertThat(actualException).hasMessageThat().startsWith(expectedViolationsPrefix);
        for (String violation : expectedViolations) {
            assertThat(actualException).hasMessageThat().contains(violation);
        }
    }
}
