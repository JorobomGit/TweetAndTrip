package com.company;

import com.google.common.collect.Lists;
import com.twitter.hbc.ClientBuilder;
import com.twitter.hbc.core.Client;
import com.twitter.hbc.core.Constants;
import com.twitter.hbc.core.Hosts;
import com.twitter.hbc.core.HttpHosts;
import com.twitter.hbc.core.endpoint.StatusesFilterEndpoint;
import com.twitter.hbc.core.event.Event;
import com.twitter.hbc.core.processor.StringDelimitedProcessor;
import com.twitter.hbc.httpclient.auth.Authentication;
import com.twitter.hbc.httpclient.auth.OAuth1;
import twitter4j.*;
import twitter4j.conf.ConfigurationBuilder;

import java.sql.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.List;

public class Main {

    public static void main(String[] args) throws TwitterException {
    }

    private static void testingDB() {
        MysqlConnect connection = new MysqlConnect();
        String sql = "SELECT * FROM `tbl_name`";
        try {
            PreparedStatement statement = connection.connect().prepareStatement(sql);
            ResultSet rs = statement.executeQuery();
            while(rs.next()){
                System.out.println(rs.getString("column1"));
            }


        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }
    }

    private static void testingTwitter4j() throws TwitterException {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey("g2VSP0upGyNqI0vz8oZkTsoTo")
                .setOAuthConsumerSecret("yV9EA3oQhwUcZ9DCA6wiHXWq997H87W0BZl99UNPwa2L2e4gn8")
                .setOAuthAccessToken("919986650691915776-yFrT4Erbgz1duaDykvaquhVktbpvBtf")
                .setOAuthAccessTokenSecret("x4AQ3o3Ia11Z0PTaRlEkbW2l2c4k9P7uO8lW8oMSNi07B");
        TwitterFactory tf = new TwitterFactory(cb.build());
        Twitter twitter = tf.getInstance();

        Query query = new Query("#viajar");
        QueryResult result;

        do {
            result = twitter.search(query);
            List<Status> tweets = result.getTweets();
            for (Status tweet : tweets) {
                System.out.println("@" + tweet.getUser().getScreenName() + " - " + tweet.getText());
            }
        } while ((query = result.nextQuery()) != null);


        //STEPS:
        //1. Read tweets from specific hashtags
        //2. Take city or country from those tweets, also pick info from user
        //3. We will need a DB with tables: user, tweet info. User could contain also destination.
        //4. We need also a table with countries and cities to filter those tweets.
    }

    private static void testingHBC() throws InterruptedException {
        /** Set up your blocking queues: Be sure to size these properly based on expected TPS of your stream */
        BlockingQueue<String> msgQueue = new LinkedBlockingQueue<String>(100000);
        BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<Event>(1000);
        /** Declare the host you want to connect to, the endpoint, and authentication (basic auth or oauth) */
        Hosts hosebirdHosts = new HttpHosts(Constants.STREAM_HOST);
        StatusesFilterEndpoint hosebirdEndpoint = new StatusesFilterEndpoint();
        // Optional: set up some followings and track terms
        List<Long> followings = Lists.newArrayList(1234L, 566788L);
        List<String> terms = Lists.newArrayList("travel");
        hosebirdEndpoint.followings(followings);
        hosebirdEndpoint.trackTerms(terms);

        // These secrets should be read from a config file
        Authentication hosebirdAuth = new OAuth1("g2VSP0upGyNqI0vz8oZkTsoTo",
                "yV9EA3oQhwUcZ9DCA6wiHXWq997H87W0BZl99UNPwa2L2e4gn8",
                "919986650691915776-yFrT4Erbgz1duaDykvaquhVktbpvBtf",
                "x4AQ3o3Ia11Z0PTaRlEkbW2l2c4k9P7uO8lW8oMSNi07B");

        ClientBuilder builder = new ClientBuilder()
                .name("Hosebird-Client-01")                              // optional: mainly for the logs
                .hosts(hosebirdHosts)
                .authentication(hosebirdAuth)
                .endpoint(hosebirdEndpoint)
                .processor(new StringDelimitedProcessor(msgQueue))
                .eventMessageQueue(eventQueue);                          // optional: use this if you want to process client events

        Client hosebirdClient = builder.build();
        // Attempts to establish a connection.
        hosebirdClient.connect();
        System.out.println("Ended successfully");


        // on a different thread, or multiple different threads....
        while (!hosebirdClient.isDone()) {
            String msg = msgQueue.take();
            System.out.println(msg);
        }

        hosebirdClient.stop();
    }
}
