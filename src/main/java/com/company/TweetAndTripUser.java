package com.company;

import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.User;

import java.util.List;

public class TweetAndTripUser {
    String username;
    User user;
    List<Status> userTweets;
    List<String> hashtags;
    TwitterUtils twitterUtils;
    public TweetAndTripUser(String username, TwitterUtils twitterUtils) throws TwitterException {
        this.twitterUtils = twitterUtils;
        this.username = username;
        this.user = twitterUtils.getUserByName(username);
        this.userTweets = twitterUtils.getTweets(username, 200);
        this.hashtags = twitterUtils.getUserHashtags(userTweets);
    }

    static String buildTestString(TweetAndTripUser tweetAndTripUser) throws Exception {
        List<String> hashtags = tweetAndTripUser.getHashtags();
        String hashtagsSingleString = parseTags(hashtags);
        User user = tweetAndTripUser.getUser();

        return parseField(user.getLocation()) + ','
                + parseField(user.getLang()) + ','
                + DatabaseUtils.getUserGender(user.getName(), DatabaseUtils.url) + ','
                //+ parseField(user.getDescription()) + ','
                + hashtagsSingleString + ','
                + "?\n";
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
        field = field.length() > 0 ? field : "Spain";
        field = field.replace("\n", "").replace("\r", "").replace(",", "");
        return field.indexOf(' ') > 0 ? "'" + field + "'" : field;
    }

    public String getUsername() {
        return username;
    }

    public User getUser() {
        return user;
    }

    public List<Status> getUserTweets() {
        return userTweets;
    }

    public List<String> getHashtags() {
        return hashtags;
    }

}
