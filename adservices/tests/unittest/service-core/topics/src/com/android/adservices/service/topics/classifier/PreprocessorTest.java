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

import static com.android.adservices.service.topics.classifier.Preprocessor.limitDescriptionSize;
import static com.android.adservices.service.topics.classifier.Preprocessor.preprocessAppDescription;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link Preprocessor}. */
public final class PreprocessorTest extends AdServicesUnitTestCase {
    private Preprocessor mPreprocessor;

    @Before
    public void setUp() {
        mPreprocessor = new Preprocessor(mContext);
    }

    @Test
    public void removeStopWords_removesLegitStopWords() {
        expect.that(
                        mPreprocessor.removeStopWords(
                                "sample it they them input string is CustomStopWord1"))
                .isEqualTo("sample it they them input string is");
        expect.that(
                        mPreprocessor.removeStopWords(
                                "do CustomStopWord1 sample it they them input string"))
                .isEqualTo("do sample it they them input string");
    }

    @Test
    public void removeStopWords_checksFinalTrimming() {
        assertThat(mPreprocessor.removeStopWords(
                "   do does sample it they them input is CustomStopWord1  "))
                .isEqualTo("do does sample it they them input is");
    }

    @Test
    public void removeStopWords_justStopWords() {
        assertThat(mPreprocessor.removeStopWords("CustomStopWord1")).isEqualTo("");
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
        expect.that(preprocessAppDescription("The website is https://youtube.com"))
                .isEqualTo("the website is");
        expect.that(preprocessAppDescription("https://youtube.com is the website"))
                .isEqualTo("is the website");
        expect.that(preprocessAppDescription("https://www.tensorflow.org/lite/tutorials"))
                .isEmpty();
    }

    @Test
    public void testPreprocessing_forHttpURLRemoval() {
        expect.that(preprocessAppDescription("The website is http://google.com"))
                .isEqualTo("the website is");
        expect.that(preprocessAppDescription("http://google.com is the website"))
                .isEqualTo("is the website");
        expect.that(preprocessAppDescription("http://google.com")).isEmpty();
    }

    @Test
    public void testPreprocessing_forNotHttpURLRemoval() {
        expect.that(preprocessAppDescription("The website is www.youtube.com"))
                .isEqualTo("the website is");
        expect.that(preprocessAppDescription("www.youtube.com is the website"))
                .isEqualTo("is the website");
        expect.that(preprocessAppDescription("www.tensorflow.org/lite/tutorials")).isEmpty();
    }

    @Test
    public void testPreprocessing_forMentionsRemoval() {
        expect.that(preprocessAppDescription("Code author: @xyz123")).isEqualTo("code author:");
        expect.that(preprocessAppDescription("@xyz123 Code author: @xyz123"))
                .isEqualTo("code author:");
        expect.that(preprocessAppDescription("Code @xyz123 author: @xyz123"))
                .isEqualTo("code author:");
        expect.that(preprocessAppDescription("@xyz123")).isEmpty();
    }

    @Test
    public void testPreprocessing_forHtmlTagsRemoval() {
        expect.that(preprocessAppDescription("<title>Google is a search engine.</title>"))
                .isEqualTo("google is a search engine.");
        expect.that(
                        preprocessAppDescription(
                                "<!DOCTYPE html>"
                                        + "<html lang=\"en\">"
                                        + "<head>"
                                        + "<title>Hello World!</title>"
                                        + "</head>"))
                .isEqualTo("hello world!");
        expect.that(preprocessAppDescription("<p></p>")).isEmpty();
    }

    @Test
    public void testPreprocessing_forUpperCaseToLowerCase() {
        String inputDescription = "SOCIAL MEDIA APP";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("social media app");
    }

    @Test
    public void testPreprocessing_forNewLineRemoval() {
        String inputDescription = "check\nnew\nline";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("check new line");
    }

    @Test
    public void testPreprocessing_forTabRemoval() {
        String inputDescription = "check\tmultiple\t\ttabs.";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("check multiple tabs.");
    }

    @Test
    public void testPreprocessing_forRemovingMultipleSpaces() {
        String inputDescription = "This sentence \n has     multiple             spaces.";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("this sentence has multiple spaces.");
    }

    @Test
    public void testPreprocessing_forAllCombinations() {
        String inputDescription =
                "This DESCRIPTION \n"
                        + " has     multiple             spaces, 234 4 (). \n"
                        + "  @Mention &*\n"
                        + " BLOCK LETTERS\n"
                        + " http://sampleURL.com as well!"
                        + " <p>Hello world!</p>";
        String result = preprocessAppDescription(inputDescription);
        assertThat(result).isEqualTo("this description has multiple spaces, 234 4 (). &* "
                + "block letters as well! hello world!");
    }

