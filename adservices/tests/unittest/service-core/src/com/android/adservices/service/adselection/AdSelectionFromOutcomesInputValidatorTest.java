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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.AdSelectionFromOutcomesInputValidator.AD_OUTCOMES_CANNOT_BE_NULL_OR_EMPTY;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesInputValidator.AD_SELECTION_IDS_DONT_EXIST;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesInputValidator.INPUT_PARAM_CANNOT_BE_NULL;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesInputValidator.SELECTION_LOGIC_URI_CANNOT_BE_NULL_OR_EMPTY;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesInputValidator.URI_IS_NOT_ABSOLUTE;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesInputValidator.URI_IS_NOT_HTTPS;

import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionFromOutcomesInputFixture;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AdSelectionOutcomeFixture;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.common.ValidatorTestUtil;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

public class AdSelectionFromOutcomesInputValidatorTest {
    private static final String AD_SELECTION_FROM_OUTCOME_INPUT_VIOLATION =
            String.format(
                    "Invalid object of type %s. The violations are:",
                    AdSelectionFromOutcomesInput.class.getName());

    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AdSelectionFromOutcomesInputValidator mValidator;

    @Before
    public void setup() {
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mValidator = new AdSelectionFromOutcomesInputValidator(mAdSelectionEntryDao);
    }

    @Test
    public void testValidAdSelectionFromOutcomeInputSuccess() {
        mValidator.validate(
                AdSelectionFromOutcomesInputFixture.anAdSelectionFromOutcomesInput(
                        Collections.singletonList(
                                AdSelectionOutcomeFixture.anAdSelectionOutcomeInDB(
                                        mAdSelectionEntryDao))));
    }

    @Test
    public void testInputParamCannotBeNull() {
        NullPointerException exception =
                Assert.assertThrows(NullPointerException.class, () -> mValidator.validate(null));
        Assert.assertEquals(exception.getMessage(), INPUT_PARAM_CANNOT_BE_NULL);
    }

    @Test
    public void testInputParamCannotBeEmpty() {
        IllegalArgumentException exception =
                Assert.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        AdSelectionFromOutcomesInputFixture
                                                .anAdSelectionFromOutcomesInput(
                                                        Collections.emptyList())));
        ValidatorTestUtil.assertValidationFailuresMatch(
                exception,
                AD_SELECTION_FROM_OUTCOME_INPUT_VIOLATION,
                Collections.singletonList(AD_OUTCOMES_CANNOT_BE_NULL_OR_EMPTY));
    }

    @Test
    public void testOutcomesShouldBeInTheDb() {
        AdSelectionOutcome outcomeNotInDb = AdSelectionOutcomeFixture.anAdSelectionOutcome();
        IllegalArgumentException exception =
                Assert.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        AdSelectionFromOutcomesInputFixture
                                                .anAdSelectionFromOutcomesInput(
                                                        Collections.singletonList(
                                                                outcomeNotInDb))));

        String expectedViolation =
                String.format(
                        AD_SELECTION_IDS_DONT_EXIST,
                        Collections.singletonList(outcomeNotInDb.getAdSelectionId()));
        ValidatorTestUtil.assertValidationFailuresMatch(
                exception,
                AD_SELECTION_FROM_OUTCOME_INPUT_VIOLATION,
                Collections.singletonList(expectedViolation));
    }

    @Test
    public void testUriCannotBeEmpty() {
        IllegalArgumentException exception =
                Assert.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        AdSelectionFromOutcomesInputFixture
                                                .anAdSelectionFromOutcomesInput(Uri.parse(""))));

        ValidatorTestUtil.assertValidationFailuresMatch(
                exception,
                AD_SELECTION_FROM_OUTCOME_INPUT_VIOLATION,
                Collections.singletonList(SELECTION_LOGIC_URI_CANNOT_BE_NULL_OR_EMPTY));
    }

    @Test
    public void testUriCannotBeRelative() {
        IllegalArgumentException exception =
                Assert.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        AdSelectionFromOutcomesInputFixture
                                                .anAdSelectionFromOutcomesInput(
                                                        Uri.parse("/this/is/relative/path"))));

        ValidatorTestUtil.assertValidationFailuresMatch(
                exception,
                AD_SELECTION_FROM_OUTCOME_INPUT_VIOLATION,
                Collections.singletonList(URI_IS_NOT_ABSOLUTE));
    }

    @Test
    public void testUriMustBeHttps() {
        IllegalArgumentException exception =
                Assert.assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                mValidator.validate(
                                        AdSelectionFromOutcomesInputFixture
                                                .anAdSelectionFromOutcomesInput(
                                                        Uri.parse("http://google.com"))));

        ValidatorTestUtil.assertValidationFailuresMatch(
                exception,
                AD_SELECTION_FROM_OUTCOME_INPUT_VIOLATION,
                Collections.singletonList(URI_IS_NOT_HTTPS));
    }
}
