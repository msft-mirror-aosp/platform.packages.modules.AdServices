/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.HpkeJni;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoConverter;
import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.Map;
import co.nstant.in.cbor.model.UnicodeString;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.util.Base64;
import java.util.List;

public class CountUniqueReportBodyTest {

    private static final String API_COUNT_UNIQUE = "count-unique";
    private static final String API_VERSION = "12";
    private static final String REPORT_ID = "A1";
    private static final Uri REPORTING_ORIGIN = Uri.parse("https://adtech.domain");
    private static final long SCHEDULED_REPORT_TIME = 1246174158155L;
    private static final String DEBUG_KEY = "27628792L";
    private static final String AGGREGATION_COORDINATOR_ORIGIN = "https://coordinator.origin";

    private static final String CONTEXT_ID = "context_id";

    private static final String DEBUG_CLEARTEXT_PAYLOAD =
            "{\"operation\":\"histogram\"," + "\"data\":[{\"bucket\":\"1234\",\"value\":128}]}";

    private static final boolean DEBUG_MODE = true;
    private Flags mMockFlags;

    @Rule
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .spyStatic(FlagsFactory.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    private CountUniqueReportBody.Builder createCountUniqueReportBodyExample1() {
        return new CountUniqueReportBody.Builder()
                .setApi(API_COUNT_UNIQUE)
                .setApiVersion(API_VERSION)
                .setReportId(REPORT_ID)
                .setReportingOrigin(REPORTING_ORIGIN)
                .setScheduledReportTime(SCHEDULED_REPORT_TIME)
                .setDebugCleartextPayload(DEBUG_CLEARTEXT_PAYLOAD)
                .setDebugKey(DEBUG_KEY)
                .setAggregationCoordinatorOrigin(Uri.parse(AGGREGATION_COORDINATOR_ORIGIN))
                .setDebugMode(DEBUG_MODE)
                .setContextId(CONTEXT_ID);
    }

    @Before
    public void before() {
        mMockFlags = mock(Flags.class);
        ExtendedMockito.doReturn(mMockFlags).when(FlagsFactory::getFlags);
        when(mMockFlags.getMeasurementAggregationCoordinatorOriginEnabled()).thenReturn(true);
    }

    @Test
    public void testSharedInfoJsonSerialization() throws JSONException {
        CountUniqueReportBody countUniqueReportBody = createCountUniqueReportBodyExample1().build();
        JSONObject sharedInfoJson = countUniqueReportBody.sharedInfoToJson();

        assertThat(sharedInfoJson.get("scheduled_report_time"))
                .isEqualTo(String.valueOf(SCHEDULED_REPORT_TIME));
        assertThat(sharedInfoJson.get("api")).isEqualTo(API_COUNT_UNIQUE);
        assertThat(sharedInfoJson.get("report_id")).isEqualTo(REPORT_ID);
        assertThat(sharedInfoJson.get("reporting_origin")).isEqualTo(REPORTING_ORIGIN.toString());
        assertThat(sharedInfoJson.get("version")).isEqualTo(API_VERSION);
        assertThat(sharedInfoJson.get("debug_mode")).isEqualTo("enabled");
    }

    @Test
    public void testSharedInfoJsonSerializationDebugModeDisabled() throws JSONException {
        CountUniqueReportBody countUniqueReportBody =
                createCountUniqueReportBodyExample1().setDebugKey(null).setDebugMode(false).build();
        JSONObject sharedInfoJson = countUniqueReportBody.sharedInfoToJson();

        assertThat(sharedInfoJson.opt("debug_mode")).isNull();
    }

    @Test
    public void testAggregationServicePayloadsJsonSerialization() throws Exception {
        CountUniqueReportBody countUniqueReportBody = createCountUniqueReportBodyExample1().build();

        AggregateEncryptionKey key = AggregateCryptoFixture.getKey();
        JSONArray aggregationServicePayloadsJson =
                countUniqueReportBody.aggregationServicePayloadsToJson(/* sharedInfo= */ null, key);

        JSONObject aggregateServicePayloads = aggregationServicePayloadsJson.getJSONObject(0);
        assertThat(aggregateServicePayloads.get("key_id")).isEqualTo(key.getKeyId());
        assertThat(aggregateServicePayloads.opt("debug_cleartext_payload"))
                .isEqualTo(
                        AggregateCryptoConverter.encode(
                                DEBUG_CLEARTEXT_PAYLOAD, /* filteringIdMaxBytes= */ null));

        assertEncodedDebugPayload(aggregateServicePayloads);
        assertEncryptedPayload(aggregateServicePayloads);
    }

    @Test
    public void testAggregationServicePayloadsJsonSerializationWithoutDebugKey() throws Exception {
        CountUniqueReportBody countUniqueReportBody =
                createCountUniqueReportBodyExample1().setDebugKey(null).setDebugMode(false).build();

        AggregateEncryptionKey key = AggregateCryptoFixture.getKey();
        JSONArray aggregationServicePayloadsJson =
                countUniqueReportBody.aggregationServicePayloadsToJson(/* sharedInfo= */ null, key);

        JSONObject aggregateServicePayloads = aggregationServicePayloadsJson.getJSONObject(0);

        assertThat(aggregateServicePayloads.opt("debug_cleartext_payload")).isNull();
    }

    @Test
    public void testCountUniqueReportBodyJsonSerialization() throws Exception {
        CountUniqueReportBody countUniqueReportBody = createCountUniqueReportBodyExample1().build();

        JSONObject countUniqueReportJson =
                countUniqueReportBody.toJson(AggregateCryptoFixture.getKey(), mMockFlags);

        assertThat(countUniqueReportJson.opt("shared_info")).isNotNull();
        assertThat(countUniqueReportJson.opt("aggregation_service_payloads")).isNotNull();

        assertThat(countUniqueReportJson.get("debug_key")).isEqualTo(DEBUG_KEY);
        assertThat(countUniqueReportJson.get("context_id")).isEqualTo(CONTEXT_ID);
        assertThat(countUniqueReportJson.get("aggregation_coordinator_origin"))
                .isEqualTo(AGGREGATION_COORDINATOR_ORIGIN);
    }

    @Test
    public void testCountUniqueReportBodyJsonSerializationWithoutDebugKey() throws Exception {
        CountUniqueReportBody countUniqueReportBody =
                createCountUniqueReportBodyExample1().setDebugKey(null).setDebugMode(false).build();

        JSONObject countUniqueReportJson =
                countUniqueReportBody.toJson(AggregateCryptoFixture.getKey(), mMockFlags);

        assertThat(countUniqueReportJson.opt("debug_key")).isNull();
    }

    @Test
    public void testCountUniqueReportBodyJsonSerializationWithoutContextId() throws Exception {
        CountUniqueReportBody countUniqueReportBody =
                createCountUniqueReportBodyExample1().setContextId(null).build();

        JSONObject countUniqueReportJson =
                countUniqueReportBody.toJson(AggregateCryptoFixture.getKey(), mMockFlags);

        assertThat(countUniqueReportJson.opt("context_id")).isNull();
    }

    private void assertEncodedDebugPayload(JSONObject aggregateServicePayloads) throws Exception {
        if (!aggregateServicePayloads.isNull("debug_cleartext_payload")) {
            final String encodedPayloadBase64 =
                    (String) aggregateServicePayloads.get("debug_cleartext_payload");
            assertThat(encodedPayloadBase64).isNotNull();

            final byte[] cborEncodedPayload = Base64.getDecoder().decode(encodedPayloadBase64);
            assertCborEncoded(cborEncodedPayload);
        }
    }

    private void assertEncryptedPayload(JSONObject aggregateServicePayloads) throws Exception {
        final String encryptedPayloadBase64 = (String) aggregateServicePayloads.get("payload");
        assertThat(encryptedPayloadBase64).isNotNull();

        final byte[] decryptedCborEncoded =
                HpkeJni.decrypt(
                        AggregateCryptoFixture.getPrivateKey(),
                        Base64.getDecoder().decode(encryptedPayloadBase64),
                        AggregateCryptoFixture.getSharedInfoPrefix().getBytes());
        assertThat(decryptedCborEncoded).isNotNull();
        assertCborEncoded(decryptedCborEncoded);
    }

    private void assertCborEncoded(byte[] value) throws CborException {
        final List<DataItem> dataItems = new CborDecoder(new ByteArrayInputStream(value)).decode();

        final Map payload = (Map) dataItems.get(0);
        assertThat(payload.get(new UnicodeString("operation")).toString()).isEqualTo("histogram");

        final Array payloadArray = (Array) payload.get(new UnicodeString("data"));
        assertThat(payloadArray.getDataItems().size()).isEqualTo(1);
        assertThat(
                        payloadArray.getDataItems().stream()
                                .anyMatch(
                                        i ->
                                                isFound((Map) i, "bucket", 1234)
                                                        && isFound((Map) i, "value", 128)))
                .isTrue();
    }

    private boolean isFound(Map map, String name, int value) {
        return BigInteger.valueOf(value)
                .equals(new BigInteger(((ByteString) map.get(new UnicodeString(name))).getBytes()));
    }
}
