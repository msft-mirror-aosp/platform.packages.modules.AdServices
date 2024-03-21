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

package com.android.adservices.ui;


import android.annotation.RequiresApi;
import android.content.Context;
import android.os.Build;

import androidx.fragment.app.FragmentActivity;

import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;

/**
 * Activities and Action Delegates should implement this interface to ensure they implement all
 * existing modes of AdServices.
 */
@RequiresApi(Build.VERSION_CODES.S)
public interface UxSelector {
    enum EndUserUx {
        UNKNOWN,
        BETA,
        GA,
        U18,
        RVC,
        GA_WITH_PAS
    }

    /**
     * This method will be called in during initialization of class to determine which ux to choose.
     *
     * @param fragmentActivity unused.
     * @param context current context.
     * @return Ux that end user should see.
     */
    default EndUserUx initWithUx(FragmentActivity fragmentActivity, Context context) {
        EndUserUx endUserUx = getEndUserUx(context);
        switch (endUserUx) {
            case U18:
                initU18();
                break;
            case GA:
                initGA();
                break;
            case BETA:
                initBeta();
                break;
            case RVC:
                initRvc();
                break;
            case GA_WITH_PAS:
                initGaUxWithPas();
                break;
            default:
                initGA();
        }
        return endUserUx;
    }

    /**
     * Returns the UX that the end user should be seeing currently.
     *
     * @param context current Context.
     * @return Ux that end user should see.
     */
    default EndUserUx getEndUserUx(Context context) {
        switch (UxUtil.getUx(context)) {
            case U18_UX:
                return EndUserUx.U18;
            case GA_UX:
                if (UxUtil.pasUxIsActive(/* beforeNotificationShown */ false)) {
                    // UI views should be updated only once notification is sent (ROW).
                    return EndUserUx.GA_WITH_PAS;
                }
                return EndUserUx.GA;
            case BETA_UX:
                return EndUserUx.BETA;
            case RVC_UX:
                return EndUserUx.RVC;
            default:
                // TODO: log some warning or error
                return EndUserUx.GA;
        }
    }

    /**
     * This method will be called in {@link #initWithUx} if app is in {@link
     * PrivacySandboxUxCollection#BETA_UX} mode.
     */
    void initBeta();

    /**
     * This method will be called in {@link #initWithUx} if app is in {@link
     * PrivacySandboxUxCollection#GA_UX} mode and PAS Ux feature is disabled.
     */
    void initGA();

    /**
     * This method will be called in {@link #initWithUx} if app is in {@link
     * PrivacySandboxUxCollection#U18_UX} mode.
     */
    void initU18();

    /**
     * This method will be called in {@link #initWithUx} if app is in {@link
     * PrivacySandboxUxCollection#RVC_UX} mode.
     */
    void initRvc();

    /**
     * This method will be called in {@link #initWithUx} if app is in {@link
     * PrivacySandboxUxCollection#GA_UX} mode and PAS Ux feature is enabled.
     */
    void initGaUxWithPas();
}
