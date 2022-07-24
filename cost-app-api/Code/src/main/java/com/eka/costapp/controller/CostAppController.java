package com.eka.costapp.controller;

import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.owasp.esapi.Logger;
import org.owasp.esapi.ESAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eka.costapp.exception.ConnectException;
import com.eka.costapp.exception.CostAppException;
import com.eka.costapp.service.CostAppService;

@RestController
@RequestMapping("/costapp")
public class CostAppController {

	final static Logger logger = ESAPI.getLogger(CostAppController.class);

	@Autowired
	CostAppService costService;

	/**
	 * Usage:The <i>ConvertDraftToActualCost</i> endpoint will convert draft cost
	 * estimate to actual cost estimate and return the converted data
	 * 
	 * @param requestBody the mapping of draftEstimateNo to entityRefNo
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/associateEstimatesWithEntity")
	public Object ConvertDraftToActualCost(@RequestBody Map<String, List<String>> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML(
						"In Cost Controller ConvertDraftToActualCost method execution started with requestBody:"
								+ requestBody));
		Object response = null;
		response = costService.ConvertDraftToActualCost(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller ConvertDraftToActualCost method execution ends"));
		return response;

	}
	
	/**
	 * Usage:The <i>ConvertDraftToActualCost2</i> endpoint will convert draft cost
	 * estimate to actual cost estimate and return the converted data
	 * 
	 * @param requestBody the mapping of draftEstimateNo to entityRefNo
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/associateEstimatesWithEntityTemp")
	public Object ConvertDraftToActualCostTemp(@RequestBody Map<String, List<String>> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML(
						"In Cost Controller ConvertDraftToActualCostTemp method execution started with requestBody:"
								+ requestBody));
		Object response = null;
		response = costService.convertDraftToActualCostTemp(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller ConvertDraftToActualCost method execution ends"));
		return response;

	}
	
	/**
	 * Usage:The <i>ConvertActualCostEstimateToDraft</i> endpoint will convert
	 * actual cost estimate to draft cost estimate and return the converted
	 * data.This API is used for draft contract save/modify
	 * 
	 * @param requestBody the mapping of entityRefNo to draftEstimateNo
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/pushToDraft")
	public Object ConvertActualToDraftCostEstimate(@RequestBody Map<String, List<String>> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML(
						"In Cost Controller ConvertActualToDraftCostEstimate method execution started with requestBody:"
								+ requestBody));
		Object response = null;
		response = costService.ConvertActualToDraftCost(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder()
				.encodeForHTML("In Cost Controller ConvertActualToDraftCostEstimate method execution ends"));
		return response;
	}
	
	/**
	 * Usage:The <i>cloneDraftWithNewEstimateNo</i> endpoint will clone draft
	 * estimates from one draftEstimateNo to another.This API is used when item is
	 * modified while cloning contract on TRM side
	 * 
	 * @param requestBody the mapping of one draftEstimateNo to new draftEstimateNo
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/cloneDraftEstimate")
	public Object cloneDraftWithNewEstimateNo(@RequestBody Map<String, List<String>> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML(
						"In Cost Controller cloneDraftWithNewEstimateNo method execution started with requestBody:"
								+ requestBody));
		Object response = null;
		response = costService.cloneDraftWithNewEstimateNo(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller cloneDraftWithNewEstimateNo method execution ends"));
		return response;
	}

	/**
	 * Usage:The <i>getCostEstimateData</i> endpoint will get the cost estimate data
	 * 
	 * @param request
	 * @param queryParams
	 * @return Object
	 */
	@PostMapping("/getAllEstimates")
	public Object getCostEstimateData(@RequestBody(required = false) Map<String, Object> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
				"In Cost Controller getCostEstimateData method execution started with requestBody:" + requestBody));
		Object response = null;
		response = costService.getCostEstimateData(requestBody, request);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller getCostEstimateData method execution ends"));
		return response;

	}

	/**
	 * Usage:The <i>getInstrumentNames</i> endpoint will get list of unique
	 * instrument names for cost curves
	 * 
	 * @param refTypeId  the app UUID
	 * @param objectUUID the object UUID
	 * @param request
	 * @return Object
	 */
	@GetMapping("/{refTypeId}/{objectUUID}/instrumentNames")
	public Object getInstrumentNames(@Valid @PathVariable("refTypeId") String refTypeId,
			@PathVariable("objectUUID") String objectUUID,
			@RequestParam(required = false, name = "costComponent") String costComponent, HttpServletRequest request)
			throws CostAppException {
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller getInstrumentNames method execution started"));
		Object response = null;
		response = costService.getInstrumentNames(request, refTypeId, objectUUID, costComponent);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller getInstrumentNames method execution ends"));
		return response;
	}

	/**
	 * Usage:The <i>instrumentNamesFx</i> endpoint will get list of unique
	 * instrument names for fx curves
	 * 
	 * @param refTypeId  the app UUID
	 * @param objectUUID the object UUID
	 * @param request
	 * @return Object
	 */
	@GetMapping("/{refTypeId}/{objectUUID}/instrumentNamesFx")
	public Object getInstrumentNamesFx(@Valid @PathVariable("refTypeId") String refTypeId,
			@PathVariable("objectUUID") String objectUUID, HttpServletRequest request) throws CostAppException {
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller getInstrumentNamesFx method execution started"));
		Object response = null;
		response = costService.getInstrumentNamesFx(request, refTypeId, objectUUID);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller getInstrumentNamesFx method execution ends"));
		return response;
	}
	
	/**
	 * Usage:The <i>getCostComponents</i> endpoint will return the cost components
	 * mandatory to a contract
	 * 
	 * @param requestBody the contract details/attributes
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/getCostComponents")
	public Object getCostComponents(@RequestBody(required = false) Map<String, Object> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
				"In Cost Controller getCostComponents method execution started with requestBody:" + requestBody));
		Object response = null;
		response = costService.getCostComponents(request, requestBody,new HashSet<>());
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller getCostComponents method execution ends"));
		return response;
	}
	
	/**
	 * Usage:The <i>validateCostEstimates</i> endpoint will validate the passed cost estimates and show success/failure message
	 * 
	 * @param requestBody the cost estimates to be validated
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/validateCostEstimates")
	public Object validateCostEstimates(@RequestBody(required = false) Map<String, Object> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller validateCostEstimates method execution started"));
		Object response = null;
		response = costService.validateCostEstimates(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller validateCostEstimates method execution ends"));
		return response;
	}
	
	/**
	 * Usage:The <i>saveCostEstimates</i> endpoint will validate the passed cost
	 * estimates and save them
	 * 
	 * @param requestBody the cost estimates to be saved
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/saveCostEstimates")
	public Object saveCostEstimates(@RequestBody(required = false) Map<String, Object> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller saveCostEstimates method execution started"));
		Object response = null;
		response = costService.saveCostEstimates(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller saveCostEstimates method execution ends"));
		return response;
	}
	
	/**
	 * Usage:The <i>saveMandatoryCostsToDraft</i> endpoint will default the madatory
	 * cost components to draft cost estimates and save them
	 * 
	 * @param requestBody the contract attributes and the drafteEstimateNo to
	 *                    default to
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/saveMandatoryCostsToDraft")
	public Object saveMandatoryCostsToDraft(@RequestBody(required = false) Map<String, Object> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML(
						"In Cost Controller saveMandatoryCostsToDraft method execution started with requestBody:"
								+ requestBody));
		Object response = null;
		response = costService.saveMandatoryCostsToDraft(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller saveMandatoryCostsToDraft method execution ends"));
		return response;
	}
	
	/**
	 * Usage:The <i>saveCostEstimates</i> endpoint will copy the estimates that
	 * match the parameters passed in source and save them into connect db with
	 * modifications passed in target of payload
	 * 
	 * @param requestBody the source and target attributes
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/copyEstimates")
	public Object copyEstimates(@RequestBody(required = false) Map<String, Object> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
				"In Cost Controller copyEstimates method execution started with requestBody:" + requestBody));
		Object response = null;
		response = costService.copyEstimates(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller copyEstimates method execution ends"));
		return response;
	}
	
	/**
	 * Usage:The <i>copyEstimatesForPBS</i> endpoint will copy the estimates that
	 * match the parameters passed in source and save them into connect db with
	 * modifications passed in target of payload
	 * 
	 * @param requestBody the source and target attributes
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/copyEstimatesForPBS")
	public Object copyEstimatesForPBS(@RequestBody(required = false) Map<String, Object> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
				"In Cost Controller copyEstimates method execution started with requestBody:" + requestBody));
		Object response = null;
		response = costService.copyEstimatesForPBS(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller copyEstimates method execution ends"));
		return response;
	}
	
	/**
	 * Usage:The <i>saveEstimates</i> endpoint is a wrapper API to connect save data
	 * API
	 * 
	 * @param requestBody the cost estimates to be saved
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/saveEstimates")
	public Object saveEstimates(@RequestBody Map<String, Object> requestBody, HttpServletRequest request)
			throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller saveEstimates method execution started"));
		Object response = null;
		response = costService.saveEstimates(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller saveEstimates method execution ends"));
		return response;
	}
	
	@PostMapping(value = "/v1/updatePBSCostEstimates")
	public Object updatePBSCostEstimates(@RequestBody Map<String, Object> requestBody, HttpServletRequest request)
			throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
				"In Cost Controller updatePBSCostEstimates method execution started with requestBody:" + requestBody));
		Object response = costService.updatePBSCostEstimates(request, requestBody);
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller updatePBSCostEstimates method execution ends"));
		return response;
	}
	
	@PostMapping(value = "/v1/updateContractItemCostEstimates")
	public Object updateContractItemCostEstimates(@RequestBody Map<String, Object> requestBody, HttpServletRequest request)
			throws CostAppException, URISyntaxException {
		logger.debug(Logger.EVENT_SUCCESS, ESAPI.encoder().encodeForHTML(
				"In Cost Controller updateContractItemCostEstimates method execution started with requestBody:" + requestBody));
		Object response = costService.updateContractItemCostEstimates(request, requestBody);
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller updateContractItemCostEstimates method execution ends"));
		return response;
	}
}
