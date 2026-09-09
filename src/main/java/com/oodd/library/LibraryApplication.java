package com.oodd.library;

import java.io.IOException;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class LibraryApplication {

	public static void main(String[] args) {
		SpringApplication.run(LibraryApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
    public void openBrowser() {
        try {
            new ProcessBuilder("cmd", "/c", "start", "http://localhost:8585").start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
