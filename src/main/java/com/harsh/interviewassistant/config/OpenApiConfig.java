package com.harsh.interviewassistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI interviewAssistantOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AI Interview Assistant API")
                .description("Upload a resume and job description, get tailored interview questions and answer feedback.")
                .version("v1"));
        // JWT "Authorize" button is added in Step 2 (security).
    }
}
