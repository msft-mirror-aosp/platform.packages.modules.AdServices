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

import static com.google.common.truth.Truth.assertThat;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.Test;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.TensorFlowLite;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public final class TfLiteTest {

    // the test model is a simple keras sequential model (consists a drop-out input layer and a
    // dense layer) that maps one 2d float to another.
    private static final String TEST_MODEL_PATH = "testSequentialModel.tflite";

    private static final String NULL_MODEL_PATH = "nullModel.tflite";

    private static final String TFL_CLASS = "org.tensorflow.lite.TensorFlowLite";

    private static final String INTERPRETER_CLASS = "org.tensorflow.lite.Interpreter";

    private static AssetManager sAssetManager = InstrumentationRegistry.getInstrumentation()
            .getTargetContext()
            .getAssets();

    /**
     * Test TFLite class loading.
     */
    @Test
    public void testTensorFlowLiteClassLoadingSuccess() throws ClassNotFoundException {
        Class tflClass = Class.forName(TFL_CLASS);
        Assert.assertNotNull(tflClass);
    }

    /**
     * Test Interpreter class loading.
     */
    @Test
    public void testInterpreterClassLoadingSuccess() throws ClassNotFoundException {
        Class interpreterClass = Class.forName(INTERPRETER_CLASS);
        Assert.assertNotNull(interpreterClass);
    }

    /**
     * Test TFLite native dependency.
     */
    @Test
    public void testTensorFlowLiteInit() {
        TensorFlowLite.init();
    }

    /**
     * Test Interpreter native dependency by loading a null tflite model.
     *
     * @throws IllegalArgumentException Expected exception when native dependencies for
     * TfLite are loaded properly.
     * @throws ClassNotFoundException Unexpected exception when native dependencies for
     * TfLite are missing.
     * @throws IOException Unexpected exception when the model file is corrupted.
     */
    @Test
    public void testInterpreterNativeDependency() throws IOException, ClassNotFoundException {
        // We expect TfLite to throw an exception upon reading a null file only if
        // the native dependencies are satisfied.
        IllegalArgumentException iae = Assert.assertThrows(
                "Expected IllegalArgumentExpected due to empty model file",
                IllegalArgumentException.class,
                () -> {
                    Interpreter unusedInterpreter =
                            new Interpreter(readModelFromAssets(NULL_MODEL_PATH));
                }
        );

        Assert.assertTrue(iae.getMessage().contains("not a valid flatbuffer model"));
    }

    /**
     * Test Interpreter model loading success.
     */
    @Test
    public void testModelLoadingSuccess() throws IOException, ClassNotFoundException {
        // Load the test model with arbitrary loading options.
        Interpreter interpreter = new Interpreter(
                readModelFromAssets(TEST_MODEL_PATH),
                new Interpreter.Options()
                    .setNumThreads(4)
                    .setUseNNAPI(false)
                    .setAllowFp16PrecisionForFp32(true)
                    .setAllowBufferHandleOutput(false)
        );

        // Test the loaded interpreter has expected (pre-determined by the test model)
        // input, output parameters.
        Assert.assertNotNull(interpreter);
        assertThat(interpreter.getInputTensorCount()).isEqualTo(1);
        assertThat(interpreter.getInputTensor(0).dataType()).isEqualTo(DataType.FLOAT32);
        assertThat(interpreter.getOutputTensorCount()).isEqualTo(1);
        assertThat(interpreter.getOutputTensor(0).dataType()).isEqualTo(DataType.FLOAT32);
    }

    // test assets are located at tests/unittest/service-core/assets
    private ByteBuffer readModelFromAssets(String modelPath) throws IOException {
        AssetFileDescriptor fd = sAssetManager.openFd(modelPath);

        FileInputStream istream = new FileInputStream(fd.getFileDescriptor());
        return istream.getChannel()
            .map(
                FileChannel.MapMode.READ_ONLY,
                fd.getStartOffset(),
                fd.getDeclaredLength()
            );
    }

}
