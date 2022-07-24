package com.eka.costapp.commons;

import java.net.URI;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.servlet.http.HttpServletRequest;

import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.eka.costapp.exception.CostAppException;
import com.eka.costapp.webclient.BaseHttpClient;

@Service
public class CommonService {
	final static Logger logger = ESAPI.getLogger(CommonService.class);

	@Value("${eka_connect_host}")
	private String ekaConnectHost;

	@Autowired
	public RestTemplate restTemplate;

	@Autowired
	private BaseHttpClient httpClient;
	
	@Autowired
	private ContextProvider contextProvider;
	
	public HttpHeaders getHttpHeader() {
		HttpHeaders headers = new HttpHeaders();
		HttpServletRequest httpRequest = contextProvider.getCurrentContext().getRequest();
		Enumeration<?> names = httpRequest.getHeaderNames();
		while (names.hasMoreElements()) {
			String name = (String) names.nextElement();
			headers.add(name, httpRequest.getHeader(name));
		}
		return headers;
	}

	public HttpHeaders getHttpHeader(HttpServletRequest request) {

		HttpHeaders headers = new HttpHeaders();

		Enumeration<?> names = request.getHeaderNames();

		while (names.hasMoreElements()) {

			String name = (String) names.nextElement();
			headers.add(name, request.getHeader(name));
		}
		addDefaultHeaders(headers);
		return headers;

	}

	private void addDefaultHeaders(HttpHeaders headers) {
		// TODO Auto-generated method stub
		headers.add("Content-Type", "application/json");
	}

	/**
	 * Usage:The <i>getPropertyFromConnect</i> fetches property from connect.If
	 * appUUID is passed as null, it will make the call for property at tenant
	 * level.
	 * <p>
	 * Note:Do not pass appUUID if you know that this property exists for tenant
	 * level only, as user-authorization issue might come for that app.
	 * </p>
	 * 
	 * @param req          the request
	 * @param propertyName the name of property
	 * @param appUUID      the app uuid
	 * @return
	 * @throws CostAppException
	 */
	public String getPropertyFromConnect(HttpServletRequest req, String propertyName, String appUUID) {
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("inside method getPropertyFromConnect"));
		String propertyUri = null;
		if (Objects.nonNull(appUUID) && !StringUtils.isEmpty(appUUID))
			propertyUri = ekaConnectHost + "/property/" + appUUID + "/" + propertyName;
		else
			propertyUri = ekaConnectHost + "/property/" + propertyName;
		HttpHeaders httpHeaders = getHttpHeader(req);
		String propertyValue = null;
		ResponseEntity<Map> propResult = null;
		try {
			propResult = httpClient.fireHttpRequest(new URI(propertyUri), HttpMethod.GET, null, httpHeaders, Map.class);
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling getPropertyFromConnect " + e.getLocalizedMessage()));
			throw new CostAppException("Exception while calling getPropertyFromConnect " + e.getLocalizedMessage());
		}
		if (propResult != null) {
			propertyValue = (String) propResult.getBody().get("propertyValue");
		}
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
				.encodeForHTML("method getPropertyFromConnect ends with returned propertyValue:" + propertyValue));
		return propertyValue;
	}
	
	/**
	 * Gets the current user details.
	 *
	 * @param headers the headers
	 * @param platform_url the platform url
	 * @return the current user details
	 */
	public LinkedHashMap getCurrentUserDetails(HttpHeaders headers, String platform_url) {
		ResponseEntity<Object> getCurrentUserRes = httpClient.fireHttpRequest(
				UriComponentsBuilder.fromHttpUrl(platform_url).path("/spring/smartapp/currentUser").build().toUri(),
				HttpMethod.GET, null, headers, Object.class);
		LinkedHashMap currentUserData = (LinkedHashMap) getCurrentUserRes.getBody();
		LinkedHashMap currentUserDetails = (LinkedHashMap) currentUserData.get("data");
		return currentUserDetails;
	}
	
	public void addDataOptionsWhileSaving(Map<String, Object> data) {
		Map<String, Object> copySrcVersion = new HashMap<>();
		copySrcVersion.put("copySourceVersion", true);
		data.put("sys__data__options", copySrcVersion);
	}
}
