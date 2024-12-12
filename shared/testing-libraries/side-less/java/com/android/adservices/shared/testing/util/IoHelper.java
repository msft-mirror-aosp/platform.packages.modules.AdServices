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
package com.android.adservices.shared.testing.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/** Helper class for IO-related stuff. */
public final class IoHelper {

    /**
     * Converts the contents of a {@link PrintWriter} to a string.
     *
     * <p>Typically used to test {@code dump()} methods.
     */
    public static String printWriterToString(ThrowingConsumer<PrintWriter, IOException> consumer)
            throws IOException {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        try (StringWriter sw = new StringWriter()) {
            PrintWriter pw = new PrintWriter(sw);
            consumer.accept(pw);
            pw.flush();
            return sw.toString();
        }
    }

    /**
     * Converts the contents of a {@link PrintStream} to a string.
     *
     * <p>Typically used to test {@code dump()} methods.
     */
    public static String printStreamToString(ThrowingConsumer<PrintStream, IOException> consumer)
            throws IOException {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PrintStream ps = new PrintStream(baos, /* autoFlush= */ true);
            consumer.accept(ps);
            return baos.toString();
        }
    }

    private IoHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
