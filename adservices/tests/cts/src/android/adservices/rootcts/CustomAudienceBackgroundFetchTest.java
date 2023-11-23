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

package android.adservices.rootcts;

import static com.android.adservices.spe.AdservicesJobInfo.FLEDGE_BACKGROUND_FETCH_JOB;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.utils.FledgeScenarioTest;
import android.adservices.utils.ScenarioDispatcher;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class CustomAudienceBackgroundFetchTest extends FledgeScenarioTest {

    private BackgroundJobHelper mBackgroundJobHelper;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mBackgroundJobHelper = new BackgroundJobHelper(sContext);
    }

    /**
     * Test to ensure that trusted signals are updated (including ads list) during the daily update.
     */
    @Test
    public void testAdSelection_withInvalidFields_backgroundJobUpdatesSuccessfully()
            throws Exception {
        ScenarioDispatcher dispatcher =
                ScenarioDispatcher.fromScenario(
                        "scenarios/remarketing-cuj-020.json", getCacheBusterPrefix());
        setupDefaultMockWebServer(dispatcher);
        AdSelectionConfig adSelectionConfig = makeAdSelectionConfig();
        String customAudienceName = "shoes";

        try {
            joinCustomAudience(makeCustomAudience(customAudienceName).setAds(List.of()).build());
            assertThrows(ExecutionException.class, () -> doSelectAds(adSelectionConfig));
            mBackgroundJobHelper.runJob(FLEDGE_BACKGROUND_FETCH_JOB.getJobId());
            assertThat(doSelectAds(adSelectionConfig).hasOutcome()).isTrue();
        } finally {
            leaveCustomAudience(customAudienceName);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
    }
}
