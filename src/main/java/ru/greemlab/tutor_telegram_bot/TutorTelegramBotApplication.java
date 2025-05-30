package ru.greemlab.tutor_telegram_bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableCaching
@EnableScheduling
@SpringBootApplication
public class TutorTelegramBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TutorTelegramBotApplication.class, args);
    }

}
