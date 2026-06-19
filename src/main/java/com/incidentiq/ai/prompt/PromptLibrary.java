package com.incidentiq.ai.prompt;

import com.incidentiq.ai.client.AiMessage;
import com.incidentiq.ai.dto.AiRequests;
import com.incidentiq.ai.util.AiTextUtils;
import com.incidentiq.enums.IncidentCategory;
import com.incidentiq.enums.IncidentPriority;
import com.incidentiq.model.Incident;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Central prompt catalogue for the IncidentIQ Copilot.
 *
 * Every prompt is deliberately scoped: the system messages forbid general-purpose chat and
 * pin the model to IncidentIQ's domain, its exact category/priority enums, and a strict
 * output contract (JSON for structured features, concise markdown for chat). This is what
 * keeps the Copilot an embedded operational assistant rather than a ChatGPT clone.
 */
public final class PromptLibrary {

    private PromptLibrary() {}

    private static final String CATEGORIES = Arrays.stream(IncidentCategory.values())
            .map(Enum::name).collect(Collectors.joining(", "));
    private static final String PRIORITIES = Arrays.stream(IncidentPriority.values())
            .map(Enum::name).collect(Collectors.joining(", "));

    /** Shared identity + guardrails prepended to scoped chat. */
    private static final String COPILOT_PERSONA = """
            You are the IncidentIQ Copilot, an assistant embedded inside the IncidentIQ IT
            incident-management platform. You help IT support staff, technicians and managers
            with the incident lifecycle: reporting, triage, similar-incident lookup, resolution,
            knowledge-base articles and operational insights.

            Rules:
            - Stay strictly within IT incident management and the IncidentIQ product. If asked
              something unrelated (general knowledge, coding help, personal questions, etc.),
              politely decline in one sentence and steer back to incidents.
            - Be concise and operational. Prefer short paragraphs and tight bullet lists.
            - Never invent incident IDs, people, or data that were not provided as context.
            - Valid categories: %s. Valid priorities: %s.
            """.formatted(CATEGORIES, PRIORITIES);

    // ── 1. Incident Creation Assistant ───────────────────────────────────────

    public static List<AiMessage> incidentAssist(AiRequests.IncidentAssist req) {
        String system = """
                You improve raw IT incident reports for the IncidentIQ system.
                Given a rough title/description, you produce a clear, professional incident draft.

                Return ONLY a JSON object (no markdown, no commentary) with exactly these keys:
                {
                  "improvedTitle": string,        // concise, specific, < 90 chars
                  "improvedDescription": string,  // 2-4 sentences: symptom, scope, impact
                  "category": string,             // one of: %s
                  "priority": string,             // one of: %s
                  "tags": string[],               // 2-5 short lowercase tags
                  "rationale": string             // one short sentence on category+priority choice
                }
                Choose priority by impact: CRITICAL=outage/security breach, HIGH=major degradation,
                MEDIUM=partial/non-blocking, LOW=cosmetic/scheduled.
                """.formatted(CATEGORIES, PRIORITIES);

        String user = """
                Raw title: %s
                Raw description: %s
                Current category hint: %s

                Produce the improved incident draft as JSON.
                """.formatted(
                AiTextUtils.truncate(nullToEmpty(req.getTitle()), 300),
                AiTextUtils.truncate(nullToEmpty(req.getDescription()), 1500),
                nullToEmpty(req.getCategory()));

        return List.of(AiMessage.system(system), AiMessage.user(user));
    }

    // ── 2. Resolution Assistant ──────────────────────────────────────────────

