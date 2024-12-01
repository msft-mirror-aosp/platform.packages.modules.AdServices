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

package com.android.adservices.service.common;

import android.annotation.NonNull;
import android.content.Context;

import com.android.adservices.LogUtil;

import java.util.function.BiConsumer;

/**
 * Provides functionality for AdServicesCommonService to be able to execute notification methods.
 */
public class AdServicesSyncUtil {
    BiConsumer<Context, Boolean> mBiConsumer;
    QuadConsumer<Context, Boolean, Boolean, Boolean> mNotificationTriggerV2Consumer;
    private static volatile AdServicesSyncUtil sAdservicesSyncUtil;

    private AdServicesSyncUtil() {}
    /**
     * Gets an instance of {@link AdServicesSyncUtil} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @NonNull
    public static AdServicesSyncUtil getInstance() {
        if (sAdservicesSyncUtil == null) {
            synchronized (AdServicesSyncUtil.class) {
                if (sAdservicesSyncUtil == null) {
                    sAdservicesSyncUtil = new AdServicesSyncUtil();
                }
            }
        }
        return sAdservicesSyncUtil;
    }

    /** Register the consumer. */
    public void register(BiConsumer<Context, Boolean> biConsumer) {
        try {
            mBiConsumer = biConsumer;
        } catch (Exception e) {
            LogUtil.e("errors on register consumer of " + e.getMessage());
        }
    }

    /** execute the consumer. */
    public void execute(Context context, Boolean param) {
        if (mBiConsumer == null) {
            return;
        }
        mBiConsumer.accept(context, param);
    }

    /**
     * Register consumer for V2 notification trigger method.
     *
     * @param consumer Consumer for showConsentNotificationV2
     */
    public void registerNotificationTriggerV2(
            QuadConsumer<Context, Boolean, Boolean, Boolean> consumer) {
        try {
            mNotificationTriggerV2Consumer = consumer;
        } catch (Exception e) {
            LogUtil.e("error while registering notificationTriggerV2Consumer" + e.getMessage());
        }
    }

    /** execute the consumer. */
    public void executeNotificationTriggerV2(
            Context context,
            boolean isRenotify,
            boolean isNewAdPersonalizationModuleEnabled,
            boolean isOngoingNotification) {
        if (mNotificationTriggerV2Consumer == null) {
            return;
        }
        mNotificationTriggerV2Consumer.accept(
                context, isRenotify, isNewAdPersonalizationModuleEnabled, isOngoingNotification);
    }
}
