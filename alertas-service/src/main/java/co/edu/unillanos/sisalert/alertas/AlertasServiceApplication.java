package co.edu.unillanos.sisalert.alertas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AlertasServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AlertasServiceApplication.class, args);
    }
}