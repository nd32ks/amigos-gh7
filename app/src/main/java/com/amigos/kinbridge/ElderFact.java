package com.amigos.kinbridge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One ground-truth fact about the elder (agents/elder_context.json equivalent). */
public class ElderFact {
    public String factId;
    public int tier;
    public String questionId;
    public String questionEn;
    public String canonical;
    public List<String> aliases = new ArrayList<>();
    public String topicId;
    public String topicEn;
    public long cooldownHours;
    public long lastProbedAt;

    public boolean isEligible(long nowMs) {
        return lastProbedAt == 0 || nowMs >= lastProbedAt + cooldownHours * 3600_000L;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("factId", factId);
        m.put("tier", tier);
        m.put("questionId", questionId);
        m.put("questionEn", questionEn);
        m.put("canonical", canonical);
        m.put("aliases", aliases);
        m.put("topicId", topicId);
        m.put("topicEn", topicEn);
        m.put("cooldownHours", cooldownHours);
        m.put("lastProbedAt", lastProbedAt);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ElderFact fromMap(Map<String, Object> m) {
        ElderFact f = new ElderFact();
        f.factId = (String) m.get("factId");
        f.tier = ((Number) m.get("tier")).intValue();
        f.questionId = (String) m.get("questionId");
        f.questionEn = (String) m.get("questionEn");
        f.canonical = (String) m.get("canonical");
        Object aliases = m.get("aliases");
        if (aliases instanceof List) {
            f.aliases = new ArrayList<>((List<String>) aliases);
        }
        f.topicId = (String) m.get("topicId");
        f.topicEn = (String) m.get("topicEn");
        f.cooldownHours = ((Number) m.get("cooldownHours")).longValue();
        Object probed = m.get("lastProbedAt");
        f.lastProbedAt = probed instanceof Number ? ((Number) probed).longValue() : 0;
        return f;
    }
}
