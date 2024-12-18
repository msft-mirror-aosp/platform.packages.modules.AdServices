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

package android.adservices.test.scenario.adservices.iapc;

import static com.android.adservices.service.FlagsConstants.KEY_AD_ID_CACHE_ENABLED;

import android.platform.test.scenario.annotation.Scenario;

import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;

import org.junit.Test;

/** The test class to measure the start-up latency for Ad ID API, with disabling Ad ID cache. */
@Scenario
@RequiresSdkLevelAtLeastS(reason = "AdServices is only available on S+.")
public class GetAdIdApiCallCacheDisabled extends GetAdIdApiCallBase {
    @Test
    @SetFlagDisabled(KEY_AD_ID_CACHE_ENABLED)
    public void testGetAdId() throws Exception {
        measureGetAdIdCall();
    }
}
