package com.company;

import twitter4j.*;

import java.io.*;
import java.util.*;


public class Main {

    private static MysqlConnect mysqlConnect = new MysqlConnect();

    public static void main(String[] args) throws Exception {

        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                /**Runs every 20 seconds**/
                try {
                    TwitterUtils twitterUtils = new TwitterUtils();
                    DatabaseUtils.connection = mysqlConnect.connect();
                    /**1. Get an user who wrote about travel**/
                    System.out.println("1. GETTING USER");
                    User user = twitterUtils.getUser("travel");
                    String userName = user.getScreenName();
                    System.out.println("1.1 Username: " + userName);
                    /**2. Get all tweets from that user**/
                    System.out.println("2. GETTING TWEETS");
                    List<Status> userTweets = twitterUtils.getTweets(userName, 200);

                    //3. Save user info into DB
                    System.out.println("3. SAVING USER INTO DB");
                    DatabaseUtils.insertUser(user);

                    //4. Insert hashtags into DB
                    System.out.println("4. SAVING TAGS INTO DB");
                    DatabaseUtils.saveUserTags(user.getId(), userTweets);

                    //5. Insert user destinations into DB
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
