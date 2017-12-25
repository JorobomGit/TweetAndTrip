package com.company;

import java.io.*;

import com.google.gson.Gson;
import fi.foyt.foursquare.api.FoursquareApiException;
import fi.foyt.foursquare.api.entities.CompactVenue;
import twitter4j.Status;
import twitter4j.TwitterException;
import twitter4j.User;

import java.util.*;
import java.util.stream.Collectors;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayesMultinomialText;
import weka.core.Instance;
import weka.core.Instances;
import weka.filters.unsupervised.instance.RemovePercentage;

import static spark.Spark.*;

public class TweetAndTrip {

    private static final int MAX_DESTINATIONS = 5;
    private static MysqlConnect mysqlConnect = new MysqlConnect();

    public static void main(String[] args) {

        /*Fill Data*/
        //fillData();
        /*Recommend destination*/
        get("/destination", (req, res) -> {
            res.type("application/json");
            String name = req.queryParams("name");
            String jsonInString;
            try {
                ArrayList<Destination> destinations = recommendDestination(name);
                Gson gson = new Gson();
                jsonInString = gson.toJson(destinations);
            } catch (TwitterException e){
                e.printStackTrace();
                System.out.println("Failed to get timeline: " + e.getMessage());
                jsonInString = e.getMessage();
                res.status(e.getStatusCode());
                res.body(e.getMessage());
            } catch (Exception e) {
                res.status(500);
                res.body(e.getMessage());
                jsonInString = e.getMessage();
            }
            return jsonInString;
        });
    }

    private static void evaluateTopN(String fullData, int N) throws Exception {
        boolean doCV=false;
        BufferedReader in = new BufferedReader(new FileReader(fullData));
        Instances data = new Instances(in);
        in.close();

        data.setClassIndex(data.numAttributes()-1);

        if (doCV){
            Classifier cls = new NaiveBayesMultinomialText();
            Evaluation eval = new Evaluation(data);
            eval.crossValidateModel(cls, data, 5, new Random(1L));
        }

        RemovePercentage filter = new RemovePercentage();
        filter.setPercentage(0.8);
        filter.setInputFormat(data);
        Instances test=filter.getOutputFormat();
        filter.setInvertSelection(true);
        Instances train=filter.getOutputFormat();

        Classifier cls = new NaiveBayesMultinomialText();
        cls.buildClassifier(train);

        int tp=0;
        int total=0;

        for (Instance i: test){
            total++;
            double[] dist = cls.distributionForInstance(i);
            int c = i.classIndex();
            //List<Integer> dests = getSortedDestinationIds(dist);
            List<Integer> ids = new ArrayList<>();
            // calculate top N
            Map<Double, Set<Integer>> map = new HashMap<>();
            for (int j=0; j<dist.length; j++){
                Set<Integer> idx = map.get(dist[j]);
                if (idx==null){
                    idx = new HashSet<>();
                    map.put(dist[j], idx);
                }
                idx.add(j);
            }
            List<Double> probs = new ArrayList<>(map.keySet());
            Collections.sort(probs, Collections.reverseOrder());
            int n = 0;
           for (Double d: probs){
                for (Integer idx: map.get(d)){
                    if (n >= N){
                        break;
                    }
                    if(idx == c){
                        // match: success
                        tp++;
                        break;
                    }
                    n++;
                }
            }
            for (Double d: probs){
                for (Integer idx: map.get(d)){
                    if (n >= N){
                        break;
                    }
                    ids.add(idx);
                }
            }
        }
        System.out.println("True positive at " + N + ": " + (1.0*tp/total));
    }

    /**
     * recommendDestination
     * Given an username, recommends Destinations, including venues on that object.
     * @param username User username
     * @return ArrayList<Destination>
     * @throws Exception exception
     */
    private static ArrayList<Destination> recommendDestination(String username) throws Exception {
        /*Get user*/
        TwitterUtils twitterUtils = new TwitterUtils();
        TweetAndTripUser tweetAndTripUser = new TweetAndTripUser(username, twitterUtils);
        /*Get user instances*/
        Instances instances = getUserInstances(tweetAndTripUser);
        /*Get user recommendedDestinations*/
        ArrayList<String> recommendedDestinations = getNRecommendedDestinations(instances, MAX_DESTINATIONS);

        /*Get user recommendedDestinations with Venues*/
        return getDestinationsWithVenues(tweetAndTripUser, recommendedDestinations);

        //TODO GET TRUE POSITIVES TO GET REAL CLASSIFICATION
        //TODO REFACTOR INTO .JAR APP
        //TODO SPA ANGULAR 5
    }

