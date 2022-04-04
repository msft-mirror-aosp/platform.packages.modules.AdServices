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
package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.AdServicesApiUtil;
import android.adservices.AdServicesVersion;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * CTS test for API provided by AdServicesVersion.
 */
@RunWith(AndroidJUnit4.class)
public class AdServicesJUnit4DeviceTest {
    @Test
    public void testApiVersion() {
        assertThat(AdServicesVersion.API_VERSION).isAtLeast(1);
    }

    @Test
    public void testAdServicesApiState() {
        assertThat(AdServicesApiUtil.getAdServicesApiState())
            .isEqualTo(AdServicesApiUtil.ADSERVICES_API_STATE_ENABLED);
    }
}

