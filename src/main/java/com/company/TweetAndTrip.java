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
import weka.filters.Filter;
import weka.filters.unsupervised.instance.RemovePercentage;

import static org.netlib.lapack.Slacon.i;
import static spark.Spark.*;

public class TweetAndTrip {

    private static final int DEFAULT_N = 5;
    private static final int MAX_DESTINATIONS = 350;
    private static MysqlConnect mysqlConnect = new MysqlConnect();

    public static void main(String[] args) throws Exception {
        boolean exit = false;
        do {
            Scanner scanner = new Scanner(System.in);
            System.out.println("Please, select option:");
            System.out.println("Option \t Description");
            System.out.println("1 \t\t Collect more data from Twitter (Database server must be running)");
            System.out.println("2 \t\t Evaluate top N");
            System.out.println("3 \t\t Run API");
            System.out.println("4 \t\t Exit");
            int app = scanner.nextInt();

            switch (app) {
                case 1:
                    fillData();
                    break;
                case 2:
                    System.out.println("Select N (1-350)");
                    int inputN = scanner.nextInt();
                    evaluateTopN("data/tweetAndTripSingleTag.arff", inputN);
                    break;
                case 3:
                    getDestination();
                    break;
                case 4:
                    System.out.println("Have a nice trip!");
                    exit = true;
                    break;
                default:
                    System.out.println("Invalid option");

            }
        } while(!exit);
    }

    private static void getDestination() {
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
        if(Double.isNaN((double) N) || N < 1 || N > MAX_DESTINATIONS){
            System.out.println("N must be between 1-350");
            return;
        }
        System.out.println("Loading... Please, be patient!");
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
        filter.setInputFormat(data);
        filter.setPercentage(80);
        Instances test = Filter.useFilter(data, filter);

        filter = new RemovePercentage();
        filter.setInvertSelection(true);
        filter.setInputFormat(data);
        filter.setPercentage(80);
        Instances train = Filter.useFilter(data, filter);

        Classifier cls = new NaiveBayesMultinomialText();
        cls.buildClassifier(train);

        int truePositives=0;
        int partialTest = 0;
        for (Instance i: test){
            ArrayList<String> recommendedDestinations = getNRecommendedDestinations(i, N, cls);
            partialTest++;
           for (String destination: recommendedDestinations){
                if(destination.equals(i.classAttribute().value((int) i.value(i.classIndex())))){
                    truePositives++;
                    System.out.println("truePositive " + truePositives + " of " + partialTest);
                    break;
                }
            }
        }
        System.out.println("True positive at " + N + ": " + (1.0*truePositives/test.size()));
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
        ArrayList<String> recommendedDestinations = getNRecommendedDestinations(instances.get(0), DEFAULT_N, null);

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
     * @param instance instance
     * @param maxDestinations max number of destinations to recommend
     * @return ArrayList<String> destinations
     * @throws Exception exception
     */
    private static ArrayList<String> getNRecommendedDestinations(Instance instance, int maxDestinations, Classifier auxCls) throws Exception {
    /*Classifier with already trained model*/
        Classifier cls = auxCls != null ? auxCls : (Classifier) weka.core.SerializationHelper.read("data/tweetAndTrip.model");
        /*Get hashmap with index, probability sorted*/
        Map<Integer, Double> classesProbability = new HashMap<>();
        double[] distributionForInstance = cls.distributionForInstance(instance);
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
            destinations.add(instance.classAttribute().value(new ArrayList<>(result.keySet()).get(i)));
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
