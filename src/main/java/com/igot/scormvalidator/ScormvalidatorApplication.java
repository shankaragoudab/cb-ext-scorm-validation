package com.igot.scormvalidator;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.cassandra.CassandraAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/**
 * Spring Boot's own CassandraAutoConfiguration is excluded: this project talks to Cassandra
 * directly via common-util's CassandraOperation (raw DataStax driver), never Spring Data
 * Cassandra, but Boot's CqlSession bean is eagerly created by the actuator health indicator
 * at context startup, which fails without a live broker.
 * <p>
 * common-util's own CommonAutoConfiguration (auth + Cassandra beans: PropertiesCache, KeyManager,
 * AccessTokenValidator, CassandraOperation, CassandraConnectionManager) is left enabled and
 * component-scanned normally.
 */
@SpringBootApplication(exclude = CassandraAutoConfiguration.class)
public class ScormvalidatorApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScormvalidatorApplication.class, args);
	}

	/**
	 * common-util's CommonConfig registers its own bare "objectMapper" bean (no JSR-310 module),
	 * which makes Spring Boot's own auto-configured ObjectMapper back off entirely (its
	 * @ConditionalOnMissingBean(ObjectMapper.class) triggers on ANY ObjectMapper bean, by type).
	 * Without this, Instant-typed fields (CREATED_AT/UPDATED_AT) fail to serialize on every
	 * response. @Primary makes this the one Spring MVC actually uses.
	 */
	@Bean
	@Primary
	public com.fasterxml.jackson.databind.ObjectMapper scormObjectMapper() {
		return Jackson2ObjectMapperBuilder.json()
				.modules(new JavaTimeModule())
				.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
				.build();
	}

}
