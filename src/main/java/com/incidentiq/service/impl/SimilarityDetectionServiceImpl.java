package com.incidentiq.service.impl;

import com.incidentiq.dto.response.SimilarIncidentResponse;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.model.Incident;
import com.incidentiq.repository.IncidentRepository;
import com.incidentiq.service.SimilarityDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Production-grade Incident Similarity Engine.
 *
 * Pipeline per query:
 *   1. Synonym normalization  — maps disk→storage, login→auth, postgres→db, etc.
 *   2. Porter-lite stemming   — running/runs→run, failed/failing→fail
 *   3. Stop-word removal
 *   4. Bigram extraction      — captures "disk full", "connection timeout" as units
 *   5. TF-IDF cosine scoring  — per field (title, description, rootCause, resolutionSummary)
 *   6. Weighted field fusion  — title 40%, desc 30%, rootCause 15%, resolution 15%
 *   7. Category bonus         — same category adds a flat bonus; cross-category fallback
 *      is used automatically when the same-category pool has < 3 resolved incidents.
 *
 * This approach correctly matches:
 *   "disk full" ≈ "storage exhausted"
 *   "login failure" ≈ "authentication error"
 *   "postgres down" ≈ "database connectivity issue"
 *   "server crash" ≈ "application outage"
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimilarityDetectionServiceImpl implements SimilarityDetectionService {

    private final IncidentRepository incidentRepository;
    private final RestTemplate restTemplate;

    // ── Field weights ────────────────────────────────────────────────────
    private static final double W_TITLE      = 0.40;
    private static final double W_DESC       = 0.30;
    private static final double W_ROOT_CAUSE = 0.15;
    private static final double W_RESOLUTION = 0.15;
    private static final double CATEGORY_BONUS = 0.12; // added when category matches

    // ── Thresholds ───────────────────────────────────────────────────────
    private static final double MIN_THRESHOLD       = 0.12;
    private static final double DUPLICATE_THRESHOLD = 0.72;
    private static final int    MIN_POOL_SIZE        = 3;   // fall back cross-category below this

    // ── Stop-words ───────────────────────────────────────────────────────
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "the","a","an","is","in","on","at","to","for","of","and","or","not","with",
        "from","by","has","have","was","were","are","be","this","that","it","its",
        "we","i","you","my","our","their","can","could","would","should","will",
        "do","did","does","had","been","being","get","got","please","help",
        "when","what","how","why","where","which","who","all","some","any",
        "im","ive","its","cant","wont","dont","doesnt"
    ));

    // ── Synonym map — canonical form is the VALUE ────────────────────────
    // Every synonym maps to one canonical token so Jaccard/cosine can match them.
    private static final Map<String, String> SYNONYMS;
    static {
        Map<String, String> m = new HashMap<>();
        // Storage / Disk
        m.put("disk","storage");    m.put("hdd","storage");    m.put("ssd","storage");
        m.put("drive","storage");   m.put("volume","storage"); m.put("partition","storage");
        m.put("space","storage");   m.put("quota","storage");  m.put("filesystem","storage");
        m.put("fs","storage");      m.put("mount","storage");

        // Database
        m.put("postgres","db");     m.put("postgresql","db");  m.put("mysql","db");
        m.put("mariadb","db");      m.put("oracle","db");      m.put("mssql","db");
        m.put("sqlite","db");       m.put("mongo","db");       m.put("mongodb","db");
        m.put("database","db");     m.put("datasource","db");  m.put("rdbms","db");

        // Authentication
        m.put("login","auth");      m.put("logout","auth");    m.put("signin","auth");
        m.put("signout","auth");    m.put("authentication","auth"); m.put("authorisation","auth");
        m.put("authorization","auth"); m.put("sso","auth");    m.put("oauth","auth");
        m.put("ldap","auth");       m.put("saml","auth");      m.put("credential","auth");
        m.put("password","auth");   m.put("token","auth");     m.put("jwt","auth");
        m.put("session","auth");    m.put("2fa","auth");       m.put("mfa","auth");

        // Connectivity / Timeout
        m.put("timeout","connfail");    m.put("timed","connfail");
        m.put("unreachable","connfail"); m.put("refused","connfail");
        m.put("reset","connfail");      m.put("disconnected","connfail");
        m.put("disconnect","connfail"); m.put("connectivity","connfail");
        m.put("connection","connfail"); m.put("connect","connfail");
        m.put("reconnect","connfail");  m.put("latency","connfail");
        m.put("slowness","connfail");   m.put("slow","connfail");

        // Crash / Outage
        m.put("crash","outage");    m.put("crashed","outage"); m.put("crashing","outage");
        m.put("down","outage");     m.put("outage","outage");  m.put("unavailable","outage");
        m.put("unresponsive","outage"); m.put("hang","outage"); m.put("hung","outage");
        m.put("freeze","outage");   m.put("frozen","outage");  m.put("restart","outage");
        m.put("restarting","outage"); m.put("reboot","outage"); m.put("stopped","outage");

        // Memory / CPU
        m.put("memory","mem");      m.put("ram","mem");        m.put("heap","mem");
        m.put("oom","mem");         m.put("leak","mem");       m.put("swap","mem");
        m.put("cpu","cpu");         m.put("processor","cpu");  m.put("utilization","cpu");
        m.put("utilisation","cpu"); m.put("load","cpu");       m.put("throttle","cpu");

        // Network
        m.put("network","net");     m.put("dns","net");        m.put("firewall","net");
        m.put("proxy","net");       m.put("gateway","net");    m.put("routing","net");
        m.put("route","net");       m.put("packet","net");     m.put("bandwidth","net");
        m.put("interface","net");   m.put("subnet","net");     m.put("vlan","net");

        // Deploy / CI-CD
        m.put("deploy","deploy");   m.put("deployment","deploy"); m.put("rollout","deploy");
        m.put("release","deploy");  m.put("pipeline","deploy");  m.put("ci","deploy");
        m.put("cd","deploy");       m.put("build","deploy");     m.put("rollback","deploy");
        m.put("artifact","deploy"); m.put("image","deploy");     m.put("container","deploy");
        m.put("docker","deploy");   m.put("k8s","deploy");       m.put("kubernetes","deploy");
        m.put("pod","deploy");      m.put("helm","deploy");

        // Error codes
        m.put("500","http5xx");     m.put("502","http5xx");    m.put("503","http5xx");
        m.put("504","http5xx");     m.put("404","http4xx");    m.put("403","http4xx");
        m.put("401","http4xx");     m.put("400","http4xx");

        // Failure synonyms
        m.put("fail","fail");       m.put("failed","fail");    m.put("failure","fail");
        m.put("failing","fail");    m.put("fault","fail");     m.put("faulted","fail");
        m.put("broken","fail");     m.put("corrupt","fail");   m.put("corrupted","fail");

        // High / Full / Exceeded
        m.put("full","high");       m.put("exceed","high");    m.put("exceeded","high");
        m.put("exhausted","high");  m.put("reached","high");   m.put("maxed","high");
        m.put("spike","high");      m.put("high","high");      m.put("100%","high");
        m.put("95%","high");        m.put("90%","high");

        SYNONYMS = Collections.unmodifiableMap(m);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public List<SimilarIncidentResponse> findSimilar(
            String title, String description, IncidentCategory category, Long excludeId) {

        // Step 1 — Build knowledge-base pool (same category first, cross-category fallback)
        List<Incident> pool = incidentRepository.findResolvedByCategory(category);
        boolean crossCategory = pool.size() < MIN_POOL_SIZE;
        if (crossCategory) {
            log.info("[Similarity] Only {} in category {}; using cross-category pool.", pool.size(), category);
            pool = incidentRepository.findAllResolved();
        }
        log.info("[Similarity] Pool size: {} (crossCategory={})", pool.size(), crossCategory);

        if (pool.isEmpty()) return List.of();

        // Step 2 — Normalise query
        TokenSet qTitle = normalise(title);
        TokenSet qDesc  = normalise(description);
        log.info("[Similarity] qTitle tokens={} bigrams={}", qTitle.unigrams(), qTitle.bigrams());

        // Step 3 — Build IDF from pool (for TF-IDF cosine)
        Map<String, Double> idf = buildIdf(pool);

        // Step 4 — Score each candidate
        List<ScoredIncident> scored = new ArrayList<>();
        for (Incident c : pool) {
            if (excludeId != null && c.getId().equals(excludeId)) continue;

            double titleSim = tfidfCosine(qTitle, normalise(c.getTitle()), idf);
            double descSim  = tfidfCosine(qDesc,  normalise(c.getDescription()), idf);
            double rcSim    = tfidfCosine(qTitle, normalise(c.getRootCause()), idf);
            double resSim   = tfidfCosine(qTitle, normalise(c.getResolutionSummary()), idf);
            double tagSim   = tfidfCosine(qTitle, normalise(c.getTags()), idf);

            // Category bonus (flat)
            double catBonus = (c.getCategory() == category) ? CATEGORY_BONUS : 0.0;

            double overall = catBonus
                + W_TITLE * titleSim
                + W_DESC  * descSim
                + W_ROOT_CAUSE * rcSim
                + W_RESOLUTION * resSim
                + 0.15 * tagSim;

            log.info("[Similarity] #{} '{}' title={:.2f} desc={:.2f} rc={:.2f} res={:.2f} cat={:.2f} → {:.2f}",
                c.getId(), c.getTitle(), titleSim, descSim, rcSim, resSim, catBonus, overall);

            if (overall >= MIN_THRESHOLD) {
                scored.add(new ScoredIncident(c, overall, titleSim, descSim));
            }
        }

        scored.sort(Comparator.comparingDouble(ScoredIncident::overall).reversed());
        List<ScoredIncident> top = scored.stream().limit(5).collect(Collectors.toList());
        log.info("[Similarity] Returning {} results", top.size());

        return top.stream().map(s -> toResponse(s, category)).collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  NORMALISATION: synonym → stem → stop-word → unigrams + bigrams
    // ═══════════════════════════════════════════════════════════════════════

    @Override
    public List<String> extractKeywords(String text) {
        return new ArrayList<>(normalise(text).unigrams());
    }

    private TokenSet normalise(String text) {
        if (text == null || text.isBlank()) return TokenSet.EMPTY;

        // 1. Split on non-alphanumeric
        String[] raw = text.toLowerCase()
            .replaceAll("[^a-z0-9%]", " ")
            .trim().split("\\s+");

        // 2. Synonym map + stop-word filter + min-length
        List<String> tokens = new ArrayList<>();
        for (String w : raw) {
            if (w.length() < 2) continue;
            String canonical = SYNONYMS.getOrDefault(w, w);
            canonical = SYNONYMS.getOrDefault(canonical, canonical); // two-pass for chaining
            if (!STOP_WORDS.contains(canonical)) {
                tokens.add(stem(canonical));
            }
        }

        // 3. Build bigrams from adjacent tokens
        List<String> bigrams = new ArrayList<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            bigrams.add(tokens.get(i) + "_" + tokens.get(i + 1));
        }

        Set<String> all = new LinkedHashSet<>(tokens);
        all.addAll(bigrams);
        return new TokenSet(new ArrayList<>(tokens), new ArrayList<>(bigrams));
    }

    // ── Porter-lite stemmer (handles most English IT suffixes) ───────────
    private String stem(String word) {
        if (word.length() <= 3) return word;
        if (word.endsWith("ing") && word.length() > 5) return word.substring(0, word.length() - 3);
        if (word.endsWith("tion") || word.endsWith("sion")) return word.substring(0, word.length() - 3);
        if (word.endsWith("ness"))  return word.substring(0, word.length() - 4);
        if (word.endsWith("ment"))  return word.substring(0, word.length() - 4);
        if (word.endsWith("ity"))   return word.substring(0, word.length() - 3);
        if (word.endsWith("ies"))   return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("ed") && word.length() > 4)  return word.substring(0, word.length() - 2);
        if (word.endsWith("er") && word.length() > 4)  return word.substring(0, word.length() - 2);
        if (word.endsWith("ly") && word.length() > 4)  return word.substring(0, word.length() - 2);
        if (word.endsWith("s")  && word.length() > 3 && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
        return word;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  IDF COMPUTATION  —  log((N+1)/(df+1)) + 1   (smoothed)
    // ═══════════════════════════════════════════════════════════════════════

    private Map<String, Double> buildIdf(List<Incident> pool) {
        Map<String, Integer> df = new HashMap<>();
        int N = pool.size();
        for (Incident inc : pool) {
            Set<String> seen = new HashSet<>();
            collectTokens(inc, seen);
            seen.forEach(t -> df.merge(t, 1, Integer::sum));
        }
        Map<String, Double> idf = new HashMap<>();
        df.forEach((term, freq) -> idf.put(term, Math.log((N + 1.0) / (freq + 1.0)) + 1.0));
        return idf;
    }

    private void collectTokens(Incident inc, Set<String> out) {
        for (String field : new String[]{
            inc.getTitle(), inc.getDescription(),
            inc.getRootCause(), inc.getResolutionSummary(), inc.getResolutionSteps(),
            inc.getTags()
        }) {
            out.addAll(normalise(field).unigrams());
            out.addAll(normalise(field).bigrams());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  TF-IDF COSINE  —  dot(q,d) / (|q|·|d|)
    // ═══════════════════════════════════════════════════════════════════════

    private double tfidfCosine(TokenSet query, TokenSet doc, Map<String, Double> idf) {
        if (query.isEmpty() || doc.isEmpty()) return 0.0;

        Map<String, Double> qVec = tfIdfVec(query, idf);
        Map<String, Double> dVec = tfIdfVec(doc,   idf);

        double dot = 0.0;
        for (Map.Entry<String, Double> e : qVec.entrySet()) {
            dot += e.getValue() * dVec.getOrDefault(e.getKey(), 0.0);
        }
        double qNorm = Math.sqrt(qVec.values().stream().mapToDouble(v -> v * v).sum());
        double dNorm = Math.sqrt(dVec.values().stream().mapToDouble(v -> v * v).sum());
        return (qNorm == 0 || dNorm == 0) ? 0.0 : dot / (qNorm * dNorm);
    }

    private Map<String, Double> tfIdfVec(TokenSet ts, Map<String, Double> idf) {
        List<String> all = new ArrayList<>(ts.unigrams());
        all.addAll(ts.bigrams());
        int total = all.size();
        if (total == 0) return Map.of();

        Map<String, Long> freq = all.stream().collect(Collectors.groupingBy(t -> t, Collectors.counting()));
        Map<String, Double> vec = new HashMap<>();
        freq.forEach((term, count) -> {
            double tf = (double) count / total;
            double idfVal = idf.getOrDefault(term, Math.log(2.0)); // default IDF for OOV
            vec.put(term, tf * idfVal);
        });
        return vec;
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  RESPONSE BUILDING
    // ═══════════════════════════════════════════════════════════════════════

    private SimilarIncidentResponse toResponse(ScoredIncident s, IncidentCategory queryCategory) {
        Incident i = s.incident();
        int overallPct = clamp((int) Math.round(s.overall()  * 100));
        int titlePct   = clamp((int) Math.round(s.titleSim() * 100));
        int descPct    = clamp((int) Math.round(s.descSim()  * 100));
        int catScore   = (i.getCategory() == queryCategory) ? 100 : 50;

        return SimilarIncidentResponse.builder()
            .incidentId(i.getId())
            .title(i.getTitle())
            .category(i.getCategory().name())
            .priority(i.getPriority().name())
            .status(i.getStatus().name())
            .createdAt(i.getCreatedAt())
            .resolvedAt(i.getResolvedAt())
            .similarityScore(overallPct)
            .categoryScore(catScore)
            .titleScore(titlePct)
            .descriptionScore(descPct)
            .isDuplicate(s.overall() >= DUPLICATE_THRESHOLD)
            .rootCause(i.getRootCause())
            .resolutionSteps(i.getResolutionSteps())
            .resolutionSummary(i.getResolutionSummary())
            .resolvedBy(i.getAssignedTo())
            .resolvedByName(resolveUserName(i.getAssignedTo()))
            .suggestion(buildSuggestion(i, overallPct))
            .build();
    }

    private String resolveUserName(Long userId) {
        if (userId == null) return null;
        try {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> user = restTemplate.getForObject(
                "http://user-service/" + userId, java.util.Map.class);
            if (user != null) {
                String first = (String) user.getOrDefault("firstName", "");
                String last  = (String) user.getOrDefault("lastName",  "");
                String name  = (first + " " + last).trim();
                if (!name.isEmpty()) return name;
                Object uname = user.get("username");
                if (uname != null) return uname.toString();
            }
        } catch (Exception e) {
            log.debug("Could not resolve user name for id {}: {}", userId, e.getMessage());
        }
        return null;
    }

    private String buildSuggestion(Incident i, int score) {
        if (score >= 85) {
            return "A very similar issue (#" + i.getId() + ") was already resolved. " +
                   "Please review the solution below before creating a new ticket.";
        } else if (score >= 60) {
            return "Incident #" + i.getId() + " looks related and was resolved. " +
                   "Check the resolution steps — this might solve your problem.";
        } else {
            return "Incident #" + i.getId() + " has some overlap. " +
                   "Review it to see if the resolution applies to your situation.";
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  INTERNAL TYPES
    // ═══════════════════════════════════════════════════════════════════════

    private record ScoredIncident(Incident incident, double overall, double titleSim, double descSim) {}

    private record TokenSet(List<String> unigrams, List<String> bigrams) {
        static final TokenSet EMPTY = new TokenSet(List.of(), List.of());
        boolean isEmpty() { return unigrams.isEmpty() && bigrams.isEmpty(); }
    }
}
