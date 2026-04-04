package com.restaurantqr;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;


@SpringBootApplication
@ConfigurationPropertiesScan
public class RestaurantQrApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Damascus"));
		SpringApplication.run(RestaurantQrApplication.class, args);
	}

	@PostConstruct
	void initTimezone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Damascus"));
	}

}
