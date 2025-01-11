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

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public final class AttributionReportingHelperTest extends AdServicesUnitTestCase {
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

    @Test
    public void testSourceToJson_happyPath() throws JSONException {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                        .build();
        JSONObject jsonObject = AttributionReportingHelper.sourceToJson(source);

        expect.withMessage("ID").that(jsonObject.getString(ID)).isEqualTo(source.getId());
        expect.withMessage("STATUS").that(jsonObject.getString(STATUS)).isEqualTo("active");
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(REGISTRATION_ORIGIN))
                .isEqualTo(source.getRegistrationOrigin().toString());

        expect.withMessage("APP_DESTINATION")
                .that(jsonObject.getString(APP_DESTINATION))
                .isEqualTo(source.getAppDestinations().toString());
        expect.withMessage("WEB_DESTINATION")
                .that(jsonObject.getString(WEB_DESTINATION))
                .isEqualTo(source.getWebDestinations().toString());

        expect.withMessage("REGISTRANT")
                .that(jsonObject.getString(REGISTRANT))
                .isEqualTo(source.getRegistrant().toString());
        expect.withMessage("EVENT_TIME")
                .that(jsonObject.getLong(EVENT_TIME))
                .isEqualTo(source.getEventTime());
        expect.withMessage("EXPIRY_TIME")
                .that(jsonObject.getLong(EXPIRY_TIME))
                .isEqualTo(source.getExpiryTime());
        expect.withMessage("SOURCE_TYPE")
                .that(jsonObject.getString(SOURCE_TYPE))
                .isEqualTo(source.getSourceType().getValue());

        String debugKeyString = jsonObject.getString(DEBUG_KEY);
        expect.withMessage("DEBUG_KEY")
                .that(debugKeyString)
                .isEqualTo(source.getDebugKey().toString());
    }
}
