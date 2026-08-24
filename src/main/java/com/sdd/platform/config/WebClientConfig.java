package com.sdd.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeStrategies;

import java.util.Base64;

/**
 * One {@link WebClient} per external API, qualified by bean name so connector
 * services can inject the right one without juggling base URLs at the call
 * site.
 *
 * For GitHub we pass an optional PAT (used by background sync — interactive
 * user
 * actions use the OAuth2 token from the security context).
 */
@Configuration
public class WebClientConfig {

    @Bean(name = "githubWebClient")
    public WebClient githubWebClient(AppProperties props) {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();

        WebClient.Builder builder = WebClient.builder()
                .exchangeStrategies(strategies)
                .baseUrl(props.connectors().github().apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28");

        String token = props.connectors().github().apiToken();
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return builder.build();
    }

    @Bean(name = "jiraWebClient")
    public WebClient jiraWebClient(AppProperties props) {
        var jira = props.connectors().jira();
        WebClient.Builder builder = WebClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (jira.baseUrl() != null && !jira.baseUrl().isBlank()) {
            builder.baseUrl(jira.baseUrl());
        }
        if (jira.email() != null && !jira.email().isBlank()
                && jira.apiToken() != null && !jira.apiToken().isBlank()) {
            String credentials = jira.email() + ":" + jira.apiToken();
            String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        }
        return builder.build();
    }

    @Bean(name = "circleciWebClient")
    public WebClient circleciWebClient(AppProperties props) {
        WebClient.Builder builder = WebClient.builder()
                .baseUrl(props.connectors().circleci().apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        String token = props.connectors().circleci().apiToken();
        if (token != null && !token.isBlank()) {
            builder.defaultHeader("Circle-Token", token);
        }
        return builder.build();
    }
}
