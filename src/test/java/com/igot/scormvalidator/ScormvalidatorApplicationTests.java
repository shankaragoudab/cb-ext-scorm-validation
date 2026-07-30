package com.igot.scormvalidator;

import com.igot.scormvalidator.transactional.cassandrautils.CassandraConnectionManager;
import com.igot.scormvalidator.transactional.cassandrautils.CassandraOperation;
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
