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
package com.android.adservices.service.measurement.aggregation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashSet;

/** Unit tests for {@link AggregateDebugReporting} */
@SmallTest
public final class AggregateDebugReportingTest {

    @Test
    public void equals_pass() {
        assertThat(createExample1AggregateDebugReporting())
                .isEqualTo(createExample1AggregateDebugReporting());
    }

    @Test
    public void equals_fail() {
        assertThat(createExample1AggregateDebugReporting())
                .isNotEqualTo(createExample2AggregateDebugReporting());
    }

    @Test
    public void hashCode_pass() {
        assertThat(createExample1AggregateDebugReporting().hashCode())
                .isEqualTo(createExample1AggregateDebugReporting().hashCode());
    }

    @Test
    public void hashCode_fail() {
        assertThat(createExample1AggregateDebugReporting().hashCode())
                .isNotEqualTo(createExample2AggregateDebugReporting().hashCode());
    }

    @Test
    public void getters() {
        AggregateDebugReporting aggregateDebugReporting = createExample1AggregateDebugReporting();
        assertThat(aggregateDebugReporting.getBudget()).isEqualTo(1024);
        assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
        assertThat(
                        Arrays.asList(
                                new AggregateDebugReportData.Builder(
                                                /* reportType= */ new HashSet<>(
                                                        Arrays.asList("source-storage-limit")),
                                                /* keyPiece= */ new BigInteger("2", 16),
                                                /* value= */ 100)
                                        .build()))
                .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList());
        assertThat(Uri.parse("https://cloud.coordination.test"))
                .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
    }

    @Test
    public void buildFromJson_success() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key_piece", "0x3");
        obj.put("budget", 65536);
        obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
        JSONObject dataObj = new JSONObject();
        dataObj.put("key_piece", "0x3");
        dataObj.put("value", 65536);
        JSONArray types = new JSONArray(Arrays.asList("source-noised", "source-unknown-error"));
        dataObj.put("types", types);
        obj.put("debug_data", new JSONArray(Arrays.asList(dataObj)));

        AggregateDebugReporting aggregateDebugReporting =
                new AggregateDebugReporting.Builder(obj).build();
        assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
        assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
        assertThat(Uri.parse("https://cloud.coordination.test"))
                .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
        assertThat(
                        Arrays.asList(
                                new AggregateDebugReportData.Builder(
                                                /* reportType= */ new HashSet<>(
                                                        Arrays.asList(
                                                                "source-noised",
                                                                "source-unknown-error")),
                                                /* keyPiece= */ new BigInteger("3", 16),
                                                /* value= */ 65536)
                                        .build()))
                .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList());
    }

    @Test
    public void buildFromJson_nullKeyPiece() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key_piece", null);
        obj.put("budget", 65536);
        obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
        JSONObject dataObj = new JSONObject();
        dataObj.put("key_piece", "0x3");
        dataObj.put("value", 65536);
        JSONArray types = new JSONArray(Arrays.asList("source-noised"));
        dataObj.put("types", types);
        obj.put("debug_data", new JSONArray(Arrays.asList(dataObj)));

        AggregateDebugReporting aggregateDebugReporting =
                new AggregateDebugReporting.Builder(obj).build();
        assertThat(aggregateDebugReporting.getKeyPiece()).isNull();
        assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
        assertThat(Uri.parse("https://cloud.coordination.test"))
                .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
        assertThat(
                        Arrays.asList(
                                new AggregateDebugReportData.Builder(
                                                /* reportType= */ new HashSet<>(
                                                        Arrays.asList("source-noised")),
                                                /* keyPiece= */ new BigInteger("3", 16),
                                                /* value= */ 65536)
                                        .build()))
                .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList());
    }

    @Test
    public void buildFromJson_nullBudget() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key_piece", "0x3");
        obj.put("budget", null);
        obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
        JSONObject dataObj = new JSONObject();
        dataObj.put("key_piece", "0x3");
        dataObj.put("value", 65536);
        JSONArray types = new JSONArray(Arrays.asList("source-noised"));
        dataObj.put("types", types);
        obj.put("debug_data", new JSONArray(Arrays.asList(dataObj)));

        AggregateDebugReporting aggregateDebugReporting =
                new AggregateDebugReporting.Builder(obj).build();
        assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
        assertThat(aggregateDebugReporting.getBudget()).isEqualTo(0);
        assertThat(Uri.parse("https://cloud.coordination.test"))
                .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
        assertThat(
                        Arrays.asList(
                                new AggregateDebugReportData.Builder(
                                                /* reportType= */ new HashSet<>(
                                                        Arrays.asList("source-noised")),
                                                /* keyPiece= */ new BigInteger("3", 16),
                                                /* value= */ 65536)
                                        .build()))
                .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList());
    }

    @Test
    public void buildFromJson_nullAggregationCoordinatorOrigin() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key_piece", "0x3");
        obj.put("budget", 65536);
        obj.put("aggregation_coordinator_origin", null);
        JSONObject dataObj = new JSONObject();
        dataObj.put("key_piece", "0x3");
        dataObj.put("value", 65536);
        JSONArray types = new JSONArray(Arrays.asList("source-noised"));
        dataObj.put("types", types);
        obj.put("debug_data", new JSONArray(Arrays.asList(dataObj)));

        AggregateDebugReporting aggregateDebugReporting =
                new AggregateDebugReporting.Builder(obj).build();
        assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
        assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
        assertThat(aggregateDebugReporting.getAggregationCoordinatorOrigin()).isNull();
        assertThat(
                        Arrays.asList(
                                new AggregateDebugReportData.Builder(
                                                /* reportType= */ new HashSet<>(
                                                        Arrays.asList("source-noised")),
                                                /* keyPiece= */ new BigInteger("3", 16),
                                                /* value= */ 65536)
                                        .build()))
                .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList());
    }

    @Test
    public void buildFromJson_nullDebugData() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key_piece", "0x3");
        obj.put("budget", 65536);
        obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
        obj.put("debug_data", null);

        AggregateDebugReporting aggregateDebugReporting =
                new AggregateDebugReporting.Builder(obj).build();
        assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
        assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
        assertThat(Uri.parse("https://cloud.coordination.test"))
                .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
        assertThat(aggregateDebugReporting.getAggregateDebugReportDataList()).isNull();
    }

    @Test
    public void buildFromJson_nullDebugDataKeyPiece() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key_piece", "0x3");
        obj.put("budget", 65536);
        obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
        JSONObject dataObj = new JSONObject();
        dataObj.put("key_piece", null);
        dataObj.put("value", 65536);
        JSONArray types = new JSONArray(Arrays.asList("source-noised"));
        dataObj.put("types", types);
        obj.put("debug_data", new JSONArray(Arrays.asList(dataObj)));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AggregateDebugReporting.Builder(obj).build());
    }

    @Test
    public void buildFromJson_nullDebugDataValue() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key_piece", "0x3");
        obj.put("budget", 65536);
        obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
        JSONObject dataObj = new JSONObject();
        dataObj.put("key_piece", "0x3");
        dataObj.put("value", null);
        JSONArray types = new JSONArray(Arrays.asList("source-noised"));
        dataObj.put("types", types);
        obj.put("debug_data", new JSONArray(Arrays.asList(dataObj)));

        AggregateDebugReporting aggregateDebugReporting =
                new AggregateDebugReporting.Builder(obj).build();
        assertThat(new BigInteger("3", 16)).isEqualTo(aggregateDebugReporting.getKeyPiece());
        assertThat(aggregateDebugReporting.getBudget()).isEqualTo(65536);
        assertThat(Uri.parse("https://cloud.coordination.test"))
                .isEqualTo(aggregateDebugReporting.getAggregationCoordinatorOrigin());
        assertThat(
                        Arrays.asList(
                                new AggregateDebugReportData.Builder(
                                                /* reportType= */ new HashSet<>(
                                                        Arrays.asList("source-noised")),
                                                /* keyPiece= */ new BigInteger("3", 16),
                                                /* value= */ 0)
                                        .build()))
                .isEqualTo(aggregateDebugReporting.getAggregateDebugReportDataList());
    }

    @Test
    public void buildFromJson_nullDebugDataTypes() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("key_piece", "0x3");
        obj.put("budget", 65536);
        obj.put("aggregation_coordinator_origin", "https://cloud.coordination.test");
        JSONObject dataObj = new JSONObject();
        dataObj.put("key_piece", "0x3");
        dataObj.put("value", 65536);
        dataObj.put("types", null);
        obj.put("debug_data", new JSONArray(Arrays.asList(dataObj)));

        assertThrows(
                IllegalArgumentException.class,
                () -> new AggregateDebugReporting.Builder(obj).build());
    }

    private static AggregateDebugReporting createExample1AggregateDebugReporting() {
        AggregateDebugReportData aggregateDebugReportData =
                new AggregateDebugReportData.Builder(
                                /* reportType= */ new HashSet<>(
                                        Arrays.asList("source-storage-limit")),
                                /* keyPiece= */ new BigInteger("2", 16),
                                /* value= */ 100)
                        .build();
        return new AggregateDebugReporting.Builder(
                        /* budget= */ 1024,
                        /* keyPiece= */ new BigInteger("3", 16),
                        /* aggregateDebugReportDataList= */ Arrays.asList(aggregateDebugReportData),
                        /* aggregationCoordinatorOrigin= */ Uri.parse(
                                "https://cloud.coordination.test"))
                .build();
    }

    private static AggregateDebugReporting createExample2AggregateDebugReporting() {
        AggregateDebugReportData aggregateDebugReportData =
                new AggregateDebugReportData.Builder(
                                /* reportType= */ new HashSet<>(
                                        Arrays.asList("source-storage-limit")),
                                /* keyPiece= */ new BigInteger("2", 16),
                                /* value= */ 100)
                        .build();
        return new AggregateDebugReporting.Builder(
                        /* budget= */ 1025,
                        /* keyPiece= */ new BigInteger("3", 16),
                        /* aggregateDebugReportDataList= */ Arrays.asList(aggregateDebugReportData),
                        /* aggregationCoordinatorOrigin= */ Uri.parse(
                                "https://cloud.coordination.test"))
                .build();
    }
}
