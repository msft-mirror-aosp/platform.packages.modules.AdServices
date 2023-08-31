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

package com.android.adservices.service.adselection;


import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

/**
 * Event-level debug reporting for ad selection.
 *
 * <p>Protected Audience debug reporting allows ad tech developers to declare remote URLs to receive
 * a GET request from devices when an auction is won / lost. This allows the following use-cases:
 *
 * <ul>
 *   <li>See if auctions are being won / lost
 *   <li>Understand why auctions are being lost (e.g. understand if it’s an issue with a bidding /
 *       scoring script implementation or a core logic issue)
 *   <li>Monitor roll-outs of new JavaScript logic to clients
 * </ul>
 *
 * <p>Debug reporting consists of two JS APIs available for usage, both of which take a URL string:
 * <li>forDebuggingOnly.reportAdAuctionWin(String url)
 * <li>forDebuggingOnly.reportAdAuctionWin(String url)
 *
 *     <p>For the classes that wrap JavaScript code, see {@link DebugReportingScriptStrategy}.
 *     <p>For the classes that send events, see {@link DebugReportSenderStrategy}.
 *     <p>For the business logic processing events, see {@link DebugReportProcessor}.
 */
public class DebugReporting {

    private final AdServicesHttpsClient mAdServicesHttpsClient;
    private final boolean mEnabled;
    private final DevContext mDevContext;

    public DebugReporting(
            Flags flags, AdServicesHttpsClient adServicesHttpsClient, DevContext devContext) {
        mAdServicesHttpsClient = adServicesHttpsClient;
        mEnabled = getEnablementStatus(flags);
        mDevContext = devContext;
    }

    public DebugReportingScriptStrategy getScriptStrategy() {
        return mEnabled
                ? new DebugReportingEnabledScriptStrategy()
                : new DebugReportingScriptDisabledStrategy();
    }

    public DebugReportSenderStrategy getSenderStrategy() {
        return mEnabled
                ? new DebugReportSenderStrategyHttpImpl(mAdServicesHttpsClient, mDevContext)
                : new DebugReportSenderStrategyNoOp();
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    private static boolean getEnablementStatus(Flags flags) {
        return flags.getFledgeEventLevelDebugReportingEnabled();
    }

}
