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

package com.android.adservices.service.measurement.reporting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.adservices.HpkeJni;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

public class AggregateReportBodyTest {

    private static final String ATTRIBUTION_DESTINATION = "https://attribution.destination";
    private static final String SOURCE_REGISTRATION_TIME = "1246174152155";
    private static final String SCHEDULED_REPORT_TIME = "1246174158155";
    private static final String VERSION = "12";
    private static final String REPORT_ID = "A1";
    private static final String REPORTING_ORIGIN = "https://adtech.domain";
    private static final String DEBUG_CLEARTEXT_PAYLOAD = "{\"operation\":\"histogram\","
            + "\"data\":[{\"bucket\":1369,\"value\":32768},{\"bucket\":3461,"
            + "\"value\":1664}]}";

    private AggregateReportBody createAggregateReportBodyExample1() {
        return new AggregateReportBody.Builder()
                .setAttributionDestination(ATTRIBUTION_DESTINATION)
                .setSourceRegistrationTime(SOURCE_REGISTRATION_TIME)
                .setScheduledReportTime(SCHEDULED_REPORT_TIME)
                .setApiVersion(VERSION)
                .setReportId(REPORT_ID)
                .setReportingOrigin(REPORTING_ORIGIN)
                .setDebugCleartextPayload(DEBUG_CLEARTEXT_PAYLOAD)
                .build();
    }

    @Test
    public void testSharedInfoJsonSerialization() throws JSONException {
        AggregateReportBody aggregateReport = createAggregateReportBodyExample1();
        JSONObject sharedInfoJson = aggregateReport.sharedInfoToJson();

        assertEquals(SCHEDULED_REPORT_TIME, sharedInfoJson.get("scheduled_report_time"));
        assertEquals(VERSION, sharedInfoJson.get("version"));
        assertEquals(REPORT_ID, sharedInfoJson.get("report_id"));
        assertEquals(REPORTING_ORIGIN, sharedInfoJson.get("reporting_origin"));
        assertEquals(ATTRIBUTION_DESTINATION, sharedInfoJson.get("attribution_destination"));
        assertEquals(SOURCE_REGISTRATION_TIME, sharedInfoJson.get("source_registration_time"));
    }

    @Test
    public void testAggregationServicePayloadsJsonSerialization() throws Exception {
        AggregateReportBody aggregateReport = createAggregateReportBodyExample1();

        AggregateEncryptionKey key = AggregateCryptoFixture.getKey();
        JSONArray aggregationServicePayloadsJson =
                aggregateReport.aggregationServicePayloadsToJson(/* sharedInfo = */ null, key);

        JSONObject aggregateServicePayloads = aggregationServicePayloadsJson.getJSONObject(0);

        assertEquals(key.getKeyId(), aggregateServicePayloads.get("key_id"));
        assertEncodedDebugPayload(aggregateServicePayloads);
        assertEncryptedPayload(aggregateServicePayloads);
    }

    private void assertEncodedDebugPayload(JSONObject aggregateServicePayloads) throws Exception {
        final String encodedPayloadBase64 =
                (String) aggregateServicePayloads.get("debug_cleartext_payload");
        assertNotNull(encodedPayloadBase64);

        final byte[] cborEncodedPayload = Base64.getDecoder().decode(encodedPayloadBase64);
        assertCborEncoded(cborEncodedPayload);
    }

    private void assertEncryptedPayload(JSONObject aggregateServicePayloads) throws Exception {
        final String encryptedPayloadBase64 = (String) aggregateServicePayloads.get("payload");
        assertNotNull(encryptedPayloadBase64);

        final byte[] decryptedCborEncoded =
                HpkeJni.decrypt(
                        AggregateCryptoFixture.getPrivateKey(),
                        Base64.getDecoder().decode(encryptedPayloadBase64),
                        AggregateCryptoFixture.getSharedInfoPrefix().getBytes());
        assertNotNull(decryptedCborEncoded);
        assertCborEncoded(decryptedCborEncoded);
    }

    private void assertCborEncoded(byte[] value) throws CborException {
        final List<DataItem> dataItems = new CborDecoder(new ByteArrayInputStream(value)).decode();

        final Map payload = (Map) dataItems.get(0);
        assertEquals("histogram", payload.get(new UnicodeString("operation")).toString());

        final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
        assertEquals(2, payloadArray.getDataItems().size());
        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", 1369)
                                                && isFound((Map) i, "value", 32768)));

        assertTrue(
                payloadArray.getDataItems().stream()
                        .anyMatch(
                                i ->
                                        isFound((Map) i, "bucket", 3461)
                                                && isFound((Map) i, "value", 1664)));
    }

    private boolean isFound(Map map, String name, int value) {
        return BigInteger.valueOf(value)
                .equals(new BigInteger(((ByteString) map.get(new UnicodeString(name))).getBytes()));
    }
}
