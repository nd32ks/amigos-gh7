package com.amigos.kinbridge.friends;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Find My Friends matching (V2 §2.3) — real logic over the mock directory:
 * shared institution + overlapping years (±2 tolerance) scores 10 for school
 * bonds, 7 for work bonds; +3 proximity bonus under 10 km; threshold ≥ 7.
 * Pure Java — unit-testable.
 */
public final class FriendMatcher {

    public static final class HistoryEntry {
        public final String institution;
        public final String type; // "sma" | work (anything else)
        public final int fromYear;
        public final int toYear;

        public HistoryEntry(String institution, String type, int fromYear, int toYear) {
            this.institution = institution;
            this.type = type;
            this.fromYear = fromYear;
            this.toYear = toYear;
        }
    }

    public static final class Person {
        public String personId;
        public String name;
        public String city;
        public double distanceKm;
        public boolean onPlatform;
        public final List<HistoryEntry> history = new ArrayList<>();
    }

    public static final class Match implements Comparable<Match> {
        public final Person person;
        public final int score;
        public final String sharedInstitution;
        public final boolean schoolBond;

        Match(Person person, int score, String sharedInstitution, boolean schoolBond) {
            this.person = person;
            this.score = score;
            this.sharedInstitution = sharedInstitution;
            this.schoolBond = schoolBond;
        }

        @Override
        public int compareTo(Match other) {
            return Integer.compare(other.score, score); // descending
        }
    }

    private FriendMatcher() {
    }

    public static List<Match> match(List<HistoryEntry> elderHistory, List<Person> candidates) {
        List<Match> out = new ArrayList<>();
        for (Person candidate : candidates) {
            int score = 0;
            String shared = null;
            boolean school = false;
            for (HistoryEntry elder : elderHistory) {
                for (HistoryEntry theirs : candidate.history) {
                    if (normalize(elder.institution).equals(normalize(theirs.institution))
                            && yearsOverlap(elder, theirs, 2)) {
                        boolean isSchool = "sma".equals(elder.type);
                        score += isSchool ? 10 : 7;
                        if (shared == null || isSchool) {
                            shared = elder.institution;
                            school = isSchool;
                        }
                    }
                }
            }
            if (candidate.distanceKm < 10) {
                score += 3;
            }
            if (score >= 7) {
                out.add(new Match(candidate, score, shared, school));
            }
        }
        Collections.sort(out);
        return out;
    }

    private static boolean yearsOverlap(HistoryEntry a, HistoryEntry b, int tolerance) {
        return a.fromYear <= b.toYear + tolerance && b.fromYear <= a.toYear + tolerance;
    }

    private static String normalize(String institution) {
        return institution.toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
    }
}