    @Test
    public void testPreprocessing_forRealAppDescription() {
        String googleTranslateDescription =
                "• Text translation: Translate between 108 languages by typing\n"
                        + "• Tap to Translate: Copy text in any app and tap the Google Translate "
                        + "icon to translate (all languages)\n"
                        + "• Offline: Translate with no internet connection (59 languages)\n"
                        + "• Instant camera translation: Translate text in images instantly by "
                        + "just pointing your camera (94 languages)\n"
                        + "• Photos: Take or import photos for higher quality translations (90 "
                        + "languages)\n"
                        + "• Conversations: Translate bilingual conversations on the fly (70 "
                        + "languages)";
        String googleTranslateResult = preprocessAppDescription(googleTranslateDescription);
        assertThat(googleTranslateResult).isEqualTo(
                "• text translation: translate between 108 languages by typing "
                        + "• tap to translate: copy text in any app and tap the google translate "
                        + "icon to translate (all languages) • offline: translate with no internet "
                        + "connection (59 languages) • instant camera translation: translate text "
                        + "in images instantly by just pointing your camera (94 languages) "
                        + "• photos: take or import photos for higher quality translations "
                        + "(90 languages) • conversations: translate bilingual conversations "
                        + "on the fly (70 languages)");

        String googleDescription =
                "• Use voice commands while navigating – even when your device has no connection."
                        + " Try saying \"cancel my navigation\" \"what's my ETA?\" or \"what's my"
                        + " next turn?\"\n"
                        + "• It's easier to access privacy settings from the homescreen. Just tap"
                        + " your Google Account profile picture.";
        String googleResult = preprocessAppDescription(googleDescription);
        assertThat(googleResult).isEqualTo("• use voice commands while navigating – even when "
                + "your device has no connection. try saying \"cancel my navigation\" \"what's my"
                + " eta?\" or \"what's my next turn?\" • it's easier to access privacy settings "
                + "from the homescreen. just tap your google account profile picture.");
    }

    @Test
    public void testPreprocessing_forEmptyDescription() {
        expect.that(preprocessAppDescription("")).isEmpty();
        expect.that(preprocessAppDescription("        ")).isEmpty();
        expect.that(preprocessAppDescription("  \n  \n   \n")).isEmpty();
    }

    @Test
    public void testPreprocessing_forNullInput() {
        assertThrows(NullPointerException.class, () -> preprocessAppDescription(null));
    }

    @Test
    public void testLimitDescriptionSize_numberOfWords() {
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                10, /*maxNumberOfCharacters*/
                                20))
                .isEqualTo("abc def gh i");
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                2, /*maxNumberOfCharacters*/
                                20))
                .isEqualTo("abc def");
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                1, /*maxNumberOfCharacters*/
                                20))
                .isEqualTo("abc");
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                0, /*maxNumberOfCharacters*/
                                20))
                .isEqualTo("");
    }

    @Test
    public void testLimitDescriptionSize_maxLength() {
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                10, /*maxNumberOfCharacters*/
                                20))
                .isEqualTo("abc def gh i");
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                10, /*maxNumberOfCharacters*/
                                13))
                .isEqualTo("abc def gh i");
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                10, /*maxNumberOfCharacters*/
                                10))
                .isEqualTo("abc def gh");
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                10, /*maxNumberOfCharacters*/
                                1))
                .isEqualTo("a");
        expect.that(
                        limitDescriptionSize(
                                "abc def gh i ", /*maxNumberOfWords*/
                                10, /*maxNumberOfCharacters*/
                                0))
                .isEqualTo("");
    }

    @Test
    public void testLimitDescriptionSize_emptyString() {
        expect.that(limitDescriptionSize("", /*maxNumberOfWords*/ 10, /*maxNumberOfCharacters*/ 20))
                .isEqualTo("");
        expect.that(
                        limitDescriptionSize(
                                " ", /*maxNumberOfWords*/ 10, /*maxNumberOfCharacters*/ 20))
                .isEqualTo("");
    }

    @Test
    public void testLimitDescriptionSize_nullString() {
        assertThrows(
                NullPointerException.class,
                () ->
                        limitDescriptionSize(
                                null, /*maxNumberOfWords*/ 10, /*maxNumberOfCharacters*/ 20));
    }
}
