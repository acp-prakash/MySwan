package org.myswan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
public class MySwanApp {
    public static void main(String[] args) {
        SpringApplication.run(MySwanApp.class, args);
    }

    // Simple demo controller
    @RestController
    static class HelloController {
        @GetMapping("/api/hello")
        public String hello() {
            return "Hello and welcome!";
        }
    }
}