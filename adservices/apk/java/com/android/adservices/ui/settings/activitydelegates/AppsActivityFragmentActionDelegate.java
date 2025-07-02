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

package com.android.adservices.ui.settings.activitydelegates;

import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_BLCOKED_TOIPCS_BUTTON;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_BLOCKED_APPS_WHEN_EMPTY_STATE_BUTTON;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_EMPTY_APPS_HIDDEN_SECTION;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_FOOTER;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_FOOTER_LEARN_MORE_LINK;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_LIST;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_NO_APPS_MESSAGE_SECTION;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_NO_TOPICS_STATE;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_PRIVACY_LEARN_MORE_LINK;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_RESET_TOPICS_BUTTON;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_SWITCH_BAR;
import static com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment.APPS_TOP_INTRO;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.Log;
import android.util.Pair;
import android.view.Gravity;

import androidx.annotation.RequiresApi;
import androidx.lifecycle.Observer;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.adservices.api.R;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.consent.App;
import com.android.adservices.service.stats.UiStatsLogger;
import com.android.adservices.ui.settings.DialogFragmentManager;
import com.android.adservices.ui.settings.DialogManager;
import com.android.adservices.ui.settings.activities.AppsActivity;
import com.android.adservices.ui.settings.activities.BlockedAppsActivity;
import com.android.adservices.ui.settings.expressivefragments.AppsActivityFragment;
import com.android.adservices.ui.settings.preferences.AdservicesTwoTargetPreference;
import com.android.adservices.ui.settings.viewmodels.AppsViewModel;
import com.android.settingslib.widget.ButtonPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.IntroPreference;
import com.android.settingslib.widget.MainSwitchPreference;
import com.android.settingslib.widget.TopIntroPreference;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

@RequiresApi(Build.VERSION_CODES.S)
public class AppsActivityFragmentActionDelegate extends BaseActionDelegate {
    private final AppsViewModel mAppsViewModel;
    private final AppsActivityFragment mFragment;

    public AppsActivityFragmentActionDelegate(
            AppsActivity appsActivity, AppsViewModel appsViewModel, AppsActivityFragment fragment) {
        super(appsActivity);
        this.mAppsViewModel = appsViewModel;
        this.mFragment = fragment;
        listenToAppsViewModelUiEvents();
    }

    @Override
    public void initGA() {
        initCommon();
        FooterPreference footer = Objects.requireNonNull(mFragment.findPreference(APPS_FOOTER));
        footer.setSummary(mActivity.getResources().getString(R.string.settingsUI_apps_view_footer));
    }

    private void initCommon() {
        // set title
        mActivity.setTitle(R.string.settingsUI_apps_ga_title);
        // consent switch
        MainSwitchPreference appsSwitchToggle =
                Objects.requireNonNull(mFragment.findPreference(APPS_SWITCH_BAR));
        appsSwitchToggle.setOnPreferenceChangeListener(
                (preference, intendedDebugLoggingValue) -> {
                    mAppsViewModel.consentSwitchPreferenceClickHandler(
                            (MainSwitchPreference) appsSwitchToggle);
                    return true;
                });
        mAppsViewModel.getAppsConsent().observe(mFragment, appsSwitchToggle::setChecked);

        IntroPreference noAppState =
                (IntroPreference)
                        Objects.requireNonNull(mFragment.findPreference(APPS_NO_TOPICS_STATE));
        noAppState.setLearnMoreText(
                mActivity
                        .getResources()
                        .getString(R.string.settingsUI_apps_view_fragment_no_apps_link_text));
        noAppState.setLearnMoreAction(
                view -> setLinkAction(mActivity, APPS_PRIVACY_LEARN_MORE_LINK));

        // enable blocked button if there is blocked topics
        Function<ButtonPreference, Observer<ImmutableList<App>>> observerProvider =
                controls ->
                        list -> {
                            if (list.isEmpty()) {
                                controls.setEnabled(false);
                                ((ButtonPreference) controls)
                                        .setTitle(R.string.settingsUI_no_blocked_topics_ga_text);
                            } else {
                                controls.setEnabled(true);
                                ((ButtonPreference) controls)
                                        .setTitle(R.string.settingsUI_view_blocked_topics_title);
                            }
                        };

        ButtonPreference buttonPreference =
                (ButtonPreference)
                        Objects.requireNonNull(
                                mFragment.findPreference(
                                        APPS_BLOCKED_APPS_WHEN_EMPTY_STATE_BUTTON));
        // set button in center
        buttonPreference.setGravity(Gravity.CENTER_HORIZONTAL);
        // set the button attribute to filled and large, direct import these attr from the
        // preference does not recognized and caused build issue.
        buttonPreference.setButtonStyle(0, 1);
        mAppsViewModel
                .getBlockedApps()
                .observe(mActivity, observerProvider.apply(buttonPreference));
        configureSharedElements();
    }

