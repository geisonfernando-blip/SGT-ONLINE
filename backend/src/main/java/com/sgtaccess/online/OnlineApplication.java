package com.sgtaccess.online;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** SGT EHS/Visitas Online (25d) — balcão de entrada do MVP do QR. Só cadastro/entrada; não decide nada. */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class OnlineApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineApplication.class, args);
    }
}
