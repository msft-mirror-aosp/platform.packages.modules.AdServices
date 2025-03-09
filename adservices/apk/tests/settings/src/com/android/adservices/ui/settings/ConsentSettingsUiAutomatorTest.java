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

package com.android.adservices.ui.settings;

import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_CONSENT_SOURCE_OF_TRUTH;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_APPSEARCH_CONSENT_DATA;
import static com.android.adservices.service.FlagsConstants.KEY_GA_UX_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_UI_DIALOGS_FEATURE_ENABLED;

import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Build;

import androidx.test.uiautomator.UiObject2;

import com.android.adservices.api.R;
import com.android.adservices.common.annotations.DisableGlobalKillSwitch;
import com.android.adservices.common.annotations.SetAllLogcatTags;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;
import com.android.adservices.shared.testing.annotations.SetStringFlag;
import com.android.adservices.ui.util.AdservicesSettingsUiTestCase;
import com.android.adservices.ui.util.ApkTestUtil;

import org.junit.Ignore;
import org.junit.Test;

@DisableGlobalKillSwitch
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_ACTIVITY_DEBUG_MODE)
@SetAllLogcatTags
@SetCompatModeFlags
@SetFlagDisabled(KEY_GA_UX_FEATURE_ENABLED)
@SetStringFlag(name = "debug_ux", value = "BETA_UX")
public final class ConsentSettingsUiAutomatorTest extends AdservicesSettingsUiTestCase {
    @Test
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 0)
    @SetFlagDisabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    @RequiresSdkLevelAtLeastT
    @Ignore("b/293366771")
    public void consentSystemServerOnlyTest() {
        consentTest(false);
    }

    @Test
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 1)
    @SetFlagDisabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    @Ignore("b/293366771")
    public void consentPpApiOnlyTest() {
        consentTest(false);
    }

    @Test
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 2)
    @SetFlagDisabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    @RequiresSdkLevelAtLeastT
    @Ignore("b/293366771")
    public void consentSystemServerAndPpApiTest() {
        consentTest(false);
    }

    @Test
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 0)
    @SetFlagEnabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    @RequiresSdkLevelAtLeastT
    @Ignore("b/293366771")
    public void consentSystemServerOnlyDialogsOnTest() {
        consentTest(true);
    }

    @Test
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 1)
    @SetFlagEnabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    @Ignore("b/293366771")
    public void consentPpApiOnlyDialogsOnTest() {
        consentTest(true);
    }

    @Test
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 2)
    @SetFlagEnabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    @RequiresSdkLevelAtLeastT
    @Ignore("b/293366771")
    public void consentSystemServerAndPpApiDialogsOnTest() {
        consentTest(true);
    }

    @Test
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 3)
    @SetFlagDisabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    @SetFlagEnabled(KEY_ENABLE_APPSEARCH_CONSENT_DATA)
    @RequiresSdkRange(atLeast = Build.VERSION_CODES.S, atMost = Build.VERSION_CODES.S_V2)
    @Ignore("b/293366771")
    public void consentAppSearchOnlyTest() {
        consentTest(false);
    }

    @Test
    @SetIntegerFlag(name = KEY_CONSENT_SOURCE_OF_TRUTH, value = 3)
    @SetFlagEnabled(KEY_UI_DIALOGS_FEATURE_ENABLED)
    @SetFlagEnabled(KEY_ENABLE_APPSEARCH_CONSENT_DATA)
    @RequiresSdkRange(atLeast = Build.VERSION_CODES.S, atMost = Build.VERSION_CODES.S_V2)
    @Ignore("b/293366771")
    public void consentAppSearchOnlyDialogsOnTest() {
        consentTest(true);
    }

    private void consentTest(boolean dialogsOn) {
        ApkTestUtil.launchSettingViewGivenUx(mDevice, LAUNCH_TIMEOUT, "BETA_UX");

        UiObject2 consentSwitch = ApkTestUtil.getConsentSwitch(mDevice);
        setConsentToFalse(dialogsOn);

        // click switch
        performSwitchClick(dialogsOn, consentSwitch);
        assertWithMessage("consent switch checked should be true")
                .that(consentSwitch.isChecked())
                .isTrue();

        // click switch
        performSwitchClick(dialogsOn, consentSwitch);
        assertWithMessage("consent switch checked should be false")
                .that(consentSwitch.isChecked())
                .isFalse();
    }

    private void setConsentToFalse(boolean dialogsOn) {
        UiObject2 consentSwitch = ApkTestUtil.getConsentSwitch(mDevice);
        if (consentSwitch.isChecked()) {
            performSwitchClick(dialogsOn, consentSwitch);
        }
    }

    private void performSwitchClick(boolean dialogsOn, UiObject2 mainSwitch) {
        if (dialogsOn && mainSwitch.isChecked()) {
            mainSwitch.click();
            UiObject2 dialogTitle =
                    ApkTestUtil.getElement(mDevice, R.string.settingsUI_dialog_opt_out_title);
            UiObject2 positiveText =
                    ApkTestUtil.getElement(
                            mDevice, R.string.settingsUI_dialog_opt_out_positive_text);
            ApkTestUtil.assertNotNull(dialogTitle, R.string.settingsUI_dialog_opt_out_title);
            ApkTestUtil.assertNotNull(
                    positiveText, R.string.settingsUI_dialog_opt_out_positive_text);
            positiveText.click();
        } else {
            mainSwitch.click();
        }
    }
}
