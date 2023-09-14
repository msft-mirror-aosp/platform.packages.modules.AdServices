/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.adselection;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.Uri;

import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class DebugReportSenderStrategyBatchImplTest {
    private static final DevContext DEV_CONTEXT = DevContext.createForDevOptionsDisabled();
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    private DebugReportSenderStrategyBatchImpl mDebugReportSender;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDebugReportSender =
                new DebugReportSenderStrategyBatchImpl(mAdSelectionDebugReportDao, DEV_CONTEXT);
    }

    @Test
    public void testConstructorWithInvalidParamsThrowsException() {
        Assert.assertThrows(
                NullPointerException.class,
                () -> new DebugReportSenderStrategyBatchImpl(null, DEV_CONTEXT));
        Assert.assertThrows(
                NullPointerException.class,
                () -> new DebugReportSenderStrategyBatchImpl(mAdSelectionDebugReportDao, null));
    }

    @Test
    public void testEnqueue() {
        Uri uri = Uri.parse("https://example.com/reportWin");
        mDebugReportSender.enqueue(uri);
    }

    @Test
    public void testEnqueueNullUriThrowsException() {
        Assert.assertThrows(NullPointerException.class, () -> mDebugReportSender.enqueue(null));
    }

    @Test
    public void testBatchEnqueue() {
        Uri uri1 = Uri.parse("https://example.com/reportWin");
        Uri uri2 = Uri.parse("https://example.com/reportLoss");
        mDebugReportSender.batchEnqueue(List.of(uri1, uri2));
    }

    @Test
    public void testBatchEnqueueNullUriThrowsException() {
        Assert.assertThrows(
                NullPointerException.class, () -> mDebugReportSender.batchEnqueue(null));
    }

    @Test
    public void testSend_withSingleReport_allRequestsAreSuccessful()
            throws ExecutionException, InterruptedException {
        Uri uri = Uri.parse("https://example.com/reportWin");
        doNothing().when(mAdSelectionDebugReportDao).persistAdSelectionDebugReporting(anyList());

        mDebugReportSender.enqueue(uri);
        ListenableFuture<Void> future = mDebugReportSender.flush();
        future.get();

        verify(mAdSelectionDebugReportDao, times(1)).persistAdSelectionDebugReporting(anyList());
    }

    @Test
    public void testSend_withMultipleReports_allRequestsAreSuccessful()
            throws ExecutionException, InterruptedException {
        Uri uri1 = Uri.parse("https://example.com/reportWin");
        Uri uri2 = Uri.parse("https://example.com/reportLoss");
        doNothing().when(mAdSelectionDebugReportDao).persistAdSelectionDebugReporting(anyList());

        mDebugReportSender.batchEnqueue(List.of(uri1, uri2));
        ListenableFuture<Void> future = mDebugReportSender.flush();
        future.get();

        verify(mAdSelectionDebugReportDao, times(1)).persistAdSelectionDebugReporting(anyList());
    }
}
