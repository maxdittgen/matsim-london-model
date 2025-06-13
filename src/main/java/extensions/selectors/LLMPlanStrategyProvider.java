package extensions.selectors;

import org.matsim.core.replanning.PlanStrategy;
import org.matsim.core.replanning.PlanStrategyImpl;
import javax.inject.Provider;

public class LLMPlanStrategyProvider implements Provider<PlanStrategy> {

    private final String apiKey;

    public LLMPlanStrategyProvider() {
        // Get API key from environment variable or system property
        this.apiKey = getApiKey();
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            throw new RuntimeException("Grok API key not found. Please set GROK_API_KEY environment variable or grok.api.key system property.");
        }
    }

    public LLMPlanStrategyProvider(String apiKey) {
        this.apiKey = apiKey;
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            throw new RuntimeException("Grok API key cannot be null or empty.");
        }
    }

    @Override
    public PlanStrategy get() {
        return new PlanStrategyImpl(new LLMPlanSelector<>(apiKey));
    }

    private String getApiKey() {
        // Try environment variable first
        String key = System.getenv("GROK_API_KEY");
        if (key != null && !key.trim().isEmpty()) {
            return key;
        }

        // Try system property
        key = System.getProperty("grok.api.key");
        if (key != null && !key.trim().isEmpty()) {
            return key;
        }

        return null;
    }
}