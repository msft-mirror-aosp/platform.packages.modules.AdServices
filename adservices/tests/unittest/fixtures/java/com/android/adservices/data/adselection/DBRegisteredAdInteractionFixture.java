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

package com.android.adservices.data.adselection;

import android.adservices.adselection.ReportEventRequest;
import android.net.Uri;

import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;

public final class DBRegisteredAdInteractionFixture {
    public static final int AD_SELECTION_ID = 1;
    public static final String INTERACTION_KEY_CLICK = "CLICK";
    public static final String INTERACTION_KEY_VIEW = "VIEW";
    private static final String BASE_URI = "https://www.seller.com/";
    public static final Uri EVENT_REPORTING_URI = Uri.parse(BASE_URI + INTERACTION_KEY_CLICK);

    /**
     * @return A valid builder for {@link DBRegisteredAdInteraction}.
     */
    public static DBRegisteredAdInteraction.Builder getValidDbRegisteredAdInteractionBuilder() {
        return DBRegisteredAdInteraction.builder()
                .setAdSelectionId(AD_SELECTION_ID)
                .setInteractionKey(INTERACTION_KEY_CLICK)
                .setDestination(ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)
                .setInteractionReportingUri(EVENT_REPORTING_URI);
    }

    /**
     * Convert a {@link DBRegisteredAdInteraction} into a {@link RegisteredAdInteraction} for
     * testing purposes.
     *
     * @param dbRegisteredAdInteraction The {@link DBRegisteredAdInteraction} data to copy.
     * @return A valid {@link RegisteredAdInteraction}.
     */
    public static RegisteredAdInteraction toRegisteredAdInteraction(
            DBRegisteredAdInteraction dbRegisteredAdInteraction) {
        return RegisteredAdInteraction.builder()
                .setInteractionReportingUri(dbRegisteredAdInteraction.getInteractionReportingUri())
                .setInteractionKey(dbRegisteredAdInteraction.getInteractionKey())
                .build();
    }

    private DBRegisteredAdInteractionFixture() {
        throw new RuntimeException("cannot construct class");
    }
}