    public static List<AiMessage> resolutionAssist(Incident inc, String notes, List<Incident> similar) {
        String system = """
                You help a technician document the resolution of an IT incident in IncidentIQ.
                Using the incident details, any technician notes, and similar past resolutions,
                draft a professional resolution record.

                Return ONLY a JSON object with exactly these keys:
                {
                  "rootCause": string,          // the underlying cause, 1-3 sentences
                  "resolutionSteps": string,    // numbered steps separated by newlines
                  "resolutionSummary": string   // 1-2 sentence summary for the ticket
                }
                Base it on the evidence provided; do not fabricate specifics that aren't implied.
                """;

        StringBuilder user = new StringBuilder();
        user.append("INCIDENT\n");
        user.append("Title: ").append(nullToEmpty(inc.getTitle())).append('\n');
        user.append("Category: ").append(inc.getCategory()).append('\n');
        user.append("Priority: ").append(inc.getPriority()).append('\n');
        user.append("Description: ").append(AiTextUtils.truncate(inc.getDescription(), 1200)).append('\n');
        if (notes != null && !notes.isBlank()) {
            user.append("\nTECHNICIAN NOTES\n").append(AiTextUtils.truncate(notes, 800)).append('\n');
        }
        appendSimilarResolutions(user, similar);
        user.append("\nDraft the resolution record as JSON.");

        return List.of(AiMessage.system(system), AiMessage.user(user.toString()));
    }

    // ── 3. AI Coaching ───────────────────────────────────────────────────────

    public static List<AiMessage> coaching(Incident inc, List<Incident> similar) {
        String system = """
                You are a senior SRE coaching a technician who just opened an IncidentIQ incident.
                Give practical, ordered guidance grounded in the similar resolved incidents provided.

                Return ONLY a JSON object with exactly these keys:
                {
                  "troubleshootingSteps": string[],  // 3-6 concrete diagnostic steps, in order
                  "nextActions": string[],           // 2-4 immediate next actions
                  "commonFixes": string[],           // 2-4 fixes that worked for similar incidents
                  "guidance": string,                // 1-2 sentence overall recommendation
                  "confidence": number               // 0-100, lower when little history exists
                }
                """;

        StringBuilder user = new StringBuilder();
        user.append("ACTIVE INCIDENT\n");
        user.append("Title: ").append(nullToEmpty(inc.getTitle())).append('\n');
        user.append("Category: ").append(inc.getCategory()).append('\n');
        user.append("Priority: ").append(inc.getPriority()).append('\n');
        user.append("Description: ").append(AiTextUtils.truncate(inc.getDescription(), 1000)).append('\n');
        appendSimilarResolutions(user, similar);
        user.append("\nProvide the coaching as JSON.");

        return List.of(AiMessage.system(system), AiMessage.user(user.toString()));
    }

    // ── 4. Knowledge Base Article ────────────────────────────────────────────

    public static List<AiMessage> kbArticle(Incident inc) {
        String system = """
                You write internal IT knowledge-base articles for IncidentIQ from a resolved incident.
                Produce a reusable article a future technician could follow.

                Return ONLY a JSON object with exactly these keys:
                {
                  "title": string,                 // generalized problem title
                  "problemStatement": string,      // what the user/system experiences
                  "rootCause": string,             // why it happens
                  "resolutionSteps": string[],     // ordered, reusable steps
                  "preventionTips": string[]       // 2-4 ways to prevent recurrence
                }
                Generalize away incident-specific noise (names, ticket numbers) where possible.
                """;

        StringBuilder user = new StringBuilder();
        user.append("RESOLVED INCIDENT\n");
        user.append("Title: ").append(nullToEmpty(inc.getTitle())).append('\n');
        user.append("Category: ").append(inc.getCategory()).append('\n');
        user.append("Description: ").append(AiTextUtils.truncate(inc.getDescription(), 1000)).append('\n');
        user.append("Root cause: ").append(AiTextUtils.truncate(inc.getRootCause(), 800)).append('\n');
        user.append("Resolution steps: ").append(AiTextUtils.truncate(inc.getResolutionSteps(), 1200)).append('\n');
        user.append("Resolution summary: ").append(AiTextUtils.truncate(inc.getResolutionSummary(), 600)).append('\n');
        user.append("\nWrite the knowledge-base article as JSON.");

        return List.of(AiMessage.system(system), AiMessage.user(user.toString()));
    }

    // ── 5. Manager Insights ──────────────────────────────────────────────────

