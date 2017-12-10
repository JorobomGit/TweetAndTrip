package com.company;

import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class TwitterUtils {
    /**Twitter API properties**/
    private static Properties properties;
    private static Twitter twitter;

    public TwitterUtils() throws IOException {
        this.twitter = this.getTwitterInstance();
    }

    /**Get properties from specific file**/
    private static Properties getProperties() throws IOException {
        if (properties == null) {
            properties = new Properties();
            FileInputStream in = new FileInputStream("config/oauth.properties");
            properties.load(in);
            in.close();
        }
        return properties;
    }

    /****STEP 2****/
    public List<Status> getTweets(String user, Integer max) throws TwitterException {
        List<Status> statuses = null;
        try {
            Paging paging = new Paging(1, max);

            statuses = twitter.getUserTimeline(user, paging);
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get timeline: " + te.getMessage());
            System.exit(-1);
        }
        return statuses;
    }

    public User getUserByName(String name) throws TwitterException {
        return twitter.showUser(name);
    }

    /****STEP 1: Get User given a word****/
    public User getUser(String word) throws TwitterException {
        Query query = new Query(word);
        QueryResult result = twitter.search(query);
        User user = null;
        if(result.getTweets().size() > 0){
            user = result.getTweets().get(0).getUser();
        }
        return user;
    }

    /**Create a new twitter instance**/
    public Twitter getTwitterInstance() throws IOException {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey(getProperties().getProperty("consumerkey"))
                .setOAuthConsumerSecret(getProperties().getProperty("consumersecret"))
                .setOAuthAccessToken(getProperties().getProperty("accesstoken"))
                .setOAuthAccessTokenSecret(getProperties().getProperty("accesstokensecret"));
        TwitterFactory tf = new TwitterFactory(cb.build());
        return tf.getInstance();
    }


    public static ArrayList<String> getUserHashtags(List<Status> tweets){
        ArrayList<String> hashtags = new ArrayList<>();
        for (Status status : tweets) {
            for(HashtagEntity hashtagEntity : status.getHashtagEntities()){
                hashtags.add(hashtagEntity.getText());
            }
        }
        return hashtags;
    }
}
