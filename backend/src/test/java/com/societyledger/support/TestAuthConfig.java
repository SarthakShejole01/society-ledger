package com.societyledger.support;

import com.societyledger.auth.OtpCodeGenerator;
import com.societyledger.auth.OtpSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestAuthConfig {

	@Bean
	RecordingOtpSender recordingOtpSender() {
		return new RecordingOtpSender();
	}

	@Bean
	@Primary
	FixedOtpCodeGenerator fixedOtpCodeGenerator() {
		return new FixedOtpCodeGenerator();
	}
}
