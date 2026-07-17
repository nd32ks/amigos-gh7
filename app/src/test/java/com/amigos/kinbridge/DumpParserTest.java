package com.amigos.kinbridge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.amigos.kinbridge.onboard.DumpParser;
import com.amigos.kinbridge.onboard.DumpParser.ExtractedFact;

import org.junit.Test;

import java.util.List;

/** Gate C evidence: the demo dump paragraph cascades into 3 tiers (V2 §5). */
public class DumpParserTest {

    private static final String DEMO_DUMP =
            "Ibu Sri suaminya Pak Budi, anaknya Dewi sama Anton. "
            + "Suka banget sama anggrek bulan di teras, tiap Rabu senam di taman. "
            + "Suka keroncong, lagu Bengawan Solo. Minum obat darah tinggi tiap pagi. "
            + "Suka pesen sate lewat Beli, restoran favoritnya omakase ENA.";

    @Test
    public void demoParagraph_extractsAllFacts() {
        List<ExtractedFact> facts = DumpParser.parse(DEMO_DUMP);
        assertTrue("at least 6 facts", facts.size() >= 6);
        assertTrue(hasFact(facts, 1, "Pak Budi"));
        assertTrue(hasFact(facts, 1, "Dewi dan Anton"));
        assertTrue(hasFact(facts, 3, "anggrek bulan"));
        assertTrue(hasFact(facts, 2, "senam di taman"));
        assertTrue(hasFact(facts, 3, "Bengawan Solo"));
        assertTrue(hasFact(facts, 2, "obat darah tinggi"));
        assertTrue(hasFact(facts, 3, "omakase ENA"));
    }

    @Test
    public void tierRules_familyIsT1_medsAreT2_tastesAreT3() {
        List<ExtractedFact> facts = DumpParser.parse(DEMO_DUMP);
        for (ExtractedFact f : facts) {
            if (f.category.startsWith("core_identity")) {
                assertEquals(1, f.tier);
            }
            if (f.category.startsWith("recent.")) {
                assertEquals(2, f.tier);
            }
            if (f.category.startsWith("preferences.")) {
                assertEquals(3, f.tier);
            }
        }
    }

    @Test
    public void emptyText_yieldsNoFacts() {
        assertTrue(DumpParser.parse("").isEmpty());
    }

    private boolean hasFact(List<ExtractedFact> facts, int tier, String fragment) {
        for (ExtractedFact f : facts) {
            if (f.tier == tier && f.canonical.toLowerCase().contains(fragment.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
