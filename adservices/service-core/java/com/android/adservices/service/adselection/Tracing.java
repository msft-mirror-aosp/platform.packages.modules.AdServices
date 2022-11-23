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

package android.adservices.adselection;

import android.annotation.NonNull;
import android.os.Trace;

import java.util.concurrent.ThreadLocalRandom;

/** Utility class providing methods for using {@link android.os.Trace}. */
public final class Tracing {

    public static final String RUN_AD_SELECTION = "RunOnDeviceAdSelection";
    public static final String PERSIST_AD_SELECTION = "PersistOnDeviceAdSelection";
    public static final String GET_BUYERS_CUSTOM_AUDIENCE = "GetBuyersCustomAudience";
    public static final String VALIDATE_REQUEST = "ValidateRequest";
    public static final String GET_BUYER_DECISION_LOGIC = "GetBuyerDecisionLogic";

    /**
     * Begins an asynchronous trace and generates random cookie.
     *
     * @param sectionName used to identify trace type.
     * @return unique cookie for identifying trace.
     */
    public static int beginAsyncSection(@NonNull String sectionName) {
        if (!Trace.isEnabled()) {
            return -1;
        }
        int traceCookie = ThreadLocalRandom.current().nextInt();
        Trace.beginAsyncSection(sectionName, traceCookie);
        return traceCookie;
    }

    /**
     * Ends an asynchronous trace section.
     *
     * @param sectionName used to identify trace type.
     * @param traceCookie unique cookie for identifying trace.
     */
    public static void endAsyncSection(@NonNull String sectionName, int traceCookie) {
        Trace.endAsyncSection(sectionName, traceCookie);
    }
}
