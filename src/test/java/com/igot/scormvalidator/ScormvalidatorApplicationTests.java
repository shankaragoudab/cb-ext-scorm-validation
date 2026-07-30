package com.igot.scormvalidator;

import org.igot.common.cassandra.CassandraConnectionManager;
import org.igot.common.cassandra.CassandraOperation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
class ScormvalidatorApplicationTests {

	@MockBean
	private CassandraOperation cassandraOperation;

	@MockBean
	private CassandraConnectionManager cassandraConnectionManager;

	@Test
	void contextLoads() {
	}

}
