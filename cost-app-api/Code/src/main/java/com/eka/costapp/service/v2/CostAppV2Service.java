package com.eka.costapp.service.v2;

import static com.eka.costapp.constant.GlobalConstants.DRAFT_ESTIMATE_NO;
import static com.eka.costapp.constant.GlobalConstants.ENTITY_ACTUAL_NO;
import static com.eka.costapp.constant.GlobalConstants.ENTITY_REF_NO;
import static com.eka.costapp.constant.GlobalConstants.ENTITY_TYPE;
import static com.eka.costapp.constant.GlobalConstants.ITEM_QTY;
import static com.eka.costapp.constant.GlobalConstants.PRODUCT;
import static com.eka.costapp.constant.GlobalConstants.CONTRACT_ITEM_REF_NO;
import static com.eka.costapp.constant.GlobalConstants.ATTRIBUTE_NAMES_IN_ESTIMATE;
import static com.eka.costapp.constant.GlobalConstants.ATTRIBUTE_NAME;
import static com.eka.costapp.constant.GlobalConstants.ATTRIBUTE_VALUE;
import static com.eka.costapp.constant.GlobalConstants.ATTRIBUTES;
import static com.eka.costapp.constant.GlobalConstants.TEMPLATE_NAME;
import static com.eka.costapp.constant.GlobalConstants._500;
import static com.eka.costapp.constant.GlobalConstants.PROFIT_CENTER;
import static com.eka.costapp.constant.GlobalConstants.QUALITY;
import static com.eka.costapp.constant.GlobalConstants._ID;
import static com.eka.costapp.constant.GlobalConstants.QUANTITY;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.eka.costapp.commons.CommonService;
import com.eka.costapp.commons.CostAppUtils;
import com.eka.costapp.error.ConnectError;
import com.eka.costapp.exception.ConnectException;
import com.eka.costapp.exception.CostAppException;
import com.eka.costapp.factory.RestTemplateGetRequestBodyFactory;
import com.eka.costapp.model.FilterData;
import com.eka.costapp.model.MongoOperations;
import com.eka.costapp.service.CostAppService;
import com.eka.costapp.webclient.BaseHttpClient;

@Service
public class CostAppV2Service {

	final static Logger logger = ESAPI.getLogger(CostAppV2Service.class);

	@Value("${eka_connect_host}")
	private String ekaConnectHost;

	private static final String COST_APP_UUID = "d33143ac-4164-4a3f-8d30-61d845c9eeed";
	private static final String DRAFT_COST_ESTIMATE_OBJECT_UUID = "00189ca9-cfc1-4327-95ac-f937f22deb60";
	private static final String COST_ESTIMATE_OBJECT_UUID = "f3d6ff89-b541-4dc0-b88d-12065d10cc90";

	@Autowired
	CommonService commonService;
	@Autowired
	BaseHttpClient baseHttpClient;
	@Autowired
	RestTemplateGetRequestBodyFactory restTemplateGetWityBody;
	@Autowired
	CostAppService costService;
	@Autowired
	CostAppUtils costAppUtils;

	/**
	 * Usage:The <i>ConvertDraftToActualCost</i> converts draft cost estimate data
	 * in connect db to actual cost estimate data
	 * 
	 * @param request
	 * @param requestBody
	 * @return
	 * @throws CostAppException
	 */
	public Object ConvertDraftToActualCost(HttpServletRequest request,
			Map<String, List<Map<String, Object>>> requestBody) throws ConnectException, CostAppException {
		HttpHeaders headers = new HttpHeaders();
		ResponseEntity<Object> getResult = null;
		ResponseEntity<Object> postResult = null;
		MultiValueMap<String, Object> existingEntityRefNoMap = new LinkedMultiValueMap<>();
		try {
			if (CollectionUtils.isEmpty(requestBody))
				return Collections.EMPTY_LIST;
			// fetch draft estimates passed in payload from connect--
			String uri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + DRAFT_COST_ESTIMATE_OBJECT_UUID;
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			Set<String> draftEstimateNos = requestBody.keySet();
			Map<String, Object> payloadToFetchDraftEstimates = new HashMap<>();
			MongoOperations filterOnDraftEstimateNo = new MongoOperations(DRAFT_ESTIMATE_NO, draftEstimateNos, "in");
			List<MongoOperations> filter = new ArrayList<MongoOperations>();
			filter.add(filterOnDraftEstimateNo);
			FilterData filterData = new FilterData(filter);
			payloadToFetchDraftEstimates.put("filterData", filterData);
			logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"calling connect GET data with payload:" + new JSONObject(payloadToFetchDraftEstimates)));
			HttpEntity<Object> entity = new HttpEntity<Object>(payloadToFetchDraftEstimates, headers);
			getResult = restTemplateGetWityBody.getRestTemplate().exchange(uri, HttpMethod.GET, entity, Object.class);

