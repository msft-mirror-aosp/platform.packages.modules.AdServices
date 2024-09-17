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

package com.android.adservices.data.common;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.adselection.JsVersionHelper;

import com.google.common.collect.ImmutableMap;

import org.junit.Test;

public final class DecisionLogicTest extends AdServicesUnitTestCase {
    private static final String PAYLOAD = "sample payload";

    private static final Long VERSION = 1L;
    private static final ImmutableMap<Integer, Long> VERSION_MAP =
            ImmutableMap.of(JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS, VERSION);

    @Test
    public void testGetVersion() {
        DecisionLogic decisionLogic = DecisionLogic.create(PAYLOAD, VERSION_MAP);
        expect.withMessage("decisionLogic.getPayload()")
                .that(decisionLogic.getPayload())
                .isEqualTo(PAYLOAD);
        expect.withMessage("decisionLogic.getVersions()")
                .that(decisionLogic.getVersions())
                .isEqualTo(VERSION_MAP);
        expect.withMessage("decisionLogic.getVersion()")
                .that(
                        decisionLogic.getVersion(
                                JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS))
                .isEqualTo(VERSION);

        DecisionLogic decisionLogicWithEmptyVersions =
                DecisionLogic.create(PAYLOAD, ImmutableMap.of());
        expect.withMessage("decisionLogicWithEmptyVersions.getPayload()")
                .that(decisionLogicWithEmptyVersions.getPayload())
                .isEqualTo(PAYLOAD);
        expect.withMessage("decisionLogicWithEmptyVersions.getVersions()")
                .that(decisionLogicWithEmptyVersions.getVersions())
                .hasSize(0);
        expect.withMessage("decisionLogicWithEmptyVersions.getVersion()")
                .that(
                        decisionLogicWithEmptyVersions.getVersion(
                                JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS))
                .isEqualTo(JsVersionHelper.DEFAULT_JS_VERSION_IF_ABSENT);
    }
}
