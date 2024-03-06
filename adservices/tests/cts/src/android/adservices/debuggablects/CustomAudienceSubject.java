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

package android.adservices.debuggablects;

import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.ACTIVATION_TIME;
import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.DAILY_UPDATE;
import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.ELIGIBLE_UPDATE_TIME;
import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.NUM_TIMEOUT_FAILURES;
import static android.adservices.debuggablects.CustomAudienceShellCommandHelper.NUM_VALIDATION_FAILURES;

import static com.google.common.truth.Truth.assertAbout;

import androidx.annotation.Nullable;

import com.google.common.truth.Fact;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.Subject;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.format.DateTimeParseException;

class CustomAudienceSubject extends Subject {

    static Factory<CustomAudienceSubject, JSONObject> builder() {
        return CustomAudienceSubject::new;
    }

    static CustomAudienceSubject assertThat(@Nullable JSONObject customAudienceJson) {
        return assertAbout(builder()).that(customAudienceJson);
    }

    @Nullable private final JSONObject mActual;

    CustomAudienceSubject(FailureMetadata metadata, @Nullable JSONObject actual) {
        super(metadata, actual);
        this.mActual = actual;
    }

    void hasValidActivationTime() {
        // Activation time is inconsistent so only parse for format correctness.
        try {
            Instant.parse(mActual.getString(ACTIVATION_TIME));
        } catch (JSONException | DateTimeParseException e) {
            failWithoutActual(Fact.simpleFact("Could not parse activation time."));
        }
    }

    void hasTimeoutFailures(int expected) {
        try {
            int actual = getBackgroundFetchData().getInt(NUM_TIMEOUT_FAILURES);
            if (actual != expected) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "Expected %d timeout failures but was %d",
                                        expected, actual)));
            }
        } catch (JSONException e) {
            failWithoutActual(
                    Fact.simpleFact("Failed to parse background fetch timeout failures."));
        }
    }

    void hasValidationFailures(int expected) {
        try {
            int actual = getBackgroundFetchData().getInt(NUM_VALIDATION_FAILURES);
            if (actual != expected) {
                failWithoutActual(
                        Fact.simpleFact(
                                String.format(
                                        "Expected %d validation failures but was %d",
                                        expected, actual)));
            }
        } catch (JSONException e) {
            failWithoutActual(
                    Fact.simpleFact("Failed to parse background fetch validation failures."));
        }
    }

    void hasValidEligibleUpdateTime() {
        try {
            Instant.parse(getBackgroundFetchData().getString(ELIGIBLE_UPDATE_TIME));
        } catch (JSONException e) {
            failWithoutActual(
                    Fact.simpleFact(
                            "Background fetch data does hot have valid eligible update time."));
        }
    }

    private JSONObject getBackgroundFetchData() {
        try {
            return mActual.getJSONObject(DAILY_UPDATE);
        } catch (JSONException e) {
            failWithoutActual(Fact.simpleFact("Failed to parse background fetch data."));
            return null;
        }
    }
}
