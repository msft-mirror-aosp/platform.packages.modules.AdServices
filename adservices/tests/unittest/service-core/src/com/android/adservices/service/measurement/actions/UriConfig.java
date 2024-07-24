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

package com.android.adservices.service.measurement.actions;

import com.android.adservices.service.measurement.E2EAbstractTest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/** Config used to specify configuration for URLs in registration list. */
public class UriConfig {
    private final boolean mShouldEnroll;
    private final List<int[]> mFakeReportConfigs;
    private final List<Long> mNullAggregatableReportsDays;

    public UriConfig(JSONObject configObj) throws JSONException {
        mShouldEnroll = configObj.optBoolean(E2EAbstractTest.TestFormatJsonMapping.ENROLL, true);
        mFakeReportConfigs = E2EAbstractTest.getFakeReportConfigs(configObj);
        mNullAggregatableReportsDays = E2EAbstractTest.getNullAggregatableReportsDays(configObj);
    }

    /** Should the URL be enrolled before the request. */
    public boolean shouldEnroll() {
        return mShouldEnroll;
    }

    /** Returns noised report configs. */
    public List<int[]> getFakeReportConfigs() {
        return mFakeReportConfigs;
    }

    /** Returns null aggregatable reports days. */
    public List<Long> getNullAggregatableReportsDays() {
        return mNullAggregatableReportsDays;
    }
}
