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

import android.net.Uri;

public final class TriggerFixture {
    private TriggerFixture() { }

    // Assume the field values in this Trigger.Builder have no relation to the field values in
    // {@link ValidTriggerParams}
    public static Trigger.Builder getValidTriggerBuilder() {
        return new Trigger.Builder()
            .setAttributionDestination(ValidTriggerParams.ATTRIBUTION_DESTINATION)
            .setAdTechDomain(ValidTriggerParams.AD_TECH_DOMAIN)
            .setRegistrant(ValidTriggerParams.REGISTRANT);
    }

    // Assume the field values in this Trigger have no relation to the field values in
    // {@link ValidTriggerParams}
    public static Trigger getValidTrigger() {
        return new Trigger.Builder()
                .setAttributionDestination(ValidTriggerParams.ATTRIBUTION_DESTINATION)
                .setAdTechDomain(ValidTriggerParams.AD_TECH_DOMAIN)
                .setRegistrant(ValidTriggerParams.REGISTRANT)
                .setTriggerTime(ValidTriggerParams.TRIGGER_TIME)
                .setEventTriggers(ValidTriggerParams.EVENT_TRIGGERS)
                .setAggregateTriggerData(ValidTriggerParams.AGGREGATE_TRIGGER_DATA)
                .setAggregateValues(ValidTriggerParams.AGGREGATE_VALUES)
                .setFilters(ValidTriggerParams.TOP_LEVEL_FILTERS_JSON_STRING)
                .build();
    }

    public static class ValidTriggerParams {
        public static final Long TRIGGER_TIME = 8640000000L;
        public static final Uri ATTRIBUTION_DESTINATION =
                Uri.parse("android-app://com.destination");
        public static final Uri REGISTRANT = Uri.parse("android-app://com.registrant");
        public static final Uri AD_TECH_DOMAIN = Uri.parse("https://com.example");
        public static final String TOP_LEVEL_FILTERS_JSON_STRING =
                "{\n"
                        + "  \"key_1\": [\"value_1\", \"value_2\"],\n"
                        + "  \"key_2\": [\"value_1\", \"value_2\"]\n"
                        + "}\n";

        public static final String EVENT_TRIGGERS =
                "[\n"
                        + "{\n"
                        + "  \"trigger_data\": \"5\",\n"
                        + "  \"priority\": \"123\"\n"
                        + "  \"filters\": {\n"
                        + "    \"source_type\": [\"navigation\"],\n"
                        + "    \"key_1\": [\"value_1\"] \n"
                        + "   }\n"
                        + "},\n"
                        + "{\n"
                        + "  \"trigger_data\": \"0\",\n"
                        + "  \"priority\": \"124\"\n"
                        + "  \"deduplication_key\": \"101\"\n"
                        + "  \"filters\": {\n"
                        + "     \"source_type\": [\"event\"]\n"
                        + "   },\n"
                        + "}\n"
                        + "]\n";

        public static final String AGGREGATE_TRIGGER_DATA =
                "["
                    + "{"
                        + "\"key_piece\":\"0xA80\","
                        + "\"source_keys\":[\"geoValue\",\"noMatch\"]"
                    + "}"
                + "]";

        public static final String AGGREGATE_VALUES =
                "{"
                    + "\"campaignCounts\":32768,"
                    + "\"geoValue\":1664"
                + "}";
    }
}
