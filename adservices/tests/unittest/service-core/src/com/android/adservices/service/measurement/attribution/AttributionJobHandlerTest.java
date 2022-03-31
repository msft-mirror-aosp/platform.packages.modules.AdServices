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

package com.android.adservices.service.measurement.attribution;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.DbHelper;
import com.android.adservices.data.measurement.DatastoreException;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.data.measurement.DatastoreManagerFactory;
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.data.measurement.ITransaction;
import com.android.adservices.service.measurement.AdtechUrl;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Unit test for {@link AttributionJobHandler}
 */
@RunWith(MockitoJUnitRunner.class)
public class AttributionJobHandlerTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    DatastoreManager mDatastoreManager;

    @Mock
    IMeasurementDao mMeasurementDao;

    @Mock
    ITransaction mTransaction;

    class FakeDatastoreManager extends DatastoreManager {

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
    public void setUp() {
        mDatastoreManager = new FakeDatastoreManager();
    }

    @Before
    public void before() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        List<AdtechUrl> adtechUrlList = new ArrayList<>();
        adtechUrlList.add(buildAdtechUrl(
                "https://test1.example.com", "test1"));
        adtechUrlList.add(buildAdtechUrl(
                "https://test2.example.com", "test1"));
        adtechUrlList.add(buildAdtechUrl(
                "https://test3.example.com", "test3"));
        for (AdtechUrl adtechUrl : adtechUrlList) {
            ContentValues values = new ContentValues();
            values.put("postback_url", adtechUrl.getPostbackUrl());
            values.put("ad_tech_id", adtechUrl.getAdtechId());
            long row = db.insert("msmt_adtech_urls", null, values);
            Assert.assertNotEquals("AdtechUrl insertion failed", -1, row);
        }
    }

    @After
    public void after() {
        SQLiteDatabase db = DbHelper.getInstance(sContext).getWritableDatabase();
        db.delete("msmt_adtech_urls", null, null);
    }

    @Test
    public void shouldIgnoreNonPendingTrigger() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.IGNORED).build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        verify(mMeasurementDao).getTrigger(trigger.getId());
        verify(mMeasurementDao, never()).updateTriggerStatus(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldIgnoreIfNoSourcesFound() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.PENDING).build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(new ArrayList<>());
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao).updateTriggerStatus(triggerArg.capture());
        Assert.assertEquals(Trigger.Status.IGNORED, triggerArg.getValue().getStatus());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRejectBasedOnDedupKey() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.PENDING)
                .setDedupKey(2L)
                .build();
        Source source = new Source.Builder()
                .setDedupKeys(Arrays.asList(1L, 2L))
                .build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao).updateTriggerStatus(triggerArg.capture());
        Assert.assertEquals(Trigger.Status.IGNORED, triggerArg.getValue().getStatus());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotAddIfRateLimitExceeded() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.PENDING)
                .build();
        Source source = new Source.Builder().build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(105L);
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        verify(mMeasurementDao).getAttributionsPerRateLimitWindow(source, trigger);
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao).updateTriggerStatus(triggerArg.capture());
        Assert.assertEquals(Trigger.Status.IGNORED, triggerArg.getValue().getStatus());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldIgnoreForMaxReportsPerSource() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.PENDING)
                .build();
        Source source = new Source.Builder().build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport2 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport3 = new EventReport.Builder().setStatus(
                EventReport.Status.DELIVERED).build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao).updateTriggerStatus(triggerArg.capture());
        Assert.assertEquals(Trigger.Status.IGNORED, triggerArg.getValue().getStatus());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldNotReplaceHighPriorityReports() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setPriority(100)
                .setStatus(Trigger.Status.PENDING)
                .build();
        Source source = new Source.Builder().build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 = new EventReport.Builder()
                .setStatus(EventReport.Status.PENDING)
                .setTriggerPriority(200)
                .build();
        EventReport eventReport2 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        EventReport eventReport3 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao).updateTriggerStatus(triggerArg.capture());
        Assert.assertEquals(Trigger.Status.IGNORED, triggerArg.getValue().getStatus());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldDoSimpleAttribution() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.PENDING)
                .setDedupKey(1L)
                .build();
        Source source = new Source.Builder().build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(mMeasurementDao.getSourceEventReports(any())).thenReturn(new ArrayList<>());
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao).updateTriggerStatus(triggerArg.capture());
        Assert.assertEquals(Trigger.Status.ATTRIBUTED, triggerArg.getValue().getStatus());
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceDedupKeys(sourceArg.capture());
        Assert.assertEquals(sourceArg.getValue().getDedupKeys(), Collections.singletonList(1L));
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldIgnoreLowPrioritySourceWhileAttribution() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.PENDING)
                .setDedupKey(2L)
                .build();
        Source source1 = new Source.Builder()
                .setPriority(100L)
                .setEventTime(1L)
                .build();
        Source source2 = new Source.Builder()
                .setPriority(200L)
                .setEventTime(2L)
                .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source1);
        matchingSourceList.add(source2);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        trigger.setStatus(Trigger.Status.ATTRIBUTED);
        verify(mMeasurementDao).updateSourceStatus(matchingSourceList, Source.Status.IGNORED);
        Assert.assertEquals(1, matchingSourceList.size());
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao).updateTriggerStatus(triggerArg.capture());
        Assert.assertEquals(Trigger.Status.ATTRIBUTED, triggerArg.getValue().getStatus());
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao).updateSourceDedupKeys(sourceArg.capture());
        Assert.assertEquals(sourceArg.getValue().getDedupKeys(), Collections.singletonList(2L));
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldReplaceLowPriorityReportWhileAttribution() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("triggerId1")
                .setPriority(200)
                .setStatus(Trigger.Status.PENDING)
                .build();
        Source source = spy(Source.class);
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(trigger.getId())).thenReturn(trigger);
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        EventReport eventReport1 = new EventReport.Builder()
                .setStatus(EventReport.Status.PENDING)
                .setTriggerPriority(100)
                .setReportTime(5L)
                .build();
        EventReport eventReport2 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .setReportTime(5L)
                .build();
        EventReport eventReport3 = new EventReport.Builder()
                .setStatus(EventReport.Status.DELIVERED)
                .setReportTime(5L)
                .build();
        List<EventReport> matchingReports = new ArrayList<>();
        matchingReports.add(eventReport1);
        matchingReports.add(eventReport2);
        matchingReports.add(eventReport3);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getSourceEventReports(source)).thenReturn(matchingReports);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        when(source.getReportingTime(anyLong())).thenReturn(5L);
        when(source.getDedupKeys()).thenReturn(new ArrayList<>());
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        verify(mMeasurementDao).deleteEventReport(eventReport1);
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao).updateTriggerStatus(triggerArg.capture());
        Assert.assertEquals(Trigger.Status.ATTRIBUTED, triggerArg.getValue().getStatus());
        verify(mMeasurementDao).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void shouldRollbackOnFailure() throws DatastoreException {
        Trigger trigger = new Trigger.Builder()
                .setId("1")
                .setStatus(Trigger.Status.PENDING)
                .setDedupKey(1L)
                .build();
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Collections.singletonList(trigger.getId()));
        when(mMeasurementDao.getTrigger(anyString())).thenReturn(trigger);
        Source source = new Source.Builder().build();
        List<Source> matchingSourceList = new ArrayList<>();
        matchingSourceList.add(source);
        when(mMeasurementDao.getMatchingActiveSources(trigger)).thenReturn(matchingSourceList);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        // Failure
        doThrow(new DatastoreException("Simulating failure")).when(mMeasurementDao)
                .updateTriggerStatus(any());
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        attributionService.performPendingAttributions();
        verify(mMeasurementDao).getTrigger(anyString());
        verify(mMeasurementDao).getMatchingActiveSources(any());
        verify(mMeasurementDao).getAttributionsPerRateLimitWindow(any(), any());
        verify(mMeasurementDao, never()).updateSourceDedupKeys(any());
        verify(mMeasurementDao, never()).insertEventReport(any());
        verify(mTransaction, times(2)).begin();
        verify(mTransaction).rollback();
        verify(mTransaction, times(2)).end();
    }

    @Test
    public void testFindAdtechUrl() {
        AttributionJobHandler attributionJobHandler = new AttributionJobHandler(
                DatastoreManagerFactory.getDatastoreManager(sContext));
        AdtechUrl adtechUrl = attributionJobHandler.findAdtechUrl(
                "https://test1.example.com");
        Assert.assertNotNull(adtechUrl);
        Assert.assertEquals(adtechUrl.getAdtechId(), "test1");
    }

    @Test
    public void testGetAllAdtechUrl() {
        AttributionJobHandler attributionJobHandler = new AttributionJobHandler(
                DatastoreManagerFactory.getDatastoreManager(sContext));
        List<String> urls = attributionJobHandler.getAllAdtechUrls(
                "https://test1.example.com");
        Assert.assertEquals(urls.size(), 2);
    }
    private AdtechUrl buildAdtechUrl(String postbackUrl, String adtechId) {
        return new AdtechUrl.Builder().setPostbackUrl(postbackUrl).setAdtechId(adtechId).build();
    }

    @Test
    public void shouldPerformMultipleAttributions() throws DatastoreException {
        Trigger trigger1 = new Trigger.Builder()
                .setId("triggerId1")
                .setStatus(Trigger.Status.PENDING)
                .setDedupKey(1L)
                .build();
        Trigger trigger2 = new Trigger.Builder()
                .setId("triggerId2")
                .setStatus(Trigger.Status.PENDING)
                .setDedupKey(2L)
                .build();
        List<Trigger> triggers = new ArrayList<>();
        triggers.add(trigger1);
        triggers.add(trigger2);
        List<Source> matchingSourceList1 = new ArrayList<>();
        matchingSourceList1.add(new Source.Builder().build());
        List<Source> matchingSourceList2 = new ArrayList<>();
        matchingSourceList2.add(new Source.Builder().build());
        when(mMeasurementDao.getPendingTriggerIds())
                .thenReturn(Arrays.asList(trigger1.getId(), trigger2.getId()));
        when(mMeasurementDao.getTrigger(trigger1.getId())).thenReturn(trigger1);
        when(mMeasurementDao.getTrigger(trigger2.getId())).thenReturn(trigger2);
        when(mMeasurementDao.getMatchingActiveSources(trigger1)).thenReturn(matchingSourceList1);
        when(mMeasurementDao.getMatchingActiveSources(trigger2)).thenReturn(matchingSourceList2);
        when(mMeasurementDao.getAttributionsPerRateLimitWindow(any(), any())).thenReturn(5L);
        AttributionJobHandler attributionService = new AttributionJobHandler(mDatastoreManager);
        Assert.assertTrue(attributionService.performPendingAttributions());
        // Verify trigger status updates.
        ArgumentCaptor<Trigger> triggerArg = ArgumentCaptor.forClass(Trigger.class);
        verify(mMeasurementDao, times(2)).updateTriggerStatus(triggerArg.capture());
        List<Trigger> statusArgs = triggerArg.getAllValues();
        for (int i = 0; i < statusArgs.size(); i++) {
            Assert.assertEquals(Trigger.Status.ATTRIBUTED, statusArgs.get(i).getStatus());
            Assert.assertEquals(triggers.get(i).getId(), statusArgs.get(i).getId());
        }
        // Verify source dedup key updates.
        ArgumentCaptor<Source> sourceArg = ArgumentCaptor.forClass(Source.class);
        verify(mMeasurementDao, times(2))
                .updateSourceDedupKeys(sourceArg.capture());
        List<Source> dedupArgs = sourceArg.getAllValues();
        for (int i = 0; i < dedupArgs.size(); i++) {
            Assert.assertEquals(dedupArgs.get(i).getDedupKeys(),
                    Collections.singletonList(triggers.get(i).getDedupKey()));
        }
        // Verify new event report insertion.
        ArgumentCaptor<EventReport> reportArg = ArgumentCaptor.forClass(EventReport.class);
        verify(mMeasurementDao, times(2))
                .insertEventReport(reportArg.capture());
        List<EventReport> newReportArgs = reportArg.getAllValues();
        for (int i = 0; i < newReportArgs.size(); i++) {
            Assert.assertEquals(newReportArgs.get(i).getTriggerDedupKey(),
                    triggers.get(i).getDedupKey());
        }
    }
}
