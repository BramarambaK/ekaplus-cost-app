package com.eka.costapp.controller.v2;

import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eka.costapp.exception.ConnectException;
import com.eka.costapp.exception.CostAppException;
import com.eka.costapp.service.v2.CostAppV2Service;

@RestController	
@RequestMapping("/costapp/v2")
public class CostAppV2Controller {
	
	final static Logger logger = ESAPI.getLogger(CostAppV2Controller.class);
	
	@Autowired
	CostAppV2Service costV2Service;
	
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
	public Object ConvertDraftToActualCost(@RequestBody Map<String, List<Map<String,Object>>> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML(
						"In Cost Controller v2 ConvertDraftToActualCost method execution started with requestBody:"
								+ requestBody));
		Object response = null;
		response = costV2Service.ConvertDraftToActualCost(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller v2 ConvertDraftToActualCost method execution ends"));
		return response;

	}
	
	/**
	 * Usage:The <i>ConvertDraftToActualCost2</i> endpoint will convert draft cost
	 * estimate to actual cost estimate and return the converted data
	 * 
	 * @param requestBody the mapping of draftEstimateNo to entityRefNo and entityActualNo
	 * @param request
	 * @param request
	 * @return Object
	 */
	@PostMapping(value = "/associateEstimatesWithEntityTemp")
	public Object ConvertDraftToActualCostTemp(@RequestBody Map<String, List<Map<String,Object>>> requestBody,
			HttpServletRequest request) throws CostAppException {
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML(
						"In Cost Controller v2 ConvertDraftToActualCostTemp method execution started with requestBody:"
								+ requestBody));
		Object response = null;
		response = costV2Service.convertDraftToActualCostTemp(request, requestBody);
		if (response == null) {
			throw new ConnectException("No Response");
		}
		logger.debug(Logger.EVENT_SUCCESS,
				ESAPI.encoder().encodeForHTML("In Cost Controller v2 ConvertDraftToActualCost method execution ends"));
		return response;
	}

}
