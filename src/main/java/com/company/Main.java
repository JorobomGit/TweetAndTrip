package com.company;

import java.io.*;

import fi.foyt.foursquare.api.entities.CompactVenue;
import twitter4j.JSONObject;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.User;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayesMultinomialText;
import weka.core.Instances;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.Evaluation;

import javax.xml.crypto.Data;

public class Main {

    private static MysqlConnect mysqlConnect = new MysqlConnect();

    public static void main(String[] args) throws Exception {
        /**Recommend destination**/
        //1. Get our already trained model
        //2. Use that model to recommend a destination with Naive Bayes Classificator
        /**Fill Data**/
        //fillData();
        /**1. Get user name
         * 2. Get location, language, gender, description, tags, ?
         * 3. Build new .arff with those fields
         * 4. Reuse .model already trained
         * 5. This will give a destination predicted
         */
        String username = "india";
        TwitterUtils twitterUtils = new TwitterUtils();
        TweetAndTripUser tweetAndTripUser = new TweetAndTripUser(username, twitterUtils);
        /**Build file with user name**/
        buildTestFile(buildTestString(tweetAndTripUser));

        /**Classifier with already trained model**/
        Classifier cls = (Classifier) weka.core.SerializationHelper.read("data/tweetAndTrip.model");

        /**Instances from new file**/
        Instances instances = getWekaInstance();
        double value = cls.classifyInstance(instances.get(0));
        //get the name of the class value
        String prediction = instances.classAttribute().value((int)value);

        System.out.println("The predicted value of instance " +
                Integer.toString(0) +
                ": " + prediction);

        /**FOURSQUARE**/
        FoursquareUtils foursquareUtils = new FoursquareUtils();
        ArrayList<CompactVenue> userVenues = new ArrayList<>();
        int i = 0;
        /**With user tag and recommended destination**/
        for (String tag : tweetAndTripUser.getHashtags()) {
            if(i > 2) { break; }
            userVenues.addAll(foursquareUtils.searchVenues(prediction, tag));
            i++;
        }
        /***RECOMMENDED DESTINATION BASED ON YOUR INFO:***/
        System.out.println("FINAL RECOMENDATION: ");
        System.out.println("For user: " + tweetAndTripUser.getUsername());
        System.out.println("Destination: " + prediction);
        for (CompactVenue venue : userVenues) {
            System.out.println("Venues: " + venue.getName());
        }
    }

    private static String buildTestString(TweetAndTripUser tweetAndTripUser) throws Exception {
        List<String> hashtags = tweetAndTripUser.getHashtags();
        String hashtagsSingleString = parseTags(hashtags);
        User user = tweetAndTripUser.getUser();

        return parseField(user.getLocation()) + ','
                + parseField(user.getLang()) + ','
                + DatabaseUtils.getUserGender(user.getName()) + ','
                //+ parseField(user.getDescription()) + ','
                + hashtagsSingleString + ','
                + "?\n";
    }

    private static void buildTestFile(String arffTestData) throws IOException {
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

    private static String parseTags(List<String> hashtags) {
        String singleString = "'";
        for (String tag: hashtags) {
            singleString += tag + ' ';
        }
        return singleString + "'";
    }

    /**If field contains blank spaces, single quotes have to be added**/
    private static String parseField(String field){
        field = field.length() > 0 ? field : "default";
        field = field.replace("\n", "").replace("\r", "");
        return field.indexOf(' ') > 0 ? "'" + field + "'" : field;
    }

    private static Instances getWekaInstance() throws IOException {
        BufferedReader reader = new BufferedReader(
                new FileReader("data/tweetAndTripWithDescriptionsTest3.arff"));
        Instances data = new Instances(reader);
        reader.close();
        // setting class attribute
        data.setClassIndex(data.numAttributes() - 1);
        return data;
    }


    private static NaiveBayesMultinomialText buildNaiveBayesMultinomialTextClassifier(Instances data) throws Exception {
        NaiveBayesMultinomialText classifier = new NaiveBayesMultinomialText();
        classifier.buildClassifier(data);
        return classifier;
    }

    private static void fillData() {
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                /**Runs every 20 seconds**/
                try {
                    TwitterUtils twitterUtils = new TwitterUtils();
                    DatabaseUtils.connection = mysqlConnect.connect();
                    /**
                     *
                     * PART 1 GET INFO FROM APIs
                     *
                     * **/
                    /**1. Get an user who wrote about travel**/
                    System.out.println("1. GETTING USER");
                    User user = twitterUtils.getUser("travel");
                    String userName = user.getScreenName();
                    System.out.println("1.1 Username: " + userName);
                    /**2. Get all tweets from that user**/
                    System.out.println("2. GETTING TWEETS");
                    List<Status> userTweets = twitterUtils.getTweets(userName, 200);

                    /**
                     *
                     * PART 2 INSERT INFO INTO DB
                     *
                     * **/
                    /**3. Save user info into DB**/
                    System.out.println("3. SAVING USER INTO DB");
                    DatabaseUtils.insertUser(user);

                    /**4. Insert hashtags into DB**/
                    System.out.println("4. SAVING TAGS INTO DB");
                    DatabaseUtils.saveUserTags(user.getId(), userTweets);

                    /**5. Insert user destinations into DB**/
                    System.out.println("5. SAVING USER DESTINATIONS INTO DB");
                    DatabaseUtils.saveUserDestinations(user.getId(), userTweets);

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (TwitterException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mysqlConnect.disconnect();
                }
            }
        }, 0, 20000);
    }

}
