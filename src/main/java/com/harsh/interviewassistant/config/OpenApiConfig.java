package com.harsh.interviewassistant.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI interviewAssistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Interview Assistant API")
                        .description("Upload a resume and job description, get tailored interview questions and answer feedback.")
                        .version("v1"))
                // Adds the "Authorize" button; public endpoints opt out with @SecurityRequirements
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Paste the accessToken from /api/v1/auth/login or /register")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
