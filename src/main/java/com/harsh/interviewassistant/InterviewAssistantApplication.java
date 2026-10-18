package com.harsh.interviewassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan // picks up @ConfigurationProperties records such as JwtProperties
public class InterviewAssistantApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewAssistantApplication.class, args);
    }
}
