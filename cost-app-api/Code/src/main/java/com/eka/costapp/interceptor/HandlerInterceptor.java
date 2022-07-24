package com.eka.costapp.interceptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.handler.MappedInterceptor;

@Component
public class HandlerInterceptor {

	@Bean
	@Autowired
	public MappedInterceptor getPropertyInterceptor(ContextSetter contextSetter) {
		return new MappedInterceptor(new String[] {}, new String[] {}, contextSetter);
	}

}
