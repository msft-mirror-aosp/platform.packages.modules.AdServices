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

package com.android.adservices.service.adselection.debug;

import android.os.Build;

import androidx.annotation.RequiresApi;

/** Class to provide implementation when Debug reporting is disabled for on device auctions. */
@RequiresApi(Build.VERSION_CODES.S)
public final class DebugReportingDisabled extends DebugReporting {

    @Override
    public DebugReportingScriptStrategy getScriptStrategy() {
        return new DebugReportingScriptDisabledStrategy();
    }

    @Override
    public DebugReportSenderStrategy getSenderStrategy() {
        return new DebugReportSenderStrategyNoOp();
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}
