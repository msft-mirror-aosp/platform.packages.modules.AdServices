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

import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionOutcome;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.service.common.Validator;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Runs validations on {@link AdSelectionFromOutcomesInput} object */
public class AdSelectionFromOutcomesInputValidator
        implements Validator<AdSelectionFromOutcomesInput> {

    @VisibleForTesting
    static final String INPUT_PARAM_CANNOT_BE_NULL = "AdSelectionFromOutcomesInput cannot be null";

    @VisibleForTesting
    static final String AD_OUTCOMES_CANNOT_BE_NULL_OR_EMPTY =
            "AdSelectionOutcomes cannot be null or empty";

    @VisibleForTesting
    static final String SELECTION_SIGNALS_CANNOT_BE_NULL = "AdSelectionSignals cannot be null";

    @VisibleForTesting
    static final String SELECTION_LOGIC_URI_CANNOT_BE_NULL_OR_EMPTY =
            "SelectionLogicUri cannot be null or empty";

    @VisibleForTesting
    static final String URI_IS_NOT_ABSOLUTE = "The SelectionLogicUri should be absolute";

    @VisibleForTesting
    static final String URI_IS_NOT_HTTPS = "The SelectionLogicUri is not secured by https";

    @VisibleForTesting
    static final String AD_SELECTION_IDS_DONT_EXIST = "Ad Selection Ids don't exist: %s";

    private static final String HTTPS_SCHEME = "https";

    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;

    public AdSelectionFromOutcomesInputValidator(@NonNull AdSelectionEntryDao adSelectionEntryDao) {
        mAdSelectionEntryDao = adSelectionEntryDao;
    }

    /** Validates the object and populate the violations. */
    @Override
    public void addValidation(
            @NonNull AdSelectionFromOutcomesInput inputParam,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(inputParam, INPUT_PARAM_CANNOT_BE_NULL);

        violations.addAll(validateAdOutcomes(inputParam.getAdOutcomes()));
        violations.addAll(validateSelectionLogicUri(inputParam.getSelectionLogicUri()));
    }

    private ImmutableList<String> validateAdOutcomes(@NonNull List<AdSelectionOutcome> adOutcomes) {
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();
        if (Objects.isNull(adOutcomes) || adOutcomes.isEmpty()) {
            violations.add(AD_OUTCOMES_CANNOT_BE_NULL_OR_EMPTY);
        }
        List<Long> notExistIds;
        if ((notExistIds = validateExistenceOfAdSelectionIds(adOutcomes)).size() > 0) {
            violations.add(String.format(AD_SELECTION_IDS_DONT_EXIST, notExistIds));
        }
        return violations.build();
    }

    private ImmutableList<String> validateSelectionLogicUri(@NonNull Uri selectionLogicUri) {
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();
        if (Objects.isNull(selectionLogicUri) || selectionLogicUri.toString().isEmpty()) {
            violations.add(SELECTION_LOGIC_URI_CANNOT_BE_NULL_OR_EMPTY);
        }
        // TODO(b/258719980): Validate seller against selection Logic Uri
        if (!selectionLogicUri.isAbsolute()) {
            violations.add(URI_IS_NOT_ABSOLUTE);
        } else if (!selectionLogicUri.getScheme().equals(HTTPS_SCHEME)) {
            violations.add(URI_IS_NOT_HTTPS);
        }
        return violations.build();
    }

    private ImmutableList<Long> validateExistenceOfAdSelectionIds(
            @NonNull List<AdSelectionOutcome> adOutcomes) {
        ImmutableList.Builder<Long> notExistingIds = new ImmutableList.Builder<>();
        List<Long> adOutcomeIds =
                adOutcomes.stream()
                        .map(AdSelectionOutcome::getAdSelectionId)
                        .collect(Collectors.toList());
        Set<Long> existingIds =
                mAdSelectionEntryDao.getAdSelectionEntities(adOutcomeIds).stream()
                        .map(DBAdSelectionEntry::getAdSelectionId)
                        .collect(Collectors.toSet());
        adOutcomeIds.stream().filter(e -> !existingIds.contains(e)).forEach(notExistingIds::add);
        return notExistingIds.build();
    }
}
