/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 3.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-3.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.adservices.rootcts;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.utils.ScenarioDispatcher;
import android.adservices.utils.ScenarioDispatcherFactory;

import androidx.test.filters.FlakyTest;

import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

import org.junit.Test;

import java.util.concurrent.ExecutionException;

@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
public final class FledgeMaintenanceJobTest extends FledgeRootScenarioTest {
    private static final int FLEDGE_MAINTENANCE_JOB_ID = 1;
    private static final int FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S_OVERRIDE = 1;

    private final BackgroundJobHelper mBackgroundJobHelper = new BackgroundJobHelper(sContext);

    @Test
    @SetIntegerFlag(
            name = KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S,
            value = FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S_OVERRIDE)
    public void testAdSelection_afterExpirationWindow_adSelectionDataCleared() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-default.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            Thread.sleep(FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S_OVERRIDE * 3 * 1000);
            assertThat(mBackgroundJobHelper.runJob(FLEDGE_MAINTENANCE_JOB_ID)).isTrue();
            Exception exception =
                    assertThrows(
                            ExecutionException.class,
                            () -> doReportImpression(result.getAdSelectionId(), adSelectionConfig));
            assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }

    @Test
    @FlakyTest(bugId = 315327390)
    @SetIntegerFlag(
            name = KEY_FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S,
            value = FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S_OVERRIDE)
    @SetFlagEnabled(KEY_FLEDGE_REGISTER_AD_BEACON_ENABLED)
    public void testAdSelection_afterExpirationWindow_adInteractionsIsCleared() throws Exception {
        ScenarioDispatcher dispatcher =
                setupDispatcher(
                        ScenarioDispatcherFactory.createFromScenarioFileWithRandomPrefix(
                                "scenarios/remarketing-cuj-beacon-no-interactions.json"));
        AdSelectionConfig adSelectionConfig =
                makeAdSelectionConfig(dispatcher.getBaseAddressWithPrefix());

        try {
            joinCustomAudience(SHOES_CA);
            AdSelectionOutcome result = doSelectAds(adSelectionConfig);
            doReportImpression(result.getAdSelectionId(), adSelectionConfig);
            Thread.sleep(FLEDGE_AD_SELECTION_EXPIRATION_WINDOW_S_OVERRIDE * 3 * 1000);
            assertThat(mBackgroundJobHelper.runJob(FLEDGE_MAINTENANCE_JOB_ID)).isTrue();
            Exception exception =
                    assertThrows(
                            ExecutionException.class,
                            () -> doReportEvent(result.getAdSelectionId(), "click"));
            assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        } finally {
            leaveCustomAudience(SHOES_CA);
        }

        assertThat(dispatcher.getCalledPaths())
                .containsAtLeastElementsIn(dispatcher.getVerifyCalledPaths());
        assertThat(dispatcher.getCalledPaths())
                .containsNoneIn(dispatcher.getVerifyNotCalledPaths());
    }
}
