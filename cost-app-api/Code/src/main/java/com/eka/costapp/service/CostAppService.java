package com.eka.costapp.service;

import static com.eka.costapp.constant.GlobalConstants.*;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.ListValuedMap;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
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
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.eka.costapp.commons.CommonService;
import com.eka.costapp.commons.CostAppUtils;
import com.eka.costapp.constant.GlobalConstants;
import com.eka.costapp.error.ConnectError;
import com.eka.costapp.exception.ConnectException;
import com.eka.costapp.exception.CostAppException;
import com.eka.costapp.factory.RestTemplateGetRequestBodyFactory;
import com.eka.costapp.model.FilterData;
import com.eka.costapp.model.MongoOperations;
import com.eka.costapp.validator.CostValidator;
import com.eka.costapp.webclient.BaseHttpClient;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@Service
public class CostAppService {
	final static Logger logger = ESAPI.getLogger(CostAppService.class);

	@Value("${eka_connect_host}")
	private String ekaConnectHost;

	public static final String COST_APP_UUID = "d33143ac-4164-4a3f-8d30-61d845c9eeed";
	private static final String DRAFT_COST_ESTIMATE_OBJECT_UUID = "00189ca9-cfc1-4327-95ac-f937f22deb60";
	public static final String COST_ESTIMATE_OBJECT_UUID = "f3d6ff89-b541-4dc0-b88d-12065d10cc90";
	private static final String RULES_OBJECT_UUID = "2d3221f6-0717-4f08-b380-25c7094dcd0b";
	private static final String COST_COMPONENT_OBJECT_UUID = "2f787174-8ed0-4d5d-8f93-b38ab0edc05a";
	private static final String PLATFORM_COLLECTION_PATH = "/collectionmapper/";
	private static final String FETCH_COLLECTION_ENDPOINT = "/fetchCollectionRecords";
	private static final String DELETED_DATA_PATH = "/deleted";
	private static final String FETCH_CORPORATE_CURRENCY_PATH = "/mdm/" + COST_APP_UUID + "/corporateInfo";
	public static final String MDM_DATA_PATH = "/mdm/" + COST_APP_UUID + "/data";
	public static final String WORKFLOW_MDM_PATH = "/workflow/mdm";
	private static final String WORKFLOW_EXECUTION_PATH = "/workflow"; 
	private MultiValueMap existingEntityRefNoMap = new LinkedMultiValueMap();

	@Autowired
	RestTemplate restTemplate;

	@Autowired
	RestTemplateGetRequestBodyFactory restTemplateGetWityBody;
	
	@Autowired
	CommonService commonService;
	
	@Autowired
	BaseHttpClient baseHttpClient;
	
	@Autowired
	CostValidator costValidator;

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
	public Object ConvertDraftToActualCost(HttpServletRequest request, Map<String, List<String>> requestBody)
			throws ConnectException, CostAppException {
		HttpHeaders headers = new HttpHeaders();
		ResponseEntity<Object> getResult = null;
		ResponseEntity<Object> postResult = null;
		try {
			String uri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + DRAFT_COST_ESTIMATE_OBJECT_UUID;
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("calling connect GET data"));
			HttpEntity<Object> entity = new HttpEntity<Object>(null, headers);
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("headers :-" + headers));
			getResult = restTemplate.exchange(uri, HttpMethod.GET, entity, Object.class);

			List<Map<String, Object>> presentCostEstimates = fetchCostEstimateDataFromConnectWithoutAnyHandling(null,
					headers, null);
			existingEntityRefNoMap.clear();
			presentCostEstimates.stream().forEach(costEstimate -> {
				Map<String, Object> individualCostEstimate = new HashMap<String, Object>();
				individualCostEstimate.putAll((Map) costEstimate);
				if (individualCostEstimate.containsKey("entityType")
						&& individualCostEstimate.containsKey("entityRefNo"))
					existingEntityRefNoMap.add(individualCostEstimate.get("entityType"),
							individualCostEstimate.get("entityRefNo"));
			});

