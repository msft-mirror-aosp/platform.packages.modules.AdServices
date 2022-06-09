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

import static com.android.adservices.service.topics.classifier.Preprocessor.preprocessAppDescription;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link Preprocessor}. */
@SmallTest
public final class PreprocessorTest {

    private Preprocessor mPreprocessor;

    @Before
    public void setUp() {
        mPreprocessor = new Preprocessor(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void removeStopWords_removesLegitStopWords() {
        assertThat(mPreprocessor.removeStopWords("sample it they them input string is are"))
                .isEqualTo("sample input string");
        assertThat(mPreprocessor.removeStopWords("do does sample it they them input string is are"))
                .isEqualTo("sample input string");
    }

    @Test
    public void removeStopWords_checksFinalTrimming() {
        assertThat(mPreprocessor.removeStopWords("   do does sample it they them input is are  "))
                .isEqualTo("sample input");
    }

    @Test
    public void removeStopWords_justStopWords() {
        assertThat(mPreprocessor.removeStopWords("can will now")).isEqualTo("");
    }

    @Test
    public void removeStopWords_forEmptyInput() {
        assertThat(mPreprocessor.removeStopWords("")).isEqualTo("");
    }

    @Test
    public void removeStopWords_forNullInput() {
        assertThrows(NullPointerException.class, () -> mPreprocessor.removeStopWords(null));
    }

    @Test
    public void testPreprocessing_forHttpsURLRemoval() {
        assertThat(preprocessAppDescription("The website is https://youtube.com"))
                .isEqualTo("the website is ");
        assertThat(preprocessAppDescription("https://youtube.com is the website"))
                .isEqualTo(" is the website");
        assertThat(preprocessAppDescription("https://www.tensorflow.org/lite/tutorials")).isEmpty();
    }

    @Test
    public void testPreprocessing_forHttpURLRemoval() {
        assertThat(preprocessAppDescription("The website is http://google.com"))
                .isEqualTo("the website is ");
        assertThat(preprocessAppDescription("http://google.com is the website"))
                .isEqualTo(" is the website");
        assertThat(preprocessAppDescription("http://google.com")).isEmpty();
    }

    @Test
    public void testPreprocessing_forMentionsRemoval() {
        assertThat(preprocessAppDescription("Code author: @xyz123")).isEqualTo("code author ");
        assertThat(preprocessAppDescription("@xyz123 Code author: @xyz123"))
                .isEqualTo(" code author ");
        assertThat(preprocessAppDescription("Code @xyz123 author: @xyz123"))
                .isEqualTo("code author ");
        assertThat(preprocessAppDescription("@xyz123")).isEmpty();
    }

    @Test
    public void testPreprocessing_forUpperCaseToLowerCase() {
        String inputDescription = "SOCIAL MEDIA APP";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("social media app");
    }

    @Test
    public void testPreprocessing_forPunctuationRemoval() {
        String inputDescription = "This. is a test, to check! punctuation removal?!@#$%^&*()_+";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("this is a test to check punctuation removal");
    }

    @Test
    public void testPreprocessing_forNewLineRemoval() {
        String inputDescription = "check\nnew\nline";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("check new line");
    }

    @Test
    public void testPreprocessing_forNumberRemoval() {
        String inputDescription = "Remove 11 numbers 0.";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("remove numbers ");
    }

    @Test
    public void testPreprocessing_forRemovingMultipleSpaces() {
        String inputDescription = "This sentence \n has     multiple             spaces.";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("this sentence has multiple spaces");
    }

    @Test
    public void testPreprocessing_forAllCombinations() {
        String inputDescription =
                "This DESCRIPTION \n"
                        + " has     multiple             spaces, 234 4 (). \n"
                        + "  @Mention &*\n"
                        + " BLOCK LETTERS\n"
                        + " http://sampleURL.com as well!";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("this description has multiple spaces block letters as well");
    }

    @Test
    public void testPreprocessing_forEmptyDescription() {
        assertThat(preprocessAppDescription("")).isEmpty();
        assertThat(preprocessAppDescription("        ")).isEqualTo(" ");
        assertThat(preprocessAppDescription("  \n  \n   \n")).isEqualTo(" ");
    }

    @Test
    public void testPreprocessing_forNullInput() {
        assertThrows(NullPointerException.class, () -> preprocessAppDescription(null));
    }
}
