package com.societyledger.auth;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.stereotype.Component;

@Component
class VirtualThreadOtpDispatcher implements OtpDispatcher {

	private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

	@Override
	public void dispatch(Runnable task) {
		executor.execute(task);
	}
}
