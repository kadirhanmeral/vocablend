package com.vocablend.vocablend_be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.vocablend.vocablend_be")
public class VocablendBeApplication {

	public static void main(String[] args) {
		SpringApplication.run(VocablendBeApplication.class, args);
	}

}
