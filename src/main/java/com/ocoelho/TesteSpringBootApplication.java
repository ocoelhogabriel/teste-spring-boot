package com.ocoelho;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TesteSpringBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(TesteSpringBootApplication.class, args);
    }

}
