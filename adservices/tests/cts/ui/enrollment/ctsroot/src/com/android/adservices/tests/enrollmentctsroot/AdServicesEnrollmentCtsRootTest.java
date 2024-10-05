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
package com.android.adservices.tests.enrollmentctsroot;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesModuleState;
import android.adservices.common.AdServicesModuleUserChoice;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.Module;
import android.adservices.common.NotificationType;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.adservices.common.AdServicesCtsTestCase;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public final class AdServicesEnrollmentCtsRootTest extends AdServicesCtsTestCase
        implements EnrollmentCtsRootTestFlags {
    /**
     * Verify that for GA, ROW devices with non zeroed-out AdId, the GA ROW notification is
     * displayed. Note that it extends AdServicesCtsTestCase, it is not part of Cts test suite.
     */
    AdServicesCommonManager mCommonManager;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastT());

        AdservicesTestHelper.killAdservicesProcess(mContext);
        mCommonManager = AdServicesCommonManager.get(mContext);
    }

    @Test
    public void testRequestAdServicesModuleOverrides() throws Exception {

        List<AdServicesModuleState> adServicesModuleStateList = new ArrayList<>();
        adServicesModuleStateList.add(
                new AdServicesModuleState.Builder()
                        .setModule(Module.MEASUREMENT)
                        .setModuleState(AdServicesModuleState.MODULE_STATE_ENABLED)
                        .build());
        int notificationType = NotificationType.NOTIFICATION_ONGOING;
        ListenableFuture<Integer> responseFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            mCommonManager.requestAdServicesModuleOverrides(
                                    adServicesModuleStateList,
                                    notificationType,
                                    Executors.newCachedThreadPool(),
                                    new AdServicesOutcomeReceiver<>() {
                                        @Override
                                        public void onResult(Void result) {
                                            completer.set(AdServicesStatusUtils.STATUS_SUCCESS);
                                        }

                                        @Override
                                        public void onError(Exception error) {
                                            completer.set(AdServicesStatusUtils.STATUS_IO_ERROR);
                                        }
                                    });
                            return "requestAdServicesModuleOverrides";
                        });
        int response = responseFuture.get();
        expect.that(response).isEqualTo(AdServicesStatusUtils.STATUS_SUCCESS);
    }

    @Test
    public void testRequestAdServicesModuleUserChoices() throws Exception {

        List<AdServicesModuleUserChoice> adServicesModuleUserChoices =
                List.of(
                        new AdServicesModuleUserChoice.Builder()
                                .setModule(Module.MEASUREMENT)
                                .setUserChoice(AdServicesModuleUserChoice.USER_CHOICE_OPTED_IN)
                                .build());

        ListenableFuture<Integer> responseFuture =
                CallbackToFutureAdapter.getFuture(
                        completer -> {
                            mCommonManager.requestAdServicesModuleUserChoices(
                                    adServicesModuleUserChoices,
                                    Executors.newCachedThreadPool(),
                                    new AdServicesOutcomeReceiver<>() {
                                        @Override
                                        public void onResult(Void result) {
                                            completer.set(null);
                                        }

                                        @Override
                                        public void onError(Exception error) {
                                            completer.set(AdServicesStatusUtils.STATUS_IO_ERROR);
                                        }
                                    });
                            return "requestAdServicesModuleUserChoices";
                        });
        int response = responseFuture.get();
        expect.that(response).isEqualTo(AdServicesStatusUtils.STATUS_SUCCESS);
    }
}
