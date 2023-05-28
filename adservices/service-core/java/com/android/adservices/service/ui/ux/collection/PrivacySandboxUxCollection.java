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

package com.android.adservices.service.ui.ux;

import android.os.Build;

import androidx.annotation.RequiresApi;

/** Collection of privacy sandbox UXs, ordered by their priority. */
@RequiresApi(Build.VERSION_CODES.S)
public enum PrivacySandboxUxCollection {
    UNSUPPORTED_UX(/* priority= */ 0, new UnsupportedUx()),

    GA_UX(/* priority= */ 1, new GaUx()),

    U18_UX(/* priority= */ 2, new U18Ux()),

    BETA_UX(/* priority= */ 3, new BetaUx());

    private final int mPriority;
    private final PrivacySandboxUx mUx;

    PrivacySandboxUxCollection(int priority, PrivacySandboxUx ux) {
        mPriority = priority;
        mUx = ux;
    }

    public int getPriority() {
        return mPriority;
    }

    public PrivacySandboxUx getUx() {
        return mUx;
    }
}
