package com.glassvue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// @ConfigurationPropertiesScan: 도메인별 @ConfigurationProperties 레코드를 자동 등록
// (도메인마다 @EnableConfigurationProperties용 @Configuration을 두지 않기 위함)
@ConfigurationPropertiesScan
@SpringBootApplication
public class GlassvueBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GlassvueBackendApplication.class, args);
	}

}
