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
package com.android.adservices.common;

import com.android.adservices.common.annotations.SetDefaultLogcatTags;
import com.android.adservices.shared.testing.CallSuper;
import com.android.adservices.shared.testing.annotations.SetSyncDisabledModeForTest;

import org.junit.ClassRule;
import org.junit.Rule;

/**
 * Base class for all CTS tests.
 *
 * <p>Contains only the bare minimum functionality required by them, like custom JUnit rules.
 *
 * <p>In fact, this class "reserves" the first 10 rules (as defined by order), so subclasses should
 * start defining rules with {@code order = 11} (although for now they can use {@code order = 0} for
 * {@code SdkLevelSupportRule}, as that rule cannot be defined here yet.
 */
@SetDefaultLogcatTags
@SetSyncDisabledModeForTest
public abstract class AdServicesCtsTestCase extends AdServicesTestCase {

    // TODO(b/295321663): move these constants (and those from LogFactory
    protected static final String LOGCAT_TAG_ADSERVICES = "adservices";
    protected static final String LOGCAT_TAG_ADSERVICES_SERVICE = LOGCAT_TAG_ADSERVICES + "-system";
    public static final String LOGCAT_TAG_TOPICS = LOGCAT_TAG_ADSERVICES + ".topics";
    public static final String LOGCAT_TAG_FLEDGE = LOGCAT_TAG_ADSERVICES + ".fledge";
    public static final String LOGCAT_TAG_MEASUREMENT = LOGCAT_TAG_ADSERVICES + ".measurement";
    protected static final String LOGCAT_TAG_UI = LOGCAT_TAG_ADSERVICES + ".ui";
    protected static final String LOGCAT_TAG_ADID = LOGCAT_TAG_ADSERVICES + ".adid";
    protected static final String LOGCAT_TAG_APPSETID = LOGCAT_TAG_ADSERVICES + ".appsetid";

    @ClassRule
    public static final AdServicesFlagsPreparerClassRule sFlagsPreparer =
            new AdServicesFlagsPreparerClassRule();

    @Rule(order = 5)
    public final AdServicesFlagsSetterRule flags = getAdServicesFlagsSetterRule();

    /**
     * Gets the {@link AdServicesFlagsSetterRule} for this test.
     *
     * <p>By default returns a rule with just the bare minimum set (like {@code logcat} tags) and
     * subclasses can customize it using class annotations (such as {@link
     * com.android.adservices.shared.testing.annotations.SetFlagEnabled}), but subclasses could
     * extend it to support more complex scenarios.
     */
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.newInstance();
    }

    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        assertTestClassHasNoFieldsFromSuperclass(
                AdServicesCtsTestCase.class, "sFlagsPreparer", "flags");
    }
}