			// fetch all present cost estimates from db--
			List<Map<String, Object>> presentCostEstimates = costService
					.fetchCostEstimateDataFromConnectWithoutAnyHandling(null, headers, null);
			presentCostEstimates.stream().forEach(costEstimate -> {
				Map<String, Object> individualCostEstimate = new HashMap<String, Object>();
				individualCostEstimate.putAll((Map<String, Object>) costEstimate);
				if (costAppUtils.checkKeysNotAbsentOrEmptyInData(
						Arrays.asList(new String[] { ENTITY_TYPE, ENTITY_REF_NO }), individualCostEstimate))
					existingEntityRefNoMap.add(individualCostEstimate.get(ENTITY_TYPE).toString(),
							individualCostEstimate.get(ENTITY_REF_NO));
			});

			if (Objects.nonNull(getResult) && Objects.nonNull(getResult.getBody())
					&& (getResult.getBody() instanceof List)) {
				List<Map<String, Object>> dataList = (List<Map<String, Object>>) getResult.getBody();
				if (dataList.isEmpty())
					return Collections.EMPTY_LIST;

				List<Map<String, Object>> retData = new ArrayList<Map<String, Object>>();
				MultiKeyMap entityTypeRefNoToAttributesMapping = new MultiKeyMap<>();
				dataList.stream()
						.map(individualData -> transformDraftIntoActualEstimate(individualData, requestBody, retData,
								existingEntityRefNoMap, entityTypeRefNoToAttributesMapping))
						.collect(Collectors.toList());
				retData.removeAll(Collections.singleton(null));
				// if no draft estimate was transformed,no need to call connect to save-
				if (Objects.nonNull(retData) && retData.isEmpty())
					return Collections.EMPTY_LIST;

				// check mandatory cost components present,throw error if not--
				checkMandatoryCostComponents(retData, entityTypeRefNoToAttributesMapping);
				String connectSaveDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID;
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("calling connect POST data with payload:" + retData));
				try {
					postResult = baseHttpClient.fireHttpRequest(new URI(connectSaveDataUri), HttpMethod.POST, retData,
							headers, Object.class);
					logger.debug(logger.EVENT_SUCCESS,
							ESAPI.encoder()
									.encodeForHTML("_id's in response body of method convertDraftToActualCostTemp:"
											+ costAppUtils.idsInData(postResult.getBody())));
				} catch (HttpClientErrorException hcee) {
					JSONObject error = new JSONObject(hcee.getResponseBodyAsString());
					if (Objects.nonNull(error) && !StringUtils.isEmpty(error.optString("errorMessage"))
							&& (error.getString("errorMessage")
									.contains("Please fill all the required fields appropriately.")
									|| error.getString("errorMessage").contains("is mandatory field"))) {
						List<ConnectError> errors = new ArrayList<>();
						String csvComponentNames = String.join(",",
								retData.stream().filter(data -> Objects.nonNull(data.get("costComponentDisplayName")))
										.map(data -> data.get("costComponentDisplayName").toString())
										.collect(Collectors.toList()));
						logger.error(Logger.EVENT_FAILURE,
								ESAPI.encoder().encodeForHTML("Cost Estimates: " + csvComponentNames
										+ " are not configured correctly. Please configure them and proceed."
										+ "Caught following error from connect while saving estimates: " + error));
						errors.add(new ConnectError(_500,
								"Cost Estimates: " + csvComponentNames
										+ " are not configured correctly. Please configure them and proceed",
								"Cost Estimates: " + csvComponentNames
										+ " are not configured correctly. Please configure them and proceed",
								"{cost component names:" + csvComponentNames + "}"));
						throw new ConnectException(errors, "Cost Estimates: " + csvComponentNames
								+ " are not configured correctly. Please configure them and proceed.");
					} else
						throw hcee;
				}
			}
			return postResult.getBody();

		} catch (ConnectException ce) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling ConvertDraftToActualCost " + ce.getLocalizedMessage()));
			if (ce.getLocalizedMessage().contains("Mandatory Cost Component Details Missing, please update and proceed")
					|| ce.getLocalizedMessage()
							.contains("are not configured correctly. Please configure them and proceed."))
				throw ce;
			throw new CostAppException(
					"Exception while getting/saving data from/to connect due to :" + ce.getLocalizedMessage(), ce);
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling ConvertDraftToActualCost "
							+ httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException("Error in getting/saving data from/to connect due to :"
					+ httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling ConvertDraftToActualCost "
							+ resourceAccessException.getLocalizedMessage()));
			throw new CostAppException("Error in getting/saving data from/to connect due to :"
					+ resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling ConvertDraftToActualCost " + e.getLocalizedMessage()));
			throw new CostAppException(
					"Error while converting draft cost estimate to actual cost estimate due to :"
							+ e.getLocalizedMessage(),
					e);
		}
	}
	
	/**
	 * Usage:The <i>ConvertDraftToActualCost2</i> converts draft cost estimate data
	 * in connect db to actual cost estimate data. Deletes any old actual estimate
	 * present with passed entityRefNo. Does not return empty list if passed
	 * entityRefNo already exists as done by ConvertDraftToActualCost API.
	 * 
	 * @param request
	 * @param requestBody
	 * @return
	 * @throws CostAppException
	 */
	public Object convertDraftToActualCostTemp(HttpServletRequest request,
			Map<String, List<Map<String, Object>>> requestBody) throws ConnectException, CostAppException {
		HttpHeaders headers = new HttpHeaders();
		ResponseEntity<Object> getResult = null;
		ResponseEntity<Object> postResult = null;
		try {
			if (CollectionUtils.isEmpty(requestBody))
				return Collections.EMPTY_LIST;
			// fetch draft estimates passed in payload from connect--
			String uri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + DRAFT_COST_ESTIMATE_OBJECT_UUID;
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			Map<String, Object> payloadToFetchDraftEstimates = new HashMap<>();
			Set<String> draftEstimateNoSet = requestBody.keySet();
			List<MongoOperations> filter = new ArrayList<MongoOperations>();
			filter.add(new MongoOperations(DRAFT_ESTIMATE_NO, draftEstimateNoSet, "in"));
			FilterData filterData = new FilterData(filter);
			payloadToFetchDraftEstimates.put("filterData", filterData);

			logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"calling connect GET data with payload" + new JSONObject(payloadToFetchDraftEstimates)));
			HttpEntity<Object> entity = new HttpEntity<Object>(payloadToFetchDraftEstimates, headers);
			getResult = restTemplateGetWityBody.getRestTemplate().exchange(uri, HttpMethod.GET, entity, Object.class);

			// fetch actual estimates of entityrefs present in payload to get their _ids
			String getActualEstimateUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
					+ COST_ESTIMATE_OBJECT_UUID;
			Map<String, Object> payloadToFetchActualEstimates = new HashMap<>();
			Set<Object> entityRefNoSet = new HashSet<>();
			requestBody.entrySet().stream().map(Map.Entry::getValue).forEach(listOfMappings -> {
				listOfMappings.stream()
						.filter(mappingDetailsObject -> mappingDetailsObject.containsKey(ENTITY_REF_NO)
								&& !mappingDetailsObject.get(ENTITY_REF_NO).toString().isEmpty())
						.forEach(mappingDetailsObject -> {
							entityRefNoSet.add(mappingDetailsObject.get(ENTITY_REF_NO));
						});
			});
			List<MongoOperations> filterOnEntityRefNo = new ArrayList<MongoOperations>();
			filterOnEntityRefNo.add(new MongoOperations(ENTITY_REF_NO, entityRefNoSet, "in"));
			FilterData filterActualEstimateData = new FilterData(filterOnEntityRefNo);
			payloadToFetchActualEstimates.put("filterData", filterActualEstimateData);
			ResponseEntity<Object> getActualEstimatesResult = null;
			HttpEntity<Object> entityToFetchActualEstimates = new HttpEntity<Object>(payloadToFetchActualEstimates,
					headers);
			logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"calling connect GET data with payload" + new JSONObject(payloadToFetchActualEstimates)));
			getActualEstimatesResult = restTemplateGetWityBody.getRestTemplate().exchange(getActualEstimateUri,
					HttpMethod.GET, entityToFetchActualEstimates, Object.class);
			if (Objects.nonNull(getActualEstimatesResult) && Objects.nonNull(getActualEstimatesResult.getBody())
					&& getActualEstimatesResult.getBody() instanceof List) {
				List<Map<String, Object>> actualEstimates = (List<Map<String, Object>>) getActualEstimatesResult
						.getBody();
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("no of actual estimates fetched:" + actualEstimates.size()));
				if (!CollectionUtils.isEmpty(actualEstimates)) {
					// prepare list of _ids--
					Set<String> _idSet = new HashSet<>();
					_idSet.addAll(actualEstimates.stream().map(actualEstimate -> actualEstimate.get(_ID).toString())
							.collect(Collectors.toSet()));
					// delete all old estimates using bulk delete API--
					String deleteDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
							+ COST_ESTIMATE_OBJECT_UUID + "/bulkDelete";
					Map<String, Object> payloadToDeleteActualEstimates = new HashMap<>();
					List<MongoOperations> filterOnEntityRefNoToDelete = new ArrayList<MongoOperations>();
					filterOnEntityRefNoToDelete.add(new MongoOperations(_ID, _idSet, "in"));
					FilterData filterActualEstimatesToDelete = new FilterData(filterOnEntityRefNoToDelete);
					payloadToDeleteActualEstimates.put("filterData", filterActualEstimatesToDelete);
					logger.debug(Logger.EVENT_SUCCESS,
							ESAPI.encoder().encodeForHTML("calling connect bulk Delete data with payload"
									+ new JSONObject(payloadToDeleteActualEstimates)));
					baseHttpClient.fireHttpRequest(new URI(deleteDataUri), HttpMethod.DELETE,
							payloadToDeleteActualEstimates, headers, Object.class);
				}
			}

			if (Objects.nonNull(getResult) && Objects.nonNull(getResult.getBody())
					&& getResult.getBody() instanceof List) {
				List<Map<String, Object>> dataList = (List<Map<String, Object>>) getResult.getBody();
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("no of draft estimates fetched:" + dataList.size()));
				// if there is no draft estimate in db of any type, return empty as
				// transformation cannot be performed--
				if (dataList.isEmpty())
					return Collections.EMPTY_LIST;

				// transform draft data into actual--
				List<Map<String, Object>> retData = new ArrayList<Map<String, Object>>();
				MultiKeyMap entityTypeRefNoToAttributesMapping = new MultiKeyMap<>();
				dataList.stream().map(individualData -> transformDraftToActualWithoutDuplicayCheck(individualData,
						requestBody, retData, entityTypeRefNoToAttributesMapping)).collect(Collectors.toList());
				retData.removeAll(Collections.singleton(null));
				// if no draft estimate was transformed,no need to call connect to save-
				if (Objects.nonNull(retData) && retData.isEmpty())
					return Collections.EMPTY_LIST;
				// check mandatory cost components present,throw error if not--
				checkMandatoryCostComponents(retData, entityTypeRefNoToAttributesMapping);
				// call connect save to save the actual estimates--
				String connectSaveDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
						+ COST_ESTIMATE_OBJECT_UUID;
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("calling connect POST data"));
				try {
					postResult = baseHttpClient.fireHttpRequest(new URI(connectSaveDataUri), HttpMethod.POST, retData,
							headers, Object.class);
					logger.debug(logger.EVENT_SUCCESS,
							ESAPI.encoder()
									.encodeForHTML("_id's in response body of method convertDraftToActualCostTemp:"
											+ costAppUtils.idsInData(postResult.getBody())));
				} catch (HttpClientErrorException hcee) {
					JSONObject error = new JSONObject(hcee.getResponseBodyAsString());
					if (Objects.nonNull(error) && !StringUtils.isEmpty(error.optString("errorMessage"))
							&& (error.getString("errorMessage")
									.contains("Please fill all the required fields appropriately.")
									|| error.getString("errorMessage").contains("is mandatory field"))) {
						List<ConnectError> errors = new ArrayList<>();
						String csvComponentNames = String.join(",",
								retData.stream().filter(data -> Objects.nonNull(data.get("costComponentDisplayName")))
										.map(data -> data.get("costComponentDisplayName").toString())
										.collect(Collectors.toList()));
						logger.error(Logger.EVENT_FAILURE,
								ESAPI.encoder().encodeForHTML("Cost Estimates: " + csvComponentNames
										+ " are not configured correctly. Please configure them and proceed."
										+ "Caught following error from connect while saving estimates: " + error));
						errors.add(new ConnectError(_500,
								"Cost Estimates: " + csvComponentNames
										+ " are not configured correctly. Please configure them and proceed",
								"Cost Estimates: " + csvComponentNames
										+ " are not configured correctly. Please configure them and proceed",
								"{cost component names:" + csvComponentNames + "}"));
						throw new ConnectException(errors, "Cost Estimates: " + csvComponentNames
								+ " are not configured correctly. Please configure them and proceed.");
					} else
						throw hcee;
				}
			}
			return postResult.getBody();

		} catch (ConnectException ce) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling convertDraftToActualCostTemp " + ce.getLocalizedMessage()));
			if (ce.getLocalizedMessage().contains("Mandatory Cost Component Details Missing, please update and proceed")
					|| ce.getLocalizedMessage()
							.contains("are not configured correctly. Please configure them and proceed."))
				throw ce;
			throw new CostAppException(
					"Exception while getting/saving data from/to connect due to :" + ce.getLocalizedMessage(), ce);
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling convertDraftToActualCostTemp "
							+ httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException("Error in getting/saving data from/to connect due to :"
					+ httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling convertDraftToActualCostTemp "
							+ resourceAccessException.getLocalizedMessage()));
			throw new CostAppException("Error in getting/saving data from/to connect due to :"
					+ resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling convertDraftToActualCostTemp " + e.getLocalizedMessage()));
			throw new CostAppException(
					"Error while converting draft cost estimate to actual cost estimate due to :"
							+ e.getLocalizedMessage(),
					e);
		}
	}

	private Map<String, Object> transformDraftIntoActualEstimate(Map<String, Object> individualData,
			Map<String, List<Map<String, Object>>> requestBody, List<Map<String, Object>> retData,
			MultiValueMap<String, Object> existingEntityRefNoMap, MultiKeyMap entityTypeRefNoToAttributesMapping) {
		Iterator<Map<String, Object>> iterator = requestBody.get(individualData.get(DRAFT_ESTIMATE_NO)).iterator();
		while (iterator.hasNext()) {
			Map<String, Object> mappingDetailsObject = iterator.next();
			if (mappingDetailsObject.containsKey(ENTITY_REF_NO)
					&& !mappingDetailsObject.get(ENTITY_REF_NO).toString().isEmpty()) {
				String entityRefNo = mappingDetailsObject.get(ENTITY_REF_NO).toString();
				if ((existingEntityRefNoMap.containsKey(individualData.get(ENTITY_TYPE))
						&& !((List) existingEntityRefNoMap.get(individualData.get(ENTITY_TYPE))).contains(entityRefNo))
						|| (!existingEntityRefNoMap.containsKey(individualData.get(ENTITY_TYPE)))) {
					Map<String, Object> newData = new HashMap<String, Object>();
					newData.putAll(individualData);
					newData.put(ENTITY_REF_NO, entityRefNo);
					if (Objects.nonNull(mappingDetailsObject.get(ENTITY_ACTUAL_NO))
							&& !mappingDetailsObject.get(ENTITY_ACTUAL_NO).toString().isEmpty())
						newData.put(ENTITY_ACTUAL_NO, mappingDetailsObject.get(ENTITY_ACTUAL_NO));
					if (Objects.nonNull(mappingDetailsObject.get(PRODUCT))
							&& !mappingDetailsObject.get(PRODUCT).toString().isEmpty())
						newData.put(PRODUCT, mappingDetailsObject.get(PRODUCT));
					if (Objects.nonNull(mappingDetailsObject.get(ITEM_QTY))
							&& !mappingDetailsObject.get(ITEM_QTY).toString().isEmpty())
						newData.put(ITEM_QTY, mappingDetailsObject.get(ITEM_QTY));
					if (Objects.nonNull(mappingDetailsObject.get(QUALITY))
							&& !mappingDetailsObject.get(QUALITY).toString().isEmpty())
						newData.put(QUALITY, mappingDetailsObject.get(QUALITY));
					if (Objects.nonNull(mappingDetailsObject.get(PROFIT_CENTER))
							&& !mappingDetailsObject.get(PROFIT_CENTER).toString().isEmpty())
						newData.put(PROFIT_CENTER, mappingDetailsObject.get(PROFIT_CENTER));
					if (Objects.nonNull(mappingDetailsObject.get(QUANTITY))
							&& !mappingDetailsObject.get(QUANTITY).toString().isEmpty())
						newData.put(QUANTITY, mappingDetailsObject.get(QUANTITY));
					if (!entityTypeRefNoToAttributesMapping.containsKey(individualData.get(ENTITY_TYPE), entityRefNo)
							&& costAppUtils.checkKeysNotAbsentOrEmptyInData(ATTRIBUTE_NAMES_IN_ESTIMATE,
									individualData)) {
						List<Map<String, Object>> attributes = prepareAttributesInNameValueFormat(individualData);
						entityTypeRefNoToAttributesMapping.put(individualData.get(ENTITY_TYPE), entityRefNo,
								attributes);
					}
					commonService.addDataOptionsWhileSaving(newData);
					retData.add(newData);
				}
			}
		}
		return null;
	}
	
	private Map<String, Object> transformDraftToActualWithoutDuplicayCheck(Map<String, Object> individualData,
			Map<String, List<Map<String, Object>>> requestBody, List<Map<String, Object>> retData,
			MultiKeyMap entityTypeRefNoToAttributesMapping) {
		if (individualData.containsKey("draftEstimateNo")
				&& requestBody.containsKey(individualData.get("draftEstimateNo"))) {
			Iterator<Map<String, Object>> iterator = requestBody.get(individualData.get(DRAFT_ESTIMATE_NO)).iterator();
			while (iterator.hasNext()) {
				Map<String, Object> mappingDetailsObject = iterator.next();
				if (mappingDetailsObject.containsKey(ENTITY_REF_NO)
						&& !mappingDetailsObject.get(ENTITY_REF_NO).toString().isEmpty()) {
					String entityRefNo = mappingDetailsObject.get(ENTITY_REF_NO).toString();
					Map<String, Object> newData = new HashMap<String, Object>();
					newData.putAll(individualData);
					newData.put(ENTITY_REF_NO, entityRefNo);
					if (Objects.nonNull(mappingDetailsObject.get(ENTITY_ACTUAL_NO))
							&& !mappingDetailsObject.get(ENTITY_ACTUAL_NO).toString().isEmpty())
						newData.put(ENTITY_ACTUAL_NO, mappingDetailsObject.get(ENTITY_ACTUAL_NO));
					if (Objects.nonNull(mappingDetailsObject.get(PRODUCT))
							&& !mappingDetailsObject.get(PRODUCT).toString().isEmpty())
						newData.put(PRODUCT, mappingDetailsObject.get(PRODUCT));
					if (Objects.nonNull(mappingDetailsObject.get(ITEM_QTY))
							&& !mappingDetailsObject.get(ITEM_QTY).toString().isEmpty())
						newData.put(ITEM_QTY, mappingDetailsObject.get(ITEM_QTY));
					if (Objects.nonNull(mappingDetailsObject.get(QUALITY))
							&& !mappingDetailsObject.get(QUALITY).toString().isEmpty())
						newData.put(QUALITY, mappingDetailsObject.get(QUALITY));
					if (Objects.nonNull(mappingDetailsObject.get(PROFIT_CENTER))
							&& !mappingDetailsObject.get(PROFIT_CENTER).toString().isEmpty())
						newData.put(PROFIT_CENTER, mappingDetailsObject.get(PROFIT_CENTER));
					if (Objects.nonNull(mappingDetailsObject.get(QUANTITY))
							&& !mappingDetailsObject.get(QUANTITY).toString().isEmpty())
						newData.put(QUANTITY, mappingDetailsObject.get(QUANTITY));
					if (!entityTypeRefNoToAttributesMapping.containsKey(individualData.get(ENTITY_TYPE), entityRefNo)
							&& costAppUtils.checkKeysNotAbsentOrEmptyInData(ATTRIBUTE_NAMES_IN_ESTIMATE,
									individualData)) {
						List<Map<String, Object>> attributes = prepareAttributesInNameValueFormat(individualData);
						entityTypeRefNoToAttributesMapping.put(individualData.get(ENTITY_TYPE), entityRefNo,
								attributes);
					}
					commonService.addDataOptionsWhileSaving(newData);
					retData.add(newData);
				}
			}
		}
		return null;
	}
	
	private List<Map<String, Object>> prepareAttributesInNameValueFormat(Map<String, Object> data) {
		List<Map<String, Object>> attributes = new ArrayList<>();
		for (String attributeName : ATTRIBUTE_NAMES_IN_ESTIMATE) {
			Map<String, Object> nameValuePair = new HashMap<>();
			nameValuePair.put(ATTRIBUTE_NAME, convertAttributeNameToDropDownValue(attributeName));
			if (attributeName.equalsIgnoreCase("contractType")
					&& data.get(attributeName).toString().equalsIgnoreCase("P"))
				nameValuePair.put(ATTRIBUTE_VALUE, "Purchase");
			else if (attributeName.equalsIgnoreCase("contractType")
					&& data.get(attributeName).toString().equalsIgnoreCase("S"))
				nameValuePair.put(ATTRIBUTE_VALUE, "Sales");
			else
				nameValuePair.put(ATTRIBUTE_VALUE, data.get(attributeName));
			attributes.add(nameValuePair);
		}
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
				.encodeForHTML("attributes prepared inside method prepareAttributesInNameValueFormat:" + attributes));
		return attributes;
	}

	private String convertAttributeNameToDropDownValue(String attributeName) {
		switch (attributeName) {
		case "contractType":
			return "Contract Type";
		case "paymentTerm":
			return "Payment Term";
		case "contractIncoTerm":
			return "Contract Incoterm";
		case "valuationIncoTerm":
			return "Valuation Incoterm";
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	private void checkMandatoryCostComponents(List<Map<String, Object>> retData,
			MultiKeyMap entityTypeRefNoToAttributesMapping) {
		MultiKeyMap entityTypeRefNoToTemplateNamesInRetData = new MultiKeyMap();
		retData.stream().filter(estimate -> estimate.containsKey(TEMPLATE_NAME)).forEach(estimate -> {
			Set<String> templateNamesForEntityTypeRefNo = new HashSet<>();
			Object entityType = estimate.get(ENTITY_TYPE);
			Object entityRefNo = estimate.get(ENTITY_REF_NO);
			if (entityTypeRefNoToTemplateNamesInRetData.containsKey(entityType, entityRefNo)) {
				templateNamesForEntityTypeRefNo = (Set<String>) entityTypeRefNoToTemplateNamesInRetData.get(entityType,
						entityRefNo);
				templateNamesForEntityTypeRefNo.add(estimate.get(TEMPLATE_NAME).toString());
			} else {
				templateNamesForEntityTypeRefNo.add(estimate.get(TEMPLATE_NAME).toString());
				entityTypeRefNoToTemplateNamesInRetData.put(entityType, entityRefNo, templateNamesForEntityTypeRefNo);
			}
		});
		Iterator<MultiKey> iterator = entityTypeRefNoToAttributesMapping.keySet().iterator();
		while (iterator.hasNext()) {
			MultiKey keys = iterator.next();
			List<Map<String, Object>> attributes = (List<Map<String, Object>>) entityTypeRefNoToAttributesMapping
					.get(keys.getKey(0), keys.getKey(1));
			Set<String> templateNamesPresent = (Set<String>) entityTypeRefNoToTemplateNamesInRetData.get(keys.getKey(0),
					keys.getKey(1));
			Map<String, Object> payloadToFetchRules = new HashMap<>();
			payloadToFetchRules.put(ATTRIBUTES, attributes);
			List<Map<String, Object>> rules = costService.getRulesMatchingToAttributes(commonService.getHttpHeader(),
					payloadToFetchRules);
			Set<String> mandatoryTemplateNames = new HashSet<>();
			costService.addMandatoryTemplateNamesToSet(rules, mandatoryTemplateNames);
			if (Objects.nonNull(templateNamesPresent) && !templateNamesPresent.containsAll(mandatoryTemplateNames)) {
				List<ConnectError> errors = new ArrayList<>();
				mandatoryTemplateNames.removeAll(templateNamesPresent);
				logger.error(Logger.EVENT_FAILURE,
						ESAPI.encoder().encodeForHTML("Mandatory cost components missing.Missing templateNames are :"
								+ new JSONArray(mandatoryTemplateNames)));
				errors.add(new ConnectError(_500, "Mandatory Cost Component Details Missing, please update and proceed",
						"Mandatory Cost Component Details Missing, please update and proceed",
						"{missing cost component templates:" + new JSONArray(mandatoryTemplateNames) + "}"));
				throw new ConnectException(errors,
						"Mandatory Cost Component Details Missing, please update and proceed");
			}
		}
	}

}
