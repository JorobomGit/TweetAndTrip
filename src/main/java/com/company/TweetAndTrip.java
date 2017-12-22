package com.company;

import java.io.*;

import com.google.gson.Gson;
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

        /**Fill Data**/
        //fillData();
        /**Recommend destination**/
        //recommendDestination();
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
/*            for (Double d: probs){
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
            }*/
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
    private static ArrayList<Destination> recommendDestination(String username) throws Exception {
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
        //Sort all classes by probability

        //1. Get all probabilities into a hashmap
        Map<Integer, Double> classesProbability = new HashMap<>();
        double[] distributionForInstance = cls.distributionForInstance(instances.get(0));
        for(int i = 0; i < distributionForInstance.length; i++){
              classesProbability.put(i, distributionForInstance[i]);
        }
        //2. Sort hashmap
        //JAVA 8 SORTING
        //TODO REFACTOR INTO ARRAYLIST
        Map<Integer, Double> result = classesProbability.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (oldValue, newValue) -> oldValue, LinkedHashMap::new));

        //get the name of the classes value
        //3. Get classes
        ArrayList<String> destinations = new ArrayList<>();

        for(int i = 0; i < MAX_DESTINATIONS; i++){
            destinations.add(instances.classAttribute().value(new ArrayList<>(result.keySet()).get(i)));
        }
        String prediction = instances.classAttribute().value((int)value);

        System.out.println("The predicted value of instance " +
                Integer.toString(0) +
                ": " + prediction);

        /**FOURSQUARE**/
        FoursquareUtils foursquareUtils = new FoursquareUtils();
        /**With user tag and recommended destination**/
        Iterator<String> iterator = tweetAndTripUser.getHashtags().iterator();
        ArrayList<Destination> finalDestinations = new ArrayList<>();
        /**Limiting foursquare calls to avoid ban**/
        for (String dest : destinations) {
            ArrayList<Venue> userVenues = new ArrayList<>();
            while (iterator.hasNext() && userVenues.size() < 2) {
                ArrayList<CompactVenue> foursquareVenues = foursquareUtils.searchVenues(dest, iterator.next());
                for (CompactVenue venue : foursquareVenues) {
                    userVenues.add(new Venue(venue.getName(),
                            venue.getCategories().length > 0 ? venue.getCategories()[0].getName() : null,
                            venue.getLocation().getCity()));
                }
            }
            finalDestinations.add(new Destination(dest, userVenues));
        }
        /***RECOMMENDED DESTINATION BASED ON YOUR INFO:***/
        System.out.println("FINAL RECOMENDATION: ");
        System.out.println("For user: " + tweetAndTripUser.getUsername());
        System.out.println("Destination: " + prediction);
        /*for (Venue venue : userVenues) {
            System.out.println("Venues: " + venue.name);
        }*/
        //TODO GET TRUE POSITIVES TO GET REAL CLASSIFICATION
        return finalDestinations;
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
