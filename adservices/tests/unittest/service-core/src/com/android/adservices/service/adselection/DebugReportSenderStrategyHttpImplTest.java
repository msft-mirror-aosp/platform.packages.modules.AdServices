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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.net.Uri;

import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class DebugReportSenderStrategyHttpImplTest {

    @Mock
    private AdServicesHttpsClient mMockHttpsClient;
    private DebugReportSenderStrategyHttpImpl mDebugReportSender;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mDebugReportSender = new DebugReportSenderStrategyHttpImpl(mMockHttpsClient);
    }

    @Test
    public void testSend_withSingleReport_allRequestsAreSuccessful()
            throws ExecutionException, InterruptedException {
        Uri uri = Uri.parse("http://example.com/reportWin");
        doReturn(Futures.immediateVoidFuture()).when(mMockHttpsClient).getAndReadNothing(uri);

        mDebugReportSender.enqueue(uri);
        ListenableFuture<Void> future = mDebugReportSender.flush();
        future.get();

        verify(mMockHttpsClient, times(1)).getAndReadNothing(uri);
    }

    @Test
    public void testSend_withMultipleReports_allRequestsAreSuccessful()
            throws ExecutionException, InterruptedException {
        Uri uri1 = Uri.parse("http://example.com/reportWin");
        Uri uri2 = Uri.parse("http://example.com/reportLoss");
        doReturn(Futures.immediateVoidFuture()).when(mMockHttpsClient).getAndReadNothing(uri1);
        doReturn(Futures.immediateVoidFuture()).when(mMockHttpsClient).getAndReadNothing(uri2);

        mDebugReportSender.batchEnqueue(List.of(uri1, uri2));
        ListenableFuture<Void> future = mDebugReportSender.flush();
        future.get();

        verify(mMockHttpsClient, times(1)).getAndReadNothing(uri1);
        verify(mMockHttpsClient, times(1)).getAndReadNothing(uri2);
    }

    @Test
    public void testSend_withFailedRequest_otherRequestsAreSuccessful()
            throws ExecutionException, InterruptedException {
        Uri uri1 = Uri.parse("http://example.com/reportWin");
        Uri uri2 = Uri.parse("http://example.com/reportLoss");
        doReturn(Futures.immediateVoidFuture()).when(mMockHttpsClient).getAndReadNothing(uri1);
        doReturn(Futures.immediateFailedFuture(new Exception())).when(
                mMockHttpsClient).getAndReadNothing(uri2);

        mDebugReportSender.enqueue(uri1);
        mDebugReportSender.enqueue(uri2);
        ListenableFuture<?> future = mDebugReportSender.flush();
        future.get();

        verify(mMockHttpsClient, times(1)).getAndReadNothing(uri1);
        verify(mMockHttpsClient, times(1)).getAndReadNothing(uri2);
    }
}