    public static List<AiMessage> managerInsights(String dataContext, String question) {
        String system = """
                You are an operations analyst for IncidentIQ. Using ONLY the aggregated incident
                data provided, answer the manager's question (or give a general digest if none).

                Return ONLY a JSON object with exactly these keys:
                {
                  "summary": string,                    // 2-3 sentence headline read of the data
                  "slaRisks": string[],                 // incidents/areas likely to breach SLA
                  "categoryHotspots": string[],         // categories driving volume
                  "workloadObservations": string[],     // who is over/under-loaded
                  "needsAttention": string[]            // specific incidents to act on now
                }
                Reference real incident IDs and numbers from the data. Keep each bullet to one line.
                If the data is empty, say so plainly in summary and return empty arrays.
                """;

        String user = """
                AGGREGATED DATA
                %s

                MANAGER QUESTION: %s

                Answer as JSON using only the data above.
                """.formatted(dataContext, (question == null || question.isBlank())
                ? "Give me an overall operational digest." : question);

        return List.of(AiMessage.system(system), AiMessage.user(user));
    }

    // ── 6. Similar-incident resolution summary ───────────────────────────────

    public static List<AiMessage> similarSummary(String title, String description, List<Incident> similar) {
        String system = """
                You summarize how similar IT incidents were resolved in the past, for IncidentIQ.
                Return ONLY a JSON object with exactly these keys:
                {
                  "summary": string,        // 2-4 sentences: the common resolution pattern
                  "confidence": number      // 0-100 based on how consistent the history is
                }
                If there is little or no history, say so and give a low confidence.
                """;

        StringBuilder user = new StringBuilder();
        user.append("NEW ISSUE\n");
        user.append("Title: ").append(nullToEmpty(title)).append('\n');
        user.append("Description: ").append(AiTextUtils.truncate(description, 800)).append('\n');
        appendSimilarResolutions(user, similar);
        user.append("\nSummarize the historical resolution pattern as JSON.");

        return List.of(AiMessage.system(system), AiMessage.user(user.toString()));
    }

    // ── 7. Scoped Copilot chat ────────────────────────────────────────────────

    public static List<AiMessage> ask(AiRequests.Ask req, String contextBlock) {
        List<AiMessage> messages = new ArrayList<>();
        String system = COPILOT_PERSONA + """

                Answer in concise markdown. When useful, end with up to 3 short suggested
                follow-up questions on their own lines, each prefixed with "- ".
                """;
        messages.add(AiMessage.system(system));

        if (contextBlock != null && !contextBlock.isBlank()) {
            messages.add(AiMessage.system("CURRENT CONTEXT\n" + contextBlock));
        }

        // Short conversational memory (kept small for cost).
        if (req.getHistory() != null) {
            int start = Math.max(0, req.getHistory().size() - 6);
            for (int i = start; i < req.getHistory().size(); i++) {
                AiRequests.Ask.Turn t = req.getHistory().get(i);
                if (t == null || t.getContent() == null) continue;
                String role = "assistant".equalsIgnoreCase(t.getRole()) ? "assistant" : "user";
                messages.add(new AiMessage(role, AiTextUtils.truncate(t.getContent(), 600)));
            }
        }

        messages.add(AiMessage.user(nullToEmpty(req.getMessage())));
        return messages;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static void appendSimilarResolutions(StringBuilder sb, List<Incident> similar) {
        if (similar == null || similar.isEmpty()) {
            sb.append("\nSIMILAR PAST INCIDENTS: none on record.\n");
            return;
        }
        sb.append("\nSIMILAR RESOLVED INCIDENTS (most relevant first)\n");
        int n = 1;
        for (Incident s : similar) {
            sb.append(n++).append(". INC-").append(s.getId())
              .append(" [").append(s.getCategory()).append("] ")
              .append(nullToEmpty(s.getTitle())).append('\n');
            if (s.getRootCause() != null && !s.getRootCause().isBlank()) {
                sb.append("   Root cause: ").append(AiTextUtils.truncate(s.getRootCause(), 300)).append('\n');
            }
            String res = s.getResolutionSummary() != null && !s.getResolutionSummary().isBlank()
                    ? s.getResolutionSummary() : s.getResolutionSteps();
            if (res != null && !res.isBlank()) {
                sb.append("   Resolution: ").append(AiTextUtils.truncate(res, 300)).append('\n');
            }
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
