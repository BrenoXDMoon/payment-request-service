package br.com.breno.itaucorp.paymentrequestservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PaymentRequestServiceApplication {

	static void main(String[] args) {
		SpringApplication.run(PaymentRequestServiceApplication.class, args);
	}

}
