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
import com.android.adservices.data.measurement.MeasurementTables.EventReportContract;
import com.android.adservices.data.measurement.MeasurementTables.SourceContract;
import com.android.adservices.data.measurement.MeasurementTables.TriggerContract;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.EventReportFixture;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public final class AttributionReportingHelperTest extends AdServicesUnitTestCase {
    private static final String APP_DESTINATION = "app_destination";
    private static final String WEB_DESTINATION = "web_destination";
    private static final String RANDOMIZED = "randomized";

    @Test
    public void testSourceToJson_happyPath() throws JSONException {
        Source source =
                SourceFixture.getValidSourceBuilder()
                        .setDebugKey(SourceFixture.ValidSourceParams.DEBUG_KEY)
                        .build();
        JSONObject jsonObject = AttributionReportingHelper.sourceToJson(source);

        expect.withMessage("ID")
                .that(jsonObject.getString(SourceContract.ID))
                .isEqualTo(source.getId());
        expect.withMessage("STATUS")
                .that(jsonObject.getString(SourceContract.STATUS))
                .isEqualTo("active");
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(SourceContract.REGISTRATION_ORIGIN))
                .isEqualTo(source.getRegistrationOrigin().toString());

        expect.withMessage("APP_DESTINATION")
                .that(jsonObject.getString(APP_DESTINATION))
                .isEqualTo(source.getAppDestinations().toString());
        expect.withMessage("WEB_DESTINATION")
                .that(jsonObject.getString(WEB_DESTINATION))
                .isEqualTo(source.getWebDestinations().toString());

        expect.withMessage("REGISTRANT")
                .that(jsonObject.getString(SourceContract.REGISTRANT))
                .isEqualTo(source.getRegistrant().toString());
        expect.withMessage("EVENT_TIME")
                .that(jsonObject.getLong(SourceContract.EVENT_TIME))
                .isEqualTo(source.getEventTime());
        expect.withMessage("EXPIRY_TIME")
                .that(jsonObject.getLong(SourceContract.EXPIRY_TIME))
                .isEqualTo(source.getExpiryTime());
        expect.withMessage("SOURCE_TYPE")
                .that(jsonObject.getString(SourceContract.SOURCE_TYPE))
                .isEqualTo(source.getSourceType().getValue());

        String debugKeyString = jsonObject.getString(SourceContract.DEBUG_KEY);
        expect.withMessage("DEBUG_KEY")
                .that(debugKeyString)
                .isEqualTo(source.getDebugKey().toString());
    }

    @Test
    public void testTriggerToJson_happyPath() throws JSONException {
        Trigger trigger =
                TriggerFixture.getValidTriggerBuilder()
                        .setTriggerTime(TriggerFixture.ValidTriggerParams.TRIGGER_TIME)
                        .setDebugKey(TriggerFixture.ValidTriggerParams.DEBUG_KEY)
                        .build();
        JSONObject jsonObject = AttributionReportingHelper.triggerToJson(trigger);

        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(TriggerContract.TRIGGER_TIME))
                .isEqualTo(trigger.getTriggerTime());
        expect.withMessage("ATTRIBUTION_DESTINATION")
                .that(jsonObject.getString(TriggerContract.ATTRIBUTION_DESTINATION))
                .isEqualTo(trigger.getAttributionDestination().toString());
        expect.withMessage("REGISTRATION_ORIGIN")
                .that(jsonObject.getString(TriggerContract.REGISTRATION_ORIGIN))
                .isEqualTo(trigger.getRegistrationOrigin().toString());
        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(TriggerContract.TRIGGER_TIME))
                .isEqualTo(trigger.getTriggerTime());
        String debugKeyString = jsonObject.getString(TriggerContract.DEBUG_KEY);
        expect.withMessage("DEBUG_KEY")
                .that(debugKeyString)
                .isEqualTo(trigger.getDebugKey().toString());
    }

    @Test
    public void testEventReportToJson_happyPath() throws JSONException {
        EventReport eventReport =
                EventReportFixture.getBaseEventReportBuild()
                        .setId("Event1")
                        .setSourceId("S1")
                        .setTriggerId(null)
                        .build();

        JSONObject jsonObject = AttributionReportingHelper.eventReportToJson(eventReport);

        expect.withMessage("STATUS")
                .that(jsonObject.getInt(EventReportContract.STATUS))
                .isEqualTo(eventReport.getStatus());
        expect.withMessage("ATTRIBUTION_DESTINATION")
                .that(jsonObject.getString(EventReportContract.ATTRIBUTION_DESTINATION))
                .isEqualTo(eventReport.getAttributionDestinations().toString());
        expect.withMessage("TRIGGER_TIME")
                .that(jsonObject.getLong(EventReportContract.TRIGGER_TIME))
                .isEqualTo(eventReport.getTriggerTime());
        expect.withMessage("REPORT_TIME")
                .that(jsonObject.getLong(EventReportContract.REPORT_TIME))
                .isEqualTo(eventReport.getReportTime());
        expect.withMessage("TRIGGER_PRIORITY")
                .that(jsonObject.getLong(EventReportContract.TRIGGER_PRIORITY))
                .isEqualTo(eventReport.getTriggerPriority());
        expect.withMessage("RANDOMIZED_TRIGGER_RATE")
                .that(jsonObject.getDouble(EventReportContract.RANDOMIZED_TRIGGER_RATE))
                .isEqualTo(eventReport.getRandomizedTriggerRate());
        expect.withMessage("RANDOMIZED").that(jsonObject.getBoolean(RANDOMIZED)).isEqualTo(true);
        expect.withMessage("REGISTRATION_ORIGIN").that(
                jsonObject.getString(EventReportContract.REGISTRATION_ORIGIN)).isEqualTo(
                eventReport.getRegistrationOrigin().toString());
    }
}
