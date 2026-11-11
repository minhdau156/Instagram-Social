package com.instagram;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SocialMediaApplication {

    public static void main(String[] args) {
        // The PostgreSQL JDBC driver sends the JVM's default zone as the session TimeZone.
        // On Windows (Vietnam region) the JDK resolves it to the legacy alias "Asia/Saigon",
        // which Debian-trixie-based postgres images no longer ship, so every connection fails.
        // Pinning UTC also matches the backend container in production.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(SocialMediaApplication.class, args);
    }
}
