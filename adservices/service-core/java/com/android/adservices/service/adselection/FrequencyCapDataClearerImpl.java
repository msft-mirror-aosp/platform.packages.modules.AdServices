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

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.FrequencyCapDao;

class FrequencyCapDataClearerImpl implements FrequencyCapDataClearer {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final FrequencyCapDao mFrequencyCapDao;

    FrequencyCapDataClearerImpl(FrequencyCapDao frequencyCapDao) {
        mFrequencyCapDao = frequencyCapDao;
    }

    @Override
    public Integer clear() {
        sLogger.v("FrequencyCapDataClearerImpl.clear()");
        return mFrequencyCapDao.deleteAllHistogramData();
    }
}
