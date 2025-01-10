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

package com.android.adservices.service.shell.attributionreporting;

import com.android.adservices.service.measurement.Source;

import com.google.common.collect.ImmutableMap;

import org.json.JSONException;
import org.json.JSONObject;

public final class AttributionReportingHelper {
    private static final String ID = "_id";
    private static final String STATUS = "status";
    private static final String REGISTRATION_ORIGIN = "registration_origin";
    private static final String REGISTRANT = "registrant";
    private static final String EVENT_TIME = "event_time";
    private static final String EXPIRY_TIME = "expiry_time";
    private static final String SOURCE_TYPE = "source_key";
    private static final String DEBUG_KEY = "debug_key";
    private static final String APP_DESTINATION = "app_destination";
    private static final String WEB_DESTINATION = "web_destination";
    private static final String ACTIVE = "active";
    private static final String IGNORED = "ignored";
    private static final String MARKED_TO_DELETE = "marked_to_delete";

    private static final ImmutableMap<Integer, String> STATUS_MAP =
            ImmutableMap.of(
                    Source.Status.ACTIVE, ACTIVE,
                    Source.Status.IGNORED, IGNORED,
                    Source.Status.MARKED_TO_DELETE, MARKED_TO_DELETE);

    private AttributionReportingHelper() {
        throw new UnsupportedOperationException(
                "AttributingReportingHelper only provides static methods");
    }

    static JSONObject sourceToJson(Source source) throws JSONException {
        JSONObject jsonObject =
                new JSONObject()
                        .put(ID, source.getId())
                        .put(STATUS, STATUS_MAP.get(source.getStatus()))
                        .put(REGISTRATION_ORIGIN, source.getRegistrationOrigin())
                        .put(REGISTRANT, source.getRegistrant())
                        .put(EVENT_TIME, source.getEventTime())
                        .put(EXPIRY_TIME, source.getExpiryTime())
                        .put(SOURCE_TYPE, source.getSourceType().getValue());

        if (source.getDebugKey() != null) {
            jsonObject.put(DEBUG_KEY, source.getDebugKey().toString());
        }

        if (source.hasAppDestinations()) {
            jsonObject.put(APP_DESTINATION, source.getAppDestinations());
        }

        if (source.hasWebDestinations()) {
            jsonObject.put(WEB_DESTINATION, source.getWebDestinations());
        }

        return jsonObject;
    }
}
