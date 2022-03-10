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
package com.android.adservices.service.topics.classifier;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;

import java.util.Locale;
import java.util.regex.Pattern;

/** Util class for pre-processing input for the classifier. */
public final class Preprocessor {

    // This regular expression is to identify URLs like https://google.com
    private static final Pattern URL_REGEX =
            Pattern.compile("(https?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]");

    // At mention regular expression. Example @Rubidium1.
    private static final Pattern MENTIONS_REGEX = Pattern.compile("@[A-Za-z0-9]+");

    // Regular expression to primarily remove punctuation marks.
    // Selects out lower case english alphabets and space.
    private static final Pattern ALPHABET_REGEX = Pattern.compile("[^a-z\\s]|");

    private static final Pattern NEW_LINE_REGEX = Pattern.compile("\\n");
    private static final Pattern MULTIPLE_SPACES_REGEX = Pattern.compile("\\s+");

    private static final String SINGLE_SPACE = " ";
    private static final String EMPTY_STRING = "";

    /**
     * Pre-process the description strings for the classifier with the following steps.
     *
     * <ul>
     *   <li>Converts text to lowercase.
     *   <li>Removes URLs.
     *   <li>Removes @mentions.
     *   <li>Removes everything other than lower case english alphabets and spaces.
     *   <li>Convert multiple spaces to a single space.
     * </ul>
     *
     * @param description is the string description of the app.
     * @return description string after pre-processing.
     */
    @NonNull
    public static String preprocessAppDescription(@NonNull String description) {
        requireNonNull(description);

        description = description.toLowerCase(Locale.ROOT);

        description = URL_REGEX.matcher(description).replaceAll(EMPTY_STRING);
        description = MENTIONS_REGEX.matcher(description).replaceAll(EMPTY_STRING);
        description = ALPHABET_REGEX.matcher(description).replaceAll(EMPTY_STRING);

        description = NEW_LINE_REGEX.matcher(description).replaceAll(SINGLE_SPACE);
        description = MULTIPLE_SPACES_REGEX.matcher(description).replaceAll(SINGLE_SPACE);

        return description;
    }
}
