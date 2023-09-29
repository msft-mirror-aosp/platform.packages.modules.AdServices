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

package com.android.adservices.service.common.compat;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockIsAtLeastT;

import static com.google.common.truth.Truth.assertThat;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Rule;
import org.junit.Test;

public final class FileCompatUtilsTest {
    private static final String BASE_FILENAME = "filename.xml";
    private static final String FILENAME_STARTS_WITH_ADSERVICES = "ADSERVICES_filename.xml";
    private static final String ANOTHER_FILENAME_STARTS_WITH_ADSERVICES = "adservicesFilename.xml";
    private static final String ADSERVICES_PREFIX = "adservices_";

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this).mockStatic(SdkLevel.class).build();

    @Test
    public void testShouldPrependAdservices_SMinus() {
        mockIsAtLeastT(false);

        assertThat(FileCompatUtils.getAdservicesFilename(BASE_FILENAME))
                .isEqualTo(ADSERVICES_PREFIX + BASE_FILENAME);
    }

    @Test
    public void testShouldNotPrependAdservicesIfNameStartsWithAdservices_Sminus() {
        mockIsAtLeastT(false);
        assertThat(FileCompatUtils.getAdservicesFilename(FILENAME_STARTS_WITH_ADSERVICES))
                .isEqualTo(FILENAME_STARTS_WITH_ADSERVICES);
        assertThat(FileCompatUtils.getAdservicesFilename(ANOTHER_FILENAME_STARTS_WITH_ADSERVICES))
                .isEqualTo(ANOTHER_FILENAME_STARTS_WITH_ADSERVICES);
    }

    @Test
    public void testShouldNotPrependAdservices_TPlus() {
        mockIsAtLeastT(true);

        assertThat(FileCompatUtils.getAdservicesFilename(BASE_FILENAME)).isEqualTo(BASE_FILENAME);
    }
}
