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
package com.android.adservices.ui.settings.delegates;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__ACTION_UNSPECIFIED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__BLOCK_APP_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__BLOCK_TOPIC_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_APPS_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_TOPICS_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_APP_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_TOPIC_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__UNBLOCK_APP_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__UNBLOCK_TOPIC_SELECTED;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;

import android.app.Activity;
import android.util.Log;

import com.android.adservices.service.consent.DeviceRegionProvider;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.UIStats;
import com.android.adservices.ui.settings.activities.AdServicesBaseActivity;

/**
 * Base Delegate class that helps activities that extend {@link AdServicesBaseActivity} to respond
 * to all view model/user events. Currently supports:
 *
 * <ul>
 *   <li>common logging events
 * </ul>
 */
public abstract class BaseActionDelegate {
    private final int mDeviceLoggingRegion;

    protected enum ActionEnum {
        MANAGE_TOPICS_SELECTED,
        MANAGE_APPS_SELECTED,
        RESET_TOPIC_SELECTED,
        RESET_APP_SELECTED,
        BLOCK_TOPIC_SELECTED,
        UNBLOCK_TOPIC_SELECTED,
        BLOCK_APP_SELECTED,
        UNBLOCK_APP_SELECTED,
        MANAGE_MEASUREMENT_SELECTED,
        RESET_MEASUREMENT_SELECTED,
    }

    public BaseActionDelegate(Activity activity) {
        mDeviceLoggingRegion =
                DeviceRegionProvider.isEuDevice(activity)
                        ? AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__EU
                        : AD_SERVICES_SETTINGS_USAGE_REPORTED__REGION__ROW;
    }

    protected void logUIAction(ActionEnum action) {
        int rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__ACTION_UNSPECIFIED;
        switch (action) {
            case MANAGE_TOPICS_SELECTED:
                rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_TOPICS_SELECTED;
                break;
            case MANAGE_APPS_SELECTED:
                rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__MANAGE_APPS_SELECTED;
                break;
            case RESET_TOPIC_SELECTED:
                rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_TOPIC_SELECTED;
                break;
            case RESET_APP_SELECTED:
                rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__RESET_APP_SELECTED;
                break;
            case BLOCK_TOPIC_SELECTED:
                rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__BLOCK_TOPIC_SELECTED;
                break;
            case UNBLOCK_TOPIC_SELECTED:
                rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__UNBLOCK_TOPIC_SELECTED;
                break;
            case BLOCK_APP_SELECTED:
                rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__BLOCK_APP_SELECTED;
                break;
            case UNBLOCK_APP_SELECTED:
                rawAction = AD_SERVICES_SETTINGS_USAGE_REPORTED__ACTION__UNBLOCK_APP_SELECTED;
                break;
            default:
                Log.e("AdservicesUI", "Unknown Action for UI Logging");
        }
        logUIActionHelper(rawAction);
    }

    private void logUIActionHelper(int action) {
        UIStats uiStats =
                new UIStats.Builder()
                        .setCode(AD_SERVICES_SETTINGS_USAGE_REPORTED)
                        .setRegion(mDeviceLoggingRegion)
                        .setAction(action)
                        .build();
        AdServicesLoggerImpl.getInstance().logUIStats(uiStats);
    }
}
