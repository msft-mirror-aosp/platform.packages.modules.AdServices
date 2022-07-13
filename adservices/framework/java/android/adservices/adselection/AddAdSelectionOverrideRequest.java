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

package android.adservices.adselection;

import android.annotation.NonNull;
import android.os.OutcomeReceiver;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * This POJO represents the {@link
 * AdSelectionManager#overrideAdSelectionConfigRemoteInfo(AddAdSelectionOverrideRequest, Executor,
 * OutcomeReceiver)} request
 *
 * <p>It contains two fields, a {@link AdSelectionConfig} which will serve as the identifier for the
 * specific override, and a {@code String} decisionLogicJs field representing the override value
 */
public class AddAdSelectionOverrideRequest {
    @NonNull private final AdSelectionConfig mAdSelectionConfig;

    @NonNull private final String mDecisionLogicJs;

    /** Builds a {@link AddAdSelectionOverrideRequest} instance. */
    public AddAdSelectionOverrideRequest(
            @NonNull AdSelectionConfig adSelectionConfig, @NonNull String decisionLogicJs) {
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(decisionLogicJs);

        mAdSelectionConfig = adSelectionConfig;
        mDecisionLogicJs = decisionLogicJs;
    }

    /**
     * @return AdSelectionConfig, the configuration of the ad selection process.
     */
    @NonNull
    public AdSelectionConfig getAdSelectionConfig() {
        return mAdSelectionConfig;
    }

    /**
     * @return The override javascript result
     */
    @NonNull
    public String getDecisionLogicJs() {
        return mDecisionLogicJs;
    }
}