			if (getResult != null) {
				List<Map<String, Object>> dataList = (List<Map<String, Object>>) getResult.getBody();
				if (dataList.isEmpty())
					return new ArrayList<Object>();

				List<Map<String, Object>> retData = new ArrayList<Map<String, Object>>();
				dataList.stream().map(individualData -> transformData(individualData, requestBody, retData))
						.collect(Collectors.toList());
				retData.removeAll(Collections.singleton(null));
				//if no draft estimate was transformed,no need to call connect to save-
				if (Objects.nonNull(retData) && retData.isEmpty())
					return new ArrayList();

				String connectSaveDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
						+ COST_ESTIMATE_OBJECT_UUID;
				HttpEntity<Object> saveDataEntity = new HttpEntity<Object>(retData, headers);
				postResult = restTemplate.exchange(connectSaveDataUri, HttpMethod.POST, saveDataEntity, Object.class);
			}
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("_id's in response body of method ConvertDraftToActualCost:"
							+ costAppUtils.idsInData(postResult.getBody())));
			return postResult.getBody();

		} catch (ConnectException ce) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling ConvertDraftToActualCost due to:" + ce.getLocalizedMessage()));
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
	 * in connect db to actual cost estimate data
	 * 
	 * @param request
	 * @param requestBody
	 * @return
	 * @throws CostAppException
	 */
	public Object convertDraftToActualCostTemp(HttpServletRequest request, Map<String, List<String>> requestBody)
			throws ConnectException, CostAppException {
		HttpHeaders headers = new HttpHeaders();
		ResponseEntity<Object> getResult = null;
		ResponseEntity<Object> postResult = null;
		try {
			// get list of all draft estimates--
			String uri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + DRAFT_COST_ESTIMATE_OBJECT_UUID;
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			// create filter to fetch draftEstimates--
			Map<String, Object> payloadToFetchDraftEstimates = new HashMap<>();
			Set<String> draftEstimateNoSet = requestBody.keySet();
			MongoOperations filterOnDraftEstimateNo = new MongoOperations(DRAFT_ESTIMATE_NO, draftEstimateNoSet, "in");
			List<MongoOperations> filter = new ArrayList<MongoOperations>();
			filter.add(filterOnDraftEstimateNo);
			FilterData filterData = new FilterData(filter);
			payloadToFetchDraftEstimates.put("filterData", filterData);

			logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"calling connect GET data with payload" + new JSONObject(payloadToFetchDraftEstimates)));
			HttpEntity<Object> entity = new HttpEntity<Object>(payloadToFetchDraftEstimates, headers);
			getResult = restTemplateGetWityBody.getRestTemplate().exchange(uri, HttpMethod.GET, entity, Object.class);

			// fetch actual estimates of entityrefs present in payload to get their _ids and
			// delete them one by one--
			String getActualEstimateUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID;
			Map<String, Object> payloadToFetchActualEstimates = new HashMap<>();
			Set<String> entityRefNoSet = new HashSet<>();
			requestBody.entrySet().stream().map(Map.Entry::getValue)
					.forEach(entityRefNo -> entityRefNoSet.addAll(entityRefNo));
			MongoOperations filterOnActualEstimateNo = new MongoOperations(ENTITY_REF_NO, entityRefNoSet, "in");
			List<MongoOperations> filterOnEntityRefNo = new ArrayList<MongoOperations>();
			filterOnEntityRefNo.add(filterOnActualEstimateNo);
			FilterData filterActualEstimateData = new FilterData(filterOnEntityRefNo);
			payloadToFetchActualEstimates.put("filterData", filterActualEstimateData);
			ResponseEntity<Object> getActualEstimatesResult = null;
			HttpEntity<Object> entityToFetchActualEstimates = new HttpEntity<Object>(payloadToFetchActualEstimates,
					headers);
			logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"calling connect GET data with payload" + new JSONObject(payloadToFetchActualEstimates)));
			getActualEstimatesResult = restTemplateGetWityBody.getRestTemplate().exchange(getActualEstimateUri,
					HttpMethod.GET, entityToFetchActualEstimates, Object.class);
			if (Objects.nonNull(getActualEstimatesResult)) {
				List<Map<String, Object>> actualEstimates = (List<Map<String, Object>>) getActualEstimatesResult
						.getBody();
				if (!CollectionUtils.isEmpty(actualEstimates)) {
					// prepare list of _ids--
					logger.debug(Logger.EVENT_SUCCESS,
							ESAPI.encoder().encodeForHTML("no of actual estimates fetched:" + actualEstimates.size()));
					Set<String> _idSet = new HashSet<>();
					_idSet.addAll(actualEstimates.stream().map(actualEstimate -> actualEstimate.get(_ID).toString())
							.collect(Collectors.toSet()));
					// call delete one by one--
					String deleteDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID;
					Iterator<String> idIterator = _idSet.iterator();
					while (idIterator.hasNext()) {
						String deleteUri = deleteDataUri + "/" + idIterator.next();
						baseHttpClient.fireHttpRequest(new URI(deleteUri), HttpMethod.DELETE, null, headers, Map.class);
					}
				}
			}

			if (getResult != null) {
				List<Map<String, Object>> dataList = (List<Map<String, Object>>) getResult.getBody();
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("no of draft estimates fetched:" + dataList.size()));
				// if there is no draft estimate in db of any type, return empty as
				// transformation cannot be performed--
				if (dataList.isEmpty())
					return new ArrayList<Object>();

				// transform draft data into actual by streaming on all draft data , and
				// checking if requestBody has entry for transforming that draft data,as well as
				// checking no actual exists for that "to be transformed into" entityrefno--
				List<Map<String, Object>> retData = new ArrayList<Map<String, Object>>();
				dataList.stream().map(individualData -> transformDraftToActualWithoutDuplicayCheck(individualData,
						requestBody, retData)).collect(Collectors.toList());
				retData.removeAll(Collections.singleton(null));
				// if no draft estimate was transformed,no need to call connect to save-
				if (Objects.nonNull(retData) && retData.isEmpty())
					return new ArrayList();

				// call connect delete API to delete all the actual estimates which were present
				// beforehand--

				// call connect save to save the actual estimates--
				String connectSaveDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID;
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("calling connect POST data"));
				HttpEntity<Object> saveDataEntity = new HttpEntity<Object>(retData, headers);
				postResult = restTemplate.exchange(connectSaveDataUri, HttpMethod.POST, saveDataEntity, Object.class);
			}
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("_id's in response body of method convertDraftToActualCostTemp:"
							+ costAppUtils.idsInData(postResult.getBody())));
			return postResult.getBody();

		} catch (ConnectException ce) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling ConvertDraftToActualCost " + ce.getLocalizedMessage()));
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
	
	private Map<String, Object> transformDraftToActualWithoutDuplicayCheck(Map<String, Object> individualData,
			Map<String, List<String>> requestBody, List<Map<String, Object>> retData) {
		if (individualData.containsKey("draftEstimateNo")
				&& requestBody.containsKey(individualData.get("draftEstimateNo"))) {
			Iterator<String> transformerIterator = requestBody.get(individualData.get("draftEstimateNo")).iterator();
			while (transformerIterator.hasNext()) {
				String entityRefNo = transformerIterator.next();
				Map<String, Object> newData = new HashMap<String, Object>();
				newData.putAll(individualData);
				newData.put("entityRefNo", entityRefNo);
				commonService.addDataOptionsWhileSaving(newData);
				retData.add(newData);
			}
		}
		return null;
	}
	
	/**
	 * Usage:The <i>ConvertDraftToActualCost</i> converts actual cost estimate data
	 * in connect db to draft cost estimate data based on the mapping passed
	 * 
	 * @param request
	 * @param requestBody
	 * @return
	 * @throws CostAppException
	 */
	public Object ConvertActualToDraftCost(HttpServletRequest request, Map<String, List<String>> requestBody)
			throws ConnectException, CostAppException {
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
				.encodeForHTML("inside method ConvertActualToDraftCost"));
		HttpHeaders headers = new HttpHeaders();
		ResponseEntity<Object> getResult = null;
		ResponseEntity<Object> postResult = null;
		if (Objects.isNull(requestBody))
			return Collections.EMPTY_LIST;
		try {
			// fetch connect data with given entityRefNos--
			String getCostEstimatesDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
					+ COST_ESTIMATE_OBJECT_UUID;
			headers = commonService.getHttpHeader(request);
			Map<String, Object> payloadToFetchActualEstimates = new HashMap<>();
			Set<String> entityRefNoSet = requestBody.keySet();
			MongoOperations filterOnEntityRefNo = new MongoOperations(ENTITY_REF_NO, entityRefNoSet, "in");
			List<MongoOperations> filter = new ArrayList<MongoOperations>();
			filter.add(filterOnEntityRefNo);
			FilterData filterData = new FilterData(filter);
			payloadToFetchActualEstimates.put("filterData", filterData);
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"calling connect GET data with payload:" + new JSONObject(payloadToFetchActualEstimates)));
			HttpEntity<Object> entity = new HttpEntity<Object>(payloadToFetchActualEstimates, headers);
			getResult = restTemplateGetWityBody.getRestTemplate().exchange(getCostEstimatesDataUri, HttpMethod.GET,
					entity, Object.class);
			// copy the data and change to draft--
			if (getResult.getStatusCode().is2xxSuccessful() && Objects.nonNull(getResult.getBody())) {
				List<Map<String, Object>> costEstimatesList = (List<Map<String, Object>>) getResult.getBody();
				List<Map<String, Object>> payloadToSaveDrafts = new ArrayList<>();
				costEstimatesList.stream().filter(costEstimate -> costEstimate.containsKey(ENTITY_REF_NO))
						.forEach(costEstimate -> {
							removeRedundantFieldsBeforeSaving(costEstimate);
							Iterator<String> entityRefNoIterator = requestBody.get(costEstimate.get(ENTITY_REF_NO))
									.iterator();
							while (entityRefNoIterator.hasNext()) {
								String draftEstimateNo = entityRefNoIterator.next();
								costEstimate.put(DRAFT_ESTIMATE_NO, draftEstimateNo);
								costEstimate.remove(ENTITY_REF_NO);
								commonService.addDataOptionsWhileSaving(costEstimate);
								payloadToSaveDrafts.add(costEstimate);
							}
						});
				costEstimatesList = null;
				// call connect save--
				String connectSaveDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
						+ DRAFT_COST_ESTIMATE_OBJECT_UUID;
				logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("calling connect POST data"));
				HttpEntity<Object> saveDataEntity = new HttpEntity<Object>(payloadToSaveDrafts, headers);
				postResult = restTemplate.exchange(connectSaveDataUri, HttpMethod.POST, saveDataEntity, Object.class);
				logger.debug(logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("_id's in response body of method ConvertActualToDraftCost:"
								+ costAppUtils.idsInData(postResult.getBody())));
				logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("no. of actual cost converted to draft:" + payloadToSaveDrafts.size()));
				logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("ConvertActualToDraftCost method ends"));
				return postResult.getBody();
			}
		}
		catch (ConnectException ce) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"ConnectException inside method ConvertActualToDraftCost " + ce.getLocalizedMessage()));
			throw ce;
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("HttpClientErrorException inside method ConvertActualToDraftCost "
							+ httpClientErrorException.getLocalizedMessage()));
			throw new ConnectException("Error while converting actual to draft estimate due to :"
					+ httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("ResourceAccessException inside method ConvertActualToDraftCost "
							+ resourceAccessException.getLocalizedMessage()));
			throw new ConnectException("Error while converting actual to draft estimate due to :"
					+ resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception inside method ConvertActualToDraftCost " + e.getLocalizedMessage()));
			throw new ConnectException(
					"Error while converting actual to draft estimate due to :" + e.getLocalizedMessage(), e);
		}
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("no actual cost converted to draft"));
		return Collections.EMPTY_LIST;
	}
	
	/**
	 * Usage:The <i>cloneDraftWithNewEstimateNo</i> gets all draft estimates of
	 * passed draftEstimateNo and saves them into connect db with the
	 * draftEstimateNo passed in mapping
	 * 
	 * @param request
	 * @param requestBody
	 * @return
	 * @throws CostAppException
	 */
	public Object cloneDraftWithNewEstimateNo(HttpServletRequest request, Map<String, List<String>> requestBody)
			throws ConnectException, CostAppException {
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("inside method cloneDraftWithNewEstimateNo"));
		HttpHeaders headers = new HttpHeaders();
		ResponseEntity<Object> getResult = null;
		ResponseEntity<Object> postResult = null;
		if (CollectionUtils.isEmpty(requestBody))
			return Collections.EMPTY_LIST;
		try {
			// fetch connect data with given draftEstimateNos--
			String getCostEstimatesDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
					+ DRAFT_COST_ESTIMATE_OBJECT_UUID;
			headers = commonService.getHttpHeader(request);
			Map<String, Object> payloadToFetchDraftEstimates = new HashMap<>();
			Set<String> draftEstimateNoSet = requestBody.keySet();
			List<MongoOperations> filter = new ArrayList<MongoOperations>();
			filter.add(new MongoOperations(DRAFT_ESTIMATE_NO, draftEstimateNoSet, "in"));
			FilterData filterData = new FilterData(filter);
			payloadToFetchDraftEstimates.put("filterData", filterData);
			logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"calling connect GET data with payload:" + new JSONObject(payloadToFetchDraftEstimates)));
			HttpEntity<Object> entity = new HttpEntity<Object>(payloadToFetchDraftEstimates, headers);
			getResult = restTemplateGetWityBody.getRestTemplate().exchange(getCostEstimatesDataUri, HttpMethod.GET,
					entity, Object.class);
			// change the draftEstimateNo in fetched data--
			if (Objects.nonNull(getResult) && Objects.nonNull(getResult.getBody())
					&& (getResult.getBody() instanceof List) && !((List) getResult.getBody()).isEmpty()) {
				List<Map<String, Object>> draftEstimates = (List<Map<String, Object>>) getResult.getBody();
				List<Map<String, Object>> payloadToSaveDrafts = new ArrayList<>();
				Gson gson = new Gson();
				Set<Object> idsToDelete = new HashSet<>();
				draftEstimates.stream()
						.filter(draftEstimate -> draftEstimate.containsKey(DRAFT_ESTIMATE_NO)
								&& !draftEstimate.get(DRAFT_ESTIMATE_NO).toString().isEmpty())
						.forEach(draftEstimate -> {
							idsToDelete.add(draftEstimate.get(_ID));
							removeRedundantFieldsBeforeSaving(draftEstimate);
							List<String> newDraftEstimateNos = requestBody.get(draftEstimate.get(DRAFT_ESTIMATE_NO));
							String draftEstimateJsonString = null;
							if (newDraftEstimateNos.size() > 1)
								draftEstimateJsonString = gson.toJson(draftEstimate);

							Iterator<String> draftEstimateNoIterator = newDraftEstimateNos.iterator();
							while (draftEstimateNoIterator.hasNext()) {
								String newDraftEstimateNo = draftEstimateNoIterator.next();
								if (Objects.nonNull(draftEstimateJsonString))
									draftEstimate = gson.fromJson(draftEstimateJsonString,
											new TypeToken<Map<String, Object>>() {
											}.getType());

								draftEstimate.put(DRAFT_ESTIMATE_NO, newDraftEstimateNo);
								draftEstimate.remove(ENTITY_REF_NO);
								commonService.addDataOptionsWhileSaving(draftEstimate);
								payloadToSaveDrafts.add(draftEstimate);
							}
						});
				draftEstimates = null;
				// delete the old drafts --
				if (!idsToDelete.isEmpty()) {
					String bulkDeleteApi = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
							+ DRAFT_COST_ESTIMATE_OBJECT_UUID + "/bulkDelete";
					Map<String, Object> payloadToBulkDelete = new HashMap<>();
					List<MongoOperations> deleteFliter = new ArrayList<MongoOperations>();
					deleteFliter.add(new MongoOperations(_ID, idsToDelete, "in"));
					FilterData deleteFilterData = new FilterData(deleteFliter);
					payloadToBulkDelete.put("filterData", deleteFilterData);
					logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
							"calling connect bulkDelete data with payload:" + new JSONObject(payloadToBulkDelete)));
					ResponseEntity<List> bulkDeleteResponse = baseHttpClient.fireHttpRequest(new URI(bulkDeleteApi),
							HttpMethod.DELETE, payloadToBulkDelete, headers, List.class);
					logger.debug(Logger.EVENT_SUCCESS,
							ESAPI.encoder()
									.encodeForHTML("response of bulk delete draft estimates:"
											+ bulkDeleteResponse.getBody() + "\n" + ", no of drafts deleted:"
											+ bulkDeleteResponse.getBody().size()));
				}
				// call connect save--
				if (!payloadToSaveDrafts.isEmpty()) {
					String connectSaveDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
							+ DRAFT_COST_ESTIMATE_OBJECT_UUID;
					logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
							.encodeForHTML("calling connect POST data with payload:" + payloadToSaveDrafts));
					postResult = baseHttpClient.fireHttpRequest(new URI(connectSaveDataUri), HttpMethod.POST,
							payloadToSaveDrafts, headers, Object.class);
					if (Objects.nonNull(postResult) && Objects.nonNull(postResult.getBody())
							&& postResult.getBody() instanceof List)
						logger.debug(Logger.EVENT_SUCCESS,
								ESAPI.encoder().encodeForHTML("no. of draft estimates cloned to new draftEstimateNo:"
										+ ((List) postResult.getBody()).size()));
					logger.debug(Logger.EVENT_SUCCESS,
							ESAPI.encoder().encodeForHTML("cloneDraftWithNewEstimateNo method ends"));
					return postResult.getBody();
				}
			}
		} catch (ConnectException ce) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"ConnectException inside method cloneDraftWithNewEstimateNo due to:" + ce.getLocalizedMessage()));
			throw ce;
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder()
							.encodeForHTML("HttpClientErrorException inside method ConvertActualToDraftCost due to:"
									+ httpClientErrorException.getLocalizedMessage()));
			throw new ConnectException("Error while cloning draft estimate with new draftEstimateNo due to :"
					+ httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder()
							.encodeForHTML("ResourceAccessException inside method cloneDraftWithNewEstimateNo due to"
									+ resourceAccessException.getLocalizedMessage()));
			throw new ConnectException("Error while cloning draft estimate with new draftEstimateNo due to :"
					+ resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception inside method cloneDraftWithNewEstimateNo due to:" + e.getLocalizedMessage()));
			throw new ConnectException(
					"Error while cloning draft estimate with new draftEstimateNo due to :" + e.getLocalizedMessage(),
					e);
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("no draft estimate cloned to new draftEstimateNo"));
		return Collections.EMPTY_LIST;
	}

	private Map<String, Object> removeRedundantFieldsBeforeSaving(Map<String, Object> data) {
		data.remove("userId");
		data.remove("sys__createdOn");
		data.remove("sys__createdBy");
		data.remove("sys__data__state");
		data.remove("_id");
		return data;
	}

	/**
	 * Usage:The <i>getCostEstimateData</i> gets cost estimate data from connect db
	 * 
	 * @param request
	 * @param queryParams
	 * @return
	 * @throws CostAppException
	 */
	public Object getCostEstimateData(Map<String, Object> requestBody, HttpServletRequest request)
			throws ConnectException, CostAppException {
		HttpHeaders headers = new HttpHeaders();
		try {
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			// fetch connect data--
			Map<String, String> entityRefNoToApplicableDateMap = new HashMap<String, String>();
			Map<String, Object> requestBodyToCallConnectGetData = createPayloadToFetchConnectDataWithFilters(
					requestBody, entityRefNoToApplicableDateMap);
			//adding feature to not fetch deleted estimates for copying to PBS--
			String getDeletedData = (!CollectionUtils.isEmpty(requestBody)) && requestBody.containsKey(GET_DELETED_DATA)
					&& requestBody.get(GET_DELETED_DATA).toString().equalsIgnoreCase(PARAM_N) ? PARAM_N : null;
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
					.encodeForHTML("getDeletedData attribute sent in getAllEstimates API= " + getDeletedData));
			List<Map<String, Object>> dataList = fetchCostEstimateDataFromConnectWithoutAnyHandling(
					requestBodyToCallConnectGetData, headers, getDeletedData);

			// make list of cost curves and month-years --
			Set<String> costCurveList = new HashSet<String>();
			Set<String> monthYearList = new HashSet<String>();
			prepareListOfInstrumentNamesForCost(dataList, costCurveList, monthYearList);

			// call collections and prepare mapping only when costCurveList and
			// monthYearList are not empty--
			MultiKeyMap instrumentAndMonthYearMap = new MultiKeyMap();
			MultiKeyMap instrumentAndMonthYearToPriceUnitMap = new MultiKeyMap();
			Map<String,List<Map<String,Object>>> productIdToServiceKeyValuesMap = new HashMap<>();
			if (!costCurveList.isEmpty() && !monthYearList.isEmpty()) {
				// call fetch collections API with costcurveList and costMonthList--
				List<Map<String, Object>> collectionList = (List<Map<String, Object>>) fetchCollectionForCost(headers,
						costCurveList, monthYearList).getBody();

				// prepare mapping from curve,month to -> costvalue
				logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("no. of data fetched from platform collection= " + collectionList.size()));
				prepareMappingFromCostCurveAndMonthYearAndPricingDateToSettlePrice(collectionList,
						instrumentAndMonthYearMap);
				//prepare mapping from curve,month to priceUnit--
				prepareMappingFromCurveAndMonthToPriceUnit(collectionList, instrumentAndMonthYearToPriceUnitMap);
			}

			// replace cost amount and costPriceUnitId in data--
			List<Map<String, Object>> dataListWithReplacedCostAmounts = new ArrayList<Map<String, Object>>();
			Map<String,List<Map<String,Object>>> productIdToWeightUnitMap = new HashMap<>();
			MultiKeyMap productIdAndFromUnitAndToUnitToConversionMap = new MultiKeyMap<>();
			List<Map<String,Object>> currencylistMap = new ArrayList<>();
			List<Map<String, Object>> estimatesToUpdateInDb = new ArrayList<>();
			dataListWithReplacedCostAmounts.addAll(dataList.stream().map((Map<String, Object> individualData) -> {
				if (individualData.containsKey("rateTypePrice")
						&& individualData.get("rateTypePrice").equals("curve")) {
					Object instrumentName = individualData.get("costCurve");
					Object monthYear = individualData.get("costMonth");
					String entityRefNo = individualData.get("entityRefNo").toString();
					String applicableDate = entityRefNoToApplicableDateMap.get(entityRefNo);
					Object applicableCostAmount = findApplicableCostAmount(instrumentName, monthYear, applicableDate,
							instrumentAndMonthYearMap, 30);
					//do cost value/ cost amount calculation--
					List<Object> calculationParams = new LinkedList<>();
					calculationParams.add(applicableCostAmount);
					calculationParams.add(instrumentName);
					calculationParams.add(monthYear);
					Object calculatedCost = calculateCostAmount(calculationParams, individualData,
							instrumentAndMonthYearToPriceUnitMap, request, productIdToWeightUnitMap,
							productIdAndFromUnitAndToUnitToConversionMap, currencylistMap);
					individualData.put("costAmountInBaseCurrency",
							Objects.nonNull(calculatedCost) ? calculatedCost : 0);
					individualData.put("costValue", Objects.nonNull(calculatedCost) ? applicableCostAmount : 0);
					addCostPriceunitIdInData(individualData, productIdToServiceKeyValuesMap,
							instrumentAndMonthYearToPriceUnitMap, instrumentName, monthYear, headers);
					if (Objects.nonNull(calculatedCost)
							&& !individualData.get(SYS_DATA_STATE).toString().equalsIgnoreCase(STATE_DELETE))
						estimatesToUpdateInDb.add(individualData);
				}
				return individualData;
			}).collect(Collectors.toList()));
			dataList = null;

			// fetch corpCurrency from connect--
			String corpCurrency = fetchCorporateCurrency(request);

			// prepare list of fx instrument names and valueDates from connect data--
			Set<String> fxCurvesList = new HashSet<String>();
			Set<String> fxValueDatesList = new HashSet<String>();
			listInstrumentNamesFxAndValueDatesAndHandlePBSEstimateFor(dataListWithReplacedCostAmounts, fxCurvesList, fxValueDatesList, corpCurrency);

			// call collections and prepare mapping only when fxCurvesList and
			// fxValueDatesList are not empty--
			MultiKeyMap fxInstrumentAndValueDateAndPeriodEndDateMap = new MultiKeyMap();
			Set<String> fxInstrumentSetInMultiKeyMap = new HashSet<String>();
			if (!fxCurvesList.isEmpty() && !fxValueDatesList.isEmpty()) {
				// call fetch collections API with fxCurveList--
				List<Map<String, Object>> collectionDataList = (List<Map<String, Object>>) fetchCollectionForFx(headers,
						fxCurvesList, fxValueDatesList).getBody();
				// prepare mapping from fxCurve,valueDate,end date to -> exchange rate --
				logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("no. of data fetched from platform collection= " + collectionDataList.size()));
				prepareMappingFromFxCurveAndValueDateAndEndDateToExchangeRate(collectionDataList,
						fxInstrumentAndValueDateAndPeriodEndDateMap, fxInstrumentSetInMultiKeyMap, corpCurrency);
			}
			// replace fx amount in data--
			List<Map<String,Object>> dataListWithHandledFx = new ArrayList<>();
			dataListWithHandledFx.addAll(dataListWithReplacedCostAmounts.stream().map(individualData -> {
				String entityRefNo = individualData.get("entityRefNo").toString();
				String applicableDate = entityRefNoToApplicableDateMap.get(entityRefNo);
				return replaceFxAmountInConnectData(individualData, fxInstrumentAndValueDateAndPeriodEndDateMap,
						fxInstrumentSetInMultiKeyMap, corpCurrency, applicableDate, estimatesToUpdateInDb);
			}).collect(Collectors.toList()));
			dataListWithReplacedCostAmounts = null;
			//handle data with rateTypePrice=%ofPrice (replace costPriceUnit,costPriceUnitIdDisplayName and costValue)--
			List<Map<String, Object>> returnDataList = new ArrayList<>();
			returnDataList.addAll(dataListWithHandledFx.stream().map(data -> {
				if (costAppUtils.checkKeysNotAbsentOrEmptyInData(
						Arrays.asList(
								new String[] { RATE_TYPE_PRICE, CONTRACT_PRICE, COST_VALUE, ITEM_QTY, PRODUCT_ID }),
						data) && data.get(RATE_TYPE_PRICE).toString().equalsIgnoreCase(PERCENTAGE_OF_PRICE))
					handlePercentageOfPriceTypeData(data, productIdToServiceKeyValuesMap, headers);
				return data;
			}).collect(Collectors.toList()));
			dataListWithHandledFx = null;
			//check and throw error if cost value,cost amount ,costPriceUnitId not present in data or equal to 0--
			List<ConnectError> errors = new ArrayList<>();
			List<ConnectError> internalErrors = new ArrayList<>();
			returnDataList.stream().forEach(data -> {
				checkMandatoryFields(data, errors, internalErrors);
			});
			if(!CollectionUtils.isEmpty(errors)) {
				logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
						"Mandatory fields missing, please update and proceed." + new JSONArray(internalErrors)));
				throw new ConnectException(errors, "Mandatory fields missing, please update and proceed.");
			}
			// call bulk update API to update modified cost curve estimates--
			if (!estimatesToUpdateInDb.isEmpty()) {
				Gson gson = new Gson();
				String clonedJson = gson.toJson(estimatesToUpdateInDb);
				List<Map<String, Object>> clonedList = gson.fromJson(clonedJson,
						new TypeToken<List<Map<String, Object>>>() {
						}.getType());
				updateCalculatedCostCurvesInDb(clonedList, headers);
			}
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"_id's in response body of method getCostEstimateData:" + costAppUtils.idsInData(returnDataList)));

			return ResponseEntity.ok(returnDataList);
		} catch (CostAppException use) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("CostAppException while calling getCostEstimateData " + use.getLocalizedMessage()));
			throw new ConnectException(
					"Exception while getting cost estimates due to : " + use.getLocalizedMessage(), use);
		} catch (ConnectException ce) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling getCostEstimateData " + ce.getLocalizedMessage()));
			if (Objects.nonNull(ce.getErrors()) && !ce.getErrors().isEmpty()) {
				//for throwing errors : which cost components do not contain cost value and cost amount 
				throw ce;
			}
			throw new CostAppException(
					"Exception while getting data from connect due to: " + ce.getLocalizedMessage(), ce);
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while calling getCostEstimateData " + httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in getting data from connect due to :" + httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while calling getCostEstimateData " + resourceAccessException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in getting data from connect due to :" + resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling getCostEstimateData " + e.getLocalizedMessage()));
			throw new CostAppException(
					"Error while getting cost estimate data due to: " + e.getLocalizedMessage(), e);
		}
	}
	
	/**
	 * Usage:The <i>getInstrumentNames</i> gets unique instrument names from
	 * platform collection
	 * 
	 * @param request
	 * @param refTypeId     the appUUID
	 * @param object        UUID the object UUID
	 * @param costComponent the costComponent name passed in query param
	 * @return
	 * @throws CostAppException
	 */
	public Object getInstrumentNames(HttpServletRequest request, String refTypeId, String objectUUID,
			String costComponent) {
		HttpHeaders headers = new HttpHeaders();
		String fetchCollectionUri = ekaConnectHost + PLATFORM_COLLECTION_PATH + refTypeId + "/" + objectUUID
				+ FETCH_COLLECTION_ENDPOINT;
		ListValuedMap instrumentNamesAndMonths = new ArrayListValuedHashMap();
		try {
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			headers.add("ttl", "300");
			Set<String> allowedInstrumentNames = new HashSet<>();
			if (!StringUtils.isEmpty(costComponent)) {
				// call fetch templates--
				List<Map<String, String>> templateMappingList = fetchTemplateMapping(headers, refTypeId, objectUUID);
				if (!CollectionUtils.isEmpty(templateMappingList)) {
					allowedInstrumentNames.addAll(templateMappingList.stream()
							.filter(data -> data.containsKey(COST_COMPONENT_NAME)
									&& data.get(COST_COMPONENT_NAME).equalsIgnoreCase(costComponent))
							.map(individualData -> individualData.get(INSTRUMENT_NAME)).collect(Collectors.toSet()));
				}
			}
			Map<String, Object> payload = new HashMap<String, Object>();
			payload.put(COLLECTION_NAME, COLLECTION_DS_MARKET_PRICE);
			HttpEntity<Object> fetchCollectionEntity = new HttpEntity<Object>(payload, headers);
			logger.debug(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("calling fetch collections API with headers :-" + headers));
			ResponseEntity<Object> fetchCollectionResponse = restTemplate.exchange(fetchCollectionUri, HttpMethod.POST,
					fetchCollectionEntity, Object.class);
			List<Map<String, Object>> collectionList = (List<Map<String, Object>>) fetchCollectionResponse.getBody();
			logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
					.encodeForHTML("no. of data fetched from platform collection= " + collectionList.size()));
			if (Objects.nonNull(collectionList) && !collectionList.isEmpty()) {
				Iterator<Map<String, Object>> collectionListIterator = collectionList.iterator();
				while (collectionListIterator.hasNext()) {
					Map<String, Object> individualCollectionData = collectionListIterator.next();
					Object instrumentName = individualCollectionData.get(INSTRUMENT_NAME);
					if (StringUtils.isEmpty(costComponent) || allowedInstrumentNames.contains(instrumentName))
						instrumentNamesAndMonths.put(instrumentName, individualCollectionData);
				}
			}
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while calling getInstrumentNames " + httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException("Error in fetching platform collections");
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while calling getInstrumentNames " + resourceAccessException.getLocalizedMessage()));
			throw new CostAppException("Error in fetching platform collections");
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling getInstrumentNames " + e.getLocalizedMessage()));
			throw new CostAppException("Error in fetching platform collections", e);
		}
		return instrumentNamesAndMonths.asMap();
	}
	
	public List<Map<String, String>> fetchTemplateMapping(HttpHeaders headers, String refTypeId, String objectUUID)
			throws URISyntaxException {
		String fetchCollectionUri = ekaConnectHost + PLATFORM_COLLECTION_PATH + refTypeId + "/" + objectUUID
				+ FETCH_COLLECTION_ENDPOINT;
		Map<String, Object> payloadToFetchTemplate = new HashMap<String, Object>();
		payloadToFetchTemplate.put(TEMPLATE_NAME, TEMPLATE_COST_COMPONENT_TO_INS_MAPPING);
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("calling fetch template collections API with headers :-" + headers
						+ ", payload:" + payloadToFetchTemplate));
		ResponseEntity<Object> fetchTemplateResponse = baseHttpClient.fireHttpRequest(new URI(fetchCollectionUri),
				HttpMethod.POST, payloadToFetchTemplate, headers, Object.class);
		List<Map<String, String>> templateMappingList = (List<Map<String, String>>) fetchTemplateResponse.getBody();
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
				.encodeForHTML("no. of data fetched from platform collection= " + templateMappingList.size()));
		return templateMappingList;
	}

	public Object getInstrumentNamesFx(HttpServletRequest request, String refTypeId, String objectUUID) {
		HttpHeaders headers = new HttpHeaders();
		String fetchCollectionUri = ekaConnectHost + PLATFORM_COLLECTION_PATH + refTypeId + "/" + objectUUID
				+ FETCH_COLLECTION_ENDPOINT;
		ListValuedMap instrumentNamesAndMonthsFx = new ArrayListValuedHashMap();
		try {
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			headers.add("ttl", "300");
			Map<String, Object> payload = new HashMap<String, Object>();
			payload.put("collectionName", "DS-Market Fx Rate");
			payload.put("skip", 0);
			payload.put("limit", 200);
			HttpEntity<Object> fetchCollectionEntity = new HttpEntity<Object>(payload, headers);
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("calling fetch collections API with headers :-" + headers));
			ResponseEntity<Object> fetchCollectionResponse = restTemplate.exchange(fetchCollectionUri, HttpMethod.POST,
					fetchCollectionEntity, Object.class);
			List<Map<String, Object>> collectionList = (List<Map<String, Object>>) fetchCollectionResponse.getBody();
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
					.encodeForHTML("no. of data fetched from platform collection= " + collectionList.size()));
			if (Objects.nonNull(collectionList) && !collectionList.isEmpty()) {
				Iterator<Map<String, Object>> collectionListIterator = collectionList.iterator();
				while (collectionListIterator.hasNext()) {
					Map<String, Object> individualCollectionData = collectionListIterator.next();
					Object instrumentName = individualCollectionData.get("Instrument Name");
					instrumentNamesAndMonthsFx.put(instrumentName, individualCollectionData);
				}
			}
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while calling getInstrumentNamesFx " + httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException("Error in fetching platform collections");
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while calling getInstrumentNamesFx " + resourceAccessException.getLocalizedMessage()));
			throw new CostAppException("Error in fetching platform collections");
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling getInstrumentNamesFx " + e.getLocalizedMessage()));
			throw new CostAppException("Error in fetching platform collections", e);
		}
		return instrumentNamesAndMonthsFx.asMap();
	}
	
	/**
	 * Usage:The <i>getCostComponents</i> gets cost components mandatory to a
	 * contract
	 * 
	 * @param request
	 * @param requestBody the request body containing contract details
	 * @param costComponentTemplates 
	 * @return
	 * @throws CostAppException
	 */
	public Object getCostComponents(HttpServletRequest request, Map<String, Object> requestBody, Set<String> costComponentTemplates) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			if (requestBody.containsKey(ATTRIBUTES) && requestBody.get(ATTRIBUTES) instanceof List
					&& !CollectionUtils.isEmpty((Collection<?>) requestBody.get(ATTRIBUTES))) {
				List<Map<String, Object>> rules = getRulesMatchingToAttributes(headers, requestBody);
				addMandatoryTemplateNamesToSet(rules, costComponentTemplates);
				List<Map<String, Object>> costComponents = new ArrayList<>();
				if (!(rules.isEmpty() || costComponentTemplates.isEmpty())) {
					// create payload to fetch cost components--
					Map<String, Object> payloadToFetchCostComponents = new HashMap<>();
					List<MongoOperations> filterOnComponents = new ArrayList<MongoOperations>();
					filterOnComponents.add(new MongoOperations(TEMPLATE_NAME, costComponentTemplates, "in"));
					FilterData filterDataOnComponents = new FilterData(filterOnComponents);
					payloadToFetchCostComponents.put("filterData", filterDataOnComponents);
					String costComponentsDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
							+ COST_COMPONENT_OBJECT_UUID;
					// call connect to fetch cost components--
					logger.debug(Logger.EVENT_SUCCESS,
							ESAPI.encoder().encodeForHTML("calling connect GET cost components with payload:"
									+ new JSONObject(payloadToFetchCostComponents)));
					HttpEntity<Object> entityToFetchCostComponents = new HttpEntity<Object>(
							payloadToFetchCostComponents, headers);
					ResponseEntity<List> costComponentsResponse = restTemplateGetWityBody.getRestTemplate()
							.exchange(costComponentsDataUri, HttpMethod.GET, entityToFetchCostComponents, List.class);
					logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
							"obtained connect GET cost components response:" + costComponentsResponse.getBody()));
					if (Objects.nonNull(costComponentsResponse) && Objects.nonNull(costComponentsResponse.getBody())
							&& costComponentsResponse.getBody() instanceof List) {
						costComponents = costComponentsResponse.getBody();
						logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
								"obtained connect GET cost components response size:" + costComponents.size()));
					}
				}
				return costComponents;
			}
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling getCostComponents due to:"
							+ httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in fetching cost components due to:" + httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling getCostComponents due  to:"
							+ resourceAccessException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in fetching cost components due to:" + resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling getCostComponents due to" + e.getLocalizedMessage()));
			throw new CostAppException("Error in fetching cost components due to:" + e.getLocalizedMessage(), e);
		}
		return Collections.EMPTY_LIST;
	}
	
	/**
	 * Usage:The <i>validateCostEstimates</i> validates the cost estimates
	 * 
	 * @param request
	 * @param requestBody the request body containing cost estimates
	 * @return
	 * @throws CostAppException
	 */
	public Object validateCostEstimates(HttpServletRequest request, Map<String, Object> requestBody) {
		List<Map<String, Object>> estimates = new ArrayList<>();
		if (!CollectionUtils.isEmpty((Collection<?>) requestBody.get(DATA))) {
			estimates = (List<Map<String, Object>>) requestBody.get(DATA);
		}
		try {
			costValidator.validateEstimates(estimates, request);
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while validating cost estimates due to:" + e.getLocalizedMessage()));
			throw new CostAppException("Error in validating cost estimates due to:" + e.getLocalizedMessage(),
					e);
		}
		return estimates;
	}
	
	/**
	 * Usage:The <i>saveCostEstimates</i> saves the cost estimates after validating
	 * 
	 * @param request
	 * @param requestBody the request body containing estimates
	 * @return
	 * @throws CostAppException
	 */
	public Object saveCostEstimates(HttpServletRequest request, Map<String, Object> requestBody) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			if (requestBody.containsKey(DATA) && requestBody.get(DATA) instanceof List
					&& !CollectionUtils.isEmpty((Collection<?>) requestBody.get(DATA))) {
				List<Map<String, Object>> estimates = (List<Map<String, Object>>) requestBody.get(DATA);
				costValidator.validateEstimates(estimates, request);
				Gson gson = new Gson();
				String clonedJson = gson.toJson(estimates);
				List<Map<String, Object>> estimatesToSave = gson.fromJson(clonedJson,
						new TypeToken<List<Map<String, Object>>>() {
						}.getType());
				estimatesToSave = estimatesToSave.stream()
						.filter(estimate -> estimate.containsKey(STATUS) && estimate.get(STATUS).equals(true))
						.map(estimate -> {
							estimate.remove(STATUS);
							return estimate;
						}).collect(Collectors.toList());
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("calling connect POST data with payload:" + estimatesToSave));
				ResponseEntity<List> saveResponse = baseHttpClient.fireHttpRequest(new URI(
						ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID),
						HttpMethod.POST, estimatesToSave, headers, List.class);
				return estimates;
			}

		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling saveCostEstimates due to:"
							+ httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in saving cost estimates due to:" + httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while saving cost estimates due to:" + resourceAccessException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in saving cost estimates due to:" + resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling saveCostEstimates due to:" + e.getLocalizedMessage()));
			throw new CostAppException("Error in saving cost estimates due to:" + e.getLocalizedMessage(), e);
		}
		return Collections.EMPTY_LIST;
	}
	
	/**
	 * Usage:The <i>saveMandatoryCostsToDraft</i> defaults mandatory cost components
	 * to draft and saves them
	 * 
	 * @param request
	 * @param requestBody the contract attributes and the drafteEstimateNo to
	 *                    default to
	 * @return
	 * @throws CostAppException
	 */
	public Object saveMandatoryCostsToDraft(HttpServletRequest request, Map<String, Object> requestBody) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			Object mandatoryCostComponents = new Object();
			String draftEstimateNo = null;
			String entityTypeFromPayload = null;
			if (requestBody.containsKey(DRAFT_ESTIMATE_NO) && !requestBody.get(DRAFT_ESTIMATE_NO).toString().isEmpty()
					&& requestBody.containsKey(ENTITY_TYPE) && !requestBody.get(ENTITY_TYPE).toString().isEmpty()) {
				draftEstimateNo = requestBody.get(DRAFT_ESTIMATE_NO).toString();
				entityTypeFromPayload = requestBody.get(ENTITY_TYPE).toString();
			}
			if (requestBody.containsKey(ATTRIBUTES) && requestBody.get(ATTRIBUTES) instanceof List
					&& !CollectionUtils.isEmpty((Collection<?>) requestBody.get(ATTRIBUTES))
					&& Objects.nonNull(draftEstimateNo) && Objects.nonNull(entityTypeFromPayload)) {
				// get all mandatory cost components--
				Map<String, Object> payloadToFetchMandatoryCosts = new HashMap<>();
				payloadToFetchMandatoryCosts.put(ATTRIBUTES, requestBody.get(ATTRIBUTES));
				Set<String> costComponentTemplates = new HashSet<>();
				mandatoryCostComponents = getCostComponents(request, payloadToFetchMandatoryCosts,costComponentTemplates);
				if (Objects.nonNull(mandatoryCostComponents) && (mandatoryCostComponents instanceof List)
						&& !((List) mandatoryCostComponents).isEmpty()) {
					List<Map<String, Object>> mandatoryCostComponentsList = (List<Map<String, Object>>) mandatoryCostComponents;
					// default these from cost component to cost estimate--
					String draftEstimateNumber = draftEstimateNo;
					String entityType = entityTypeFromPayload;
					mandatoryCostComponentsList.stream().forEach(costComponent -> {
						costComponent.put(DRAFT_ESTIMATE_NO, draftEstimateNumber);
						costComponent.put(ENTITY_TYPE, entityType);
						// as fxToBase is mandatory, setting it as one here--
						costComponent.put(FX_TO_BASE, 1);
					});
					
					List<Map<String, Object>> costComponentsDraft = new ArrayList<>();
					Map<String, Object> payloadToFetchCostComponentsDraft = new HashMap<>();
					payloadToFetchCostComponentsDraft.put(DRAFT_ESTIMATE_NO, draftEstimateNumber);
					List<MongoOperations> filterOnComponents = new ArrayList<MongoOperations>();
					filterOnComponents.add(new MongoOperations(TEMPLATE_NAME, costComponentTemplates, "in"));
					FilterData filterDataOnComponents = new FilterData(filterOnComponents);
					payloadToFetchCostComponentsDraft.put("filterData", filterDataOnComponents);
					String costComponentsDraftDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
							+ DRAFT_COST_ESTIMATE_OBJECT_UUID + "?" + DRAFT_ESTIMATE_NO + "=" + draftEstimateNumber;
					// call connect to fetch draft cost components--
					logger.debug(Logger.EVENT_SUCCESS,
							ESAPI.encoder().encodeForHTML("calling connect GET cost components draft with payload:"
									+ new JSONObject(payloadToFetchCostComponentsDraft)));
					HttpEntity<Object> entityToFetchDraftofCostComponents = new HttpEntity<Object>(
							payloadToFetchCostComponentsDraft, headers);
					ResponseEntity<List> costComponentsDraftResponse = restTemplateGetWityBody.getRestTemplate()
							.exchange(costComponentsDraftDataUri, HttpMethod.GET, entityToFetchDraftofCostComponents, List.class);
					logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
							"obtained connect GET cost components draft response:" + costComponentsDraftResponse.getBody()));
					if (Objects.nonNull(costComponentsDraftResponse) && Objects.nonNull(costComponentsDraftResponse.getBody())
							&& costComponentsDraftResponse.getBody() instanceof List) {
						costComponentsDraft = costComponentsDraftResponse.getBody();
						logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
								"obtained connect GET cost components draft response size:" + costComponentsDraft.size()));
					}
					
					filterCostEstimateToBeDraft(mandatoryCostComponentsList, costComponentsDraft); 
					
					if (mandatoryCostComponentsList.size() == 0) {
						return Collections.EMPTY_LIST;
					}

					else {
						String connectSaveDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
								+ DRAFT_COST_ESTIMATE_OBJECT_UUID;
						logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
								"calling connect POST data with payload:" + mandatoryCostComponentsList));
						ResponseEntity<List> saveResponse = baseHttpClient.fireHttpRequest(new URI(connectSaveDataUri),
								HttpMethod.POST, mandatoryCostComponentsList, headers, List.class);
						if (Objects.nonNull(saveResponse) && Objects.nonNull(saveResponse.getBody())) {
							logger.debug(logger.EVENT_SUCCESS,
									ESAPI.encoder()
											.encodeForHTML("_id's in response body of method saveMandatoryCostsToDraft:"
													+ costAppUtils.idsInData(saveResponse.getBody())));
							logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
									"no of mandatory cost components saved as draft:" + saveResponse.getBody().size()));
							return saveResponse.getBody();
						}
					}
				}
			}
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling saveMandatoryCostsToDraft due to:"
							+ httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException("Error in saving mandatory costs to draft due to:"
					+ httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while  saving mandatory costs to draft due  to:"
							+ resourceAccessException.getLocalizedMessage()));
			throw new CostAppException("Error in  saving mandatory costs to draft due to:"
					+ resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while  saving mandatory costs to draft due to" + e.getLocalizedMessage()));
			throw new CostAppException(
					"Error in  saving mandatory costs to draft due to:" + e.getLocalizedMessage(), e);
		}
		return Collections.EMPTY_LIST;
	}

	/**
	 * Filter cost estimate to be draft.
	 *
	 * @param mandatoryCostComponentsList the mandatory cost components list
	 * @param costComponentsDraft the cost components draft
	 */
	private void filterCostEstimateToBeDraft(List<Map<String, Object>> mandatoryCostComponentsList,
			List<Map<String, Object>> costComponentsDraft) {
		List<Map<String, Object>> unsavedDraft = new ArrayList<>();
		mandatoryCostComponentsList.stream().forEach(estimate -> {
			ListIterator<Map<String, Object>> costCompIterator = costComponentsDraft.listIterator();
			while (costCompIterator.hasNext()) {
				Map<String, Object> draftedCost = costCompIterator.next();
				if (estimate.get(GlobalConstants.TEMPLATE_NAME).equals(draftedCost.get(GlobalConstants.TEMPLATE_NAME))) {
					unsavedDraft.add(estimate);
				}
			}
		});
		mandatoryCostComponentsList.removeAll(unsavedDraft);
	}
	
	/**
	 * Usage:The <i>copyEstimates</i> will copy the estimates that match the
	 * parameters passed in source and save them into connect db with modifications
	 * passed in target of payload
	 * 
	 * @param request
	 * @param requestBody the request body containing source and target attributes
	 * @return
	 * @throws CostAppException
	 */
	public Object copyEstimates(HttpServletRequest request, Map<String, Object> requestBody) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers = commonService.getHttpHeader(request);
			logger.info(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("payload obtained in copyEstimates:" + new JSONObject(requestBody)));
			if (CollectionUtils.isEmpty(requestBody) || !requestBody.containsKey(SOURCE)
					|| !requestBody.containsKey(TARGET) || !requestBody.containsKey(APPLICABLE_DATE)
					|| StringUtils.isEmpty(requestBody.get(APPLICABLE_DATE).toString())
					|| !(requestBody.get(SOURCE) instanceof Map)
					|| CollectionUtils.isEmpty((Map) requestBody.get(SOURCE))
					|| !(requestBody.get(TARGET) instanceof Map)
					|| CollectionUtils.isEmpty((Map) requestBody.get(TARGET))) {
				logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
						.encodeForHTML("payload sent to copy estimates is not correct inside method copyEstimates"));
				throw new ConnectException("payload sent to copy estimates is not correct!");
			}
			Gson gson = new Gson();
			String sourceString = gson.toJson(requestBody.get(SOURCE));
			FilterData sourceFilterData = gson.fromJson(sourceString, FilterData.class);
			// call connect get data with this filter--
			Map<String, Object> payloadToFetchEstimates = new HashMap<>();
			payloadToFetchEstimates.put("filterData", sourceFilterData);
			String getDeletedData = requestBody.containsKey(GET_DELETED_DATA)
					? requestBody.get(GET_DELETED_DATA).toString()
					: null;
			List<Map<String, Object>> estimates = fetchCostEstimateDataFromConnectWithoutAnyHandling(
					payloadToFetchEstimates, headers, getDeletedData);
			if (estimates.isEmpty())
				return Collections.EMPTY_LIST;
			// target--
			Map<String, Object> target = (Map<String, Object>) requestBody.get(TARGET);
			// write values from target--
			estimates.stream().forEach(estimate -> writeFieldValuesFromTarget(estimate, target));
			// save estimates into db--
			ResponseEntity<Object> saveResponse = baseHttpClient.fireHttpRequest(
					new URI(ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID),
					HttpMethod.POST, estimates, headers, Object.class);
			if (Objects.nonNull(saveResponse.getBody()) && saveResponse.getBody() instanceof List
					&& !((List) saveResponse.getBody()).isEmpty()) {
				// prepare payload for getAllEstimates API format--
				Map<String, Object> payloadToGetAllEstimates = preparePayloadFromTargetForGetAllEstimates(target,
						requestBody.get(APPLICABLE_DATE).toString(), getDeletedData);
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder()
								.encodeForHTML("calling getAllEstimates from inside method copyEstimates with payload:"
										+ payloadToGetAllEstimates));
				ResponseEntity<List> getAllEstimatesResponse = (ResponseEntity<List>) getCostEstimateData(
						payloadToGetAllEstimates, request);
				logger.debug(logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("_id's in response body of method copyEstimates:"
								+ costAppUtils.idsInData(getAllEstimatesResponse.getBody())));
				logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
						"no of estimates obtained from getAllEstimates:" + getAllEstimatesResponse.getBody().size()));
				return getAllEstimatesResponse.getBody();
			} else {
				logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
						"error while saving estimates after copying with target attributes inside method copyEstimates"));
				throw new ConnectException("error while saving estimates after copying with target attributes");
			}

		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while calling copyEstimates due to:" + httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in copying cost estimates due to:" + httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while copying cost estimates due to:" + resourceAccessException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in copying cost estimates due to:" + resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling copyEstimates due to:" + e.getLocalizedMessage()));
			throw new CostAppException("Error in copying cost estimates due to:" + e.getLocalizedMessage(), e);
		}
	}
	
	/**
	 * Usage:The <i>copyEstimatesForPBS</i> will copy the contract estimates linked
	 * to a PBS and calculate weighted average and default them to save as pbs
	 * estimates in db
	 * 
	 * @param request
	 * @param requestBody the request body containing source and target attributes
	 * @return Object defaulted pbs estimates
	 * @throws UtilityServiceException
	 */
	public Object copyEstimatesForPBS(HttpServletRequest request, Map<String, Object> requestBody) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			String entityRefNo = null;
			if (Objects.nonNull(requestBody.get(ENTITY_REF_NO)) && !requestBody.get(ENTITY_REF_NO).toString().isEmpty())
				entityRefNo = requestBody.get(ENTITY_REF_NO).toString();
			if (Objects.isNull(entityRefNo))
				return Collections.EMPTY_LIST;
			// fetch platform URL
			String platform_url = commonService.getPropertyFromConnect(request, "platform_url", null);
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("copyEstimatesForPBS- platform_url : " + platform_url));
			// call TRM api to get all CI linked with pbs refNo
			LinkedHashMap trmResponse = fetchPbsLinkedContractItems(requestBody, request, headers, platform_url);
			if (!(Objects.nonNull(trmResponse.get("data")) && trmResponse.get("data") instanceof Map
					&& !CollectionUtils.isEmpty((Map) trmResponse.get("data")))) {
				logger.debug(logger.EVENT_FAILURE, ESAPI.encoder()
						.encodeForHTML("data node is null/empty/invalid in /getPBSLinkDetails response"));
				throw new ConnectException("data node absent/invalid in /getPBSLinkDetails response");
			}
			Map<String, Object> dataInTrmResponse = (Map<String, Object>) trmResponse.get("data");
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("dataInTrmResponse contains- quantity:"
					+ dataInTrmResponse.get("quantity") + ", quantityUnit:" + dataInTrmResponse.get("quantityUnit")));
			if (Objects.isNull(dataInTrmResponse.get("quantity"))
					|| Objects.isNull(dataInTrmResponse.get("quantityUnit"))
					|| dataInTrmResponse.get("quantity").toString().isEmpty()
					|| dataInTrmResponse.get("quantityUnit").toString().isEmpty()) {
				logger.debug(logger.EVENT_FAILURE, ESAPI.encoder()
						.encodeForHTML("quantity/quantityUnit is null/empty in /getPBSLinkDetails response"));
				throw new ConnectException("quantity/quantityUnit absent inside data in /getPBSLinkDetails response");
			}
			String totalPbsQuantity = dataInTrmResponse.get("quantity").toString();
			String totalPbsQuantityUnit = dataInTrmResponse.get("quantityUnit").toString();
			if (!(Objects.nonNull(dataInTrmResponse.get("contractItemList"))
					&& dataInTrmResponse.get("contractItemList") instanceof List
					&& !((List) dataInTrmResponse.get("contractItemList")).isEmpty())) {
				logger.debug(logger.EVENT_FAILURE, ESAPI.encoder()
						.encodeForHTML("contractItemList absent/null/invalid in /getPBSLinkDetails response"));
				throw new ConnectException("contractItemList absent/invalid in /getPBSLinkDetails response");
			}
			List<Map<String, Object>> contractItemList = (List<Map<String, Object>>) dataInTrmResponse
					.get("contractItemList");
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("contractItemList in trm response: " + contractItemList));
			// prepare set of entityRefNos whose contract estimates need to be fetched and
			// mapping from entityRefNo to pbs linked qty and unit mapping--
			Set<Object> entityRefNos = new HashSet<>();
			Map<String, Object[]> entityRefNoToLinkedItemQtyMap = new HashMap<>();
			List<ConnectError> errors = new ArrayList<>();
			contractItemList.stream()
					.map(item -> {
						if (Objects.isNull(item.get("internalContractItemRefNo")) || Objects.isNull(item.get("itemQty"))
								|| item.get("internalContractItemRefNo").toString().isEmpty()
								|| item.get("itemQty").toString().isEmpty())
							errors.add(new ConnectError(_500, "inappropriate PBS Link Details",
									"inappropriate PBS Link Details",
									"{internalContractItemRefNo:" + item.get("internalContractItemRefNo") + ", itemQty:"
											+ item.get("itemQty") + ", contractRefNo:" + item.get("contractRefNo")
											+ "}"));
						return item;
					})
					.filter(item -> Objects.nonNull(item.get("internalContractItemRefNo"))
							&& !item.get("internalContractItemRefNo").toString().isEmpty()
							&& Objects.nonNull(item.get("itemQty")) && !item.get("itemQty").toString().isEmpty())
					.forEach(item -> {
						entityRefNos.add(item.get("internalContractItemRefNo"));
						String linkedItemQty = item.get("itemQty").toString();
						Object linkedItemQtyUnit = item.get("itemQtyUnit");
						Object linkedItemQtyUnitId = item.get("itemQtyUnitId");
						Object[] linkedItemQtyAndUnit = new Object[] { linkedItemQty, linkedItemQtyUnit,
								linkedItemQtyUnitId };
						entityRefNoToLinkedItemQtyMap.put(item.get("internalContractItemRefNo").toString(),
								linkedItemQtyAndUnit);
					});
			if (!CollectionUtils.isEmpty(errors)) {
				logger.error(Logger.EVENT_FAILURE,
						ESAPI.encoder().encodeForHTML("inappropriate PBS Link Details" + new JSONArray(errors)));
				throw new ConnectException(errors, "inappropriate PBS Link Details");
			}
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("entityRefNos of contract items:" + entityRefNos
							+ ", entityRefNoToLinkedItemQtyMap:" + new JSONObject(entityRefNoToLinkedItemQtyMap)));
			// fetch contract estimates--
			Map<String, Object> payloadToFetchContractEstimates = new HashMap<>();
			MongoOperations filterOnEntityType = new MongoOperations("entityType", "Contract Item", "eq");
			MongoOperations filterOnEntityRefNo = new MongoOperations("entityRefNo", entityRefNos, "in");
			MongoOperations filterOnEstimateFor = new MongoOperations("estimateFor", "Valuation", "ne");
			List<MongoOperations> filter = new ArrayList<MongoOperations>();
			filter.add(filterOnEntityType);
			filter.add(filterOnEntityRefNo);
			filter.add(filterOnEstimateFor);
			FilterData filterData = new FilterData(filter);
			payloadToFetchContractEstimates.put("filterData", filterData);
			List<Map<String, Object>> contractEstimates = fetchCostEstimateDataFromConnectWithoutAnyHandling(
					payloadToFetchContractEstimates, headers, PARAM_N);
			if (CollectionUtils.isEmpty(contractEstimates)) {
				logger.debug(logger.EVENT_SUCCESS,
						ESAPI.encoder()
								.encodeForHTML("returning empty response as no actual estimates found for entitRefNos:"
										+ new JSONArray(entityRefNos)));
				return Collections.EMPTY_LIST;
			}
			// group the estimates's _ids on the basis of costComponent,incExpense and
			// currency--
			MultiKeyMap costCompAndIncExpenseAndCurrencyToIdMapping = new MultiKeyMap<>();
			Map<String, List<Map<String, Object>>> existingProductIdToWeightUnitMap = new HashMap<>();
			MultiKeyMap productIdAndFromUnitAndToUnitToConversionMap = new MultiKeyMap<>();
			groupContractEstimatesForAverage(contractEstimates, totalPbsQuantityUnit, existingProductIdToWeightUnitMap,
					productIdAndFromUnitAndToUnitToConversionMap, costCompAndIncExpenseAndCurrencyToIdMapping, request);
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
					"costCompAndIncExpenseAndCurrencyToIdMapping:" + costCompAndIncExpenseAndCurrencyToIdMapping));
			// convert list of data into mapping by _id--
			Map<String, Map<String, Object>> _idToEstimatesMap = new HashMap<>();
			contractEstimates.stream().filter(estimate -> Objects.nonNull(estimate.get(_ID)))
					.forEach(estimate -> _idToEstimatesMap.put(estimate.get(_ID).toString(), estimate));
			// start the calculations for weighted average--
			List<Map<String, Object>> pbsEstimatesToSave = new ArrayList<>();
			Iterator<Entry<MultiKey, Set<String>>> iterator = costCompAndIncExpenseAndCurrencyToIdMapping.entrySet()
					.iterator();
			while (iterator.hasNext()) {
				Entry<MultiKey, Set<String>> entry = iterator.next();
				logger.debug(logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("calculating weighted average for group:" + entry));
				MultiKey costCompAndIncExpenseAndCurrency = entry.getKey();
				Set<String> _idsInOneGroup = entry.getValue();
				calculateWeightedAverage(costCompAndIncExpenseAndCurrency, _idsInOneGroup, _idToEstimatesMap,
						entityRefNoToLinkedItemQtyMap, totalPbsQuantity, totalPbsQuantityUnit, entityRefNo,
						pbsEstimatesToSave, request, existingProductIdToWeightUnitMap);
			}
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("calling connect save data API with payload :" + pbsEstimatesToSave));
			ResponseEntity<Object> saveResponse = baseHttpClient.fireHttpRequest(
					new URI(ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID),
					HttpMethod.POST, pbsEstimatesToSave, headers, Object.class);
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("_id's in response body of method saveEstimates:"
							+ costAppUtils.idsInData(saveResponse.getBody())));
			return saveResponse;
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling copyEstimatesForPBS due to:"
							+ httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in copying cost estimates for pbs due to:" + httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception inside method copyEstimatesForPBS due to:"
							+ resourceAccessException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in copying cost estimates for pbs due to:" + resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			e.printStackTrace();
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling copyEstimatesForPBS due to:" + e.getLocalizedMessage()));
			throw new CostAppException(
					"Error in copying cost estimates for pbs due to:" + e.getLocalizedMessage(), e);
		}
	}

	private void groupContractEstimatesForAverage(List<Map<String, Object>> contractEstimates,
			String totalPbsQuantityUnit, Map<String, List<Map<String, Object>>> existingProductIdToWeightUnitMap,
			MultiKeyMap productIdAndFromUnitAndToUnitToConversionMap,
			MultiKeyMap costCompAndIncExpenseAndCurrencyToIdMapping, HttpServletRequest request) {
		contractEstimates.stream()
				.filter(estimate -> costAppUtils.checkKeysNotAbsentOrEmptyInData(Arrays.asList(new String[] { RATE_TYPE_PRICE,
						COST_COMPONENT_DISPLAY_NAME, INC_EXPENSE, COST_PRICE_UNIT_ID, COST_VALUE, PRODUCT_ID }),
						estimate))
				.forEach(estimate -> {
					String costComponent = estimate.get(COST_COMPONENT_DISPLAY_NAME).toString();
					String incExpense = estimate.get(INC_EXPENSE).toString();
					String currencyToMatch = null;
					String costValueString = null;
					double costValueDouble = 0;
					switch (estimate.get(RATE_TYPE_PRICE).toString()) {
					case RATE_TYPE_ABSOLUTE:
						currencyToMatch = estimate.get(CP_UNIT_ID_DISPLAY_NAME).toString();
						costValueString = estimate.get(COST_VALUE).toString();
						costValueDouble = Double.parseDouble(costValueString);
						if (org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase(currencyToMatch, "USc", "UScents",
								"US cents", "UScent", "US cent")) {
							currencyToMatch = "USD";
							estimate.put(CP_UNIT_ID_DISPLAY_NAME, "USD");
							costValueDouble = costValueDouble / 100;
							estimate.put(COST_VALUE, String.valueOf(costValueDouble));
						}
						break;
					case RATE_TYPE_RATE:
						String cpUnitId = estimate.get(CP_UNIT_ID_DISPLAY_NAME).toString();
						currencyToMatch = cpUnitId.split("/")[0];
						String weightUnit = cpUnitId.split("/")[1];
						costValueString = estimate.get(COST_VALUE).toString();
						costValueDouble = Double.parseDouble(costValueString);
						if (org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase(currencyToMatch, "USc", "UScents",
								"US cents", "UScent", "US cent")) {
							currencyToMatch = "USD";
							estimate.put(CP_UNIT_ID_DISPLAY_NAME, "USD/" + weightUnit);
							costValueDouble = costValueDouble / 100;
							estimate.put(COST_VALUE, String.valueOf(costValueDouble));
						}
						// if weight unit mismatches from itemQtyUnitId from TRM , do the unit
						// conversion--
						if (!weightUnit.equalsIgnoreCase(totalPbsQuantityUnit)) {
							String weightUnitId = findMatchingWeightUnit(estimate, existingProductIdToWeightUnitMap,
									weightUnit, request);
							String totalPbsQuantityUnitId = findMatchingWeightUnit(estimate,
									existingProductIdToWeightUnitMap, totalPbsQuantityUnit, request);
							String conversionFactorString = getWeightUnitConversionFactor(estimate,
									productIdAndFromUnitAndToUnitToConversionMap, weightUnit, totalPbsQuantityUnitId,
									request);
							costValueDouble = costValueDouble / Double.parseDouble(conversionFactorString);
						}
						estimate.put(COST_VALUE, String.valueOf(costValueDouble));
						break;
					}
					Set<String> _idsInOneGroup = new HashSet<>();
					if (costCompAndIncExpenseAndCurrencyToIdMapping.containsKey(costComponent, incExpense,
							currencyToMatch)) {
						_idsInOneGroup = (Set<String>) costCompAndIncExpenseAndCurrencyToIdMapping.get(costComponent,
								incExpense, currencyToMatch);
						_idsInOneGroup.add(estimate.get(_ID).toString());
					} else {
						_idsInOneGroup.add(estimate.get(_ID).toString());
						costCompAndIncExpenseAndCurrencyToIdMapping.put(costComponent, incExpense, currencyToMatch,
								_idsInOneGroup);
					}
				});
	}

	private void calculateWeightedAverage(MultiKey costCompAndIncExpenseAndCurrency, Set<String> _idsInOneGroup,
			Map<String, Map<String, Object>> _idToEstimatesMap, Map<String, Object[]> entityRefNoToLinkedItemQtyMap,
			String totalPbsQuantity, String totalPbsQuantityUnit, String entityRefNo,
			List<Map<String, Object>> pbsEstimatesToSave, HttpServletRequest request,
			Map<String, List<Map<String, Object>>> existingProductIdToWeightUnitMap) {
		Set<String> counterPartiesInGroup = new HashSet<>();
		Set<String> counterPartiesIdInGroup = new HashSet<>();
		Map<String, Object> pbsEstimateForCurrentGroup = new HashMap<>();
		double numeratorTotal = 0;
		double denominator = 1;
		Iterator<String> _idIterator = _idsInOneGroup.iterator();
		while (_idIterator.hasNext()) {
			String _id = _idIterator.next();
			Map<String, Object> contractEstimate = _idToEstimatesMap.get(_id);
			if (CollectionUtils.isEmpty(pbsEstimateForCurrentGroup))
				pbsEstimateForCurrentGroup.putAll(contractEstimate);
			switch (contractEstimate.get(RATE_TYPE_PRICE).toString()) {
			case RATE_TYPE_ABSOLUTE:
				numeratorTotal = numeratorTotal + Double.parseDouble(contractEstimate.get(COST_VALUE).toString());
				break;
			case RATE_TYPE_RATE:
				numeratorTotal = numeratorTotal
						+ Double.parseDouble(contractEstimate.get(COST_VALUE).toString()) * Double.parseDouble(
								entityRefNoToLinkedItemQtyMap.get(contractEstimate.get(ENTITY_REF_NO).toString())[0]
										.toString());
				break;
			}
			if (Objects.nonNull(contractEstimate.get(CP_GRP_NAME))
					&& !contractEstimate.get(CP_GRP_NAME).toString().isEmpty()
					&& Objects.nonNull(contractEstimate.get(CP_GRP_NAME_DISPLAY_NAME))
					&& !contractEstimate.get(CP_GRP_NAME_DISPLAY_NAME).toString().isEmpty()) {
				counterPartiesInGroup.add(contractEstimate.get(CP_GRP_NAME_DISPLAY_NAME).toString());
				counterPartiesIdInGroup.add(contractEstimate.get(CP_GRP_NAME).toString());
			}
		}
		double finalCostValueDouble = 0;
		finalCostValueDouble = numeratorTotal / Double.parseDouble(totalPbsQuantity);
		pbsEstimateForCurrentGroup.put(COST_VALUE, finalCostValueDouble);
		String currencyInEstimate = pbsEstimateForCurrentGroup.get(CP_UNIT_ID_DISPLAY_NAME).toString().split("/")[0];
		pbsEstimateForCurrentGroup.put(CP_UNIT_ID_DISPLAY_NAME, currencyInEstimate + "/" + totalPbsQuantityUnit);
		pbsEstimateForCurrentGroup.put(COST_AMOUNT_IN_BASE_CURRENCY, numeratorTotal);
		pbsEstimateForCurrentGroup.put(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID, currencyInEstimate);
		pbsEstimateForCurrentGroup.put(ITEM_QTY, totalPbsQuantity);
		pbsEstimateForCurrentGroup.put(ITEM_QTY_UNIT_DISPLAY_NAME, totalPbsQuantityUnit);
		pbsEstimateForCurrentGroup.put(ITEM_QTY_UNIT_ID, findMatchingWeightUnit(pbsEstimateForCurrentGroup,
				existingProductIdToWeightUnitMap, totalPbsQuantityUnit, request));
		// temporarily keeping fxToBaseType as absolute--
		pbsEstimateForCurrentGroup.put(FX_TO_BASE_TYPE, FX_TO_BASE_TYPE_ABSOLUTE);
		pbsEstimateForCurrentGroup.put(FX_TO_BASE, 1);
		pbsEstimateForCurrentGroup.put(ENTITY_TYPE, ENTITY_TYPE_PLANNED_SHIPMENT);
		pbsEstimateForCurrentGroup.put(ENTITY_REF_NO, entityRefNo);
		pbsEstimateForCurrentGroup.put(RATE_TYPE_PRICE, RATE_TYPE_RATE);
		pbsEstimateForCurrentGroup.put(RATE_TYPE_PRICE_DISP_NAME, "Rate");
		if (counterPartiesInGroup.size() == 1) {
			pbsEstimateForCurrentGroup.put(CP_GRP_NAME_DISPLAY_NAME, counterPartiesInGroup.toArray()[0]);
			pbsEstimateForCurrentGroup.put(CP_GRP_NAME, counterPartiesIdInGroup.toArray()[0]);
		} else {
			pbsEstimateForCurrentGroup.remove(CP_GRP_NAME_DISPLAY_NAME);
			pbsEstimateForCurrentGroup.remove(CP_GRP_NAME);
		}
		pbsEstimatesToSave.add(pbsEstimateForCurrentGroup);

	}
	
	/**
	 * Usage:The <i>saveEstimates</i> saves the cost estimates by calling connect
	 * save data API
	 * 
	 * @param request
	 * @param requestBody the request body containing estimates
	 * @return
	 * @throws CostAppException
	 */
	public Object saveEstimates(HttpServletRequest request, Map<String, Object> requestBody) {
		try {
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
			
			if (Objects.nonNull(requestBody) && requestBody.containsKey(DATA) && requestBody.get(DATA) instanceof List
					&& !CollectionUtils.isEmpty((Collection<?>) requestBody.get(DATA))) {
				List<Map<String, Object>> estimates = (List<Map<String, Object>>) requestBody.get(DATA);
				estimates.stream().forEach(estimate -> {
					List<String> missingMandatoryFields = new ArrayList<String>();
					if (!(!StringUtils.isEmpty(estimate.get(ENTITY_TYPE)) && estimate.get(ENTITY_TYPE).equals(PLANNED_SHIPMENT)
							&& !StringUtils.isEmpty(estimate.get(COST_COMPONENT_DISPLAY_NAME)) && estimate.get(COST_COMPONENT_DISPLAY_NAME).toString().toLowerCase().contains(FREIGHT)
							&& !StringUtils.isEmpty(estimate.get(RATE_TYPE_PRICE)) && estimate.get(RATE_TYPE_PRICE).equals(RATE)
							&& StringUtils.isEmpty(estimate.get(PRODUCT))
							&& StringUtils.isEmpty(estimate.get(ITEM_QTY_UNIT_DISPLAY_NAME)))) {
						missingMandatoryFields=	costValidator.validateMandatoryFieldsAndRateTypePrice(estimate);
					}
					if (!missingMandatoryFields.isEmpty()) {
						logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
								"validation of estimate failed due to missing fields:" + missingMandatoryFields));
						throw new ConnectException(
								"validation of estimate failed due to missing fields:" + missingMandatoryFields);
					}
				});
				logger.debug(logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("calling connect save data API with payload:" + estimates));
				ResponseEntity<Object> saveResponse = baseHttpClient.fireHttpRequest(new URI(
						ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID),
						HttpMethod.POST, estimates, headers, Object.class);
				logger.debug(logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("_id's in response body of method saveEstimates:"
								+ costAppUtils.idsInData(saveResponse.getBody())));
				return saveResponse;
			}
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while calling saveEstimates due to:" + httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in saving cost estimates due to:" + httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while saving cost estimates due to:" + resourceAccessException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in saving cost estimates due to:" + resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling saveEstimates due to:" + e.getLocalizedMessage()));
			throw new CostAppException("Error in saving cost estimates due to:" + e.getLocalizedMessage(), e);
		}
		return Collections.EMPTY_LIST;
	}
	
	public List<Map<String, Object>> getRulesMatchingToAttributes(HttpHeaders headers,
			Map<String, Object> requestBody) {
		List<Map<String, Object>> attributes = (List<Map<String, Object>>) requestBody.get(ATTRIBUTES);
		// create payload to fetch matching rules--
		Map<String, Object> payloadToFetchRulesData = new HashMap<>();
		List<MongoOperations> filterToFetchRulesData = createFilterOnAttributeDetails(attributes);
		FilterData filterDataOnRules = new FilterData(filterToFetchRulesData);
		payloadToFetchRulesData.put("filterData", filterDataOnRules);
		String rulesDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + RULES_OBJECT_UUID;
		// call connect to fetch matching rules--
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
				.encodeForHTML("calling connect GET rules with payload:" + new JSONObject(payloadToFetchRulesData)));
		HttpEntity<Object> entityToFetchRules = new HttpEntity<Object>(payloadToFetchRulesData, headers);
		ResponseEntity<List> rulesResponse = restTemplateGetWityBody.getRestTemplate().exchange(rulesDataUri,
				HttpMethod.GET, entityToFetchRules, List.class);
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("obtained connect GET rules response:" + rulesResponse.getBody()));
		List<Map<String, Object>> rules = new ArrayList<>();
		if (Objects.nonNull(rulesResponse) && Objects.nonNull(rulesResponse.getBody())
				&& rulesResponse.getBody() instanceof List) {
			rules = rulesResponse.getBody();
			logger.debug(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("obtained connect GET rules response size:" + rules.size()));
		}
		return rules;
	}

	public void addMandatoryTemplateNamesToSet(List<Map<String, Object>> rules, Set<String> costComponentTemplates) {
		if (!CollectionUtils.isEmpty(rules))
			rules.stream()
					.filter(rule -> rule.containsKey(COST_COMPONENT_TEMPLATES)
							&& Objects.nonNull(rule.get(COST_COMPONENT_TEMPLATES))
							&& rule.get(COST_COMPONENT_TEMPLATES) instanceof List)
					.forEach(rule -> {
						List<String> costComponentTemplateInRule = (List<String>) rule.get(COST_COMPONENT_TEMPLATES);
						costComponentTemplates.addAll(costComponentTemplateInRule);
					});

	}
	
	public String fetchCorporateCurrency(HttpServletRequest req) {
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("inside method fetchCorporateCurrency"));
		String corporateCurrency = null;
		try {
			String mdmUri = commonService.getPropertyFromConnect(req, "eka_mdm_host", null)
					+ FETCH_CORPORATE_CURRENCY_PATH;
			HttpHeaders httpHeaders = commonService.getHttpHeader(req);
			ResponseEntity<Map> mdmResult = null;
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("calling mdm corporateInfo api with headers :-" + httpHeaders));
			try {
				mdmResult = baseHttpClient.fireHttpRequest(new URI(mdmUri), HttpMethod.GET, null, httpHeaders,
						Map.class);
			} catch (HttpServerErrorException httpServerErrorException) {
				logger.error(Logger.EVENT_FAILURE,
						ESAPI.encoder().encodeForHTML(
								"Exception inside method fetchCorporateCurrency while calling mdm due to:"
										+ httpServerErrorException.getLocalizedMessage()));
				throw new CostAppException("error while fetching corporate currency from mdm due to :"
						+ httpServerErrorException.getLocalizedMessage());
			}
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("obtained response from mdm corporateInfo api :-" + mdmResult));
			Map<String, Object> mdmResponseBody = new HashMap<String, Object>();
			if (Objects.nonNull(mdmResult) && Objects.nonNull(mdmResult.getBody())
					&& (mdmResult.getBody() instanceof Map))
				mdmResponseBody = mdmResult.getBody();
			if (mdmResponseBody.containsKey("baseCurId"))
				corporateCurrency = mdmResponseBody.get("baseCurId").toString();
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("method fetchCorporateCurrency ends"));
		} catch (CostAppException use) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("CostAppException inside method fetchCorporateCurrency due to:"
							+ use.getLocalizedMessage()));
			throw use;
		} catch (Exception ex) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"CostAppException inside method fetchCorporateCurrency due to:" + ex.getLocalizedMessage()));
			throw new ConnectException("Exception while getting cost estimates due to : " + ex.getLocalizedMessage(),
					ex);
		}
		return corporateCurrency;
	}

	private Map<String, Object> transformData(Map<String, Object> individualData, Map<String, List<String>> requestBody,
			List<Map<String, Object>> retData) {
		if (individualData.containsKey("draftEstimateNo")
				&& requestBody.containsKey(individualData.get("draftEstimateNo"))) {
			Iterator<String> transformerIterator = requestBody.get(individualData.get("draftEstimateNo")).iterator();
			while (transformerIterator.hasNext()) {
				String entityRefNo = transformerIterator.next();
				if ((existingEntityRefNoMap.containsKey(individualData.get("entityType"))
						&& !((List) existingEntityRefNoMap.get(individualData.get("entityType"))).contains(entityRefNo))
						|| (!existingEntityRefNoMap.containsKey(individualData.get("entityType")))) {
					Map<String, Object> newData = new HashMap<String, Object>();
					newData.putAll(individualData);
					newData.put("entityRefNo", entityRefNo);
					newData.remove("userId");
					newData.remove("sys__createdOn");
					newData.remove("sys__createdBy");
					newData.remove("sys__version");
					newData.remove("sys__data__state");
					commonService.addDataOptionsWhileSaving(newData);
					retData.add(newData);
				}
			}
		}
		return null;
	}
	
	public List<Map<String, Object>> fetchCostEstimateDataFromConnectWithoutAnyHandling(
			Map<String, Object> requestBody, HttpHeaders headers, String getDeletedData)
			throws ResourceAccessException, HttpClientErrorException, ConnectException {
		ResponseEntity<Object> result = null;
		String uri = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID;
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("calling connect GET data"));
		HttpEntity<Object> entity = new HttpEntity<Object>(requestBody, headers);
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("headers :-" + headers + "and payload :" + new JSONObject(requestBody)));
		result = restTemplateGetWityBody.getRestTemplate().exchange(uri, HttpMethod.GET, entity, Object.class);
		List<Map<String, Object>> dataList = new ArrayList<>();
		if (Objects.nonNull(result))
			dataList.addAll((List<Map<String, Object>>) result.getBody());
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML(
						"getDeletedData parameter inside method fetchCostEstimateDataFromConnectWithoutAnyHandling= "
								+ getDeletedData));
		if (Objects.isNull(getDeletedData) || getDeletedData.isEmpty() || getDeletedData.equalsIgnoreCase(PARAM_Y)) {
			ResponseEntity<Object> fetchDeletedDataResult = restTemplateGetWityBody.getRestTemplate()
					.exchange(uri + DELETED_DATA_PATH, HttpMethod.GET, entity, Object.class);
			if (Objects.nonNull(fetchDeletedDataResult)) {
				List<Map<String, Object>> deletedDataList = (List<Map<String, Object>>) fetchDeletedDataResult
						.getBody();
				dataList.addAll(deletedDataList);
			}
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("no. of costestimate data fetched from connect=" + dataList.size()));
		return dataList;
	}

	private Map<String, Object> createPayloadToFetchConnectDataWithFilters(Map<String, Object> requestBody,
			Map<String, String> entityRefNoToApplicableDateMap) {
		Map<String, Object> payload = new HashMap<String, Object>();
		if (Objects.nonNull(requestBody) && requestBody.containsKey("itemDetails")) {
			List<Map<String, Object>> itemDetails = (List<Map<String, Object>>) requestBody.get("itemDetails");
			if (!itemDetails.isEmpty()) {
				Set<Object> entityTypeSet = itemDetails.stream()
						.map(individualItemDetail -> individualItemDetail.get("entityType"))
						.collect(Collectors.toSet());
				Set<Object> entityRefNoSet = itemDetails.stream()
						.map(individualItemDetail -> individualItemDetail.get("entityRefNo"))
						.collect(Collectors.toSet());
				Map<String, String> refNoToDateMapping = itemDetails.stream()
						.collect(Collectors.toMap(
								individualItemDetail -> ((Map) individualItemDetail).get("entityRefNo").toString(),
								individualItemDetail -> ((Map) individualItemDetail).get("applicableDate").toString(),
								(oldValue, newValue) -> newValue));
				entityRefNoToApplicableDateMap.putAll(refNoToDateMapping);
				MongoOperations filterOnEntityType = new MongoOperations("entityType", entityTypeSet, "in");
				MongoOperations filterOnEntityRefNo = new MongoOperations("entityRefNo", entityRefNoSet, "in");
				List<MongoOperations> filter = new ArrayList<MongoOperations>();
				filter.add(filterOnEntityType);
				filter.add(filterOnEntityRefNo);
				FilterData filterData = new FilterData(filter);
				payload.put("filterData", filterData);
			}
		}
		return payload;
	}

	private void prepareListOfInstrumentNamesForCost(List<Map<String, Object>> connectDataList,
			Set<String> costCurveList, Set<String> monthYearList) {

		Supplier<Stream<Map<String, Object>>> streamSupplier = () -> connectDataList.stream()
				.filter(individualData -> individualData.containsKey("rateTypePrice")
						&& individualData.get("rateTypePrice").equals("curve"));
		costCurveList.addAll(streamSupplier.get().map(individualData -> {
			Object instrumentName = individualData.get("costCurve");
			Object monthYear = individualData.get("costMonth");
			Object entityRefObj = individualData.get("entityRefNo");
			if (!individualData.containsKey("costCurve") || Objects.isNull(instrumentName)
					|| !individualData.containsKey("costMonth") || Objects.isNull(monthYear)
					|| !individualData.containsKey("entityRefNo") || Objects.isNull(entityRefObj)) {
				logger.error(Logger.EVENT_FAILURE,
						ESAPI.encoder().encodeForHTML(
								"either of costCurve/costMonth/entityRefNo not found in connect data :costCurve="
										+ instrumentName + ",costMonth=" + monthYear + ",entityRefNo=" + entityRefObj));
				throw new ConnectException(
						"either of costCurve/costMonth/entityRefNo not found in connect data :costCurve="
								+ instrumentName + ",costMonth=" + monthYear + ",entityRefNo=" + entityRefObj);
			}
			return individualData.get("costCurve").toString();
		}).collect(Collectors.toSet()));
		monthYearList.addAll(streamSupplier.get().map(individualData -> individualData.get("costMonth").toString())
				.collect(Collectors.toSet()));
	}
	
	private void prepareListOfInstrumentNamesForCostEstimates(List<Map<String, Object>> connectDataList,
			Set<String> costCurveList, Set<String> monthYearList) {

		Supplier<Stream<Map<String, Object>>> streamSupplier = () -> connectDataList.stream()
				.filter(individualData -> individualData.containsKey("rateTypePrice")
						&& individualData.get("rateTypePrice").equals("curve"));
		costCurveList.addAll(streamSupplier.get().map(individualData -> {
			Object instrumentName = individualData.get("costCurve");
			Object monthYear = individualData.get("costMonth");
			Object entityRefObj = individualData.get("entityRefNo");
			if (!individualData.containsKey("costCurve") || Objects.isNull(instrumentName)
					|| !individualData.containsKey("costMonth") || Objects.isNull(monthYear)) {
				logger.error(Logger.EVENT_FAILURE,
						ESAPI.encoder().encodeForHTML(
								"either of costCurve/costMonth/entityRefNo not found in connect data :costCurve="
										+ instrumentName + ",costMonth=" + monthYear + ",entityRefNo=" + entityRefObj));
				throw new ConnectException(
						"either of costCurve/costMonth/entityRefNo not found in connect data :costCurve="
								+ instrumentName + ",costMonth=" + monthYear + ",entityRefNo=" + entityRefObj);
			}
			return individualData.get("costCurve").toString();
		}).collect(Collectors.toSet()));
		monthYearList.addAll(streamSupplier.get().map(individualData -> individualData.get("costMonth").toString())
				.collect(Collectors.toSet()));
	}

	private ResponseEntity<Object> fetchCollectionForCost(HttpHeaders headers, Set<String> costCurveList,
			Set<String> monthYearList) throws ConnectException, HttpClientErrorException, ResourceAccessException {
		String fetchCollectionUri = ekaConnectHost + PLATFORM_COLLECTION_PATH + COST_APP_UUID + "/"
				+ COST_ESTIMATE_OBJECT_UUID + FETCH_COLLECTION_ENDPOINT;
		headers.add("ttl", "300");
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("collectionName", "DS-Market Price");
		payload.put("skip", 0);
		payload.put("limit", 200);
		String instrumentNameInCsv = costCurveList.stream().collect(Collectors.joining(","));
		MongoOperations filterOnInstrumentName = new MongoOperations("Instrument Name", instrumentNameInCsv, "in");
		String monthYearInCsv = monthYearList.stream().collect(Collectors.joining(","));
		MongoOperations filterOnMonthYear = new MongoOperations("Month/Year", monthYearInCsv, "in");
		List<MongoOperations> filter = new ArrayList<MongoOperations>();
		filter.add(filterOnInstrumentName);
		filter.add(filterOnMonthYear);
		FilterData filterData = new FilterData(filter);
		JSONObject criteria = new JSONObject(filterData);
		criteria.put("sort", new JSONArray()
				.put(new JSONObject().put("fieldName", "Pricing Date").put("direction", "DESC")).toList());
		payload.put("criteria", criteria.toMap());
		HttpEntity<Object> fetchCollectionEntity = new HttpEntity<Object>(payload, headers);
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
				"calling fetch collection url with headers:" + headers + ", and payload:" + new JSONObject(payload)));
		ResponseEntity<Object> fetchCollectionResponse = restTemplate.exchange(fetchCollectionUri, HttpMethod.POST,
				fetchCollectionEntity, Object.class);
		return fetchCollectionResponse;
	}

	private void prepareMappingFromCostCurveAndMonthYearAndPricingDateToSettlePrice(
			List<Map<String, Object>> collectionList, MultiKeyMap instrumentAndMonthYearMap) {
		if (Objects.nonNull(collectionList) && !collectionList.isEmpty()) {
			Iterator<Map<String, Object>> collectionListIterator = collectionList.iterator();
			DateTimeFormatter sdf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
			DateTimeFormatter sdfWithTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
			while (collectionListIterator.hasNext()) {
				Map<String, Object> individualCollectionData = collectionListIterator.next();
				Object instrumentName = individualCollectionData.get("Instrument Name");
				Object monthYear = individualCollectionData.get("Month/Year");
				String pricingDate = individualCollectionData.get("Pricing Date").toString();
				TemporalAccessor dateWithTime = sdfWithTime.parse(pricingDate);
				String formattedPricingDate = sdf.format(dateWithTime);
				if (!instrumentAndMonthYearMap.containsKey(instrumentName, monthYear, formattedPricingDate))
					instrumentAndMonthYearMap.put(instrumentName, monthYear, formattedPricingDate,
							individualCollectionData.get("Settle Price"));
			}
		}
	}

	// handle missing pricing date in cost curve--
	private Object findApplicableCostAmount(Object instrumentName, Object monthYear, String applicableDate,
			MultiKeyMap instrumentAndMonthYearMap, int limit) {
		if (Objects.isNull(applicableDate))
			return null;
		if (instrumentAndMonthYearMap.containsKey(instrumentName, monthYear, applicableDate) && limit >= 0) {
			return instrumentAndMonthYearMap.get(instrumentName, monthYear, applicableDate);
		} else if (limit >= 0) {
			// reduce pricing date by one
			DateTimeFormatter yyyyMMddFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			DateTimeFormatter ddMMyyyyFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy");
			TemporalAccessor applicableDateInddMMyyyy = ddMMyyyyFormat.parse(applicableDate);
			String applicableDateInyyyyMMdd = yyyyMMddFormat.format(applicableDateInddMMyyyy);
			LocalDate date = LocalDate.parse(applicableDateInyyyyMMdd);
			LocalDate oneDayBefore = date.minus(Period.ofDays(1));
			return findApplicableCostAmount(instrumentName, monthYear, oneDayBefore.format(ddMMyyyyFormat),
					instrumentAndMonthYearMap, limit - 1);
		}
		logger.error(Logger.EVENT_FAILURE,
				ESAPI.encoder().encodeForHTML("no applicable settle price found for: costCurve=" + instrumentName
						+ ",costMonth=" + monthYear + ",applicableDate=" + applicableDate));
		return null;
	}
	
	private void prepareMappingFromCurveAndMonthToPriceUnit(List<Map<String, Object>> collectionList,
			MultiKeyMap instrumentAndMonthYearToPriceUnitMap) {
		if (!CollectionUtils.isEmpty(collectionList)) {
			collectionList.stream().forEach(individualCollectionData -> {
				Object instrumentName = individualCollectionData.get(INSTRUMENT_NAME);
				Object monthYear = individualCollectionData.get(MONTH_YEAR);
				Object priceUnit = individualCollectionData.get(PRICE_UNIT);
				if (!instrumentAndMonthYearToPriceUnitMap.containsKey(instrumentName, monthYear))
					instrumentAndMonthYearToPriceUnitMap.put(instrumentName, monthYear, priceUnit);
			});
		}
	}

	private void handleCostPriceUnitIdForCurveTypeData(Map<String, Object> individualData,
			MultiKeyMap instrumentAndMonthYearToPriceUnitMap, HttpHeaders httpHeaders,
			Map<String, List<Map<String, Object>>> productIdToServiceKeyValuesMap) {
		Object instrumentName = individualData.get(COST_CURVE);
		Object monthYear = individualData.get(COST_MONTH);
		if (!instrumentAndMonthYearToPriceUnitMap.containsKey(instrumentName, monthYear)) {
			logger.debug(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML(
							"Price Unit not found in platform collection DS-Market Price for instrumentName:"
									+ instrumentName + " and monthYear:" + monthYear));
			return;
		}
		Object priceUnit = instrumentAndMonthYearToPriceUnitMap.get(instrumentName, monthYear);
		individualData.put(CP_UNIT_ID_DISPLAY_NAME, priceUnit);
		if (individualData.containsKey(PAY_IN_CUR_ID) && !StringUtils.isEmpty(individualData.get(PAY_IN_CUR_ID)))
			individualData.put(COST_PRICE_UNIT_ID, individualData.get(PAY_IN_CUR_ID));
		else
			logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
					.encodeForHTML("cannot populate costPriceUnitId as payInCurId value not found in data:"));
	}

	private Map<String, Object> preparePayloadToCallWorkflowMdm(Object productId) {
		return new JSONObject().put(APP_ID, COST_APP_UUID)
				.put(WORKFLOW_TASK, WORKFLOW_TASK_COST_ITEMS_FOR_EXISTING_ENTITY)
				.put(DATA, new JSONArray().put(new JSONObject().put(SERVICE_KEY, SERVICE_KEY_PRODUCT_PRICE_UNIT)
						.put(DEPENDS_ON, new JSONArray().put(productId))))
				.toMap();
	}

	private Object findCostPriceUnitIdFromMdmResponse(List<Map<String, Object>> ppuKeyValues, Object priceUnit,
			Object productId) {
		Object costPriceUnitId = null;
		try {
			costPriceUnitId = ppuKeyValues.stream().filter(keyValuePair -> keyValuePair.get(VALUE).equals(priceUnit))
					.findAny().get().get(KEY);
		} catch (NoSuchElementException noSuchElementException) {
			logger.debug(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("costPriceUnitId not found in mdm response for the Price Unit:"
							+ priceUnit + " ,and productId:" + productId));
			return null;
		}
		return costPriceUnitId;
	}

	private void listInstrumentNamesFxAndValueDatesAndHandlePBSEstimateFor(List<Map<String, Object>> connectDataList,
			Set<String> fxCurvesList, Set<String> fxValueDatesList, String corpCurrency) {
		Iterator<Map<String, Object>> dataIteratorToListFxCurves = connectDataList.iterator();
		while (dataIteratorToListFxCurves.hasNext()) {
			Map<String, Object> individualData = dataIteratorToListFxCurves.next();
			//remove those cost estimates which have estimateForDisplayName = Execution--
			if (individualData.get(ENTITY_TYPE).toString().equalsIgnoreCase(ENTITY_TYPE_PLANNED_SHIPMENT)
					&& individualData.containsKey(ESTIMATE_FOR_DISPLAY_NAME) && individualData
							.get(ESTIMATE_FOR_DISPLAY_NAME).toString().equalsIgnoreCase(ESTIMATE_FOR_VALUATION))
				dataIteratorToListFxCurves.remove();
			// prepare list of fx value dates and instrument names--
			if (individualData.containsKey(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID)
					&& !individualData.get(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID).equals(corpCurrency)
					&& Objects.nonNull(individualData.get("fxToBaseType")) && individualData.get("fxToBaseType").toString().equalsIgnoreCase(FX_TO_BASE_TYPE_CURVE)) {
				if (!individualData.containsKey("fxValueDate") || (individualData.containsKey("fxValueDate")
						&& Objects.isNull(individualData.get("fxValueDate")))) {
					logger.error(Logger.EVENT_FAILURE,
							ESAPI.encoder().encodeForHTML("fxValueDate not present in connect data"));
					throw new CostAppException("fxValueDate not present in connect data");
				}
				String fromCurrency = individualData.get(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID).toString();
				String toCurrency = corpCurrency;
				fxCurvesList.add(fromCurrency + "/" + toCurrency);
				fxCurvesList.add(toCurrency + "/" + fromCurrency);
				String fxValueDate = individualData.get("fxValueDate").toString();
				fxValueDatesList.add(fxValueDate + "T00:00:00");
			}
		}
	}

	private ResponseEntity<Object> fetchCollectionForFx(HttpHeaders headers, Set<String> fxCurveList,
			Set<String> fxValueDatesList) throws ConnectException, HttpClientErrorException, ResourceAccessException {
		String fetchCollectionUri = ekaConnectHost + PLATFORM_COLLECTION_PATH + COST_APP_UUID + "/"
				+ COST_ESTIMATE_OBJECT_UUID + FETCH_COLLECTION_ENDPOINT;
		headers.remove("ttl");
		headers.add("ttl", "300");
		Map<String, Object> payload = new HashMap<String, Object>();
		payload.put("collectionName", "DS-Market Fx Rate");
		payload.put("skip", 0);
		payload.put("limit", 200);
		String instrumentNameInCsv = fxCurveList.stream().collect(Collectors.joining(","));
		MongoOperations filterOnInstrumentName = new MongoOperations("Instrument Name", instrumentNameInCsv, "in");
		String valueDatesInCsv = fxValueDatesList.stream().collect(Collectors.joining(","));
		MongoOperations filterOnValueDates = new MongoOperations("Value Date", valueDatesInCsv, "in");
		List<MongoOperations> filter = new ArrayList<MongoOperations>();
		filter.add(filterOnInstrumentName);
		filter.add(filterOnValueDates);
		FilterData filterData = new FilterData(filter);
		JSONObject criteria = new JSONObject(filterData);
		criteria.put("sort", new JSONArray()
				.put(new JSONObject().put("fieldName", "Period End Date").put("direction", "DESC")).toList());
		payload.put("criteria", criteria.toMap());
		HttpEntity<Object> fetchCollectionEntity = new HttpEntity<Object>(payload, headers);
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
				"calling fetch collection url with headers:" + headers + ", and payload:" + new JSONObject(payload)));
		ResponseEntity<Object> fetchCollectionResponse = restTemplate.exchange(fetchCollectionUri, HttpMethod.POST,
				fetchCollectionEntity, Object.class);
		return fetchCollectionResponse;
	}

	private void prepareMappingFromFxCurveAndValueDateAndEndDateToExchangeRate(
			List<Map<String, Object>> collectionDataList, MultiKeyMap fxInstrumentAndValueDateAndPeriodEndDateMap,
			Set<String> fxInstrumentSetInMultiKeyMap, String corpCurrency) {
		if (Objects.nonNull(collectionDataList) && !collectionDataList.isEmpty()) {
			Iterator<Map<String, Object>> collectionDataListIterator = collectionDataList.iterator();
			while (collectionDataListIterator.hasNext()) {
				Map<String, Object> individualCollectionData = collectionDataListIterator.next();
				// Object instrumentName = individualCollectionData.get("Instrument Name");
				String fromCurrency = individualCollectionData.get("From Currency").toString();
				String toCurrency = individualCollectionData.get("To Currency").toString();
				;
				DateTimeFormatter sdf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
				DateTimeFormatter sdfWithTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
				String valueDate = individualCollectionData.get("Value Date").toString();
				TemporalAccessor valueDateWithTime = sdfWithTime.parse(valueDate);
				String formattedValueDate = sdf.format(valueDateWithTime);
				String periodEndDate = individualCollectionData.get("Period End Date").toString();
				TemporalAccessor periodEndDateWithTime = sdfWithTime.parse(periodEndDate);
				String formattedPeriodEndDate = DateTimeFormatter.ofPattern("dd-MM-yyyy").format(periodEndDateWithTime);
				fxInstrumentSetInMultiKeyMap.add(fromCurrency + "/" + toCurrency);
				fxInstrumentSetInMultiKeyMap.add(toCurrency + "/" + fromCurrency);
				fxInstrumentAndValueDateAndPeriodEndDateMap.put(fromCurrency + "/" + toCurrency, formattedValueDate,
						formattedPeriodEndDate, individualCollectionData.get("Exchange Rate"));
				fxInstrumentAndValueDateAndPeriodEndDateMap.put(toCurrency + "/" + fromCurrency, formattedValueDate,
						formattedPeriodEndDate, 1 / ((double) individualCollectionData.get("Exchange Rate")));
			}
		}
	}

	private Map<String, Object> replaceFxAmountInConnectData(Map<String, Object> individualData,
			MultiKeyMap fxInstrumentAndValueDateAndPeriodEndDateMap, Set<String> fxInstrumentSetInMultiKeyMap,
			String corpCurrency, String applicableDate, List<Map<String, Object>> estimatesToUpdateInDb) {
		if (individualData.containsKey(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID)
				&& !individualData.get(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID).equals(corpCurrency)
				&& fxInstrumentSetInMultiKeyMap.contains(individualData.get("fxRate"))
				&& individualData.containsKey("fxValueDate") && Objects.nonNull(applicableDate)
				&& Objects.nonNull(individualData.get("fxToBaseType")) && individualData.get("fxToBaseType").toString().equalsIgnoreCase(FX_TO_BASE_TYPE_CURVE)) {
			String fromCurrency = individualData.get(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID).toString();
			String toCurrency = corpCurrency;
			String valueDate = individualData.get("fxValueDate").toString();
			DateTimeFormatter sdf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
			double applicableFxExchangeRate = findApplicableFxExchangeRate(fromCurrency + "/" + toCurrency, valueDate,
					applicableDate, fxInstrumentAndValueDateAndPeriodEndDateMap, 30);
			if (applicableFxExchangeRate != 0) {
				individualData.put("fxToBase", applicableFxExchangeRate);
				if (!individualData.get(SYS_DATA_STATE).toString().equalsIgnoreCase(STATE_DELETE))
					estimatesToUpdateInDb.add(individualData);
			}
			else
				logger.error(Logger.EVENT_FAILURE,
						ESAPI.encoder()
								.encodeForHTML("applicable exchange rate not found for :curveName=" + fromCurrency + "/"
										+ toCurrency + ",valueDate=" + valueDate + ",periodEndDate=" + applicableDate));
		}
		return individualData;
	}

	// handle missing end date in fx curve--
	private double findApplicableFxExchangeRate(String instrumentName, String valueDate, String applicableDate,
			MultiKeyMap fxInstrumentAndValueDateAndPeriodEndDateMap, int limit) {
		if (fxInstrumentAndValueDateAndPeriodEndDateMap.containsKey(instrumentName, valueDate, applicableDate)
				&& limit >= 0) {
			return (double) fxInstrumentAndValueDateAndPeriodEndDateMap.get(instrumentName, valueDate, applicableDate);
		} else if (limit >= 0) {
			// reduce end date(applicable Date) by one
			DateTimeFormatter yyyyMMddFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd");
			DateTimeFormatter ddMMyyyyFormat = DateTimeFormatter.ofPattern("dd-MM-yyyy");
			TemporalAccessor applicableDateInddMMyyyy = ddMMyyyyFormat.parse(applicableDate);
			String applicableDateInyyyyMMdd = yyyyMMddFormat.format(applicableDateInddMMyyyy);
			LocalDate date = LocalDate.parse(applicableDateInyyyyMMdd);
			LocalDate oneDayBefore = date.minus(Period.ofDays(1));
			return (double) findApplicableFxExchangeRate(instrumentName, valueDate, oneDayBefore.format(ddMMyyyyFormat),
					fxInstrumentAndValueDateAndPeriodEndDateMap, limit - 1);
		}
		return 0;
	}
	
	private void handlePercentageOfPriceTypeData(Map<String, Object> data,
			Map<String, List<Map<String, Object>>> productIdToServiceKeyValuesMap, HttpHeaders httpHeaders) {
		String contractPrice = data.get(CONTRACT_PRICE).toString();
		String costValue = data.get(COST_VALUE).toString();
		String productId = data.get(PRODUCT_ID).toString();
		String priceUnit = contractPrice;
		priceUnit = priceUnit.replaceAll("[0-9]|\\.|\\s", "");
		String contractPriceWithDecimal = contractPrice;
		contractPriceWithDecimal = contractPriceWithDecimal.replaceAll("[^0-9|\\.]", "");
		boolean isContractPriceInCents = false;
		if (org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase(priceUnit.split("/")[0], "USc", "UScents",
				"US cents", "UScent", "US cent")) {
			isContractPriceInCents = true;
			priceUnit = "USD/" + priceUnit.split("/")[1];
		}
		// replace costPriceUnitIdDisplayName with the priceUnit separated from
		// contractPrice--
		data.put(CP_UNIT_ID_DISPLAY_NAME, priceUnit);
		// calculate costValue and populate--
		double costValueDouble = Double.parseDouble(costValue);
		double contractPriceDouble = Double.parseDouble(contractPriceWithDecimal);
		if (isContractPriceInCents)
			contractPriceDouble = contractPriceDouble / 100;
		double calculatedCostValue = costValueDouble * contractPriceDouble / 100;
		data.put(COST_VALUE, calculatedCostValue);
		// find costPriceUnitId by fetching from mdm--
		if (productIdToServiceKeyValuesMap.containsKey(productId)) {
			List<Map<String, Object>> productPriceUnits = productIdToServiceKeyValuesMap.get(productId);
			Object costPriceUnitId = findCostPriceUnitIdFromMdmResponse(productPriceUnits, priceUnit, productId);
			if (Objects.nonNull(costPriceUnitId))
				data.put(COST_PRICE_UNIT_ID, costPriceUnitId);
		} else {
			Map<String, Object> workflowMdmResponse = costAppUtils.callWorkflowMdmApi(httpHeaders,
					preparePayloadToCallWorkflowMdm(productId));
			if (CollectionUtils.isEmpty(workflowMdmResponse) || !workflowMdmResponse.containsKey(PRODUCT_PRICE_UNIT)) {
				logger.debug(Logger.EVENT_SUCCESS,
						ESAPI.encoder().encodeForHTML("productPriceUnit not found in mdm response"));
				return;
			}
			List<Map<String, Object>> productPriceUnits = (List<Map<String, Object>>) workflowMdmResponse
					.get(PRODUCT_PRICE_UNIT);
			productIdToServiceKeyValuesMap.put(productId.toString(), productPriceUnits);
			Object costPriceUnitId = findCostPriceUnitIdFromMdmResponse(productPriceUnits, priceUnit, productId);
			if (Objects.nonNull(costPriceUnitId))
				data.put(COST_PRICE_UNIT_ID, costPriceUnitId);
		}
	}
	
	private Object calculateCostAmount(List<Object> calculationParams, Map<String, Object> individualData,
			MultiKeyMap instrumentAndMonthYearToPriceUnitMap, HttpServletRequest req,
			Map<String, List<Map<String, Object>>> productIdToWeightUnitMap,
			MultiKeyMap productIdAndFromUnitAndToUnitToConversionMap, List<Map<String, Object>> currencylistMap) {
		Object applicableCostAmount = calculationParams.get(0);
		Object instrumentName = calculationParams.get(1);
		Object monthYear = calculationParams.get(2);
		String itemQty = individualData.containsKey(ITEM_QTY) && !StringUtils.isEmpty(individualData.get(ITEM_QTY))
				? individualData.get(ITEM_QTY).toString()
				: null;
		String itemQtyUnitId = individualData.containsKey(ITEM_QTY_UNIT_ID)
				&& !StringUtils.isEmpty(individualData.get(ITEM_QTY_UNIT_ID))
						? individualData.get(ITEM_QTY_UNIT_ID).toString()
						: null;
		String priceUnit = Objects.nonNull(instrumentAndMonthYearToPriceUnitMap.get(instrumentName, monthYear))
				&& !instrumentAndMonthYearToPriceUnitMap.get(instrumentName, monthYear).toString().isEmpty()
						? instrumentAndMonthYearToPriceUnitMap.get(instrumentName, monthYear).toString()
						: null;
		double calculatedCostValue = 0;
		String[] priceUnitSplit = null;
		double applicableSettlePrice = -1;
		if (Objects.nonNull(applicableCostAmount)) {
			priceUnitSplit = priceUnit.split("/");
			String currencyUnitFromPriceUnit = priceUnitSplit[0];
			applicableSettlePrice = Double.parseDouble(applicableCostAmount.toString());
			// if currency is USc, divide by hundred and change to USD--
			if (org.apache.commons.lang3.StringUtils.equalsAnyIgnoreCase(currencyUnitFromPriceUnit, "USc", "US cents",
					"UScents")) {
				applicableSettlePrice = applicableSettlePrice / 100;
				currencyUnitFromPriceUnit = "USD";
			}
			// put costPriceUnitIdDisplayName and costPriceUnitId and costAmountInBaseCurrencyUnitId into data to be returned--
			if (currencylistMap.isEmpty())
				currencylistMap.addAll(callMdmToFetchServiceKeyMapping(SERVICEKEY_CURRENCYLIST, new JSONArray(), req));
			// find currencyId for this currency and populate--
			String finalCurrencyId = costAppUtils.findMatchingKeyFromValuesInMdmData(currencylistMap, currencyUnitFromPriceUnit);
			//commenting out below two lines as CPUId will now contain the priceunit from collection :CCA-491
			//individualData.put(CP_UNIT_ID_DISPLAY_NAME, currencyUnitFromPriceUnit);
			//individualData.put(COST_PRICE_UNIT_ID, finalCurrencyId);
			individualData.put(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID, currencyUnitFromPriceUnit);
		}

		if (!StringUtils.isEmpty(itemQty) && !StringUtils.isEmpty(itemQtyUnitId) && !StringUtils.isEmpty(priceUnit)
				&& Objects.nonNull(applicableCostAmount) && Objects.nonNull(priceUnitSplit)
				&& applicableSettlePrice != -1) {
			String weightUnitFromPriceUnit = priceUnitSplit[1];
			double itemQuantity = Double.parseDouble(itemQty);
			// find mdm key of this weight unit--
			String productId = individualData.get(PRODUCT_ID).toString();
			String weightUnitIdFromPriceUnit = findMatchingWeightUnit(individualData, productIdToWeightUnitMap,
					weightUnitFromPriceUnit, req);
			// if weight unit mismatches from itemQtyUnitId from TRM , do the unit conversion--
			if (!itemQtyUnitId.equalsIgnoreCase(weightUnitIdFromPriceUnit)) {
				// unit conversion--
				String conversionFactorString = getWeightUnitConversionFactor(individualData,
						productIdAndFromUnitAndToUnitToConversionMap, itemQtyUnitId, weightUnitIdFromPriceUnit, req);
				double conversionFactor = Double.parseDouble(conversionFactorString);
				try {
					calculatedCostValue = itemQuantity * applicableSettlePrice / conversionFactor;
				} catch (Exception e) {
					logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
							.encodeForHTML("Exception while calculating costValue with itemQuantity:" + itemQuantity
									+ " ,settlePrice:" + applicableSettlePrice + " ,unitConversionFactor:"
									+ conversionFactor + " due to:" + e.getLocalizedMessage()));
					throw new CostAppException(
							"Exception while calculating costValue due to: " + e.getLocalizedMessage(), e);
				}
			} else {
				// when units are same, but need to multiply for calculation--
				calculatedCostValue = itemQuantity * applicableSettlePrice;
			}
			return calculatedCostValue;
		}
		return null;
	}
	
	private String findMatchingWeightUnit(Map<String, Object> individualData,
			Map<String, List<Map<String, Object>>> existingProductIdToWeightUnitMap, String weightUnit,
			HttpServletRequest request) {
		String productId = individualData.get(PRODUCT_ID).toString();
		List<Map<String, Object>> weightUnitMappingToId = new ArrayList<>();
		if (existingProductIdToWeightUnitMap.containsKey(productId))
			weightUnitMappingToId.addAll(existingProductIdToWeightUnitMap.get(productId));
		else {
			weightUnitMappingToId.addAll(callMdmToFetchServiceKeyMapping(PHYSICAL_PRODUCT_QTY_LIST,
					new JSONArray().put(productId), request));
			existingProductIdToWeightUnitMap.put(productId, weightUnitMappingToId);
		}
		// find weight unit id of platform's priceunit--
		String weightUnitId = costAppUtils.findMatchingKeyFromValuesInMdmData(weightUnitMappingToId, weightUnit);
		return weightUnitId;
	}

	private String getWeightUnitConversionFactor(Map<String, Object> individualData,
			MultiKeyMap productIdAndFromUnitAndToUnitToConversionMap, String fromWeightUnitId, String toWeightUnitId,
			HttpServletRequest request) {
		String productId = individualData.get(PRODUCT_ID).toString();
		// make mdm call to fetch unit conversions for this unitId--
		List<Map<String, Object>> unitConversionFromMdm = new ArrayList<>();
		if (productIdAndFromUnitAndToUnitToConversionMap.containsKey(productId, toWeightUnitId, fromWeightUnitId))
			unitConversionFromMdm.addAll((List<Map<String, Object>>) productIdAndFromUnitAndToUnitToConversionMap
					.get(productId, toWeightUnitId, fromWeightUnitId));
		else {
			unitConversionFromMdm.addAll(callMdmToFetchServiceKeyMapping(QTY_CONVERSION_FACTOR,
					new JSONArray().put(productId).put(toWeightUnitId).put(fromWeightUnitId), request));
			productIdAndFromUnitAndToUnitToConversionMap.put(productId, toWeightUnitId, fromWeightUnitId,
					unitConversionFromMdm);
		}
		// extract conversion factor from mdm data--
		String conversionFactorString = unitConversionFromMdm.get(0).get(VALUE).toString();
		return conversionFactorString;
	}

	private List<Map<String, Object>> callMdmToFetchServiceKeyMapping(String serviceKey, JSONArray dependsOn,
			HttpServletRequest req) {
		Map<String, Object> payload = new HashMap<>();
		ResponseEntity<Map> mdmResult = null;
		try {
			String mdmUri = commonService.getPropertyFromConnect(req, "eka_mdm_host", null) + MDM_DATA_PATH;
			HttpHeaders httpHeaders = commonService.getHttpHeader(req);
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("calling mdm data api with payload :-" + payload));
			payload = preparePayloadForMdmServicekey(serviceKey, dependsOn);
			mdmResult = baseHttpClient.fireHttpRequest(new URI(mdmUri), HttpMethod.POST,
					new JSONArray().put(payload).toList(), httpHeaders, Map.class);
		} catch (HttpServerErrorException httpServerErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("mdm call inside method callMdmToFetchServiceKeyMapping with payload:"
							+ payload + " failed due to:" + httpServerErrorException.getLocalizedMessage()));
			throw new CostAppException("mdm call with payload:" + payload + " failed due to:"
					+ httpServerErrorException.getLocalizedMessage());
		} catch (URISyntaxException uriSyntaxException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception inside method callMdmToFetchServiceKeyMapping due to:"
							+ uriSyntaxException.getLocalizedMessage()));
			throw new CostAppException("URISyntaxException due to :" + uriSyntaxException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("mdm call with payload:" + payload + " failed due to:" + e.getLocalizedMessage()));
			throw new CostAppException("mdm call inside method callMdmToFetchServiceKeyMapping with payload:"
					+ payload + " failed due to:" + e.getLocalizedMessage());
		}
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("obtained response from workflow/mdm api :-" + mdmResult));

		if (Objects.nonNull(mdmResult) && Objects.nonNull(mdmResult.getBody())
				&& (((mdmResult.getBody().get(serviceKey) instanceof List)
						&& ((List) mdmResult.getBody().get(serviceKey)).isEmpty())
						|| (mdmResult.getBody().isEmpty()))) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("mdm data obtained for serviceKey:" + serviceKey + " is empty"));
			throw new CostAppException("mdm data obtained for serviceKey:" + serviceKey + " is empty");
		}
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("method callMdmToFetchServiceKeyMapping ends"));
		return (List<Map<String, Object>>) mdmResult.getBody().get(serviceKey);
	}
	
	private Map<String, Object> preparePayloadForMdmServicekey(String serviceKey, JSONArray dependsOn) {
		return new JSONObject().put(DEPENDS_ON, dependsOn).put(SERVICE_KEY, serviceKey).toMap();
	}
	
	private void updateCalculatedCostCurvesInDb(List<Map<String, Object>> estimatesToUpdateInDb, HttpHeaders headers)
			throws URISyntaxException {
		List<Map<String, Object>> estimatesWithId = new ArrayList<>();
		// bulk update supports only 'id' and not '_id' .So change to id--
		estimatesWithId = estimatesToUpdateInDb.stream().map(data -> {
			data.put("id", data.get(_ID));
			data.remove(_ID);
			return data;
		}).collect(Collectors.toList());
		estimatesToUpdateInDb = null;
		ResponseEntity<Map> bulkUpdateResult = null;
		String uri = ekaConnectHost + WORKFLOW_EXECUTION_PATH;
		bulkUpdateResult = baseHttpClient.fireHttpRequest(new URI(uri), HttpMethod.POST,
				preparePayloadForWorkflowExecutionCall(estimatesWithId, TASK_BULK_UPDATE_ESTIMATES), headers, Map.class);
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("response of bulk update:" + bulkUpdateResult.getBody()));
	}

	private Map<String, Object> preparePayloadForWorkflowExecutionCall(List<Map<String, Object>> dataList, String workflowTaskName) {
		return new JSONObject().put(WORKFLOW_TASK_NAME, workflowTaskName)
				.put(TASK, workflowTaskName).put(APP_NAME, APP_NAME_COST_APP).put(APP_ID, COST_APP_UUID)
				.put(OUTPUT, new JSONObject().put(workflowTaskName, dataList)).toMap();
	}
	
	private void addCostPriceunitIdInData(Map<String, Object> individualData,
			Map<String, List<Map<String, Object>>> productIdToServiceKeyValuesMap,
			MultiKeyMap instrumentAndMonthYearToPriceUnitMap, Object instrumentName, Object monthYear,
			HttpHeaders httpHeaders) {
		String priceUnit = Objects.nonNull(instrumentAndMonthYearToPriceUnitMap.get(instrumentName, monthYear))
				&& !instrumentAndMonthYearToPriceUnitMap.get(instrumentName, monthYear).toString().isEmpty()
						? instrumentAndMonthYearToPriceUnitMap.get(instrumentName, monthYear).toString()
						: null;
		if (Objects.isNull(priceUnit)) {
			logger.debug(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("priceUnit not found in collection response for instrumentName:"
							+ instrumentName + ", monthYear:" + monthYear));
			return;
		}
		Object productId = individualData.get(PRODUCT_ID);
		if (productIdToServiceKeyValuesMap.containsKey(productId)) {
			List<Map<String, Object>> productPriceUnits = productIdToServiceKeyValuesMap.get(productId);
			Object costPriceUnitId = findCostPriceUnitIdFromMdmResponse(productPriceUnits, priceUnit, productId);
			if (Objects.nonNull(costPriceUnitId)) {
				individualData.put(COST_PRICE_UNIT_ID, costPriceUnitId);
				individualData.put(CP_UNIT_ID_DISPLAY_NAME, priceUnit);
			}
		} else {
			Map<String, Object> workflowMdmResponse = costAppUtils.callWorkflowMdmApi(httpHeaders,
					preparePayloadToCallWorkflowMdm(productId));
			if (CollectionUtils.isEmpty(workflowMdmResponse) || !workflowMdmResponse.containsKey(PRODUCT_PRICE_UNIT)) {
				logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("productPriceUnit not found in mdm response for productId:" + productId));
				return;
			}
			List<Map<String, Object>> productPriceUnits = (List<Map<String, Object>>) workflowMdmResponse
					.get(PRODUCT_PRICE_UNIT);
			productIdToServiceKeyValuesMap.put(productId.toString(), productPriceUnits);
			Object costPriceUnitId = findCostPriceUnitIdFromMdmResponse(productPriceUnits, priceUnit, productId);
			if (Objects.nonNull(costPriceUnitId)) {
				individualData.put(COST_PRICE_UNIT_ID, costPriceUnitId);
				individualData.put(CP_UNIT_ID_DISPLAY_NAME, priceUnit);
			}
		}
	}
	
	private List<MongoOperations> createFilterOnAttributeDetails(List<Map<String, Object>> attributes) {
		List<MongoOperations> filter = new ArrayList<MongoOperations>();
		attributes.stream()
				.filter(attributeDetail -> costAppUtils.checkKeysNotAbsentOrEmptyInData(
						Arrays.asList(new String[] { ATTRIBUTE_NAME, ATTRIBUTE_VALUE }), attributeDetail))
				.forEach(attributeDetail -> {
					List<Object> values = new ArrayList<>();
					values.add(attributeDetail.get(ATTRIBUTE_VALUE));
					values.add(null);
					values.add("");
					filter.add(new MongoOperations(
							attributeDetail.get(ATTRIBUTE_NAME).toString(), values, "in"));
				});
		return filter;
	}
	
	private void saveCostEstimatesInDb(List<Map<String, Object>> estimates, HttpHeaders headers)
			throws URISyntaxException {
		if (CollectionUtils.isEmpty(estimates)) {
			return;
		}
		ResponseEntity<Map> bulkSaveResult = null;
		String uri = ekaConnectHost + WORKFLOW_EXECUTION_PATH;
		bulkSaveResult = baseHttpClient.fireHttpRequest(new URI(uri), HttpMethod.POST,
				preparePayloadForWorkflowExecutionCall(estimates, TASK_BULK_SAVE_ESTIMATES), headers, Map.class);
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("response of bulk update:" + bulkSaveResult.getBody()));
	}
	
	private void checkMandatoryFields(Map<String, Object> data, List<ConnectError> errors, List<ConnectError> internalErrors) {
		Iterator<String> iterator = MANDATORY_FIELDS_IN_GET_ALL_ESTIMATES.iterator();
		while (iterator.hasNext()) {
			checkFieldPresentAndGenerateError(data, iterator.next(), errors, internalErrors);
		}
	}

	private void checkFieldPresentAndGenerateError(Map<String, Object> data, String fieldName,
			List<ConnectError> errors, List<ConnectError> internalErrors) {
		if (!(data.containsKey(fieldName) && Objects.nonNull(data.get(fieldName))
				&& !data.get(fieldName).toString().isEmpty() && !data.get(fieldName).equals(0))) {
			String costComponentName = data.containsKey(COST_COMP_DISP_NAME)
					&& Objects.nonNull(data.get(COST_COMP_DISP_NAME))
					&& !data.get(COST_COMP_DISP_NAME).toString().isEmpty() ? data.get(COST_COMP_DISP_NAME).toString()
							: "not available";
			String entityRefNo = Objects.nonNull(data.get(ENTITY_REF_NO))
					&& !data.get(ENTITY_REF_NO).toString().isEmpty() ? data.get(ENTITY_REF_NO).toString()
							: "not available";
			String entityActualNo = Objects.nonNull(data.get(ENTITY_ACTUAL_NO))
					&& !data.get(ENTITY_ACTUAL_NO).toString().isEmpty() ? data.get(ENTITY_ACTUAL_NO).toString()
							: "not available";
			String _id = Objects.nonNull(data.get(_ID)) && !data.get(_ID).toString().isEmpty()
					? data.get(_ID).toString()
					: "not available";
			String rateTypePrice = Objects.nonNull(data.get(RATE_TYPE_PRICE))
					&& !data.get(RATE_TYPE_PRICE).toString().isEmpty() ? data.get(RATE_TYPE_PRICE).toString()
							: "not available";
			String costCurve = Objects.nonNull(data.get(COST_CURVE)) && !data.get(COST_CURVE).toString().isEmpty()
					? data.get(COST_CURVE).toString()
					: "not available";
			String fxToBaseType = Objects.nonNull(data.get(FX_TO_BASE_TYPE))
					&& !data.get(FX_TO_BASE_TYPE).toString().isEmpty() ? data.get(FX_TO_BASE_TYPE).toString()
							: "not available";
			internalErrors.add(new ConnectError(_500, "inappropriate cost estimate", "inappropriate cost estimate",
					"{cost component name:" + costComponentName + ", field name:" + fieldName + ", entityRefNo:"
							+ entityRefNo + ", entityActualNo:" + entityActualNo + ", _id:" + _id + ", rateTypePrice:"
							+ rateTypePrice + ", costCurve:" + costCurve + ", fxToBaseType:" + fxToBaseType + "}"));
			errors.add(new ConnectError(_500, "inappropriate cost estimate", "inappropriate cost estimate",
					"{cost component name:" + costComponentName + ", field name:" + fieldName + "}"));
		}
	}
	
	private void writeFieldValuesFromTarget(Map<String, Object> estimate, Map<String, Object> target) {
		estimate.put(CONTRACT_ITEM_REF_NO, estimate.get(ENTITY_ACTUAL_NO));
		Iterator<String> iterator = target.keySet().iterator();
		while (iterator.hasNext()) {
			String key = iterator.next();
			estimate.put(key, target.get(key));
		}
	}
	
	private Map<String, Object> preparePayloadFromTargetForGetAllEstimates(Map<String, Object> target,
			String applicableDate, String getDeletedData) {
		Map<String, Object> payload = new HashMap<>();
		Map<String, Object> itemDetail = new HashMap<>();
		itemDetail.put(ENTITY_TYPE, target.get(ENTITY_TYPE));
		itemDetail.put(ENTITY_REF_NO, target.get(ENTITY_REF_NO));
		itemDetail.put(APPLICABLE_DATE, applicableDate);
		List<Object> itemDetails = new ArrayList<>();
		itemDetails.add(itemDetail);
		payload.put(ITEM_DETAILS, itemDetails);
		payload.put(GET_DELETED_DATA, getDeletedData);
		return payload;
	}
	
	public Object updatePBSCostEstimates(HttpServletRequest request, Map<String, Object> requestBody) {
		try {
			ResponseEntity<Object> getResult = null;
			HttpHeaders headers = new HttpHeaders();
			headers.set("Authorization", request.getHeader("Authorization"));
			headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
			headers.add("X-TenantID", request.getHeader("X-TenantID"));
			headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));

			// Get platform URL
			String platform_url = commonService.getPropertyFromConnect(request, "platform_url", null);
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("UpdatePBSCostEstimates- platform_url : " + platform_url));
			
			// Step 1 - Call TRM api to get all CI linked with pbs refNo. Response: PBS
			LinkedHashMap pBSLinkDetails = fetchPbsLinkedContractItems(requestBody, request, headers, platform_url);
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("pBSLinkDetails : " + pBSLinkDetails));
			LinkedHashMap pBSLinkContactItemsData = (LinkedHashMap) pBSLinkDetails.get("data");
			Set<String> entityRefNoSet = new HashSet<String>();
			HashMap CIMapWithInternalConItemRefNo = new HashMap();
			if (null != pBSLinkContactItemsData.get("contractItemList")) {
				ArrayList pBSLinkContactItemsList = (ArrayList) pBSLinkContactItemsData.get("contractItemList");
				logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("size of pBSLinkContactItemsList : " + pBSLinkContactItemsList.size()));
				pBSLinkContactItemsList.stream().forEach(pBSLinkContact -> {
					LinkedHashMap pBSLinkedContactItems = (LinkedHashMap) pBSLinkContact;
					entityRefNoSet.add(pBSLinkedContactItems.get("internalContractItemRefNo").toString());
					CIMapWithInternalConItemRefNo.put(pBSLinkedContactItems.get("internalContractItemRefNo"),
							pBSLinkedContactItems);
				});
			}
			
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("entityRefNoSet : " + entityRefNoSet));

			// Step 2 - Get CI from CI1, CI2, CI3 to get all EST from connect
			String getCostEstimatesDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
					+ COST_ESTIMATE_OBJECT_UUID;
			ArrayList costEstimatesList = getAllESTList(headers, entityRefNoSet, getCostEstimatesDataUri);

			 Set<String> costCurveList = new HashSet<String>();
	         Set<String> monthYearList = new HashSet<String>();
	         prepareListOfInstrumentNamesForCostEstimates(costEstimatesList, costCurveList, monthYearList);

	         // call collections and prepare mapping only when costCurveList and
	         // monthYearList are not empty--
	         MultiKeyMap instrumentAndMonthYearMap = new MultiKeyMap();
	         MultiKeyMap instrumentAndMonthYearToPriceUnitMap = new MultiKeyMap();
	         Map<String,List<Map<String,Object>>> productIdToServiceKeyValuesMap = new HashMap<>();
			if (!costCurveList.isEmpty() && !monthYearList.isEmpty()) {
				// call fetch collections API with costcurveList and costMonthList--
				List<Map<String, Object>> collectionList = (List<Map<String, Object>>) fetchCollectionForCost(headers,
						costCurveList, monthYearList).getBody();
				// prepare mapping from curve,month to -> costvalue
				logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
						.encodeForHTML("no. of data fetched from platform collection= " + collectionList.size()));
				prepareMappingFromCostCurveAndMonthYearAndPricingDateToSettlePrice(collectionList,
						instrumentAndMonthYearMap);
				// prepare mapping from curve,month to priceUnit--
				prepareMappingFromCurveAndMonthToPriceUnit(collectionList, instrumentAndMonthYearToPriceUnitMap);
			}
			// Step 3 - Data modified on each CI with required updated fields for save/update of CI in connect ans TRM
			constructESTtoLinkPBSEstimates(pBSLinkContactItemsData, CIMapWithInternalConItemRefNo, costEstimatesList,headers,instrumentAndMonthYearMap,instrumentAndMonthYearToPriceUnitMap,productIdToServiceKeyValuesMap,request);
			
			// Step 4 - Get Saved pbs EST for Entity Ref no of pbs
			ArrayList SavedCostEstimatesList = getSavedPBSLinkedEstimates(headers, pBSLinkContactItemsData,
					getCostEstimatesDataUri,requestBody);
			
			//Step 5 - Filter Saved pbs linked CI withOutOriginalMongoId
			Set<Object> idsToDelete = new HashSet<>();
			ArrayList SavedCostEstimatesListWithOutOriginalMongoId = SavedCostEstimatesListWithOutOriginalMongoId(SavedCostEstimatesList);
			SavedCostEstimatesListWithOutOriginalMongoId = FilterCostEstimatesListWithOutOriginalMongoIdWhichHasUnLinkedWithPBS(SavedCostEstimatesListWithOutOriginalMongoId,CIMapWithInternalConItemRefNo,idsToDelete);
			constructESTWhichAlreadyLinkedPBS(pBSLinkContactItemsData, CIMapWithInternalConItemRefNo, SavedCostEstimatesListWithOutOriginalMongoId,headers);
			
			// Step 5 - filtering EST to be inserted/updated in both Connect and TRM
			ArrayList estimatesToUpdateTRM = new ArrayList();
			ArrayList updateCI = new ArrayList();
			ArrayList insertCI = new ArrayList();
			filterListOfEstimatesToCreateOrUpdatePBSCostEstimates(costEstimatesList, SavedCostEstimatesList, updateCI,
					insertCI);

			// Step 6 - filtering EST to be deleted in both Connect and TRM
			filterListOfEstimatesToDeletePBSCostEstimates(costEstimatesList, SavedCostEstimatesList,
					idsToDelete,CIMapWithInternalConItemRefNo);
			updateCI.addAll(SavedCostEstimatesListWithOutOriginalMongoId);

			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("size of updateCI  : " + updateCI.size()));
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("size of insertCI  : " + insertCI.size()));
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("size of idsToDele " + idsToDelete.size()));

			// Step 7 - insert pbs linked EST to connect
			insertPBSLinkedESTs(headers, estimatesToUpdateTRM, insertCI);

			// Step 8 - update pbs linked EST cost estimates to connect
			updatePBSLinkedESTs(headers, estimatesToUpdateTRM, updateCI);

			// Step 9 - delete pbs linked EST to connect
			deletePBSLinkedESTs(headers, estimatesToUpdateTRM, idsToDelete);

			// Step 10 -insert/Update/Delete pbs linked ESTs to TRM
			String trmUriToSaveEstimates = "${platform_url}/api/costEstimates/saveEstimates";
			trmUriToSaveEstimates = trmUriToSaveEstimates.replace("${platform_url}", platform_url);
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("size of final list eSTToUpdateTRM  : " + estimatesToUpdateTRM.size()));
			HttpEntity<Object> fetchTrmCollectionEntity = new HttpEntity<Object>(estimatesToUpdateTRM, headers);
			ResponseEntity<Object> trmSavedEstimatesResponseObj = restTemplate.exchange(trmUriToSaveEstimates,
					HttpMethod.POST, fetchTrmCollectionEntity, Object.class);
			LinkedHashMap trmSavedEstimatesResponse = (LinkedHashMap) trmSavedEstimatesResponseObj.getBody();
			logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
					.encodeForHTML("size of trmSavedEstimatesResponse  : " + trmSavedEstimatesResponse.size()));
			return trmSavedEstimatesResponseObj;
		} catch (HttpClientErrorException httpClientErrorException) {
			logger.error(Logger.EVENT_FAILURE,
					ESAPI.encoder().encodeForHTML("Exception while calling updatePBSCostEstimates due to:"
							+ httpClientErrorException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in updatePBSCostEstimates due to:" + httpClientErrorException.getLocalizedMessage());
		} catch (ResourceAccessException resourceAccessException) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder().encodeForHTML(
					"Exception while updatePBSCostEstimates due to:" + resourceAccessException.getLocalizedMessage()));
			throw new CostAppException(
					"Error in updatePBSCostEstimates due to:" + resourceAccessException.getLocalizedMessage());
		} catch (Exception e) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("Exception while calling updatePBSCostEstimates due to:" + e.getLocalizedMessage()));
			throw new CostAppException("Error in updatePBSCostEstimates due to:" + e.getLocalizedMessage(), e);
		}

	}

	private ArrayList SavedCostEstimatesListWithOutOriginalMongoId(ArrayList SavedCostEstimatesList) {
		ArrayList SavedCostEstimatesListWithOutOriginalMongoId = new ArrayList();
		Iterator iterator = SavedCostEstimatesList.iterator();
		while(iterator.hasNext()){
			LinkedHashMap SavedCostEstimate=(LinkedHashMap) iterator.next();
			if (!SavedCostEstimate.containsKey("originalMongoId")) {
				SavedCostEstimate.put("id", SavedCostEstimate.get(_ID));
				SavedCostEstimate.remove(_ID);
				SavedCostEstimatesListWithOutOriginalMongoId.add(SavedCostEstimate);
				iterator.remove();
			}
		}
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("SavedCostEstimatesListWithOutOriginalMongoId : " + SavedCostEstimatesListWithOutOriginalMongoId));
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("Size of SavedCostEstimatesListWithOutOriginalMongoId : " + SavedCostEstimatesListWithOutOriginalMongoId.size()));
		return SavedCostEstimatesListWithOutOriginalMongoId;
	}
	
	private ArrayList FilterCostEstimatesListWithOutOriginalMongoIdWhichHasUnLinkedWithPBS(
			ArrayList SavedCostEstimatesList, HashMap cIMapWithInternalConItemRefNo, Set<Object> idsToDelete) {
		Iterator iterator = SavedCostEstimatesList.iterator();
		while (iterator.hasNext()) {
			LinkedHashMap SavedCostEstimate = (LinkedHashMap) iterator.next();
			if (null != SavedCostEstimate.get("internalContractItemRefNo")
					&& !cIMapWithInternalConItemRefNo.containsKey(SavedCostEstimate.get("internalContractItemRefNo"))) {
				idsToDelete.add(SavedCostEstimate.get("id"));
				iterator.remove();
			}
		}
		return SavedCostEstimatesList;
	}
	
	private void constructESTWhichAlreadyLinkedPBS(LinkedHashMap pBSLinkContactItems, HashMap itemQtyMapNew,
			ArrayList savedcostEstimatesList, HttpHeaders headers) {
		savedcostEstimatesList.stream().forEach(costEstimate -> {
			LinkedHashMap costEstimates = (LinkedHashMap) costEstimate;
			double itemQtyNewFromTRM1;
			if (null != costEstimates.get("internalContractItemRefNo") && !itemQtyMapNew.isEmpty()) {
				Map itemQtyNewFromTRM = (Map) itemQtyMapNew.get(costEstimates.get("internalContractItemRefNo"));
				if (null == itemQtyNewFromTRM) {
					return;
				}
				itemQtyNewFromTRM1 = Double.parseDouble(itemQtyNewFromTRM.get("itemQty").toString());
				costEstimates.put("contractItemRefNo", itemQtyNewFromTRM.get("contractRefNo"));
				costEstimates.put("contractItemRefNoDisplayName", itemQtyNewFromTRM.get("contractRefNo"));
			} else {
				itemQtyNewFromTRM1 = Double.parseDouble(pBSLinkContactItems.get("quantity").toString());
			}

			Double costValueOfContract = Double.parseDouble(costEstimates.get("costValue").toString());
			Object costAmountInBaseCurrency = costValueOfContract * itemQtyNewFromTRM1;
			costEstimates.put("costAmountInBaseCurrency", costAmountInBaseCurrency);
			costEstimates.put("quantity", itemQtyNewFromTRM1 + " " + pBSLinkContactItems.get("quantityUnit"));
			costEstimates.put("itemQty", itemQtyNewFromTRM1);
		});
	}

	private void filterListOfEstimatesToDeletePBSCostEstimates(ArrayList costEstimatesList,
			ArrayList SavedCostEstimatesList, Set<Object> idsToDelete, HashMap cIMapWithInternalConItemRefNo) {
		if (!cIMapWithInternalConItemRefNo.isEmpty() || !SavedCostEstimatesList.isEmpty() ) {
			SavedCostEstimatesList.stream().forEach(savedcostEstimate -> {
				LinkedHashMap SavedCostEstimates = (LinkedHashMap) savedcostEstimate;
				boolean isCIExist = false;

				for (int i = 0; i < costEstimatesList.size(); i++) {
					LinkedHashMap costEstimatesNew = (LinkedHashMap) costEstimatesList.get(i);
					if (costEstimatesNew.get("sys__UUID").equals(SavedCostEstimates.get("originalMongoId"))) {
						isCIExist = true;
						break;
					}
				}
				if (!isCIExist) {
					idsToDelete.add(SavedCostEstimates.get(_ID));
				}
			});
		}
	}

	private void filterListOfEstimatesToCreateOrUpdatePBSCostEstimates(ArrayList costEstimatesList,
			ArrayList SavedCostEstimatesList, ArrayList updateCI, ArrayList insertCI) {
		costEstimatesList.stream().forEach(costEstimate -> {
			LinkedHashMap costEstimates = (LinkedHashMap) costEstimate;
			boolean isInserted = false;
			for (int i = 0; i < SavedCostEstimatesList.size(); i++) {
				LinkedHashMap costEstimatesNew = (LinkedHashMap) SavedCostEstimatesList.get(i);
				if (costEstimates.get("sys__UUID").equals(costEstimatesNew.get("originalMongoId"))) {
					costEstimates.put("id", costEstimatesNew.get(_ID));
					costEstimates.remove(_ID);
					updateCI.add(costEstimates);
					isInserted = true;
					break;
				}
			}
			if (!isInserted) {
				insertCI.add(costEstimates);
			}
		});
	}

	private ArrayList getSavedPBSLinkedEstimates(HttpHeaders headers, LinkedHashMap pBSLinkContactItems,
			String getCostEstimatesDataUri, Map<String, Object> requestBody) {
		Set<String> entityRefNoSet1 = new HashSet<String>();
		entityRefNoSet1.add(requestBody.get("entityRefNo").toString());
		Map<String, Object> payloadToFetchActualEstimates1 = new HashMap<>();
		MongoOperations filterOnEntityRefNo1 = new MongoOperations(ENTITY_REF_NO, entityRefNoSet1, "in");
		List<MongoOperations> filter1 = new ArrayList<MongoOperations>();
		filter1.add(filterOnEntityRefNo1);
		FilterData filterData1 = new FilterData(filter1);
		payloadToFetchActualEstimates1.put("filterData", filterData1);
		HttpEntity<Object> entity1 = new HttpEntity<Object>(payloadToFetchActualEstimates1, headers);
		ResponseEntity<Object> costEstimatesResult1 = restTemplateGetWityBody.getRestTemplate()
				.exchange(getCostEstimatesDataUri, HttpMethod.GET, entity1, Object.class);
		ArrayList SavedCostEstimatesList = (ArrayList) costEstimatesResult1.getBody();
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("SavedCostEstimatesList : " + SavedCostEstimatesList));
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("Size of SavedCostEstimatesList : " + SavedCostEstimatesList.size()));
		return SavedCostEstimatesList;
	}

	private void constructESTtoLinkPBSEstimates(LinkedHashMap pBSLinkContactItems, HashMap CIMapWithInternalConItemRefNo,
			ArrayList costEstimatesList, HttpHeaders headers, MultiKeyMap instrumentAndMonthYearMap, MultiKeyMap instrumentAndMonthYearToPriceUnitMap, Map<String, List<Map<String, Object>>> productIdToServiceKeyValuesMap, HttpServletRequest request) {
		costEstimatesList.stream().forEach(costEstimate -> {
			LinkedHashMap costEstimates = (LinkedHashMap) costEstimate;
			Map itemQtyNewFromTRM = (Map) CIMapWithInternalConItemRefNo.get(costEstimates.get("entityRefNo"));
			double itemQtyNewFromTRM1 = 0;
			if (itemQtyNewFromTRM.containsKey("itemQty")) {
				itemQtyNewFromTRM1 = Double.parseDouble(itemQtyNewFromTRM.get("itemQty").toString());
			}
			if (costEstimates.containsKey("rateTypePrice") && costEstimates.get("rateTypePrice").equals("rate")) {
				double costValueOfContract = 0;
				if (costEstimates.containsKey("costValue")) {
					costValueOfContract = Double.parseDouble(costEstimates.get("costValue").toString());
				}
				costEstimates.put("costAmountInBaseCurrency", costValueOfContract * itemQtyNewFromTRM1);
			} else if (costEstimates.containsKey("rateTypePrice")
					&& costEstimates.get("rateTypePrice").equals("% of Price")) {
				double costValueOfContract = Double.parseDouble(costEstimates.get("costValue").toString());
				String contractPrice[] = ((String) costEstimates.get("contractPrice")).split(" ");
				String contractPriceValue = contractPrice[0];
				String costPriceUnitIdDisplayName = contractPrice[1];
				costEstimates.put("costAmountInBaseCurrency",
						(costValueOfContract * itemQtyNewFromTRM1 * Double.parseDouble(contractPriceValue)) / 100);
				costEstimates.put("costValue", (costValueOfContract * Double.parseDouble(contractPriceValue)) / 100);
				costEstimates.put("costPriceUnitIdDisplayName", costPriceUnitIdDisplayName);
				costEstimates.put("rateTypePrice", "rate");
				costEstimates.put("rateTypePriceDisplayName", "Rate");
				Object costPriceUnitId = callMDMToGetCostPriceUnitId(headers, costEstimates,
						costPriceUnitIdDisplayName);
				if (Objects.nonNull(costPriceUnitId)) {
					costEstimates.put(COST_PRICE_UNIT_ID, costPriceUnitId);
				}
			} else if (costEstimates.containsKey("rateTypePrice")
					&& costEstimates.get("rateTypePrice").equals("curve")) {
				// make list of cost curves and month-years --
				// replace cost amount and costPriceUnitId in data--
				costEstimates.put("itemQty", itemQtyNewFromTRM1);
				Map<String, List<Map<String, Object>>> productIdToWeightUnitMap = new HashMap<>();
				MultiKeyMap productIdAndFromUnitAndToUnitToConversionMap = new MultiKeyMap<>();
				List<Map<String, Object>> currencylistMap = new ArrayList<>();
				Object instrumentName = costEstimates.get("costCurve");
				Object monthYear = costEstimates.get("costMonth");
				DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
				LocalDateTime now = LocalDateTime.now();
				String applicableDate = dtf.format(now);
				Object applicableCostAmount = findApplicableCostAmount(instrumentName, monthYear, applicableDate,
						instrumentAndMonthYearMap, 30);
				// do cost value/ cost amount calculation--
				List<Object> calculationParams = new LinkedList<>();
				calculationParams.add(applicableCostAmount);
				calculationParams.add(instrumentName);
				calculationParams.add(monthYear);
				Object calculatedCost = calculateCostAmount(calculationParams, costEstimates,
						instrumentAndMonthYearToPriceUnitMap, request, productIdToWeightUnitMap,
						productIdAndFromUnitAndToUnitToConversionMap, currencylistMap);
				costEstimates.put("costAmountInBaseCurrency", Objects.nonNull(calculatedCost) ? calculatedCost : 0);
				costEstimates.put("costValue", Objects.nonNull(calculatedCost) ? applicableCostAmount : 0);
				addCostPriceunitIdInData(costEstimates, productIdToServiceKeyValuesMap,
						instrumentAndMonthYearToPriceUnitMap, instrumentName, monthYear, headers);
			}
			if (costEstimates.containsKey("estimateFor")
					&& costEstimates.get("estimateFor").equals("Execution & Valuation")) {
				costEstimates.put("estimateFor", "Execution");
				costEstimates.put("estimateForDisplayName", "Execution");
			}
			String contractItemRefNo = (String) itemQtyNewFromTRM.get("contractRefNo");
			costEstimates.put("contractItemRefNo", contractItemRefNo);
			costEstimates.put("contractItemRefNoDisplayName", contractItemRefNo);
			costEstimates.put("profitCenter", itemQtyNewFromTRM.get("profitCenter"));
			costEstimates.put("quantity", itemQtyNewFromTRM1 + " " + pBSLinkContactItems.get("quantityUnit"));
			costEstimates.put("itemQty", itemQtyNewFromTRM1);
			costEstimates.put("internalContractItemRefNo", costEstimates.get("entityRefNo"));
			costEstimates.put("originalMongoId", costEstimates.get("sys__UUID"));
			costEstimates.put("entityActualNo", pBSLinkContactItems.get("entityActualNo"));
			costEstimates.put("entityRefNo", pBSLinkContactItems.get("entityRefNo"));
			costEstimates.put("entityType", "Planned Shipment");
		});
	}

	private Object callMDMToGetCostPriceUnitId(HttpHeaders headers, LinkedHashMap costEstimates,
			String costPriceUnitIdDisplayName) {
		Map<String, Object> workflowMdmResponse = costAppUtils.callWorkflowMdmApi(headers,
				preparePayloadToCallWorkflowMdm(costEstimates.get("productId")));
		if (CollectionUtils.isEmpty(workflowMdmResponse) || !workflowMdmResponse.containsKey(PRODUCT_PRICE_UNIT)) {
			logger.debug(Logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("productPriceUnit not found in mdm response"));
			throw new CostAppException(
					"Error in callMDMToGetCostPriceUnitId due to productPriceUnit not found in mdm response");
		}
		List<Map<String, Object>> productPriceUnits = (List<Map<String, Object>>) workflowMdmResponse
				.get(PRODUCT_PRICE_UNIT);
		Object costPriceUnitId = findCostPriceUnitIdFromMdmResponse(productPriceUnits, costPriceUnitIdDisplayName,
				costEstimates.get("productId"));
		return costPriceUnitId;
	}

	private ArrayList getAllESTList(HttpHeaders headers, Set<String> entityRefNoSet, String getCostEstimatesDataUri) {
		ArrayList costEstimatesList = new ArrayList();
		if (!entityRefNoSet.isEmpty()) {
			Map<String, Object> payloadToFetchActualEstimates = new HashMap<>();
			MongoOperations filterOnEntityRefNo = new MongoOperations(ENTITY_REF_NO, entityRefNoSet, "in");
			MongoOperations filterOnValuationNE = new MongoOperations("estimateFor", "Valuation", "ne");
			List<MongoOperations> filter = new ArrayList<MongoOperations>();
			filter.add(filterOnEntityRefNo);
			filter.add(filterOnValuationNE);
			FilterData filterData = new FilterData(filter);
			payloadToFetchActualEstimates.put("filterData", filterData);
			HttpEntity<Object> entity = new HttpEntity<Object>(payloadToFetchActualEstimates, headers);
			ResponseEntity<Object> costEstimatesResult = restTemplateGetWityBody.getRestTemplate()
					.exchange(getCostEstimatesDataUri, HttpMethod.GET, entity, Object.class);
		    costEstimatesList = (ArrayList) costEstimatesResult.getBody();
		}
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("costEstimatesList : " + costEstimatesList));
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML("Size of costEstimatesList : " + costEstimatesList.size()));
		return costEstimatesList;
	}

	private void deletePBSLinkedESTs(HttpHeaders headers, ArrayList eSTToUpdateTRM, Set<Object> idsToDelete)
			throws URISyntaxException {
		if (!idsToDelete.isEmpty()) {
			String bulkDeleteApi = ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID
					+ "/bulkDelete";
			Map<String, Object> payloadToBulkDelete = new HashMap<>();
			List<MongoOperations> deleteFliter = new ArrayList<MongoOperations>();
			deleteFliter.add(new MongoOperations(_ID, idsToDelete, "in"));
			FilterData deleteFilterData = new FilterData(deleteFliter);
			payloadToBulkDelete.put("filterData", deleteFilterData);
			ResponseEntity<List> pbsLinkedContractDeleteResponseOfConnect = baseHttpClient.fireHttpRequest(
					new URI(bulkDeleteApi), HttpMethod.DELETE, payloadToBulkDelete, headers, List.class);
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("size of pbsLinkedContractDeleteResponseOfConnect  : "
							+ pbsLinkedContractDeleteResponseOfConnect.getBody().size()));
			eSTToUpdateTRM.addAll(pbsLinkedContractDeleteResponseOfConnect.getBody());
		}
	}

	private void updatePBSLinkedESTs(HttpHeaders headers, ArrayList eSTToUpdateTRM, ArrayList updateCI)
			throws URISyntaxException {
		if (!updateCI.isEmpty()) {
			String idToUpdate = null;
			ResponseEntity<Object> updateResponse = baseHttpClient.fireHttpRequest(new URI(
					ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID + "/" + idToUpdate),
					HttpMethod.PUT, updateCI, headers, Object.class);
			ArrayList pbsLinkedContractUpdateResponseOfConnect = (ArrayList) updateResponse.getBody();
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("size of pbsLinkedContractUpdateResponseOfConnect  : "
							+ pbsLinkedContractUpdateResponseOfConnect.size()));
			eSTToUpdateTRM.addAll(pbsLinkedContractUpdateResponseOfConnect);
		}
	}

	private void insertPBSLinkedESTs(HttpHeaders headers, ArrayList eSTToUpdateTRM, ArrayList insertCI)
			throws URISyntaxException {
		ArrayList pbsLinkedContractSavedResponseOfConnect = new ArrayList();
		if (!insertCI.isEmpty()) {
			ResponseEntity<Object> saveResponse = baseHttpClient.fireHttpRequest(
					new URI(ekaConnectHost + "/data/" + COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID),
					HttpMethod.POST, insertCI, headers, Object.class);
			pbsLinkedContractSavedResponseOfConnect = (ArrayList) saveResponse.getBody();
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("size of pbsLinkedContractSavedResponseOfConnect  : "
							+ pbsLinkedContractSavedResponseOfConnect.size()));
			eSTToUpdateTRM.addAll(pbsLinkedContractSavedResponseOfConnect);
		}
	}
	
	/**
	 * Update contract item cost estimates.
	 *
	 * @param request the request
	 * @param requestBody the request body
	 * @return the object
	 * @throws URISyntaxException the URI syntax exception
	 */
	public Object updateContractItemCostEstimates(HttpServletRequest request, Map<String, Object> requestBody) throws URISyntaxException {
		ResponseEntity<Object> getResult = null;
		HttpHeaders headers = new HttpHeaders();
		headers.set("Authorization", request.getHeader("Authorization"));
		headers.setContentType(MediaType.APPLICATION_JSON_UTF8);
		headers.add("X-TenantID", request.getHeader("X-TenantID"));
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON_UTF8));
		String draftEstimateNo=(String) requestBody.get("draftEstimateNo");
		String entityType=(String) requestBody.get("entityType");
		
		List<Map<String, Object>> costComponentsDraft = new ArrayList<>();
		String costComponentsDraftDataUri = ekaConnectHost + "/data/" + COST_APP_UUID + "/"
				+ DRAFT_COST_ESTIMATE_OBJECT_UUID + "?" + DRAFT_ESTIMATE_NO + "=" + draftEstimateNo + "&" + ENTITY_TYPE + "=" + entityType;
		
		HttpEntity<Object> entity = new HttpEntity<Object>(null, headers);
		getResult = restTemplate.exchange(costComponentsDraftDataUri, HttpMethod.GET, entity, Object.class);
		List<Map<String, Object>> dataList =null;
		if (getResult != null) {
			dataList = (List<Map<String, Object>>) getResult.getBody();
		}
		 Set<String> costCurveList = new HashSet<String>();
         Set<String> monthYearList = new HashSet<String>();
         prepareListOfInstrumentNamesForCostEstimates(dataList, costCurveList, monthYearList);

         // call collections and prepare mapping only when costCurveList and
         // monthYearList are not empty--
         MultiKeyMap instrumentAndMonthYearMap = new MultiKeyMap();
         MultiKeyMap instrumentAndMonthYearToPriceUnitMap = new MultiKeyMap();
         Map<String,List<Map<String,Object>>> productIdToServiceKeyValuesMap = new HashMap<>();
         if (!costCurveList.isEmpty() && !monthYearList.isEmpty()) {
             // call fetch collections API with costcurveList and costMonthList--
             List<Map<String, Object>> collectionList = (List<Map<String, Object>>) fetchCollectionForCost(headers,
                     costCurveList, monthYearList).getBody();
             // prepare mapping from curve,month to -> costvalue
             logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
                     .encodeForHTML("no. of data fetched from platform collection= " + collectionList.size()));
             prepareMappingFromCostCurveAndMonthYearAndPricingDateToSettlePrice(collectionList,
                     instrumentAndMonthYearMap);
             //prepare mapping from curve,month to priceUnit--
             prepareMappingFromCurveAndMonthToPriceUnit(collectionList, instrumentAndMonthYearToPriceUnitMap);
         }
		ArrayList updateCI = new ArrayList();
		dataList.stream().forEach(costEstimate -> {
			LinkedHashMap costEstimates = (LinkedHashMap) costEstimate;
			if (costEstimates.containsKey("costValue") && !StringUtils.isEmpty(costEstimates.get("costValue"))) {
				if (costEstimates.containsKey("rateTypePrice") && costEstimates.get("rateTypePrice").equals("rate")) {
					double costValueOfContract = Double.parseDouble(costEstimates.get("costValue").toString());
					double itemQtyNewFromTRM1 = Double.parseDouble(requestBody.get("itemQty").toString());
					double conversionFactor = Double.parseDouble(costEstimates.get("conversionFactor").toString());
					costEstimates.put("costAmountInBaseCurrency", (costValueOfContract * itemQtyNewFromTRM1)/conversionFactor);
					costEstimates.put("itemQty", itemQtyNewFromTRM1);
					costEstimates.put("contractPrice", requestBody.get("contractPrice"));
				} else if (costEstimates.containsKey("rateTypePrice")
						&& costEstimates.get("rateTypePrice").equals("% of Price")) {
					double itemQtyNewFromTRM1 = Double.parseDouble(requestBody.get("itemQty").toString());
					double costValueOfContract = Double.parseDouble(costEstimates.get("costValue").toString());
					String contractPrice[] = ((String) requestBody.get("contractPrice")).split(" ");
					String contractPriceValue = contractPrice[0];
					String costPriceUnitIdDisplayName = contractPrice[1];
					costEstimates.put("costAmountInBaseCurrency",
							(costValueOfContract * itemQtyNewFromTRM1 * Double.parseDouble(contractPriceValue)) / 100);
					costEstimates.put("itemQty", itemQtyNewFromTRM1);
					costEstimates.put("contractPrice", requestBody.get("contractPrice"));
				}}
				if (costEstimates.containsKey("rateTypePrice")
					&& costEstimates.get("rateTypePrice").equals("curve")) {
				// make list of cost curves and month-years --
				// replace cost amount and costPriceUnitId in data--
				double itemQtyNewFromTRM1 = Double.parseDouble(requestBody.get("itemQty").toString());
				costEstimates.put("itemQty", itemQtyNewFromTRM1);
				Map<String, List<Map<String, Object>>> productIdToWeightUnitMap = new HashMap<>();
				MultiKeyMap productIdAndFromUnitAndToUnitToConversionMap = new MultiKeyMap<>();
				List<Map<String, Object>> currencylistMap = new ArrayList<>();
				Object instrumentName = costEstimates.get("costCurve");
				Object monthYear = costEstimates.get("costMonth");
				DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy");
				LocalDateTime now = LocalDateTime.now();
				String applicableDate = dtf.format(now);
				Object applicableCostAmount = findApplicableCostAmount(instrumentName, monthYear, applicableDate,
						instrumentAndMonthYearMap, 30);
				// do cost value/ cost amount calculation--
				List<Object> calculationParams = new LinkedList<>();
				calculationParams.add(applicableCostAmount);
				calculationParams.add(instrumentName);
				calculationParams.add(monthYear);
				Object calculatedCost = calculateCostAmount(calculationParams, costEstimates,
						instrumentAndMonthYearToPriceUnitMap, request, productIdToWeightUnitMap,
						productIdAndFromUnitAndToUnitToConversionMap, currencylistMap);
				costEstimates.put("costAmountInBaseCurrency", Objects.nonNull(calculatedCost) ? calculatedCost : 0);
				costEstimates.put("costValue", Objects.nonNull(calculatedCost) ? applicableCostAmount : 0);
				addCostPriceunitIdInData(costEstimates, productIdToServiceKeyValuesMap,
						instrumentAndMonthYearToPriceUnitMap, instrumentName, monthYear, headers);
			}
				if (costEstimates.containsKey("rateTypePrice") && costEstimates.get("rateTypePrice").equals("absolute")) {
					double itemQtyNewFromTRM1 = Double.parseDouble(requestBody.get("itemQty").toString());
					costEstimates.put("itemQty", itemQtyNewFromTRM1);
					costEstimates.put("contractPrice", requestBody.get("contractPrice"));
				}
				costEstimates.put("id", costEstimates.get(_ID));
				costEstimates.remove(_ID);
				updateCI.add(costEstimates);
			
		});
		
		if (!updateCI.isEmpty()) {
			String idToUpdate = null;
			ResponseEntity<Object> updateResponse = baseHttpClient.fireHttpRequest(new URI(
					ekaConnectHost + "/data/" + COST_APP_UUID + "/" + DRAFT_COST_ESTIMATE_OBJECT_UUID + "/" + idToUpdate),
					HttpMethod.PUT, updateCI, headers, Object.class);
			ArrayList updateContractItemCostEstimates = (ArrayList) updateResponse.getBody();
			logger.debug(logger.EVENT_SUCCESS,
					ESAPI.encoder().encodeForHTML("size of updateContractItemCostEstimates  : "
							+ updateContractItemCostEstimates.size()));
		}
     return updateCI;
	}
	
	private LinkedHashMap fetchPbsLinkedContractItems(Map<String, Object> requestBody, HttpServletRequest request,
			HttpHeaders headers, String platform_url) {
		// Get UserDetails of platform
		LinkedHashMap currentUserDetails = commonService.getCurrentUserDetails(headers, platform_url);

		// Step 1 - Call TRM api to get all CI linked with pbs refNo. Response: PBS
		// quantity[{CI1,Q-50..}{CI2, Q-60}, {CI3, Q-100}]
		String trmUriToGetPBSLinkDetails = "${platform_url}/api/costEstimates/getPBSLinkDetails";
		trmUriToGetPBSLinkDetails = trmUriToGetPBSLinkDetails.replace("${platform_url}", platform_url);
		headers.set("userName", currentUserDetails.get("userId").toString());
		HttpEntity<Object> fetchPBSLinkEntity = new HttpEntity<Object>(requestBody, headers);
		ResponseEntity<Object> fetchPBSLinkDetailsResponse = restTemplate.exchange(trmUriToGetPBSLinkDetails,
				HttpMethod.POST, fetchPBSLinkEntity, Object.class);
		if (Objects.nonNull(fetchPBSLinkDetailsResponse) && Objects.nonNull(fetchPBSLinkDetailsResponse.getBody())
				&& fetchPBSLinkDetailsResponse.getBody() instanceof LinkedHashMap)
			return (LinkedHashMap) fetchPBSLinkDetailsResponse.getBody();
		return new LinkedHashMap<>();
	}
}
