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
