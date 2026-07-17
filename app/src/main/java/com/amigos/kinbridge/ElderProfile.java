package com.amigos.kinbridge;

import java.util.List;

/** The elder's profile: identity + ground-truth facts + last smoothed score. */
public class ElderProfile {
    public String name;
    public String location;
    public double prevEwma;
    public List<ElderFact> facts;

    public ElderProfile(String name, String location, double prevEwma, List<ElderFact> facts) {
        this.name = name;
        this.location = location;
        this.prevEwma = prevEwma;
        this.facts = facts;
    }
}
