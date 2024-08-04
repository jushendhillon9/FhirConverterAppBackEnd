package com.localhost.fhirconverter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    //securityfilterchain is a chain of filters that spring security applies to incoming http requests
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        //http is configuration builder, this is kinda like a http object that allows you to edit its possible endpoints
        http.authorizeHttpRequests(authorizeRequests -> authorizeRequests.anyRequest()
            .permitAll())
            .csrf(AbstractHttpConfigurer::disable); //disable csrf token

        return http.build();
    }
}


// http://localhost:8080/login/oauth2/code/google