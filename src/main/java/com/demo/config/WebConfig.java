package com.demo.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/*
 * GLOBAL CORS (alternative to @CrossOrigin on each controller).
 *
 * CORS is enforced by the BROWSER, not the server. For a "non-simple" request
 * (e.g. PUT, or JSON body) the browser first sends a preflight:
 *   OPTIONS /api/users/1
 *   Origin: http://localhost:3000
 *   Access-Control-Request-Method: PUT
 * Spring answers:
 *   Access-Control-Allow-Origin: http://localhost:3000
 *   Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
 * Only then does the browser send the real PUT.
 *
 * Rule: never use allowedOrigins("*") together with allowCredentials(true). Spring throws on startup.
 * With Spring Security you ALSO need http.cors(...) in the SecurityFilterChain,
 * because security filters run before this MVC config.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "https://myapp.com")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowCredentials(true)
                .maxAge(3600); // browser caches the preflight result for 1 hour
    }
}