    @Override
    public void initU18() {
        // set title
        mActivity.setTitle(R.string.settingsUI_apps_ga_title);
    }

    @Override
    public void initGaUxWithPas() {
        initCommon();
        // initial intro
        TopIntroPreference intro = Objects.requireNonNull(mFragment.findPreference(APPS_TOP_INTRO));
        intro.setTitle(getResourcesString(R.string.settingsUI_pas_apps_view_body_text));
        // initial footer
        FooterPreference footer = Objects.requireNonNull(mFragment.findPreference(APPS_FOOTER));
        SpannableString infoString =
                new SpannableString(
                        getResourcesString(R.string.settingsUI_pas_apps_view_fragment_footer));
        SpannableString privacyLearnMoreString =
                new SpannableString(
                        getResourcesString(
                                R.string.settingsUI_pas_apps_view_fragment_privacy_learn_more));
        privacyLearnMoreString.setSpan(
                new URLSpan(APPS_PRIVACY_LEARN_MORE_LINK),
                0,
                privacyLearnMoreString.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        CharSequence footerString = TextUtils.concat(infoString + "\n\n", privacyLearnMoreString);
        footer.setSummary(footerString);
        footer.setLearnMoreText(
                getResourcesString(R.string.settingsUI_pas_apps_view_fragment_app_learn_more));
        footer.setLearnMoreAction(view -> setLinkAction(mActivity, APPS_FOOTER_LEARN_MORE_LINK));
    }

    private void configureSharedElements() {
        // blocked topics and reset button
        Preference blockedAppsButton =
                (Preference)
                        Objects.requireNonNull(
                                mFragment.findPreference(APPS_BLCOKED_TOIPCS_BUTTON));
        blockedAppsButton.setOnPreferenceClickListener(
                preference -> {
                    mAppsViewModel.blockedAppsFragmentButtonClickHandler();
                    return true;
                });
        Preference resetAppsButton =
                (Preference)
                        Objects.requireNonNull(mFragment.findPreference(APPS_RESET_TOPICS_BUTTON));
        resetAppsButton.setOnPreferenceClickListener(
                preference -> {
                    mAppsViewModel.resetAppsButtonClickHandler();
                    return true;
                });
        ButtonPreference blockedAppsWhenEmptyStateButton =
                (ButtonPreference)
                        Objects.requireNonNull(
                                mFragment.findPreference(
                                        APPS_BLOCKED_APPS_WHEN_EMPTY_STATE_BUTTON));
        blockedAppsWhenEmptyStateButton.setOnClickListener(
                preference -> {
                    mAppsViewModel.blockedAppsFragmentButtonClickHandler();
                });
        mAppsViewModel.refresh();
        setUpAppsListObserve();
    }

    private AdservicesTwoTargetPreference getAppPreference(App app) {
        Context context = mActivity.getApplicationContext();
        AdservicesTwoTargetPreference twoTargetPreference =
                new AdservicesTwoTargetPreference(
                        context, getResourcesString(R.string.settingsUI_block_app_title));
        twoTargetPreference.setOnClickListener(
                (v) -> mAppsViewModel.revokeAppConsentButtonClickHandler(app));
        twoTargetPreference.setTitle(app.getAppDisplayName(context.getPackageManager()));
        Drawable appIcon = app.getAppIcon(context);
        if (appIcon != null) {
            twoTargetPreference.setIcon(appIcon);
        }

        return twoTargetPreference;
    }

    private void setUpAppsListObserve() {
        PreferenceCategory emptyAppsHiddenSection =
                (PreferenceCategory)
                        Objects.requireNonNull(
                                mFragment.findPreference(APPS_EMPTY_APPS_HIDDEN_SECTION));
        emptyAppsHiddenSection.setVisible(false);
        PreferenceCategory noAppsMessageSection =
                (PreferenceCategory)
                        Objects.requireNonNull(
                                mFragment.findPreference(APPS_NO_APPS_MESSAGE_SECTION));
        PreferenceCategory appsCategory =
                (PreferenceCategory) Objects.requireNonNull(mFragment.findPreference(APPS_LIST));
        Observer<ImmutableList<App>> appsObserver =
                list -> {
                    appsCategory.removeAll();
                    if (list.isEmpty()) {
                        emptyAppsHiddenSection.setVisible(false);
                        noAppsMessageSection.setVisible(true);
                    } else {
                        emptyAppsHiddenSection.setVisible(true);
                        noAppsMessageSection.setVisible(false);
                        for (App app : list) {
                            appsCategory.addPreference(getAppPreference(app));
                        }
                    }
                };
        mAppsViewModel.getApps().observe(mActivity, appsObserver);
    }

    private void listenToAppsViewModelUiEvents() {
        Observer<Pair<AppsViewModel.AppsViewModelUiEvent, App>> observer =
                eventAppPair -> {
                    if (eventAppPair == null) {
                        return;
                    }
                    AppsViewModel.AppsViewModelUiEvent event = eventAppPair.first;
                    App app = eventAppPair.second;
                    if (event == null) {
                        return;
                    }
                    try {
                        switch (event) {
                            case SWITCH_ON_APPS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptInAppsDialog(mActivity);
                                }
                                mAppsViewModel.setAppsConsent(true);
                                mAppsViewModel.refresh();
                                break;
                            case SWITCH_OFF_APPS:
                                if (FlagsFactory.getFlags().getToggleSpeedBumpEnabled()) {
                                    DialogFragmentManager.showOptOutAppsDialog(
                                            mActivity, mAppsViewModel);
                                } else {
                                    mAppsViewModel.setAppsConsent(false);
                                    mAppsViewModel.refresh();
                                }
                                break;
                            case BLOCK_APP:
                                UiStatsLogger.logBlockAppSelected();
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showBlockAppDialog(
                                                mActivity, mAppsViewModel, app);
                                    } else {
                                        DialogManager.showBlockAppDialog(
                                                mActivity, mAppsViewModel, app);
                                    }
                                } else {
                                    mAppsViewModel.revokeAppConsent(app);
                                }
                                break;
                            case RESET_APPS:
                                UiStatsLogger.logResetAppSelected();
                                if (FlagsFactory.getFlags().getUiDialogsFeatureEnabled()) {
                                    if (FlagsFactory.getFlags().getUiDialogFragmentEnabled()) {
                                        DialogFragmentManager.showResetAppDialog(
                                                mActivity, mAppsViewModel);
                                    } else {
                                        DialogManager.showResetAppDialog(mActivity, mAppsViewModel);
                                    }
                                } else {
                                    mAppsViewModel.resetApps();
                                }
                                break;
                            case DISPLAY_BLOCKED_APPS_FRAGMENT:
                                Intent intent = new Intent(mActivity, BlockedAppsActivity.class);
                                mActivity.startActivity(intent);
                                break;
                            default:
                                Log.e("AdservicesUI", "Unknown Action for UI Logging");
                        }
                    } catch (IOException e) {
                        Log.e(
                                "AdServicesUI",
                                "Error while processing AppsViewModelUiEvent " + event + ":" + e);
                    } finally {
                        mAppsViewModel.uiEventHandled();
                    }
                };
        mAppsViewModel.getUiEvents().observe(mActivity, observer);
    }
}
