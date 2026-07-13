package io.github.dbarciela.aura;

import java.io.IOException;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuraApplication {

	private static final Logger log = LoggerFactory.getLogger(AuraApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(AuraApplication.class, args);
	}

	@EventListener(ApplicationReadyEvent.class)
	public void openBrowser(ApplicationReadyEvent event) {
		if (!event.getApplicationContext().getEnvironment().getProperty("aura.browser.auto-open", Boolean.class, true)) {
			return;
		}
		try {
			new ProcessBuilder("cmd", "/c", "start http://localhost:8081").start();
		} catch (IOException e) {
			log.error("Failed to open browser automatically.", e);
		}
	}
}
