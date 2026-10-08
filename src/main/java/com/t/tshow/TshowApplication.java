package com.t.tshow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan("com.t.tshow.global.config")
@EnableScheduling
public class TshowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TshowApplication.class, args);
    }

}
