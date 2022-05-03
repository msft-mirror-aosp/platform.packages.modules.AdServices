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

/** Represents a consent given to certain {@link AdServicesApiType}. */
public class AdServicesApiConsent {
    private boolean mGiven;

    AdServicesApiConsent(boolean given) {
        this.mGiven = given;
    }

    public boolean isGiven() {
        return mGiven;
    }

    public static AdServicesApiConsent getGivenConsent() {
        return new AdServicesApiConsent(true);
    }

    public static AdServicesApiConsent getRevokedConsent() {
        return new AdServicesApiConsent(false);
    }

}
