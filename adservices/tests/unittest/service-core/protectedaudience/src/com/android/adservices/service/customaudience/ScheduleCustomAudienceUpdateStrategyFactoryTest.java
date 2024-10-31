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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.MIN_DELAY;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;

import org.junit.Test;
import org.mockito.Mock;

public class ScheduleCustomAudienceUpdateStrategyFactoryTest extends AdServicesMockitoTestCase {

    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Test
    public void testCreateStrategy_AdditionalScheduleRequestsFalse_ReturnsDisabledStrategy() {
        ScheduleCustomAudienceUpdateStrategy strategy =
                ScheduleCustomAudienceUpdateStrategyFactory.createStrategy(
                        mContext,
                        mCustomAudienceDaoMock,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        FledgeAuthorizationFilter.create(
                                mContext, AdServicesLoggerImpl.getInstance()),
                        MIN_DELAY,
                        /* additionalScheduleRequestsEnabled= */ false,
                        /* disableFledgeEnrollmentCheck= */ false,
                        mAdServicesLoggerMock);

        expect.that(strategy).isInstanceOf(AdditionalScheduleRequestsDisabledStrategy.class);
    }

    @Test
    public void testCreateStrategy_AdditionalScheduleRequestsTrue_ReturnsEnabledStrategy() {
        ScheduleCustomAudienceUpdateStrategy strategy =
                ScheduleCustomAudienceUpdateStrategyFactory.createStrategy(
                        mContext,
                        mCustomAudienceDaoMock,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        FledgeAuthorizationFilter.create(
                                mContext, AdServicesLoggerImpl.getInstance()),
                        MIN_DELAY,
                        /* additionalScheduleRequestsEnabled= */ true,
                        /* disableFledgeEnrollmentCheck= */ false,
                        mAdServicesLoggerMock);

        expect.that(strategy).isInstanceOf(AdditionalScheduleRequestsEnabledStrategy.class);
    }
}
