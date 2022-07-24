package com.eka.costapp.commons;

import static com.eka.costapp.constant.GlobalConstants.KEY;
import static com.eka.costapp.constant.GlobalConstants.VALUE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;

import com.eka.costapp.constant.ErrorConstants;
import com.eka.costapp.constant.GlobalConstants;
import com.eka.costapp.exception.ConnectException;
import com.eka.costapp.exception.CostAppException;
import com.eka.costapp.service.CostAppService;
import com.eka.costapp.webclient.BaseHttpClient;

@Service
public class CostAppUtils {
	final static Logger logger = ESAPI.getLogger(CostAppUtils.class);

	@Value("${eka_connect_host}")
	private String ekaConnectHost;

	@Autowired
	CommonService commonService;

	@Autowired
	BaseHttpClient baseHttpClient;
	
	@Autowired
	ContextProvider contextProvider;

	/**
	 * Usage:The <i>findMatchingKeyFromValuesInMdmData</i> method checks and returns
	 * matching mdm-id of a display name.
	 * 
	 * @param mdmMapping The Map containing mdm keys and values
	 * @return String This method returns the matching string id in mdm
	 * @throws CostAppException if there is no matching value
	 */
	public String findMatchingKeyFromValuesInMdmData(List<Map<String, Object>> mdmMapping, String value) {
		String matchingKey = null;
		try {
			matchingKey = mdmMapping.stream().filter(mapping -> mapping.get(VALUE).toString().equalsIgnoreCase(value))
					.findAny().get().get(KEY).toString();
		} catch (NoSuchElementException nsee) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("mdm data does not contain this value:" + value + " ,mdm data:" + mdmMapping));
			throw new CostAppException(
					"mdm data does not contain value:" + value + " exception due to:" + nsee.getLocalizedMessage());
		}
		return matchingKey;
	}

	/**
	 * Usage:The <i>callMdm</i> calls mdm with serviceKey payload passed.
	 * 
	 * @param payload The payload containing service keys
	 * @return Map<String, List<Map<String, Object>>> This method returns the mdm
	 *         response body containing keys and values for serviceKeys
	 * @throws CostAppException
	 */
	public Map<String, List<Map<String, Object>>> callMdm(List<Map<String, String>> payload, HttpServletRequest req) {
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("inside method callMdm"));
		ResponseEntity<Map> mdmResult = null;
		try {
			String mdmUri = commonService.getPropertyFromConnect(req, "eka_mdm_host", null) + CostAppService.MDM_DATA_PATH;
			HttpHeaders httpHeaders = commonService.getHttpHeader(req);
			logger.debug(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("calling mdm data api with payload:" + payload));
			mdmResult = baseHttpClient.fireHttpRequest(new URI(mdmUri), HttpMethod.POST, payload, httpHeaders,
					Map.class);
			logger.debug(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("obtained response from workflow/mdm api :-" + mdmResult.getBody()));
		} catch (HttpServerErrorException httpServerErrorException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"mdm call inside method callMdm failed due to:" + httpServerErrorException.getLocalizedMessage()));
			throw new CostAppException(
					"mdm call failed due to:" + httpServerErrorException.getLocalizedMessage());
		} catch (URISyntaxException uriSyntaxException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception inside method callMdm due to:" + uriSyntaxException.getLocalizedMessage()));
			throw new CostAppException("URISyntaxException due to :" + uriSyntaxException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("mdm call with payload:" + payload + " failed due to:" + e.getLocalizedMessage()));
			throw new CostAppException(
					"mdm call inside method callMdm" + " failed due to:" + e.getLocalizedMessage());
		}
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("method callMdm ends"));
		return mdmResult.getBody();
	}
	
	/**
	 * Usage:The <i>getErrorMessage</i> fetches error bundle from connect
	 * 
	 * @param errorCode the error code
	 * @param refTypeId the refTypeId of the document
	 * @param locale    the locale
	 * @return the error message for that locale
	 * @throws CostAppException
	 */
	public String getErrorMessage(String errorCode, String refTypeId) throws ConnectException, CostAppException {
		ResponseEntity<Map> errorBundleResponse = null;
		try {
			String locale = StringUtils.isEmpty(contextProvider.getCurrentContext().getLocale())
					? ErrorConstants.ERROR_DEFAULT_LOCALE
					: contextProvider.getCurrentContext().getLocale();
			String type = ErrorConstants.TYPE_ERROR;
			// call connect get /meta/type/refTypeId--
			String url = ekaConnectHost + "/meta/" + ErrorConstants.TYPE_ERROR + "/" + CostAppService.COST_APP_UUID;
			HttpHeaders httpHeaders = commonService.getHttpHeader();
			httpHeaders.add(GlobalConstants.HEADER_X_LOCALE, locale);
			errorBundleResponse = baseHttpClient.fireHttpRequest(new URI(url), HttpMethod.GET, null, httpHeaders,
					Map.class);
			if (Objects.isNull(errorBundleResponse)) {
				logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("got error bundle null response from connect for refTypeId:" + refTypeId));
				return ErrorConstants.ERROR_MSG_UNAVAILABLE;
			}
			Map<String, String> errorBundle = errorBundleResponse.getBody();
			if (Objects.isNull(errorBundle.get(errorCode))) {
				logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("error message not available in bundle for errorcode:" + errorCode));
				return ErrorConstants.ERROR_MSG_UNAVAILABLE;
			}
			return errorBundle.get(errorCode);
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("error inside method getError due to :" + e.getLocalizedMessage()));
			throw new CostAppException("error occurred while fetching error message bundle");
		}
	}
	
	public Map<String, Object> callWorkflowMdmApi(HttpHeaders httpHeaders, Map<String, Object> payload) {
		ResponseEntity<Map> workflowMdmResult = null;
		String workflowMdmUri = ekaConnectHost + CostAppService.WORKFLOW_MDM_PATH;
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("calling workflow/mdm with payload:" + payload));
		try {
			workflowMdmResult = baseHttpClient.fireHttpRequest(new URI(workflowMdmUri), HttpMethod.POST,
					payload, httpHeaders, Map.class);
		} catch (HttpServerErrorException httpServerErrorException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"workflow/mdm call failed due to:" + httpServerErrorException.getLocalizedMessage()));
		} catch (URISyntaxException uriSyntaxException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception inside method callWorkflowMdmApi due to:"
							+ uriSyntaxException.getLocalizedMessage()));
			throw new CostAppException("URISyntaxException due to :" + uriSyntaxException.getLocalizedMessage());
		}
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("obtained response from workflow/mdm api :-" + workflowMdmResult));
		Map<String, Object> workflowMdmResponseBody = new HashMap<String, Object>();
		if (Objects.nonNull(workflowMdmResult) && Objects.nonNull(workflowMdmResult.getBody())
				&& (workflowMdmResult.getBody() instanceof Map)) {
			workflowMdmResponseBody = workflowMdmResult.getBody();
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("method callWorkflowMdmApi ends"));
		}
		return workflowMdmResponseBody;
	}
	
	public boolean checkKeysNotAbsentOrEmptyInData(@NonNull List<String> keys, @NonNull Map<String, Object> data) {
		return keys.stream().allMatch(key -> data.containsKey(key)&&!data.get(key).toString().isEmpty());
	}
	
	public String idsInData(Object data) {
		if (Objects.nonNull(data) && data instanceof List && !((List) data).isEmpty()) {
			List<Map<String, Object>> dataList = (List<Map<String, Object>>) data;
			List<String> ids = dataList.stream().filter(datum -> Objects.nonNull(datum.get("_id")))
					.map(datum -> datum.get("_id").toString()).collect(Collectors.toList());
			String csvIds = String.join(",", ids);
			return csvIds;
		}
		return "";
	}
}
