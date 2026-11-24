package com.techenglish.vector;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.vector")
public class VectorProperties {
    private boolean enabled = true;
    private int dimension = 384;
    private String provider = "hash";
    private String model = "text-embedding-3-small";
    private String baseUrl = "https://api.openai.com/v1";
    private String apiKey = "";
    private int timeoutSeconds = 30;
    private String sqliteExtension = "lib/sqlite-vec/vec0";
    private int maxResults = 20;
}
