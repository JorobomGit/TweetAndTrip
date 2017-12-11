package com.company;

import java.io.*;

import com.google.gson.Gson;
import fi.foyt.foursquare.api.entities.CompactVenue;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.User;

import java.util.*;

import weka.classifiers.Classifier;
import weka.core.Instances;

import static spark.Spark.*;

public class TweetAndTrip {

    private static MysqlConnect mysqlConnect = new MysqlConnect();

    public static void main(String[] args) {

        /**Fill Data**/
        //fillData();
        /**Recommend destination**/
        //recommendDestination();
        get("/destination", (req, res) -> {
            res.type("application/json");
            String name = req.queryParams("name");
            String jsonInString;
            try {
                Destination destination = recommendDestination(name);
                Gson gson = new Gson();

                jsonInString = gson.toJson(destination);
            } catch (TwitterException e){
                e.printStackTrace();
                System.out.println("Failed to get timeline: " + e.getMessage());
                jsonInString = e.getMessage();
                res.status(e.getStatusCode());
                res.body(e.getMessage());
            }
            return jsonInString;
        });
    }

    private static Destination recommendDestination(String username) throws Exception {
        /**1. Get user name
         * 2. Get location, language, gender, description, tags, ?
         * 3. Build new .arff with those fields
         * 4. Reuse .model already trained
         * 5. This will give a destination predicted
         */
        TwitterUtils twitterUtils = new TwitterUtils();
        TweetAndTripUser tweetAndTripUser = null;
        tweetAndTripUser = new TweetAndTripUser(username, twitterUtils);
        /**Build file with user name**/
        WekaUtils.buildTestFile(TweetAndTripUser.buildTestString(tweetAndTripUser));

        /**Classifier with already trained model**/
        Classifier cls = (Classifier) weka.core.SerializationHelper.read("data/tweetAndTrip.model");

        /**Instances from new file**/
        Instances instances = WekaUtils.getWekaInstance();
        double value = cls.classifyInstance(instances.get(0));
        //get the name of the class value
        String prediction = instances.classAttribute().value((int)value);

        System.out.println("The predicted value of instance " +
                Integer.toString(0) +
                ": " + prediction);

        /**FOURSQUARE**/
        FoursquareUtils foursquareUtils = new FoursquareUtils();
        ArrayList<Venue> userVenues = new ArrayList<>();
        /**With user tag and recommended destination**/
        Iterator<String> iterator = tweetAndTripUser.getHashtags().iterator();
        /**Limiting foursquare calls to avoid ban**/
        while(iterator.hasNext() && userVenues.size() < 2) {
            ArrayList<CompactVenue> foursquareVenues = foursquareUtils.searchVenues(prediction, iterator.next());
            for (CompactVenue venue : foursquareVenues) {
                userVenues.add(new Venue(venue.getName(),
                        venue.getCategories()[0].getName(),
                        venue.getLocation().getCity()));
            }
        }

        /***RECOMMENDED DESTINATION BASED ON YOUR INFO:***/
        System.out.println("FINAL RECOMENDATION: ");
        System.out.println("For user: " + tweetAndTripUser.getUsername());
        System.out.println("Destination: " + prediction);
        for (Venue venue : userVenues) {
            System.out.println("Venues: " + venue.name);
        }
        return new Destination(prediction, userVenues);
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
