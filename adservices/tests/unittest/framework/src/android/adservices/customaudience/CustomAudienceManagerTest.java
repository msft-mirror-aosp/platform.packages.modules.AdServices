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

package android.adservices.customaudience;

import static com.google.common.truth.Truth.assertThat;

import android.os.Build;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;

import org.junit.Test;

/** Unit tests for {@link CustomAudienceManager} */
@RequiresSdkLevelAtLeastS
public class CustomAudienceManagerTest extends AdServicesUnitTestCase {
    @Test
    @RequiresSdkLevelAtLeastT
    public void testCustomAudienceManagerCtor_TPlus() {
        assertThat(CustomAudienceManager.get(mContext)).isNotNull();
        assertThat(mContext.getSystemService(CustomAudienceManager.class)).isNotNull();
    }

    @Test
    @RequiresSdkRange(atMost = Build.VERSION_CODES.S_V2)
    public void testCustomAudienceManagerCtor_SMinus() {
        assertThat(CustomAudienceManager.get(mContext)).isNotNull();
        assertThat(mContext.getSystemService(CustomAudienceManager.class)).isNull();
    }
}
