package com.company;

import twitter4j.HashtagEntity;
import twitter4j.JSONObject;
import twitter4j.Status;
import twitter4j.User;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseUtils {
    public static Connection connection;

    /****STEP 3****/
    public static void insertUser(User user) throws Exception {
        try{
            String insertTableSQL = "INSERT INTO person"
                    + "(id, name, email, screen_name, location, description, lang, gender, age) VALUES"
                    + "(?,?,?,?,?,?,?,?,?)";
            PreparedStatement preparedStatement = connection.prepareStatement(insertTableSQL);
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
        }
    }

    private static String getUserGender(String name) throws Exception {

        JSONObject json = new JSONObject();
        //String url = "https://api.genderize.io/?name=" + name;
        String url = "https://gender-api.com/get?name=" + name;

        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();

        // optional default is GET
        con.setRequestMethod("GET");

        //add request header
        con.setRequestProperty("User-Agent", "your bot 0.1");

        try{
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            json = new JSONObject(response.toString());

        } catch (IOException e){
            System.out.println(e);
        }

        return json.getString("gender");
    }

    private static String getFirstName(String fullName) {
        return fullName.indexOf(' ') > -1 ? fullName.substring(0, fullName.indexOf(' ')) : fullName;
    }

    /****STEP 4****/
    static void saveUserTags(Long userId, List<Status> tweets) throws Exception {
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

    private static HashMap<String, Date> getHashtagsWithDate(List<Status> tweets){
        HashMap<String, Date> hashtags = new HashMap<>();
        for (Status status : tweets) {
            for(HashtagEntity hashtagEntity : status.getHashtagEntities()){
                hashtags.put(hashtagEntity.getText(), new Date(status.getCreatedAt().getTime()));
            }
        }
        return hashtags;
    }

    private static void insertUserTag(Long personId, String tag, Date creationDate) throws Exception {

        try {
            String insertTableSQL = "INSERT INTO person_tag"
                    + "(person_id, tag, created_at) VALUES"
                    + "(?,?,?)";
            PreparedStatement preparedStatement = connection.prepareStatement(insertTableSQL);
            preparedStatement.setLong(1, personId);
            preparedStatement.setString(2, tag);
            preparedStatement.setDate(3, creationDate);

            // execute insert SQL statement
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /****STEP 5****/
    static void saveUserDestinations(Long userId, List<Status> tweets) throws Exception {
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

        try {
            PreparedStatement statement = connection.prepareStatement("SELECT * FROM `destinations`");
            ResultSet rs = statement.executeQuery();
            while(rs.next()){
                destinations.put(rs.getInt("id"), rs.getString("name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return destinations;
    }

    private static void insertUserDestination(Long personId, Integer destinationId, Date creationDate) throws Exception {

        try {
            String insertTableSQL = "INSERT INTO person_destination"
                    + "(person_id, destination_id, created_at) VALUES"
                    + "(?,?,?)";
            PreparedStatement preparedStatement = connection.prepareStatement(insertTableSQL);
            preparedStatement.setLong(1, personId);
            preparedStatement.setInt(2, destinationId);
            preparedStatement.setDate(3, creationDate);

            // execute insert SQL statement
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
