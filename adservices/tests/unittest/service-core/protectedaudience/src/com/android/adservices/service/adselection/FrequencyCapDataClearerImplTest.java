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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.data.adselection.FrequencyCapDao;

import org.junit.Test;
import org.mockito.Mock;

public final class FrequencyCapDataClearerImplTest extends AdServicesMockitoTestCase {

    @Mock private FrequencyCapDao mFrequencyCapDao;

    @Test
    public void testClear_happyPath() {
        int expectedNumEventsCleared = 5;
        doReturn(expectedNumEventsCleared).when(mFrequencyCapDao).deleteAllHistogramData();
        FrequencyCapDataClearerImpl frequencyCapDataClearer =
                new FrequencyCapDataClearerImpl(mFrequencyCapDao);

        int actualNumEventsCleared = frequencyCapDataClearer.clear();

        assertThat(actualNumEventsCleared).isEqualTo(expectedNumEventsCleared);
    }
}
