package com.igot.scormvalidator;

import com.igot.scormvalidator.util.PropertiesCache;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ScormvalidatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScormvalidatorApplication.class, args);
	}

	@Bean
	public PropertiesCache propertiesCache() {
		return PropertiesCache.getInstance();
	}

}
