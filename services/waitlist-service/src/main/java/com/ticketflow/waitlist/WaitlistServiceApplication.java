package com.ticketflow.waitlist;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WaitlistServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(WaitlistServiceApplication.class, args);
    }
}
