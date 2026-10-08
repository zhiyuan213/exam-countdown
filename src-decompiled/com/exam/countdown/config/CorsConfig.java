package com.exam.countdown.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
   public void addCorsMappings(CorsRegistry registry) {
      registry.addMapping("/api/**").allowedOriginPatterns(new String[]{"*"}).allowedMethods(new String[]{"GET", "POST", "OPTIONS"});
   }
}
