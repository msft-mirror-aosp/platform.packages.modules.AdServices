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

package com.android.adservices.service.signals.evict;

import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIGNAL_EVICTOR_FIFO;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIGNAL_EVICTOR_PRIORITIZED_FIFO;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SIGNAL_EVICTOR_UNSPECIFIED;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class SignalEvictionControllerTest extends AdServicesMockitoTestCase {
    @Mock private SignalEvictor mSignalEvictorMock;
    @Mock private FifoSignalEvictor mFifoSignalEvictorMock;
    @Mock private PrioritizedFifoSignalEvictor mPrioritizedFifoSignalEvictorMock;
    @Mock private UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLoggerMock;
    private SignalEvictionController mController;

    @Before
    public void setup() {
        mController =
                new SignalEvictionController(
                        List.of(
                                mSignalEvictorMock,
                                mFifoSignalEvictorMock,
                                mPrioritizedFifoSignalEvictorMock),
                        /* maxAllowedSignalSize= */ 100,
                        /* maxAllowedSignalSizeWithOversubscription= */ 200);
    }

    @Test
    public void evict_3Evictor_mSignalEvictorMockReturnFalse() {
        when(mSignalEvictorMock.evict(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(false);

        mController.evict(
                CommonFixture.VALID_BUYER_1,
                /* updatedSignals= */ List.of(),
                new UpdateOutput(),
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mSignalEvictorMock).evict(any(), any(), any(), anyInt(), anyInt(), any());
        verifyNoMoreInteractions(
                mFifoSignalEvictorMock,
                mPrioritizedFifoSignalEvictorMock,
                mUpdateSignalsProcessReportedLoggerMock);
    }

    @Test
    public void evict_3Evictor_mFifoSignalEvictorMockReturnFalse() {
        when(mSignalEvictorMock.evict(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(true);
        when(mFifoSignalEvictorMock.evict(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(false);

        mController.evict(
                CommonFixture.VALID_BUYER_1,
                /* updatedSignals= */ List.of(),
                new UpdateOutput(),
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mSignalEvictorMock).evict(any(), any(), any(), anyInt(), anyInt(), any());
        verify(mFifoSignalEvictorMock).evict(any(), any(), any(), anyInt(), anyInt(), any());
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .addSignalEvictorUsed(SIGNAL_EVICTOR_UNSPECIFIED);
        verifyNoMoreInteractions(mPrioritizedFifoSignalEvictorMock);
    }

    @Test
    public void evict_3Evictor_mPrioritizedFifoSignalEvictorMockReturnFalse() {
        when(mSignalEvictorMock.evict(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(true);
        when(mFifoSignalEvictorMock.evict(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(true);
        when(mPrioritizedFifoSignalEvictorMock.evict(
                        any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(false);

        mController.evict(
                CommonFixture.VALID_BUYER_1,
                /* updatedSignals= */ List.of(),
                new UpdateOutput(),
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mSignalEvictorMock).evict(any(), any(), any(), anyInt(), anyInt(), any());
        verify(mFifoSignalEvictorMock).evict(any(), any(), any(), anyInt(), anyInt(), any());
        verify(mPrioritizedFifoSignalEvictorMock)
                .evict(any(), any(), any(), anyInt(), anyInt(), any());
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .addSignalEvictorUsed(SIGNAL_EVICTOR_UNSPECIFIED);
        verify(mUpdateSignalsProcessReportedLoggerMock).addSignalEvictorUsed(SIGNAL_EVICTOR_FIFO);
        verifyNoMoreInteractions(mUpdateSignalsProcessReportedLoggerMock);
    }

    @Test
    public void evict_3Evictor_usedAllEvictors() {
        when(mSignalEvictorMock.evict(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(true);
        when(mFifoSignalEvictorMock.evict(any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(true);
        when(mPrioritizedFifoSignalEvictorMock.evict(
                        any(), any(), any(), anyInt(), anyInt(), any()))
                .thenReturn(true);

        mController.evict(
                CommonFixture.VALID_BUYER_1,
                /* updatedSignals= */ List.of(),
                new UpdateOutput(),
                mUpdateSignalsProcessReportedLoggerMock);

        verify(mSignalEvictorMock).evict(any(), any(), any(), anyInt(), anyInt(), any());
        verify(mFifoSignalEvictorMock).evict(any(), any(), any(), anyInt(), anyInt(), any());
        verify(mPrioritizedFifoSignalEvictorMock)
                .evict(any(), any(), any(), anyInt(), anyInt(), any());
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .addSignalEvictorUsed(SIGNAL_EVICTOR_UNSPECIFIED);
        verify(mUpdateSignalsProcessReportedLoggerMock).addSignalEvictorUsed(SIGNAL_EVICTOR_FIFO);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .addSignalEvictorUsed(SIGNAL_EVICTOR_PRIORITIZED_FIFO);
        verifyNoMoreInteractions(mUpdateSignalsProcessReportedLoggerMock);
    }
}
