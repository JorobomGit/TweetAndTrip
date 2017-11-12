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

import javax.net.ssl.HttpsURLConnection;
import javax.xml.transform.Result;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.sql.Date;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class Main {

    // init properties object
    private static Properties properties;

    private static final String USER_AGENT = "Mozilla/5.0";

    public static void main(String[] args) throws Exception {


        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                Twitter tf = null;
                try {
                    tf = getTwitterInstance();
                    //RUN EVERY 20 SECONDS
                    //1. Get an user who wrote about travel
                    System.out.println("1. GETTING USER");
                    User user = getUser("travel", tf);
                    String userName = user.getScreenName();
                    System.out.println("1.1 Username: " + userName);

                    //2. Get all tweets from that user
                    System.out.println("2. GETTING TWEETS");
                    List<Status> userTweets = getTweets(userName, tf, 200);

                    //3. Save user info into DB
                    System.out.println("3. SAVING USER INTO DB");
                    insertUser(user);

                    //4. Insert hashtags into DB
                    System.out.println("4. SAVING TAGS INTO DB");
                    saveUserTags(user.getId(), userTweets);

                    //4. Insert user destinations into DB
                    saveUserDestinations(user.getId(), userTweets);

                } catch (IOException e) {
                    e.printStackTrace();
                } catch (TwitterException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 0, 20000);
    }



    private static User getUserInfo(String user, Twitter twitter) throws TwitterException {
        return twitter.showUser(user);
    }


    private static List<Status> getTweets(String user, Twitter twitter, Integer max) throws TwitterException {
        List<Status> statuses = null;
        try {
            Paging paging = new Paging(1, max);

            statuses = twitter.getUserTimeline(user, paging);
            System.out.println("Showing @" + user + "'s user timeline.");
        } catch (TwitterException te) {
            te.printStackTrace();
            System.out.println("Failed to get timeline: " + te.getMessage());
            System.exit(-1);
        }
        return statuses;
        //STEPS:
        //1. Read tweets from specific hashtags
        //2. Take city or country from those tweets, also pick info from user
        //3. We will need a DB with tables: user, tweet info. User could contain also destination.
        //4. We need also a table with countries and cities to filter those tweets.

    }

    private static User getUser(String word, Twitter twitter) throws TwitterException {
        Query query = new Query(word);
        QueryResult result = twitter.search(query);
        User user = null;
        if(result.getTweets().size() > 0){
            user = result.getTweets().get(0).getUser();
        }
        return user;
    }

    private static HashMap<String, Date> getHashtagsWithDate(List<Status> tweets){
        HashMap<String, Date> hashtags = new HashMap<>();
        for (Status status : tweets) {
            for(HashtagEntity hashtagEntity : status.getHashtagEntities()){
                hashtags.put(hashtagEntity.getText(), new Date(status.getCreatedAt().getTime()));
            }
        }
        return hashtags;
    }


    private static void saveUserTags(Long userId, List<Status> tweets) throws Exception {
        HashMap<String, Date> hashtags = getHashtagsWithDate(tweets);
        Integer insertCount = 0;
        //Insertions limited to 50 per user
        for (Map.Entry<String, Date> tag: hashtags.entrySet()) {
            if(insertCount > 50){
                break;
            }
            if(tag.getKey().length() < 50){
                insertUserTag(userId, tag.getKey(), tag.getValue());
            }
            insertCount++;
        }
    }

    private static void saveUserDestinations(Long userId, List<Status> tweets) throws Exception {
        //1. Get our database destinations list
        HashMap<Integer, String> dbDestinations = getDBDestinations();
        //2. Compare each city with each tweet to look matches
        for (Map.Entry<Integer, String> destination: dbDestinations.entrySet()) {
            for(Status tweet: tweets){
                if(tweet.getText().contains(destination.getValue())){
                    insertUserDestination(userId,
                            destination.getKey(),
                            new Date(tweet.getCreatedAt().getTime()));
                }
            }
        }
    }


    private static HashMap<Integer, String> getDBDestinations() throws SQLException {
        HashMap<Integer, String> destinations = new HashMap<>();

        MysqlConnect connection = new MysqlConnect();
        try {
            PreparedStatement statement = connection.connect().prepareStatement("SELECT * FROM `destinations`");
            ResultSet rs = statement.executeQuery();
            while(rs.next()){
                destinations.put(rs.getInt("id"), rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }

        return destinations;
    }


    // create properties
    private static Properties getProperties() throws IOException {
        if (properties == null) {
            properties = new Properties();
            FileInputStream in = new FileInputStream("config/oauth.properties");
            properties.load(in);
            in.close();
        }
        return properties;
    }

    private static Twitter getTwitterInstance() throws IOException {
        ConfigurationBuilder cb = new ConfigurationBuilder();
        cb.setDebugEnabled(true)
                .setOAuthConsumerKey(getProperties().getProperty("consumerkey"))
                .setOAuthConsumerSecret(getProperties().getProperty("consumersecret"))
                .setOAuthAccessToken(getProperties().getProperty("accesstoken"))
                .setOAuthAccessTokenSecret(getProperties().getProperty("accesstokensecret"));
        TwitterFactory tf = new TwitterFactory(cb.build());
        return tf.getInstance();
    }

    public static TwitterAds getTwitterAdsInstance() throws IOException {
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        configurationBuilder.setOAuthConsumerSecret("").setOAuthConsumerKey("").setOAuthAccessToken("").setOAuthAccessTokenSecret("git ").setHttpRetryCount(0).setHttpConnectionTimeout(5000);
        return new TwitterAdsFactory(configurationBuilder.build()).getAdsInstance();
    }

    private static void testingHBC() throws InterruptedException, IOException {
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
        Authentication hosebirdAuth = new OAuth1(getProperties().getProperty("consumerkey"),
                getProperties().getProperty("consumersecret"),
                getProperties().getProperty("accesstoken"),
                getProperties().getProperty("accesstokensecret"));

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

    // HTTP POST request
    private void sendPost() throws Exception {

        String url = "https://selfsolve.apple.com/wcResults.do";
        URL obj = new URL(url);
        HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

        //add reuqest header
        con.setRequestMethod("POST");
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

        String urlParameters = "sn=C02G8416DRJM&cn=&locale=&caller=&num=12345";

        // Send post request
        con.setDoOutput(true);
        DataOutputStream wr = new DataOutputStream(con.getOutputStream());
        wr.writeBytes(urlParameters);
        wr.flush();
        wr.close();

        int responseCode = con.getResponseCode();
        System.out.println("\nSending 'POST' request to URL : " + url);
        System.out.println("Post parameters : " + urlParameters);
        System.out.println("Response Code : " + responseCode);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //print result
        System.out.println(response.toString());

    }

    private static String getFirstName(String fullName) {
        return fullName.indexOf(' ') > -1 ? fullName.substring(0, fullName.indexOf(' ')) : fullName;
    }

    private static void insertUser(User user) throws Exception {
        /*System.out.println("User info: " );
        System.out.println("User name: " + user.getName());
        System.out.println("User screenName: " + user.getScreenName());
        System.out.println("User email: " + user.getEmail());
        System.out.println("User location: " + user.getLocation());
        System.out.println("User description: " + user.getDescription());
        System.out.println("User lang: " + user.getLang());
        System.out.println("User gender: " + 0);
        System.out.println("User age: " + 0);*/

        MysqlConnect connection = new MysqlConnect();
        try{
            String insertTableSQL = "INSERT INTO person"
                    + "(id, name, email, screen_name, location, description, lang, gender, age) VALUES"
                    + "(?,?,?,?,?,?,?,?,?)";
            PreparedStatement preparedStatement = connection.connect().prepareStatement(insertTableSQL);
            preparedStatement.setLong(1, user.getId());
            preparedStatement.setString(2, user.getName());
            preparedStatement.setString(3, user.getEmail());
            preparedStatement.setString(4, user.getScreenName());
            preparedStatement.setString(5, user.getLocation());
            preparedStatement.setString(6, user.getDescription());
            preparedStatement.setString(7, user.getLang());
            preparedStatement.setString(8, getUserGender(getFirstName(user.getName())));
            preparedStatement.setInt(9, 0);

            // execute insert SQL statement
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }
    }

    // HTTP GET request
    private static String getUserGender(String name) throws Exception {

        //String url = "https://api.genderize.io/?name=" + name;
        String url = "https://gender-api.com/get?name=" + name;

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        //add request header
        con.setRequestProperty("User-Agent", USER_AGENT);

        BufferedReader in = new BufferedReader(
                new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer response = new StringBuffer();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject json = new JSONObject(response.toString());
        return json.getString("gender");
    }

    private static void insertUserTag(Long personId, String tag, Date creationDate) throws Exception {

        MysqlConnect connection = new MysqlConnect();
        try {
            String insertTableSQL = "INSERT INTO person_tag"
                    + "(person_id, tag, created_at) VALUES"
                    + "(?,?,?)";
            PreparedStatement preparedStatement = connection.connect().prepareStatement(insertTableSQL);
            preparedStatement.setLong(1, personId);
            preparedStatement.setString(2, tag);
            preparedStatement.setDate(3, creationDate);

            // execute insert SQL statement
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }
    }

    private static void insertUserDestination(Long personId, Integer destinationId, Date creationDate) throws Exception {

        MysqlConnect connection = new MysqlConnect();
        try {
            String insertTableSQL = "INSERT INTO person_destination"
                    + "(person_id, destination_id, created_at) VALUES"
                    + "(?,?,?)";
            PreparedStatement preparedStatement = connection.connect().prepareStatement(insertTableSQL);
            preparedStatement.setLong(1, personId);
            preparedStatement.setInt(2, destinationId);
            preparedStatement.setDate(3, creationDate);

            // execute insert SQL statement
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            connection.disconnect();
        }
    }

}
