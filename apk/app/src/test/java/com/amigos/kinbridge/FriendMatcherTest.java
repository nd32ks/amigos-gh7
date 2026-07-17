package com.amigos.kinbridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.amigos.kinbridge.friends.FriendMatcher;
import com.amigos.kinbridge.friends.FriendMatcher.HistoryEntry;
import com.amigos.kinbridge.friends.FriendMatcher.Match;
import com.amigos.kinbridge.friends.FriendMatcher.Person;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** V2 §2.3 matching: school bonds outrank work bonds, proximity counts. */
public class FriendMatcherTest {

    private List<HistoryEntry> elderHistory() {
        List<HistoryEntry> h = new ArrayList<>();
        h.add(new HistoryEntry("SMA Negeri 1 Solo", "sma", 1967, 1970));
        h.add(new HistoryEntry("Bank Rakyat Indonesia, Cabang Solo", "work", 1972, 1985));
        h.add(new HistoryEntry("BRI Kantor Wilayah Jakarta", "work", 1985, 1998));
        return h;
    }

    private Person person(String name, String institution, String type, int from, int to, double km) {
        Person p = new Person();
        p.name = name;
        p.distanceKm = km;
        p.history.add(new HistoryEntry(institution, type, from, to));
        return p;
    }

    @Test
    public void ratna_scoresSchoolPlusProximity() {
        List<Match> matches = FriendMatcher.match(elderHistory(),
                java.util.Arrays.asList(person("Ratna Kusuma", "SMA Negeri 1 Solo", "sma", 1966, 1969, 6.1)));
        assertEquals(1, matches.size());
        assertEquals(13, matches.get(0).score);
        assertTrue(matches.get(0).schoolBond);
    }

    @Test
    public void sutrisno_scoresWorkOnly() {
        List<Match> matches = FriendMatcher.match(elderHistory(),
                java.util.Arrays.asList(person("Sutrisno Adji", "Bank Rakyat Indonesia, Cabang Solo", "work", 1973, 1981, 14.8)));
        assertEquals(1, matches.size());
        assertEquals(7, matches.get(0).score);
    }

    @Test
    public void noOverlapYears_noMatch() {
        List<Match> matches = FriendMatcher.match(elderHistory(),
                java.util.Arrays.asList(person("Lilis Hartono", "SMA Negeri 1 Solo", "sma", 1974, 1977, 9.3)));
        // years differ by 4 (> ±2 tolerance) even though institution matches
        assertTrue(matches.isEmpty());
    }

    @Test
    public void sortedDescending() {
        List<Match> matches = FriendMatcher.match(elderHistory(), java.util.Arrays.asList(
                person("Sutrisno Adji", "Bank Rakyat Indonesia, Cabang Solo", "work", 1973, 1981, 14.8),
                person("Ratna Kusuma", "SMA Negeri 1 Solo", "sma", 1966, 1969, 6.1)));
        assertEquals(2, matches.size());
        assertEquals("Ratna Kusuma", matches.get(0).person.name);
    }
}
