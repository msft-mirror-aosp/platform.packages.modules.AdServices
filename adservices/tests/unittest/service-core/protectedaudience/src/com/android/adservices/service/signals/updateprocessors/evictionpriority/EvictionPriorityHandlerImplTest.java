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

package com.android.adservices.service.signals.updateprocessors.evictionpriority;

import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.service.signals.evict.EvictionPriority;
import com.android.adservices.service.stats.pas.UpdateSignalsProcessReportedLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mock;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class EvictionPriorityHandlerImplTest extends AdServicesMockitoTestCase {
    private static final String EVICTION_PRIORITY = "eviction_priority";

    private final EvictionPriorityHandler mEvictionPriorityHandler =
            new EvictionPriorityHandlerImpl();

    @Mock private UpdateSignalsProcessReportedLogger mUpdateSignalsProcessReportedLoggerMock;

    @Test
    public void testGetEvictionPriority_noEvictionPriority() {
        JSONObject update = new JSONObject();

        EvictionPriority evictionPriority =
                mEvictionPriorityHandler.getEvictionPriority(
                        BB_KEY_1, update, mUpdateSignalsProcessReportedLoggerMock);

        verifyNoMoreInteractions(mUpdateSignalsProcessReportedLoggerMock);
        expect.withMessage("evictionPriority")
                .that(evictionPriority)
                .isEqualTo(EvictionPriority.DEFAULT);
    }

    @Test
    public void testGetEvictionPriority_invalidEvictionPriority_wrongString() throws Exception {
        String invalidEvictionPriority = "NOT_AN_EVICTION_PRIORITY";
        JSONObject update = new JSONObject();
        update.put(EVICTION_PRIORITY, invalidEvictionPriority);

        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () ->
                        mEvictionPriorityHandler.getEvictionPriority(
                                BB_KEY_1, update, mUpdateSignalsProcessReportedLoggerMock));

        verifyNoMoreInteractions(mUpdateSignalsProcessReportedLoggerMock);
    }

    @Test
    public void testGetEvictionPriority_invalidEvictionPriority_wrongType() throws Exception {
        JSONObject invalidEvictionPriority = new JSONObject();
        JSONObject update = new JSONObject();
        update.put(EVICTION_PRIORITY, invalidEvictionPriority);

        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () ->
                        mEvictionPriorityHandler.getEvictionPriority(
                                BB_KEY_1, update, mUpdateSignalsProcessReportedLoggerMock));

        verifyNoMoreInteractions(mUpdateSignalsProcessReportedLoggerMock);
    }

    @Test
    public void testGetEvictionPriority_invalidEvictionPriority_valueInsteadOfName()
            throws Exception {
        int invalidEvictionPriority = EvictionPriority.EVICT_LATER.getValue();
        JSONObject update = new JSONObject();
        update.put(EVICTION_PRIORITY, invalidEvictionPriority);

        assertThrows(
                "Expected exception",
                IllegalArgumentException.class,
                () ->
                        mEvictionPriorityHandler.getEvictionPriority(
                                BB_KEY_1, update, mUpdateSignalsProcessReportedLoggerMock));

        verifyNoMoreInteractions(mUpdateSignalsProcessReportedLoggerMock);
    }

    @Test
    public void testGetEvictionPriority_validEvictionPriority() throws Exception {
        JSONObject update = new JSONObject();
        update.put(EVICTION_PRIORITY, EvictionPriority.EVICT_SOONER.name());

        EvictionPriority evictionPriority =
                mEvictionPriorityHandler.getEvictionPriority(
                        BB_KEY_1, update, mUpdateSignalsProcessReportedLoggerMock);

        verify(mUpdateSignalsProcessReportedLoggerMock)
                .addUpdatedSignalWithEvictionPriorityForCount(BB_KEY_1);
        verify(mUpdateSignalsProcessReportedLoggerMock)
                .addUpdatedSignalEvictionPriority(EvictionPriority.EVICT_SOONER);
        expect.withMessage("evictionPriority")
                .that(evictionPriority)
                .isEqualTo(EvictionPriority.EVICT_SOONER);
    }
}
