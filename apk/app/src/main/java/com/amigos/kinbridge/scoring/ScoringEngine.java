package com.amigos.kinbridge.scoring;

import java.util.List;

/**
 * Workflow Evaluation Agent scoring — deterministic math from
 * agents/workflow_agent_spec.md §4. No Android deps: unit-testable.
 *
 * CRI = 100 * Σ(wᵢ·sᵢ) / Σ(wᵢ)   (bounded 0–100, probe-count invariant)
 * EWMA_t = 0.3·CRI_t + 0.7·EWMA_{t−1}
 */
public final class ScoringEngine {

    public enum Verdict {EXACT, PARTIAL, MISS, NO_ANSWER}

    public enum Escalation {ACUTE, WARNING, SILENT, NONE}

    /** One scored probe: tier weight w and CRI credit s (optional override). */
    public static final class Event {
        public final int tier;
        public final Verdict verdict;
        /** When >= 0, replaces the verdict-derived credit (e.g. cocok_kata exact = 0.75). */
        public final double creditOverride;

        public Event(int tier, Verdict verdict) {
            this(tier, verdict, -1);
        }

        public Event(int tier, Verdict verdict, double creditOverride) {
            this.tier = tier;
            this.verdict = verdict;
            this.creditOverride = creditOverride;
        }

        public double credit() {
            return creditOverride >= 0 ? creditOverride : ScoringEngine.credit(verdict);
        }
    }

    public static final int MIN_PROBES = 3;
    public static final double EWMA_ALPHA = 0.3;
    public static final double ACUTE_CONFIDENCE = 0.8;
    public static final double ACUTE_CRI_SESSION = 40.0;
    public static final int WARNING_T2_MISSES_7D = 2;
    public static final double WARNING_EWMA_DROP_7D = 15.0;

    private ScoringEngine() {
    }

    /** Tier weight w: T1=10, T2=3, T3=2 (Table B). */
    public static int tierWeight(int tier) {
        switch (tier) {
            case 1:
                return 10;
            case 2:
                return 3;
            default:
                return 2;
        }
    }

    /** CRI credit s: exact=1.0, partial=0.5, miss/no_answer=0 (Table B). */
    public static double credit(Verdict verdict) {
        switch (verdict) {
            case EXACT:
                return 1.0;
            case PARTIAL:
                return 0.5;
            default:
                return 0.0;
        }
    }

    /** Raw ledger points (Table A): exact +1; partial/no_answer 0; miss T1 −10, T2 −4, T3 −2. */
    public static int rawPoints(Verdict verdict, int tier) {
        if (verdict == Verdict.EXACT) {
            return 1;
        }
        if (verdict == Verdict.MISS) {
            switch (tier) {
                case 1:
                    return -10;
                case 2:
                    return -4;
                default:
                    return -2;
            }
        }
        return 0;
    }

    /** Session CRI; 0 when there are no scored events. */
    public static double cri(List<Event> events) {
        double weighted = 0;
        double weights = 0;
        for (Event e : events) {
            int w = tierWeight(e.tier);
            weighted += w * e.credit();
            weights += w;
        }
        return weights == 0 ? 0 : 100.0 * weighted / weights;
    }

    /** EWMA_t = 0.3·CRI_t + 0.7·EWMA_{t−1}. */
    public static double ewma(double cri, double prevEwma) {
        return EWMA_ALPHA * cri + (1 - EWMA_ALPHA) * prevEwma;
    }

    /**
     * Escalation rules (spec §4 thresholds + §5 fail-safe):
     * ACUTE — T1 miss at confidence ≥ 0.8 (below that the miss is downgraded
     *         to partial for escalation purposes), or CRI_session < 40.
     * WARNING — ≥ 2 T2 misses in 7d, or EWMA drop > 15 pts in 7d.
     * SILENT — any T3 miss (trend line only). Otherwise NONE.
     */
    public static Escalation escalation(Verdict verdict, double confidence, int tier,
                                        int t2MissesLast7d, double ewmaDrop7d, double criSession) {
        if (tier == 1 && verdict == Verdict.MISS
                && confidence >= ACUTE_CONFIDENCE) {
            return Escalation.ACUTE;
        }
        if (criSession > 0 && criSession < ACUTE_CRI_SESSION) {
            return Escalation.ACUTE;
        }
        if (t2MissesLast7d >= WARNING_T2_MISSES_7D || ewmaDrop7d > WARNING_EWMA_DROP_7D) {
            return Escalation.WARNING;
        }
        if (tier == 3 && verdict == Verdict.MISS) {
            return Escalation.SILENT;
        }
        return Escalation.NONE;
    }
}
