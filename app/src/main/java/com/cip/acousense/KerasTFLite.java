package com.cip.acousense;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class KerasTFLite {
    private static final String MODEL_FILE = "saved_model.tflite";
    private Interpreter mInterpreter;
    private Context context;
    private MappedByteBuffer modelFile;

    public KerasTFLite(Context context) throws IOException {
        this.context = context;
//        File file = loadModelFile(context);
        modelFile = readModel(MODEL_FILE);
        mInterpreter = new Interpreter(modelFile, new Interpreter.Options());
    }

    public float run(float[][][][] input) {
        //result will be number between 0~9
        float[][] labelProbArray = new float[1][1];
        mInterpreter.run(input, labelProbArray);
//        List<String> labels = new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            labels.add(String.valueOf(i));
//        }
        return labelProbArray[0][0];
    }

    public String getInput() {
        //result will be number between 0~9
//        float[] labelProbArray = new float[1];
        int[] inputshape = mInterpreter.getInputTensor(0).shape();

//        List<String> labels = new ArrayList<>();
//        for (int i = 0; i < 10; i++) {
//            labels.add(String.valueOf(i));
//        }
        return ""+ inputshape.length + " " + inputshape[0]+ " " + inputshape[1]+ " " + inputshape[2]+ " " + inputshape[3];
    }


    private MappedByteBuffer readModel(String modelFilename) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private int getMax(float[] results) {
        int maxID = 0;
        float maxValue = results[maxID];
        for (int i = 1; i < results.length; i++) {
            if (results[i] > maxValue) {
                maxID = i;
                maxValue = results[maxID];
            }
        }
        return maxID;
    }

    public void release() {
        mInterpreter.close();
    }
}
