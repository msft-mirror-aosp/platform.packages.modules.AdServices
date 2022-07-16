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

package com.android.adservices.service.consent;

import android.annotation.NonNull;

/**
 * POJO Represents a App.
 *
 * @hide
 */
public class App {

    private int mAppId;

    /** Returns an Integer represents the app identifier. */
    public int getAppId() {
        return mAppId;
    }

    App(int appId) {
        this.mAppId = appId;
    }

    /** Creates an instance of an App. */
    @NonNull
    public static App create(int appId) {
        return new App(appId);
    }
}
