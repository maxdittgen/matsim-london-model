package extensions.selectors;

import org.matsim.api.core.v01.population.BasicPlan;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.core.replanning.selectors.PlanSelector;
import java.util.List;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Duration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LLMPlanSelector<T extends BasicPlan, I> implements PlanSelector<T, I> {

    private static final Logger log = LogManager.getLogger(LLMPlanSelector.class);
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String apiUrl = "https://api.x.ai/v1/chat/completions";

    public LLMPlanSelector(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public T selectPlan(HasPlansAndId<T, I> person) {
        List<? extends T> plans = person.getPlans();

        if (plans.isEmpty()) {
            log.warn("No plans available for person {}", person.getId());
            return null;
        }

        // prevent needless queries
        if (plans.size() == 1) {
            return plans.get(0);
        }

        try {
            int selectedPlanIndex = queryGrokAPI(person);

            // Validate the returned index
            if (selectedPlanIndex < 0 || selectedPlanIndex >= plans.size()) {
                log.warn("Grok API returned invalid plan index {} for person {} with {} plans. Using fallback.",
                        selectedPlanIndex, person.getId(), plans.size());
                return selectFallbackPlan(plans);
            }

            log.debug("Selected plan {} for person {}", selectedPlanIndex, person.getId());
            return plans.get(selectedPlanIndex);

        } catch (Exception e) {
            log.error("Error calling Grok API for person {}: {}. Using fallback selection.",
                    person.getId(), e.getMessage());
            return selectFallbackPlan(plans);
        }
    }

    private int queryGrokAPI(HasPlansAndId<T, I> person) throws Exception {
        String query = prepareQuery(person);

        // Prepare the request body
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", "grok-3");
        requestBody.put("temperature", 0.1); // Low temperature for consistent responses

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode systemMessage = objectMapper.createObjectNode();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a transportation planning assistant. Given a list of travel plans with their scores, select the best plan by returning only the plan number (0-indexed). Return only a single integer, nothing else.");

        ObjectNode userMessage = objectMapper.createObjectNode();
        userMessage.put("role", "user");
        userMessage.put("content", query);

        messages.add(systemMessage);
        messages.add(userMessage);
        requestBody.set("messages", messages);

        // Make the HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Grok API returned status code: " + response.statusCode() +
                    ", body: " + response.body());
        }

        // Parse the response
        ObjectNode responseJson = (ObjectNode) objectMapper.readTree(response.body());
        String content = responseJson.get("choices").get(0).get("message").get("content").asText().trim();

        // Extract the plan number
        try {
            return Integer.parseInt(content);
        } catch (NumberFormatException e) {
            throw new RuntimeException("Grok API returned non-numeric response: " + content);
        }
    }

    private String prepareQuery(HasPlansAndId<T, I> person) {
        StringBuilder query = new StringBuilder();
        query.append("Person ID: ").append(person.getId()).append("\n");
        query.append("Available plans with their scores:\n");

        List<? extends T> plans = person.getPlans();
        for (int i = 0; i < plans.size(); i++) {
            T plan = plans.get(i);
            query.append("Plan ").append(i).append(": Score = ");

            if (plan.getScore() != null) {
                query.append(String.format("%.2f", plan.getScore()));
            } else {
                query.append("No score yet");
            }

//            // Add plan details if available
//            if (plan.getPlanElements() != null && !plan.getPlanElements().isEmpty()) {
//                query.append(", Activities: ").append(plan.getPlanElements().size());
//            }

            query.append("\n");
        }

        query.append("\nSelect the best plan by returning only the plan number (0 to ")
                .append(plans.size() - 1).append(").");

        System.out.println("QUERYYYYYY: " + query.toString());
        return query.toString();
    }

    private T selectFallbackPlan(List<? extends T> plans) {
        // Fallback: select plan with highest score, or first plan if no scores
        T bestPlan = plans.stream()
                .max((p1, p2) -> {
                    Double score1 = p1.getScore();
                    Double score2 = p2.getScore();
                    if (score1 == null && score2 == null) return 0;
                    if (score1 == null) return -1;
                    if (score2 == null) return 1;
                    return Double.compare(score1, score2);
                })
                .orElse(null);

//         If no best plan found (shouldn't happen), return first plan
        return bestPlan != null ? bestPlan : (T) plans.get(0);
    }
}