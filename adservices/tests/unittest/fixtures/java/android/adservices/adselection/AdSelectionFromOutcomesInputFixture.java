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

import android.adservices.common.AdSelectionSignals;
import android.net.Uri;

import java.util.Collections;
import java.util.List;

public class AdSelectionFromOutcomesInputFixture {
    public static final AdSelectionOutcome SAMPLE_AD_OUTCOME =
            AdSelectionOutcomeFixture.anAdSelectionOutcome();
    public static final AdSelectionSignals SAMPLE_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{bidFloor: 10}");
    public static final Uri SAMPLE_SELECTION_LOGIC_URI =
            Uri.parse("https://my.uri.com/finalWinnerSelectionLogic");
    public static final String CALLER_PACKAGE_NAME = "com.android.myApp";

    public static AdSelectionFromOutcomesInput anAdSelectionFromOutcomesInput() {
        return new AdSelectionFromOutcomesInput.Builder()
                .setAdOutcomes(Collections.singletonList(SAMPLE_AD_OUTCOME))
                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                .setSelectionUri(SAMPLE_SELECTION_LOGIC_URI)
                .setCallerPackageName(CALLER_PACKAGE_NAME)
                .build();
    }

    public static AdSelectionFromOutcomesInput anAdSelectionFromOutcomesInput(
            final List<AdSelectionOutcome> adOutcomes) {
        return new AdSelectionFromOutcomesInput.Builder()
                .setAdOutcomes(adOutcomes)
                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                .setSelectionUri(SAMPLE_SELECTION_LOGIC_URI)
                .setCallerPackageName(CALLER_PACKAGE_NAME)
                .build();
    }

    public static AdSelectionFromOutcomesInput anAdSelectionFromOutcomesInput(final Uri uri) {
        return new AdSelectionFromOutcomesInput.Builder()
                .setAdOutcomes(Collections.singletonList(SAMPLE_AD_OUTCOME))
                .setSelectionSignals(SAMPLE_SELECTION_SIGNALS)
                .setSelectionUri(uri)
                .setCallerPackageName(CALLER_PACKAGE_NAME)
                .build();
    }
}
