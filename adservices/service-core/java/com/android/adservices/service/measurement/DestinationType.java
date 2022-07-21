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

package com.android.adservices.service.measurement;

import android.annotation.NonNull;
import android.net.Uri;

/**
 * Used to identify if a {@link Uri} is app based or web based. For example,
 * "android-app://com.example.app" is an app based {@link Uri} and "https://web.example.com is a web
 * based {@link Uri}.
 */
public enum DestinationType {
    APP,
    WEB;

    private static final String ANDROID_APP_SCHEME = "android-app";

    /**
     * {@link #APP} if the provided destination {@link Uri} is app based, otherwise returns {@link
     * #WEB}.
     *
     * @param destinationUri destination {@link Uri} to check
     * @return {@link #APP} if the provided destination {@link Uri} is app based, otherwise returns
     *     {@link #WEB}
     */
    public static DestinationType getDestinationType(@NonNull Uri destinationUri) {
        if (ANDROID_APP_SCHEME.equals(destinationUri.getScheme())) {
            return DestinationType.APP;
        }

        return DestinationType.WEB;
    }
}
