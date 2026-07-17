package com.amigos.kinbridge;

import java.util.List;

/** The elder's profile: identity + ground-truth facts + community groups. */
public class ElderProfile {
    public String name;
    public String preferredAddress;
    public String city;
    public double prevEwma;
    public String daughterPhone;
    public List<ElderFact> facts;
    public List<CommunityGroup> groups;

    public ElderProfile(String name, String preferredAddress, String city, double prevEwma,
                        String daughterPhone, List<ElderFact> facts, List<CommunityGroup> groups) {
        this.name = name;
        this.preferredAddress = preferredAddress;
        this.city = city;
        this.prevEwma = prevEwma;
        this.daughterPhone = daughterPhone;
        this.facts = facts;
        this.groups = groups;
    }

    /** Mock community group for the social-match engine (elder_context.json). */
    public static class CommunityGroup {
        public String groupId;
        public String name;
        public String meets;
        public double distanceKm;
        public List<String> keywords;
    }
}
