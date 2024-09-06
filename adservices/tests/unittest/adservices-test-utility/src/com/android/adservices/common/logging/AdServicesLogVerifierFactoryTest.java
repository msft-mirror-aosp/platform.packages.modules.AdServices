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

package com.android.adservices.common.logging;

import static com.android.adservices.common.logging.AdServicesLogVerifierFactory.LogType.ERROR_LOG_UTIL;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.LogVerifier;

import org.junit.Test;

import java.util.List;

public final class AdServicesLogVerifierFactoryTest extends AdServicesUnitTestCase {
    @Test
    public void testCreate_withErrorLogUtilEnum_returnsErrorLogUtilVerifier() {
        List<LogVerifier> verifiers = AdServicesLogVerifierFactory.create(ERROR_LOG_UTIL);

        assertThat(verifiers).hasSize(2);
        expect.that(verifiers.get(0).getClass()).isEqualTo(AdServicesErrorLogUtilVerifier.class);
        expect.that(verifiers.get(1).getClass())
                .isEqualTo(AdServicesErrorLogUtilWithExceptionVerifier.class);
    }

    @Test
    public void testCreate_withNullEnum_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> AdServicesLogVerifierFactory.create(null));
    }
}
