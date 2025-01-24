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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_QUANTITY_CHECKER_REACHED_MAX_NUMBER_OF_CUSTOM_AUDIENCE_PER_OWNER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_QUANTITY_CHECKER_REACHED_MAX_NUMBER_OF_OWNER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_QUANTITY_CHECKER_REACHED_MAX_NUMBER_OF_TOTAL_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceStats;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

@SetErrorLogUtilDefaultParams(ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FLEDGE)
public final class CustomAudienceQuantityCheckerTest extends AdServicesExtendedMockitoTestCase {

    @Mock private CustomAudienceDao mCustomAudienceDao;

    private CustomAudienceQuantityChecker mChecker;

    @Before
    public void setup() {
        mChecker = new CustomAudienceQuantityChecker(mCustomAudienceDao, mFakeFlags);
    }

    @Test
    public void testNullCustomAudience_throwNPE() {
        assertThrows(
                NullPointerException.class,
                () -> mChecker.check(null, CustomAudienceFixture.VALID_OWNER));
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testNullCallerPackageName_throwNPE() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mChecker.check(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .build(),
                                null));
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testExistOwnerAndOwnerReachMax_success() {
        when(mCustomAudienceDao.getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(20L)
                                .setPerOwnerCustomAudienceCount(1L)
                                .setTotalOwnerCount(
                                        mFakeFlags.getFledgeCustomAudienceMaxOwnerCount())
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setPerBuyerCustomAudienceCount(5L)
                                .build());

        mChecker.check(
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build(),
                CustomAudienceFixture.VALID_OWNER);
        verify(mCustomAudienceDao)
                .getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    @ExpectErrorLogUtilCall(errorCode =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_QUANTITY_CHECKER_REACHED_MAX_NUMBER_OF_OWNER)
    public void testOwnerExceedMax() {
        when(mCustomAudienceDao.getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(20L)
                                .setPerOwnerCustomAudienceCount(0L)
                                .setTotalOwnerCount(
                                        mFakeFlags.getFledgeCustomAudienceMaxOwnerCount())
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setPerBuyerCustomAudienceCount(5L)
                                .build());

        assertViolations(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mChecker.check(
                                        CustomAudienceFixture.getValidBuilderForBuyer(
                                                        CommonFixture.VALID_BUYER_1)
                                                .build(),
                                        CustomAudienceFixture.VALID_OWNER)),
                CustomAudienceQuantityChecker
                        .THE_MAX_NUMBER_OF_OWNER_ALLOWED_FOR_THE_DEVICE_HAD_REACHED);

        verify(mCustomAudienceDao)
                .getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    @ExpectErrorLogUtilCall(errorCode =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_QUANTITY_CHECKER_REACHED_MAX_NUMBER_OF_TOTAL_CUSTOM_AUDIENCE)
    public void testTotalCountExceedMax() {
        when(mCustomAudienceDao.getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(
                                        mFakeFlags.getFledgeCustomAudienceMaxCount())
                                .setPerOwnerCustomAudienceCount(0L)
                                .setTotalOwnerCount(1L)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setPerBuyerCustomAudienceCount(5L)
                                .build());

        assertViolations(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mChecker.check(
                                        CustomAudienceFixture.getValidBuilderForBuyer(
                                                        CommonFixture.VALID_BUYER_1)
                                                .build(),
                                        CustomAudienceFixture.VALID_OWNER)),
                CustomAudienceQuantityChecker
                        .THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_DEVICE_HAD_REACHED);
        verify(mCustomAudienceDao)
                .getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    @ExpectErrorLogUtilCall(errorCode =
            AD_SERVICES_ERROR_REPORTED__ERROR_CODE__CUSTOM_AUDIENCE_QUANTITY_CHECKER_REACHED_MAX_NUMBER_OF_CUSTOM_AUDIENCE_PER_OWNER)
    public void testPerOwnerCountExceedMax() {
        when(mCustomAudienceDao.getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(20L)
                                .setPerOwnerCustomAudienceCount(
                                        mFakeFlags.getFledgeCustomAudiencePerAppMaxCount())
                                .setTotalOwnerCount(1L)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setPerBuyerCustomAudienceCount(5L)
                                .build());

        assertViolations(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mChecker.check(
                                        CustomAudienceFixture.getValidBuilderForBuyer(
                                                        CommonFixture.VALID_BUYER_1)
                                                .build(),
                                        CustomAudienceFixture.VALID_OWNER)),
                CustomAudienceQuantityChecker
                        .THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_OWNER_HAD_REACHED);

        verify(mCustomAudienceDao)
                .getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testPerBuyerCountExceedMax() {
        when(mCustomAudienceDao.getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(20L)
                                .setPerBuyerCustomAudienceCount(
                                        mFakeFlags.getFledgeCustomAudiencePerBuyerMaxCount())
                                .setTotalBuyerCount(1L)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .build());

        assertViolations(
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mChecker.check(
                                        CustomAudienceFixture.getValidBuilderForBuyer(
                                                        CommonFixture.VALID_BUYER_1)
                                                .build(),
                                        CustomAudienceFixture.VALID_OWNER)),
                CustomAudienceQuantityChecker
                        .THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_BUYER_HAD_REACHED);

        verify(mCustomAudienceDao)
                .getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    @Test
    public void testAllGood() {
        when(mCustomAudienceDao.getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1))
                .thenReturn(
                        CustomAudienceStats.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setTotalCustomAudienceCount(0L)
                                .setPerOwnerCustomAudienceCount(0L)
                                .setTotalOwnerCount(0L)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setPerBuyerCustomAudienceCount(5L)
                                .build());
        mChecker.check(
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build(),
                CustomAudienceFixture.VALID_OWNER);

        verify(mCustomAudienceDao)
                .getCustomAudienceStats(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1);
        verifyNoMoreInteractions(mCustomAudienceDao);
    }

    private void assertViolations(Exception exception, String... violations) {
        expect.withMessage("mChecker.check").that(exception).hasMessageThat().isEqualTo(
                String.format(
                        CustomAudienceQuantityChecker.CUSTOM_AUDIENCE_QUANTITY_CHECK_FAILED,
                        List.of(violations)));
    }
}
