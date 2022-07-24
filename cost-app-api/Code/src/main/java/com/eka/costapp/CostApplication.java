package com.eka.costapp;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication

public class CostApplication {

	public static void main(String[] args) {
		SpringApplication.run(CostApplication.class, args);
	}

	@Bean
	public RestTemplate restTemplate() {

		RestTemplate restTemplate = new RestTemplate();

		// Do any additional configuration here
		MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter = new MappingJackson2HttpMessageConverter();
		mappingJackson2HttpMessageConverter.setSupportedMediaTypes(Arrays.asList(MediaType.APPLICATION_JSON,MediaType.APPLICATION_JSON_UTF8,
				MediaType.APPLICATION_OCTET_STREAM, MediaType.MULTIPART_FORM_DATA, MediaType.TEXT_PLAIN));

		restTemplate.getMessageConverters().add(mappingJackson2HttpMessageConverter);

		return restTemplate;
	}
}