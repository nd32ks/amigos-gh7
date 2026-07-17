package com.amigos.kinbridge.onboard;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic context-dump extraction (V2 §5 + §7: the mock/cached fallback
 * for the GPT-4o extraction call). Ordered keyword rules classify clauses into
 * tiered facts: family/identity/address → 1; meds/routines/recent → 2;
 * tastes/hobbies → 3. Pure Java — unit-testable.
 */
public final class DumpParser {

    public static final class ExtractedFact {
        public final int tier;
        public final String category;
        public final String canonical;

        ExtractedFact(int tier, String category, String canonical) {
            this.tier = tier;
            this.category = category;
            this.canonical = canonical;
        }
    }

    private interface Rule {
        boolean apply(String text, List<ExtractedFact> out);
    }

    private DumpParser() {
    }

    public static List<ExtractedFact> parse(String text) {
        List<ExtractedFact> out = new ArrayList<>();
        for (Rule rule : RULES) {
            rule.apply(text, out);
        }
        return out;
    }

    private static void add(List<ExtractedFact> out, int tier, String category, String canonical) {
        String cleaned = canonical.trim().replaceAll("\\s+", " ");
        if (!cleaned.isEmpty()) {
            out.add(new ExtractedFact(tier, category, cleaned));
        }
    }

    private static String capitalize(String s) {
        String[] words = s.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static String group(Pattern p, String text, int group) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(group) : null;
    }

    private static final Pattern SPOUSE = Pattern.compile(
            "suaminya\\s+((?:pak|bu)?\\s*[a-zA-Z]+(?:\\s+[a-zA-Z]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHILDREN = Pattern.compile(
            "anaknya\\s+([a-zA-Z]+)(?:\\s+(?:dan|sama)\\s+([a-zA-Z]+))?", Pattern.CASE_INSENSITIVE);
    private static final Pattern ADDRESS = Pattern.compile(
            "alamat(?:nya)?\\s+(?:di\\s+)?([^.,;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BIRTH_YEAR = Pattern.compile(
            "lahir\\s+tahun\\s+(\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final Pattern MEDICATION = Pattern.compile(
            "(?:minum|minum obat|obat)\\s+obat\\s+([^.,;]+)|minum\\s+obat\\s+([^.,;]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ROUTINE = Pattern.compile(
            "tiap\\s+(?:hari\\s+)?([a-zA-Z]+)\\s+([^.,;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MUSIC = Pattern.compile(
            "suka\\s+keroncong[^.,;]*lagu\\s+([^.,;]+)|lagu\\s+([^.,;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOBBY = Pattern.compile(
            "suka(?:\\s+banget)?(?:\\s+sama)?\\s+([^.,;]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DINING = Pattern.compile(
            "(?:restoran\\s+)?favorit(?:nya)?\\s+([^.,;]+)", Pattern.CASE_INSENSITIVE);

    private static final Rule[] RULES = {
            (text, out) -> {
                String v = group(SPOUSE, text, 1);
                if (v != null) {
                    add(out, 1, "core_identity.family", capitalize(v));
                    return true;
                }
                return false;
            },
            (text, out) -> {
                Matcher m = CHILDREN.matcher(text);
                if (m.find()) {
                    String first = capitalize(m.group(1));
                    String second = m.group(2) != null ? capitalize(m.group(2)) : null;
                    add(out, 1, "core_identity.family",
                            second != null ? first + " dan " + second : first);
                    return true;
                }
                return false;
            },
            (text, out) -> {
                String v = group(ADDRESS, text, 1);
                if (v != null) {
                    add(out, 1, "core_identity.location", v.trim());
                    return true;
                }
                return false;
            },
            (text, out) -> {
                String v = group(BIRTH_YEAR, text, 1);
                if (v != null) {
                    add(out, 1, "core_identity.self", v);
                    return true;
                }
                return false;
            },
            (text, out) -> {
                Matcher m = MEDICATION.matcher(text);
                if (m.find()) {
                    String v = m.group(1) != null ? m.group(1) : m.group(2);
                    add(out, 2, "recent.health_routine", "obat " + v.trim());
                    return true;
                }
                return false;
            },
            (text, out) -> {
                Matcher m = ROUTINE.matcher(text);
                if (m.find() && !m.group(2).toLowerCase().contains("obat")) {
                    add(out, 2, "recent.routine",
                            m.group(2).trim() + " tiap " + m.group(1).toLowerCase());
                    return true;
                }
                return false;
            },
            (text, out) -> {
                Matcher m = MUSIC.matcher(text);
                if (m.find()) {
                    String v = m.group(1) != null ? m.group(1) : m.group(2);
                    add(out, 3, "preferences.music", "keroncong, lagu " + v.trim());
                    return true;
                }
                return false;
            },
            (text, out) -> {
                String v = group(DINING, text, 1);
                if (v != null) {
                    add(out, 3, "preferences.dining", v.trim());
                    return true;
                }
                return false;
            },
            (text, out) -> {
                Matcher m = HOBBY.matcher(text);
                while (m.find()) {
                    String v = m.group(1).trim();
                    String lower = v.toLowerCase();
                    if (!lower.contains("keroncong") && !lower.contains("obat")
                            && !lower.startsWith("pesen") && !lower.startsWith("pesan")) {
                        add(out, 3, "preferences.hobbies", v);
                    }
                }
                return true;
            },
    };
}
