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

package com.android.adservices.service.stats;

import static com.android.adservices.service.stats.FledgeAuctionServerExecutionLoggerImplTest.sCallerMetadata;

import static org.junit.Assert.assertTrue;

import com.android.adservices.service.Flags;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class FledgeAuctionServerExecutionLoggerFactoryTest {

    private static int UNKNOWN_API_CODE = -1;

    private AdServicesLogger mAdServicesLoggerMock;

    @Before
    public void setup() {
        mAdServicesLoggerMock = Mockito.spy(AdServicesLoggerImpl.getInstance());
    }

    @Test
    public void testGetFledgeAuctionServerApiUsageMetricsEnabled() {
        FledgeAuctionServerExecutionLoggerFactory fledgeAuctionServerExecutionLoggerFactory =
                new FledgeAuctionServerExecutionLoggerFactory(
                        sCallerMetadata,
                        Clock.getInstance(),
                        mAdServicesLoggerMock,
                        new FlagsWithGetFledgeAuctionServerApiUsageMetricsEnabled(),
                        UNKNOWN_API_CODE);
        assertTrue(fledgeAuctionServerExecutionLoggerFactory.getFledgeAuctionServerExecutionLogger()
                instanceof FledgeAuctionServerExecutionLoggerImpl);
    }

    @Test
    public void testGetFledgeAuctionServerApiUsageMetricsDisabled() {
        FledgeAuctionServerExecutionLoggerFactory fledgeAuctionServerExecutionLoggerFactory =
                new FledgeAuctionServerExecutionLoggerFactory(
                        sCallerMetadata,
                        Clock.getInstance(),
                        mAdServicesLoggerMock,
                        new FlagsWithGetFledgeAuctionServerApiUsageMetricsDisabled(),
                        UNKNOWN_API_CODE);
        assertTrue(fledgeAuctionServerExecutionLoggerFactory.getFledgeAuctionServerExecutionLogger()
                instanceof FledgeAuctionServerExecutionLoggerNoLoggingImpl);
    }

    private static class FlagsWithGetFledgeAuctionServerApiUsageMetricsEnabled implements Flags {
        @Override
        public boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
            return true;
        }
    }

    private static class FlagsWithGetFledgeAuctionServerApiUsageMetricsDisabled implements Flags {
        @Override
        public boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
            return false;
        }
    }
}
