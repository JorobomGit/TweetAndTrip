package com.company;

import fi.foyt.foursquare.api.FoursquareApi;
import fi.foyt.foursquare.api.FoursquareApiException;
import fi.foyt.foursquare.api.Result;
import fi.foyt.foursquare.api.entities.CompactVenue;
import fi.foyt.foursquare.api.entities.VenuesSearchResult;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

public class FoursquareUtils {

    private static Properties properties;

    /**Get properties from specific file**/
    private static Properties getProperties() throws IOException {
        if (properties == null) {
            properties = new Properties();
            FileInputStream in = new FileInputStream("config/foursquare.oauth.properties");
            properties.load(in);
            in.close();
        }
        return properties;
    }

    public ArrayList<CompactVenue> searchVenues(String near, String tag) throws FoursquareApiException, IOException {
        // First we need a initialize FoursquareApi.
        FoursquareApi foursquareApi = new FoursquareApi(getProperties().getProperty("clientid"), getProperties().getProperty("clientsecret"), "Callback URL");
        ArrayList<CompactVenue> venues = new ArrayList<>();
        // After client has been initialized we can make queries.
        Result<VenuesSearchResult> result = foursquareApi.venuesSearch(near, tag, 3, null, null, null, null, null);
        if (result.getMeta().getCode() == 200) {
            // if query was ok we can finally we do something with the data
            for (CompactVenue venue : result.getResult().getVenues()) {
                System.out.println(venue.getName());
                venues.add(venue);
            }

        } else {
            System.out.println("Error occured: ");
            System.out.println("  code: " + result.getMeta().getCode());
            System.out.println("  type: " + result.getMeta().getErrorType());
            System.out.println("  detail: " + result.getMeta().getErrorDetail());
        }
        return venues;
    }
}
