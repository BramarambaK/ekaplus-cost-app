package com.eka.costapp.interceptor;

import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import com.eka.costapp.commons.ContextProvider;
import com.eka.costapp.constant.GlobalConstants;
import com.eka.costapp.model.ContextInfo;

/**
 * <p>
 * <code>PropertyInterceptor</code> make Property API call and injects the same
 * into ApplicationProps.
 * <p>
 * <hr>
 * 
 * @author Ranjan.Jha
 * @version 1.0
 */

@Component
public class ContextSetter implements AsyncHandlerInterceptor {

	@Autowired
	public RestTemplate restTemplate;
	@Autowired
	public ContextProvider contextProvider;

	final static Logger logger = ESAPI.getLogger(ContextSetter.class);

	private static final String X_REQUEST_ID = "X-Request-Id";

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
			throws Exception {
		setTenantNameAndRequestIdToLog(request);
		setContextDefaultValues(request);
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder()
						.encodeForHTML("headers in current request: tenant:" + request.getHeader("X-TenantID")
								+ ",authToken:" + request.getHeader("Authorization"))
						+ ",content-type:" + request.getHeader("Content-Type"));
		return true;
	}

	@Override
	public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
			@Nullable ModelAndView modelAndView) throws Exception {
		removeContext();
		removeRequestId();
	}

	public void setContextDefaultValues(HttpServletRequest request) {

		ContextInfo freshContext = new ContextInfo();
		freshContext.setRequest(request);
		contextProvider.setCurrentContext(freshContext);
	}

	public void removeContext() {

		contextProvider.clear();
	}

	private void removeRequestId() {
		MDC.remove(GlobalConstants.REQUEST_ID);
	}
	
	private void setTenantNameAndRequestIdToLog(HttpServletRequest request) {
		String requestId = null;
		String tenantName = null;
		if (null != request.getHeader(GlobalConstants.REQUEST_ID)) {
			requestId = request.getHeader(GlobalConstants.REQUEST_ID);
		} else {
			requestId = UUID.randomUUID().toString().replace("-", "");
		}
		if (null == request.getHeader(GlobalConstants.X_TENANT_ID)) {
			tenantName = request.getServerName();
			tenantName = tenantName.split(GlobalConstants.REGEX_DOT)[0];
		} else {
			tenantName = request.getHeader(GlobalConstants.X_TENANT_ID);
		}
		MDC.put(GlobalConstants.REQUEST_ID, requestId);
		MDC.put("tenantName", tenantName);
	}

}
