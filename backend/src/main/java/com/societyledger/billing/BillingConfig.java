package com.societyledger.billing;

import com.societyledger.common.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BillingConfig {

	/** The late fee is Rs 100 for every society until societies get their own settings. */
	@Bean
	LateFeePolicy lateFeePolicy(@Value("${society-ledger.late-fee-rupees:100}") long lateFeeRupees) {
		return LateFeePolicy.of(Money.rupees(lateFeeRupees));
	}
}
