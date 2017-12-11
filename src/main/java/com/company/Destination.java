package com.company;

import java.util.ArrayList;

public class Destination {

    private String name;
    private ArrayList<Venue> userVenues;

    public Destination(String name) {
        this.name = name;
    }

    public Destination(String name, ArrayList<Venue> userVenues) {
        this.name = name;
        this.userVenues = userVenues;
    }

    public String getName() {
        return name;
    }

    public ArrayList<Venue> getUserVenues() {
        return userVenues;
    }
}
