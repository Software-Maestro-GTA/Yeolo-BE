package com.soma.yeolo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YeoloApplication {

    public static void main(String[] args) {
        SpringApplication.run(YeoloApplication.class, args);
    }

}
