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
import android.content.Context;

import com.android.adservices.LogUtil;

import com.google.common.collect.ImmutableSet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    // Stop words file path with all lower case stop words.
    private static final String STOP_WORDS_FILE_PATH = "classifier/stopwords.txt";

    private Context mContext;
    private ImmutableSet<String> mStopWords;

    public Preprocessor(@NonNull Context context) {
        mContext = context;
    }

    /**
     * Removes stop words from description. Stop words are loaded if required.
     *
     * @param description pre-processed app description.
     * @return description with stop words filtered out.
     */
    @NonNull
    public String removeStopWords(@NonNull String description) {
        requireNonNull(description);

        // Load stop words if needed.
        if (mStopWords == null || mStopWords.isEmpty()) {
            loadStopWords();
        }

        return Arrays.stream(description.split(SINGLE_SPACE))
                .filter(word -> !mStopWords.contains(word))
                .collect(Collectors.joining(SINGLE_SPACE))
                .trim();
    }

    // Load stop words from the assets. If the assets are missing, log the error and load nothing.
    private void loadStopWords() {
        ImmutableSet.Builder<String> setBuilder = ImmutableSet.builder();

        int countWords = 0;
        try {
            InputStream inputStream = mContext.getAssets().open(STOP_WORDS_FILE_PATH);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            while (reader.ready()) {
                countWords++;
                String word = reader.readLine().trim();
                setBuilder.add(word);
            }
        } catch (IOException e) {
            // TODO(b/225937395): Fix logging upgrades.
            LogUtil.e(e, "Unable to read the stop words assets at %s", STOP_WORDS_FILE_PATH);
        }
        mStopWords = setBuilder.build();

        LogUtil.d("Read %d stop words for pre-processing.", countWords);
    }

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
