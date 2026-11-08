package com.slink.ansimheung;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan   // config/AnsimheungProperties (application.yml 의 ansimheung.*)
public class AnsimheungApplication {

    public static void main(String[] args) {
        SpringApplication.run(AnsimheungApplication.class, args);
    }
}
