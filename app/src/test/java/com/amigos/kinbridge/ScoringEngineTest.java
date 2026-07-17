package com.amigos.kinbridge;

import static org.junit.Assert.assertEquals;

import com.amigos.kinbridge.scoring.ScoringEngine;
import com.amigos.kinbridge.scoring.ScoringEngine.Escalation;
import com.amigos.kinbridge.scoring.ScoringEngine.Event;
import com.amigos.kinbridge.scoring.ScoringEngine.Verdict;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** Spec examples from agents/workflow_agent_spec.md §4. */
public class ScoringEngineTest {

    @Test
    public void cri_gateA_example_is33_3() {
        // GATE A (BUILDER_PROTOCOL §3) — tier1_alert_payload.json derivation:
        // T1 miss (w=10,s=0) + T2 exact (w=3,s=1) + T3 exact (w=2,s=1) → 100×5/15
        List<Event> events = Arrays.asList(
                new Event(1, Verdict.MISS),
                new Event(2, Verdict.EXACT),
                new Event(3, Verdict.EXACT));
        assertEquals(33.3, ScoringEngine.cri(events), 0.05);
    }

    @Test
    public void cri_weightsTiersAndCredits() {
        // T1 exact (10×1) + T2 partial (3×0.5) + T3 miss (2×0) → 100×11.5/15
        List<Event> events = Arrays.asList(
                new Event(1, Verdict.EXACT),
                new Event(2, Verdict.PARTIAL),
                new Event(3, Verdict.MISS));
        assertEquals(76.666, ScoringEngine.cri(events), 0.001);
    }

    @Test
    public void cri_emptyIsZero() {
        assertEquals(0.0, ScoringEngine.cri(Arrays.asList()), 0.0);
    }

    @Test
    public void ewma_smoothsWithAlpha03() {
        // 0.3×33.3 + 0.7×62.5 = 53.74 (dashboard summary example)
        assertEquals(53.74, ScoringEngine.ewma(33.3, 62.5), 0.001);
    }

    @Test
    public void rawPoints_matchTableA() {
        assertEquals(-10, ScoringEngine.rawPoints(Verdict.MISS, 1));
        assertEquals(-4, ScoringEngine.rawPoints(Verdict.MISS, 2));
        assertEquals(-2, ScoringEngine.rawPoints(Verdict.MISS, 3));
        assertEquals(1, ScoringEngine.rawPoints(Verdict.EXACT, 1));
        assertEquals(0, ScoringEngine.rawPoints(Verdict.PARTIAL, 2));
        assertEquals(0, ScoringEngine.rawPoints(Verdict.NO_ANSWER, 3));
    }

    @Test
    public void escalation_t1MissHighConfidenceIsAcute() {
        assertEquals(Escalation.ACUTE,
                ScoringEngine.escalation(Verdict.MISS, 0.94, 1, 0, 0, 100));
    }

    @Test
    public void escalation_t1MissLowConfidenceIsDowngraded() {
        // Fail-safe (spec §5): judge confidence < 0.8 → no acute alert
        assertEquals(Escalation.NONE,
                ScoringEngine.escalation(Verdict.MISS, 0.7, 1, 0, 0, 100));
    }

    @Test
    public void escalation_lowSessionCriIsAcute() {
        assertEquals(Escalation.ACUTE,
                ScoringEngine.escalation(Verdict.PARTIAL, 0.9, 2, 0, 0, 33.3));
    }

    @Test
    public void escalation_twoT2MissesIsWarning() {
        assertEquals(Escalation.WARNING,
                ScoringEngine.escalation(Verdict.MISS, 0.9, 2, 2, 0, 80));
    }

    @Test
    public void escalation_ewmaDropOver15IsWarning() {
        assertEquals(Escalation.WARNING,
                ScoringEngine.escalation(Verdict.PARTIAL, 0.9, 2, 0, 16, 80));
    }

    @Test
    public void escalation_t3MissIsSilent() {
        assertEquals(Escalation.SILENT,
                ScoringEngine.escalation(Verdict.MISS, 0.95, 3, 0, 0, 90));
    }

    @Test
    public void escalation_exactIsNone() {
        assertEquals(Escalation.NONE,
                ScoringEngine.escalation(Verdict.EXACT, 0.98, 1, 0, 0, 90));
    }

    @Test
    public void cocokKataExact_earnsDiscountedCredit() {
        // MASTER_CHECKLIST §5 fix: recognition exact on a 2-choice game earns
        // s=0.75, not 1.0 — 50% guess chance must not inflate the trend.
        List<Event> events = Arrays.asList(
                new Event(1, Verdict.EXACT, 0.75),
                new Event(3, Verdict.EXACT));
        // 100 × (10×0.75 + 2×1.0) / 12 = 79.17
        assertEquals(79.166, ScoringEngine.cri(events), 0.01);
    }

    @Test
    public void cocokKataMiss_countsFullWeight() {
        // A miss on the 2-choice game is full weight (strong signal)
        List<Event> events = Arrays.asList(new Event(1, Verdict.MISS));
        assertEquals(0.0, ScoringEngine.cri(events), 0.0);
    }
}
