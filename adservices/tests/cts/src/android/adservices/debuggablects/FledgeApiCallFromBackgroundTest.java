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

package android.adservices.debuggablects;

import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;

import static com.android.adservices.service.FlagsConstants.KEY_FOREGROUND_STATUS_LEVEL;

import android.adservices.utils.FledgeScenarioTest;
import android.adservices.utils.ScenarioDispatcher;

import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

import com.google.common.truth.Truth;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.ExecutionException;

@SetIntegerFlag(name = KEY_FOREGROUND_STATUS_LEVEL, value = IMPORTANCE_FOREGROUND)
@RequiresSdkLevelAtLeastT(reason = "No foreground check in S-")
public class FledgeApiCallFromBackgroundTest extends FledgeScenarioTest {
    private ScenarioDispatcher mDispatcher;

    @Before
    public void setup() throws Exception {
        mDispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-default.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(mDispatcher);
    }

    @After
    public void teardown() throws Exception {
        Truth.assertThat(mDispatcher.getCalledPaths()).isEmpty();
    }

    /** CUJ 054: Calling select ads API from background will fail. */
    @Test
    public void testRunAdSelectionFromBackground() {
        Exception e =
                Assert.assertThrows(
                        ExecutionException.class, () -> doSelectAds(makeAdSelectionConfig()));
        Assert.assertTrue(e.getCause() instanceof IllegalStateException);
        Assert.assertEquals(
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE, e.getCause().getMessage());
    }
}
