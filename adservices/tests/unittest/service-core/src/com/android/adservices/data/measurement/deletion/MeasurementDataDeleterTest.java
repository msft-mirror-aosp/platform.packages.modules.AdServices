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

package com.android.adservices.data.measurement.deletion;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.SourceFixture;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.TriggerFixture;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionSource;
import com.android.adservices.service.measurement.aggregation.AggregatableAttributionTrigger;
import com.android.adservices.service.measurement.aggregation.AggregateAttributionData;
import com.android.adservices.service.measurement.aggregation.AggregateHistogramContribution;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.measurement.aggregation.AggregateReportFixture;

import com.google.common.collect.ImmutableList;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class MeasurementDataDeleterTest {
    private static final List<AggregateHistogramContribution> CONTRIBUTIONS_1 =
            Arrays.asList(
                    new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("10"))
                            .setValue(45)
                            .build(),
                    new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("100"))
                            .setValue(87)
                            .build());

    private static final AggregateReport AGGREGATE_REPORT_1 =
            AggregateReportFixture.getValidAggregateReportBuilder()
                    .setId("reportId1")
                    .setAggregateAttributionData(
                            new AggregateAttributionData.Builder()
                                    .setId(1L)
                                    .setContributions(CONTRIBUTIONS_1)
                                    .build())
                    .setSourceId("source1")
                    .setTriggerId("trigger1")
                    .build();

    private static final List<AggregateHistogramContribution> CONTRIBUTIONS_2 =
            Arrays.asList(
                    new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("500"))
                            .setValue(2000)
                            .build(),
                    new AggregateHistogramContribution.Builder()
                            .setKey(new BigInteger("10000"))
                            .setValue(3454)
                            .build());

    private static final AggregateReport AGGREGATE_REPORT_2 =
            AggregateReportFixture.getValidAggregateReportBuilder()
                    .setId("reportId2")
                    .setAggregateAttributionData(
                            new AggregateAttributionData.Builder()
                                    .setId(2L)
                                    .setContributions(CONTRIBUTIONS_2)
                                    .build())
                    .setSourceId("source2")
                    .setTriggerId("trigger2")
                    .build();

    @Mock private IMeasurementDao mMeasurementDao;
    @Mock private ITransaction mTransaction;
    @Mock private AggregatableAttributionSource mAggregatableAttributionSource1;
    @Mock private AggregatableAttributionSource mAggregatableAttributionSource2;
    @Mock private AggregatableAttributionTrigger mAggregatableAttributionTrigger1;
    @Mock private AggregatableAttributionTrigger mAggregatableAttributionTrigger2;

    private MeasurementDataDeleter mMeasurementDataDeleter;

    private class FakeDatastoreManager extends DatastoreManager {
        @Override
        public ITransaction createNewTransaction() {
            return mTransaction;
        }

        @Override
        public IMeasurementDao getMeasurementDao() {
            return mMeasurementDao;
        }
    }

    @Before
    public void setUp() throws Exception {
        mMeasurementDataDeleter = new MeasurementDataDeleter(new FakeDatastoreManager());
    }

    @Test
    public void resetAggregateContributions_hasMatchingReports_resetsContributions()
            throws DatastoreException {
        // Setup
        Source source1 =
                SourceFixture.getValidSourceBuilder()
                        .setId("source1")
                        .setAggregatableAttributionSource(mAggregatableAttributionSource1)
                        .setAggregateContributions(32666)
                        .build();
        Source source2 =
                SourceFixture.getValidSourceBuilder()
                        .setId("source2")
                        .setAggregatableAttributionSource(mAggregatableAttributionSource2)
                        .setAggregateContributions(6235)
                        .build();
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("trigger1")
                        .setAggregatableAttributionTrigger(mAggregatableAttributionTrigger1)
                        .build();
        Trigger trigger2 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("trigger2")
                        .setAggregatableAttributionTrigger(mAggregatableAttributionTrigger2)
                        .build();

        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);
        when(mMeasurementDao.getSource(source2.getId())).thenReturn(source2);
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
        List<String> triggersToDelete = ImmutableList.of(trigger1.getId(), trigger2.getId());
        when(mMeasurementDao.fetchMatchingAggregateReports(triggersToDelete))
                .thenReturn(Arrays.asList(AGGREGATE_REPORT_1, AGGREGATE_REPORT_2));

        // Execute
        mMeasurementDataDeleter.resetAggregateContributions(triggersToDelete);

        // Verify
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(2))
                .updateSourceAggregateContributions(sourceCaptor.capture());
        assertEquals(2, sourceCaptor.getAllValues().size());
        assertEquals(
                32534,
                sourceCaptor.getAllValues().get(0).getAggregateContributions()); // 32666-87-45
        assertEquals(
                781,
                sourceCaptor.getAllValues().get(1).getAggregateContributions()); // 6235-3454-2000
    }

    @Test
    public void resetAggregateContributions_withSourceContributionsGoingBelowZero_resetsToZero()
            throws DatastoreException {
        // Setup
        Source source1 =
                SourceFixture.getValidSourceBuilder()
                        .setId("source1")
                        .setAggregatableAttributionSource(mAggregatableAttributionSource1)
                        .setAggregateContributions(10)
                        .build();
        Trigger trigger1 =
                TriggerFixture.getValidTriggerBuilder()
                        .setId("trigger1")
                        .setAggregatableAttributionTrigger(mAggregatableAttributionTrigger1)
                        .build();

        when(mMeasurementDao.getSource(source1.getId())).thenReturn(source1);
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        List<String> triggersToDelete = ImmutableList.of(trigger1.getId());
        when(mMeasurementDao.fetchMatchingAggregateReports(triggersToDelete))
                .thenReturn(Collections.singletonList(AGGREGATE_REPORT_1));

        // Execute
        mMeasurementDataDeleter.resetAggregateContributions(triggersToDelete);

        // Verify
        ArgumentCaptor<Source> sourceCaptor = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(1))
                .updateSourceAggregateContributions(sourceCaptor.capture());
        assertEquals(1, sourceCaptor.getAllValues().size());
        assertEquals(0, sourceCaptor.getValue().getAggregateContributions());
    }
}
