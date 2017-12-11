package com.company;

import weka.core.Instances;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

public class WekaUtils {
    static void buildTestFile(String arffTestData) throws IOException {
        // input the file content to the StringBuffer "input"
        BufferedReader file = new BufferedReader(new FileReader("data/tweetAndTripWithDescriptionsTest3.arff"));
        String line;
        StringBuffer inputBuffer = new StringBuffer();

        while ((line = file.readLine()) != null) {
            inputBuffer.append(line);
            inputBuffer.append('\n');
        }
        String inputStr = inputBuffer.toString();

        file.close();

        System.out.println(inputStr); // check that it's inputted right
        StringBuilder sb = new StringBuilder(inputStr);
        sb.replace(inputStr.indexOf("@data") + 6, inputStr.length(), arffTestData);
        FileOutputStream fileOut = new FileOutputStream("data/tweetAndTripWithDescriptionsTest3.arff");
        fileOut.write(sb.toString().getBytes());
        fileOut.close();
    }

    static Instances getWekaInstance() throws IOException {
        BufferedReader reader = new BufferedReader(
                new FileReader("data/tweetAndTripWithDescriptionsTest3.arff"));
        Instances data = new Instances(reader);
        reader.close();
        // setting class attribute
        data.setClassIndex(data.numAttributes() - 1);
        return data;
    }
}