    /**
     * Builds user test file and get instances for Weka
     * @param tweetAndTripUser User tweetAndTrip format
     * @return Instances instances
     * @throws Exception exception
     */
    private static Instances getUserInstances(TweetAndTripUser tweetAndTripUser) throws Exception {
        /*Build test file with user name and gets instance*/
        WekaUtils.buildTestFile(TweetAndTripUser.buildTestString(tweetAndTripUser));
        return WekaUtils.getTestInstances();
    }

    /**
     * getNRecommendedDestinations
     * Gets N recommended destinations based on trained model with Weka library.
     * @param instances instances
     * @param maxDestinations max number of destinations to recommend
     * @return ArrayList<String> destinations
     * @throws Exception exception
     */
    private static ArrayList<String> getNRecommendedDestinations(Instances instances, int maxDestinations) throws Exception {
    /*Classifier with already trained model*/
        Classifier cls = (Classifier) weka.core.SerializationHelper.read("data/tweetAndTrip.model");
        /*Get hashmap with index, probability sorted*/
        Map<Integer, Double> classesProbability = new HashMap<>();
        double[] distributionForInstance = cls.distributionForInstance(instances.get(0));
        for(int i = 0; i < distributionForInstance.length; i++){
              classesProbability.put(i, distributionForInstance[i]);
        }
        /*Sort hashmap*/
        Map<Integer, Double> result = classesProbability.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        /*Get top classes*/
        ArrayList<String> destinations = new ArrayList<>();
        for(int i = 0; i < maxDestinations; i++){
            destinations.add(instances.classAttribute().value(new ArrayList<>(result.keySet()).get(i)));
        }
        return destinations;
    }

    /**
     * getDestinationsWithVenues
     * Adds venues to destinations with foursquare API
     * @param tweetAndTripUser User tweetAndTrip format
     * @param recommendedDestinations recommended destinations
     * @return ArrayList<String> destinations with venues
     * @throws FoursquareApiException exception
     * @throws IOException exception
     */
    private static ArrayList<Destination> getDestinationsWithVenues(TweetAndTripUser tweetAndTripUser, ArrayList<String> recommendedDestinations) throws FoursquareApiException, IOException {
        FoursquareUtils foursquareUtils = new FoursquareUtils();
        /*With user tag and recommended destination*/
        Iterator<String> iterator = tweetAndTripUser.getHashtags().iterator();
        ArrayList<Destination> finalDestinations = new ArrayList<>();
        /*Limiting foursquare calls to avoid ban*/
        for (String dest : recommendedDestinations) {
            ArrayList<Venue> userVenues = new ArrayList<>();
            while (iterator.hasNext() && userVenues.size() < 2) {
                ArrayList<CompactVenue> foursquareVenues = foursquareUtils.searchVenues(dest, iterator.next());
                for (CompactVenue venue : foursquareVenues) {
                    userVenues.add(new Venue(venue.getName(),
                            venue.getCategories().length > 0 ? venue.getCategories()[0].getName() : null,
                            venue.getLocation().getCity()));
                }
            }
            if(userVenues.size() == 0){
                ArrayList<CompactVenue> foursquareVenues = foursquareUtils.searchVenues(dest, null);
                for (CompactVenue venue : foursquareVenues) {
                    userVenues.add(new Venue(venue.getName(),
                            venue.getCategories().length > 0 ? venue.getCategories()[0].getName() : null,
                            venue.getLocation().getCity()));
                }
            }
            finalDestinations.add(new Destination(dest, userVenues));
        }
        return finalDestinations;
    }

    /**
     * fillData
     * Method to get more entries for the database
     * Get users that tweeted something about #travel
     * Inserts them, with their tags and destinations
     */
    private static void fillData() {
        Timer t = new Timer();
        t.schedule(new TimerTask() {
            @Override
            public void run() {
                /*Runs every 20 seconds**/
                try {
                    TwitterUtils twitterUtils = new TwitterUtils();
                    DatabaseUtils.connection = mysqlConnect.connect();

                    /*1. Get an user who wrote about travel*/
                    User user = twitterUtils.getUser("travel");
                    String userName = user.getScreenName();

                    /*2. Get all tweets from that user*/
                    List<Status> userTweets = twitterUtils.getTweets(userName, 200);

                    /*3. Save user info into DB**/
                    DatabaseUtils.insertUser(user);

                    /*4. Insert hashtags into DB*/
                    DatabaseUtils.saveUserTags(user.getId(), userTweets);

                    /*5. Insert user destinations into DB*/
                    DatabaseUtils.saveUserDestinations(user.getId(), userTweets);

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mysqlConnect.disconnect();
                }
            }
        }, 0, 20000);
    }

}
