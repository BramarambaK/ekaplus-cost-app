package com.eka.costapp.validator;

import static com.eka.costapp.constant.GlobalConstants.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.json.JSONObject;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.eka.costapp.commons.CommonService;
import com.eka.costapp.commons.CostAppUtils;
import com.eka.costapp.controller.CostAppController;
import com.eka.costapp.exception.CostAppException;
import com.eka.costapp.service.CostAppService;

@Component
public class CostValidator {

	final static Logger logger = ESAPI.getLogger(CostValidator.class);

	@Value("${eka_connect_host}")
	private String ekaConnectHost;

	@Autowired
	CostAppUtils costAppUtils;

	@Autowired
	CommonService commonService;
	
	@Autowired
	CostAppController costAppController;

	/**
	 * Usage:The <i>validateEstimates</i> method validates the cost estimates and
	 * populates status and remarks accordingly
	 * 
	 * @param estimates The List of estimates to be validated
	 */
	public void validateEstimates(List<Map<String, Object>> estimates, HttpServletRequest request) {
		//convert labels/column names to proper field names--
		convertLabelsToFieldNames(estimates);
		//fetch all instrument names fx--
		Set<String> allowedFxRatesForFxCurve = fetchInstrumentNamesfx(request);
		// call mdm to fetch independent service keys--
		List<Map<String, String>> independentMdmCallPayload = new ArrayList<>();
		Iterator<String> iterator = INDEPENDENT_MDM_SERVICE_KEYS.iterator();
		while (iterator.hasNext()) {
			String serviceKey = iterator.next();
			independentMdmCallPayload.add(prepareInnerPayloadForIndependentMdmCall(serviceKey));
		}
		Map<String, List<Map<String, Object>>> independentMdmMappings = costAppUtils.callMdm(independentMdmCallPayload,
				request);
		// call mdm with dependent fields,prepare mapping of serviceKeys with dependent
		// fields-
		Map<String, MultiKeyMap> dependentServiceKeyMapping = new HashMap<>();
		estimates.stream().forEach(estimate -> {
			List<String> missingMandatoryFields = validateMandatoryFieldsAndRateTypePrice(estimate);
			if (!missingMandatoryFields.isEmpty()) {
				setStatusAndRemarksInEstimateBasedOnMissingFields(estimate, missingMandatoryFields);
				return;
			}
			try {
				validateFieldValueLimitations(estimate);
				validateMdmDrivenFields(estimate, independentMdmMappings);
				if (estimate.get(RATE_TYPE_PRICE).toString().equalsIgnoreCase(RATE_TYPE_RATE)) {
					checkExistingMdmMapAndCallMdmForRequiredDependentServiceKeys(request, estimate,
							dependentServiceKeyMapping, DEPENDENT_MDM_SERVICE_KEYS_FIRST_CALL);
					validateDependentMdmDrivenFields(estimate, dependentServiceKeyMapping,
							DEPENDENT_MDM_SERVICE_KEYS_FIRST_CALL);
				}
				checkExistingMdmMapAndCallMdmForRequiredDependentServiceKeys(request, estimate,
						dependentServiceKeyMapping, DEPENDENT_MDM_SERVICE_KEYS);
				validateDependentMdmDrivenFields(estimate, dependentServiceKeyMapping, DEPENDENT_MDM_SERVICE_KEYS);
				if (estimate.get(RATE_TYPE_PRICE).toString().equalsIgnoreCase(RATE_TYPE_CURVE)) {
					checkExistingMdmMapAndCallMdmForRequiredDependentServiceKeys(request, estimate,
							dependentServiceKeyMapping, DEPENDENT_MDM_SERVICE_KEYS_CURVE);
					validateDependentMdmDrivenFields(estimate, dependentServiceKeyMapping,
							DEPENDENT_MDM_SERVICE_KEYS_CURVE);
				}
				populateCostAmountAndCostAmountUnit(estimate);
			} catch (CostAppException use) {
				setStatusAndRemarksBasedOnStatement(estimate, use.getLocalizedMessage());
				return;
			}
			List<String> missingFieldsForFxToBaseTypeScenarios = new ArrayList<>();
			try {
				validateFxToBaseTypeScenarios(estimate, missingFieldsForFxToBaseTypeScenarios, allowedFxRatesForFxCurve);
			} catch (CostAppException use) {
				setStatusAndRemarksBasedOnStatement(estimate, use.getLocalizedMessage());
				return;
			}
			if (!missingFieldsForFxToBaseTypeScenarios.isEmpty()) {
				setStatusAndRemarksInEstimateBasedOnMissingFields(estimate, missingFieldsForFxToBaseTypeScenarios);
				return;
			}
			// if no error thrown till now, set success status--
			setSuccessStatusInEstimate(estimate);
		});
	}
	
	private void populateCostAmountAndCostAmountUnit(Map<String, Object> estimate) {
		String rateTypePrice = estimate.get(RATE_TYPE_PRICE).toString();
		switch (rateTypePrice) {
		case RATE_TYPE_ABSOLUTE:
			estimate.put(COST_AMOUNT_IN_BASE_CURRENCY, estimate.get(COST_VALUE));
			estimate.put(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID, estimate.get(CP_UNIT_ID_DISPLAY_NAME));
			break;
		case RATE_TYPE_RATE:
			double costValueRate = Double.parseDouble(estimate.get(COST_VALUE).toString());
			double itemQtyRate = Double.parseDouble(estimate.get(ITEM_QTY).toString());
			double conversionFactor = Double.parseDouble(estimate.get(CONVERSION_FACTOR).toString());
			double costAmountInBaseCurrencyRate = costValueRate * itemQtyRate / conversionFactor;
			String costAmountInBaseCurrencyUnitIdRate = estimate.get(CP_UNIT_ID_DISPLAY_NAME).toString().split("/")[0];
			estimate.put(COST_AMOUNT_IN_BASE_CURRENCY, costAmountInBaseCurrencyRate);
			estimate.put(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID, costAmountInBaseCurrencyUnitIdRate);
			break;
		case RATE_TYPE_PERCENT_OF_PRICE:
			double costValuePercentOfPrice = Double.parseDouble(estimate.get(COST_VALUE).toString());
			double itemQtyPoP = Double.parseDouble(estimate.get(ITEM_QTY).toString());
			String contractPrice = estimate.get(CONTRACT_PRICE).toString();
			double contractPriceValue = Double.parseDouble(contractPrice.split(" ")[0]);
			double costAmountInBaseCurrencyPop = costValuePercentOfPrice * itemQtyPoP * contractPriceValue / 100;
			String costAmountInBaseCurrencyUnitIdPop = contractPrice.split(" ")[1].split("/")[0];
			estimate.put(COST_AMOUNT_IN_BASE_CURRENCY, costAmountInBaseCurrencyPop);
			estimate.put(COST_AMOUNT_IN_BASE_CURRENCY_UNIT_ID, costAmountInBaseCurrencyUnitIdPop);
			break;
		}
	}

	private void convertLabelsToFieldNames(List<Map<String, Object>> estimates) {
		JSONObject labelToFieldNameMapping = new JSONObject(LABEL_TO_FIELDNAME_MAPPING);
		estimates = estimates.stream().map(estimate -> {
			Iterator<String> iterator = labelToFieldNameMapping.keySet().iterator();
			while (iterator.hasNext()) {
				String label = iterator.next();
				if (estimate.containsKey(label)) {
					estimate.put(labelToFieldNameMapping.get(label).toString(), estimate.get(label));
					estimate.remove(label);
				}
			}
			return estimate;
		}).collect(Collectors.toList());
	}

	private Map<String, String> prepareInnerPayloadForIndependentMdmCall(String serviceKey) {
		Map<String, String> innerPayload = new HashMap<>();
		innerPayload.put(SERVICE_KEY, serviceKey);
		return innerPayload;
	}

	public List<String> validateMandatoryFieldsAndRateTypePrice(Map<String, Object> estimate) {
		List<String> missingMandatoryFields = getMissingFieldsInData(estimate, MANDATORY_FIELDS_IN_COST_ESTIMATE);
		validateRateTypePriceDisplayName(estimate, missingMandatoryFields);
		return missingMandatoryFields;
	}

	private void validateRateTypePriceDisplayName(Map<String, Object> estimate, List<String> missingMandatoryFields) {
		if (estimate.containsKey(RATE_TYPE_PRICE_DISP_NAME) && !estimate.get(RATE_TYPE_PRICE_DISP_NAME).toString().isEmpty()) {
			String rateTypePrice = estimate.get(RATE_TYPE_PRICE_DISP_NAME).toString();
			switch (rateTypePrice) {
			case "Absolute":
				missingMandatoryFields.addAll(getMissingFieldsInData(estimate, MANDATORY_FIELDS_ABS));
				estimate.put(RATE_TYPE_PRICE, RATE_TYPE_ABSOLUTE);
				break;
			case "Rate":
				missingMandatoryFields.addAll(getMissingFieldsInData(estimate, MANDATORY_FIELDS_RATE));
				estimate.put(RATE_TYPE_PRICE, RATE_TYPE_RATE);
				break;
			case RATE_TYPE_PERCENT_OF_PRICE:
				missingMandatoryFields.addAll(getMissingFieldsInData(estimate, MANDATORY_FIELDS_PERCENT_OF_PRICE));
				estimate.put(RATE_TYPE_PRICE, RATE_TYPE_PERCENT_OF_PRICE);
				break;
			case "Curve":
				missingMandatoryFields.addAll(getMissingFieldsInData(estimate, MANDATORY_FIELDS_FOR_CURVE));
				estimate.put(RATE_TYPE_PRICE, RATE_TYPE_CURVE);
				break;
			}
		}
	}

	private List<String> getMissingFieldsInData(Map<String, Object> data, List<String> fields) {
		List<String> missingFields = new ArrayList<>();
		Iterator<String> fieldsIterator = fields.iterator();
		while (fieldsIterator.hasNext()) {
			String field = fieldsIterator.next();
			if (!data.containsKey(field) || Objects.isNull(data.get(field)) || data.get(field).toString().isEmpty()) {
				field = FIELDNAME_TO_LABEL_MAPPING.has(field) ? FIELDNAME_TO_LABEL_MAPPING.getString(field) : field;
				missingFields.add(field);
			}
		}
		return missingFields;
	}

	public void setStatusAndRemarksInEstimateBasedOnMissingFields(Map<String, Object> estimate,
			List<String> missingMandatoryFields) {
		estimate.put(STATUS, false);
		String csvMissingFields = String.join(",", missingMandatoryFields);
		estimate.put(REMARKS, "value of " + csvMissingFields + " is missing");
	}

	private void validateFieldValueLimitations(Map<String, Object> estimate) {
		// validate entityType--
		validateAnyFieldForAllowedValues(estimate, ENTITY_TYPE, ALLOWED_ENTITY_TYPES);
		// validate incExpense--
		validateAnyFieldForAllowedValues(estimate, INC_EXP_DISPLAY_NAME, ALLOWED_INC_EXPENSE);
		// validate estimateFor--
		if (Objects.nonNull(estimate.get(ENTITY_TYPE))
				&& estimate.get(ENTITY_TYPE).equals(ENTITY_TYPE_PLANNED_SHIPMENT))
			validateAnyFieldForAllowedValues(estimate, ESTIMATE_FOR_DISPLAY_NAME,
					ALLOWED_ESTIMATE_FOR_DISPLAY_NAME_PBS);
		else
			validateAnyFieldForAllowedValues(estimate, ESTIMATE_FOR_DISPLAY_NAME, ALLOWED_ESTIMATE_FOR_DISPLAY_NAME);
		// validate rateTypePriceDisplayName--
		validateAnyFieldForAllowedValues(estimate, RATE_TYPE_PRICE_DISP_NAME, ALLOWED_RATE_TYPE_PRICE_DISP_NAME);
		// validate fxToBaseType--
		validateAnyFieldForAllowedValues(estimate, FX_TO_BASE_TYPE, ALLOWED_FX_TO_BASE_TYPE);
	}

	private void validateAnyFieldForAllowedValues(Map<String, Object> estimate, String fieldName,
			List<String> allowedFieldValues) {
		if (Objects.nonNull(estimate.get(fieldName)) && !allowedFieldValues.contains(estimate.get(fieldName))) {
			fieldName = FIELDNAME_TO_LABEL_MAPPING.has(fieldName) ? FIELDNAME_TO_LABEL_MAPPING.getString(fieldName)
					: fieldName;
			throw new CostAppException("value of " + fieldName + " is invalid");
		}
		if (fieldName.equalsIgnoreCase(ESTIMATE_FOR_DISPLAY_NAME))
			estimate.put("estimateFor", estimate.get(ESTIMATE_FOR_DISPLAY_NAME));
	}

	private void validateMdmDrivenFields(Map<String, Object> estimate,
			Map<String, List<Map<String, Object>>> mdmMapping) {
		Iterator<Entry<String, List<Map<String, Object>>>> mdmMappingIterator = mdmMapping.entrySet().iterator();
		while (mdmMappingIterator.hasNext()) {
			Entry<String, List<Map<String, Object>>> pair = mdmMappingIterator.next();
			String serviceKey = pair.getKey();
			List<Map<String, Object>> serviceKeyMappings = pair.getValue();
			switch (serviceKey) {
			case "costcomponents":
				validateIndividualMdmDrivenField(estimate, COST_COMP_DISP_NAME, COST_COMPONENT, serviceKeyMappings);
				break;
			case "CostIncExp":
				validateIndividualMdmDrivenField(estimate, INC_EXP_DISPLAY_NAME, INC_EXPENSE, serviceKeyMappings);
				break;
			case "corporatebusinesspartnerCombo":
				validateIndividualMdmDrivenField(estimate, CP_GRP_NAME_DISPLAY_NAME, CP_GRP_NAME, serviceKeyMappings);
				break;
			case "productCurrencyList":
				if (Objects.nonNull(estimate.get(RATE_TYPE_PRICE))
						&& estimate.get(RATE_TYPE_PRICE).toString().equalsIgnoreCase(RATE_TYPE_ABSOLUTE))
					validateIndividualMdmDrivenField(estimate, CP_UNIT_ID_DISPLAY_NAME, COST_PRICE_UNIT_ID,
							serviceKeyMappings);
				break;
			case "productComboDropDrown":
				validateIndividualMdmDrivenField(estimate, PRODUCT, PRODUCT_ID, serviceKeyMappings);
				break;
			}
		}
	}

	private void validateIndividualMdmDrivenField(Map<String, Object> estimate, String fieldName,
			String fieldNameHavingId, List<Map<String, Object>> serviceKeyMappings) {
		try {
			if (Objects.nonNull(estimate.get(fieldName))) {
				String fieldValueInId = costAppUtils.findMatchingKeyFromValuesInMdmData(serviceKeyMappings,
						estimate.get(fieldName).toString());
				estimate.put(fieldNameHavingId, fieldValueInId);
			}
		} catch (CostAppException use) {
			fieldName = FIELDNAME_TO_LABEL_MAPPING.has(fieldName) ? FIELDNAME_TO_LABEL_MAPPING.getString(fieldName)
					: fieldName;
			throw new CostAppException("value of " + fieldName + " is invalid");
		}
	}

	private void checkExistingMdmMapAndCallMdmForRequiredDependentServiceKeys(HttpServletRequest request,
			Map<String, Object> estimate, Map<String, MultiKeyMap> dependentServiceKeyMapping,
			List<String> dependentServiceKeys) {
		LinkedList<Map<String, Object>> dataForMdmCall = new LinkedList<>();
		checkExistingMdmMapAndPreparePayloadForRequiredDependentKeys(estimate, dataForMdmCall,
				dependentServiceKeyMapping, dependentServiceKeys);
		// call /workflow/mdm with this payload and add response to the mapping--
		if (!dataForMdmCall.isEmpty()) {
			Map<String, Object> mdmResponseForDependentKeys = costAppUtils.callWorkflowMdmApi(commonService.getHttpHeader(request),
					createPayloadForWorkflowMdmCall(estimate, dataForMdmCall,
							WORKFLOW_TASK_COST_ITEMS_FOR_EXISTING_ENTITY, extractParamsFromEstimate(estimate)));
			addMdmResponseToMapping(mdmResponseForDependentKeys, dependentServiceKeyMapping, estimate);
		}
	}

	private void checkExistingMdmMapAndPreparePayloadForRequiredDependentKeys(Map<String, Object> estimate,
			LinkedList<Map<String, Object>> dataForMdmCall, Map<String, MultiKeyMap> dependentServiceKeyMapping,
			List<String> dependentServiceKeys) {
		Iterator<String> iterator = dependentServiceKeys.iterator();
		while (iterator.hasNext()) {
			String serviceKey = iterator.next();
			if (checkToSkipMdmCall(estimate, serviceKey))
				continue;
			createPayloadForIndividualDependentMdmKeys(serviceKey, estimate, dependentServiceKeyMapping, dataForMdmCall,
					getDependsOnFieldsForServiceKey(serviceKey));
		}
	}

	private boolean checkToSkipMdmCall(Map<String, Object> estimate, String serviceKey) {
		String rateTypePrice = estimate.get(RATE_TYPE_PRICE).toString();
		if ((!rateTypePrice.equals(RATE_TYPE_CURVE) && serviceKey.equals(COST_CURVE))
				|| (!rateTypePrice.equals(RATE_TYPE_RATE) && serviceKey.equals(CONVERSION_FACTOR)))
			return true;
		return false;
	}

	private Map<String, Object> createPayloadForWorkflowMdmCall(Map<String, Object> estimate,
			List<Map<String, Object>> dataForMdmCall, String workflowTask, Map<String, Object> params) {
		Map<String, Object> payload = new HashMap<>();
		payload.put(APP_ID, CostAppService.COST_APP_UUID);
		payload.put(DATA, dataForMdmCall);
		payload.put(WORKFLOW_TASK, workflowTask);
		payload.put(PARAMS, params);
		payload.put(PAYLOAD_DATA, estimate);
		payload.put(_TEMPORARY, Collections.EMPTY_MAP);
		payload.put(WORKFLOW_TASK_COST_ITEMS_FOR_EXISTING_ENTITY, estimate);
		return payload;
	}

	private Map<String, Object> extractParamsFromEstimate(Map<String, Object> estimate) {
		Map<String, Object> params = new HashMap<>();
		PARAMS_FIELDS.stream().forEach(param -> params.put(param, estimate.get(param)));
		return params;
	}

	private void addMdmResponseToMapping(Map<String, Object> mdmResponseForDependentKeys,
			Map<String, MultiKeyMap> dependentServiceKeyMapping, Map<String, Object> estimate) {
		mdmResponseForDependentKeys.remove("errors");
		Iterator<Entry<String, Object>> iterator = mdmResponseForDependentKeys.entrySet().iterator();
		while (iterator.hasNext()) {
			Entry<String, Object> pair = iterator.next();
			String serviceKey = pair.getKey();
			List<Map<String, Object>> mdmKeyValuesForServiceKey = new ArrayList<>();
			if (Objects.nonNull(pair.getValue())) {
				if (serviceKey.equalsIgnoreCase(CONVERSION_FACTOR) && pair.getValue() instanceof String) {
					mdmKeyValuesForServiceKey
							.add(new JSONObject().put("key", pair.getValue()).put("value", CONVERSION_FACTOR).toMap());
				} else
					mdmKeyValuesForServiceKey = (List<Map<String, Object>>) pair.getValue();
			}
			addDependentKeyResponseIntoMapping(serviceKey, estimate, dependentServiceKeyMapping,
					mdmKeyValuesForServiceKey, getDependsOnFieldsForServiceKey(serviceKey));
		}
	}

	private void createPayloadForIndividualDependentMdmKeys(String serviceKey, Map<String, Object> estimate,
			Map<String, MultiKeyMap> dependentServiceKeyMapping, LinkedList<Map<String, Object>> dataForMdmCall,
			String... dependsOnFields) {
		List<String> dependsOnFieldsList = Arrays.asList(dependsOnFields);
		if (Objects.nonNull(dependsOnFields) && costAppUtils.checkKeysNotAbsentOrEmptyInData(dependsOnFieldsList, estimate)) {
			List<String> dependsOnFieldValues = dependsOnFieldsList.stream()
					.map(field -> estimate.get(field).toString()).collect(Collectors.toList());
			MultiKey key = new MultiKey(dependsOnFieldValues.stream().toArray(String[]::new));
			if (!dependentServiceKeyMapping.containsKey(serviceKey)
					|| !dependentServiceKeyMapping.get(serviceKey).containsKey(key)) {
				Map<String, Object> dataForOneServiceKey = prepareInnerPayloadForDependentMdmCall(serviceKey,
						dependsOnFieldValues);
				dataForMdmCall.add(dataForOneServiceKey);
			}
		}
	}

	private String[] getDependsOnFieldsForServiceKey(String serviceKey) {
		switch (serviceKey) {
		case "costCurve":
			return DEPENDS_ON_FOR_COST_CURVE;
		case "fxRate":
			return DEPENDS_ON_FOR_FX_RATE;
		case "conversionFactor":
			return DEPENDS_ON_FOR_CONVERSION_FACTOR;
		case "productCurrencyList":
			return DEPENDS_ON_FOR_PRODUCT_CURR_LIST;
		case "productPriceUnit":
			return DEPENDS_ON_FOR_PRODUCT_PRICE_UNIT;
		case PHYSICAL_PRODUCT_QTY_LIST:
			return DEPENDS_ON_FOR_ITEM_QTY_UNIT_ID;
		case COST_MONTH:
			return DEPENDS_ON_FOR_COST_MONTH;
		case "curveCurrency":
			return DEPENDS_ON_FOR_CURVE_CURRENCY;
		}
		return new String[0];
	}

	private void addDependentKeyResponseIntoMapping(String serviceKey, Map<String, Object> estimate,
			Map<String, MultiKeyMap> dependentServiceKeyMapping, List<Map<String, Object>> mdmKeyValuesForServiceKey,
			String... dependsOnFieldValues) {
		List<String> dependsOnValuesList = new ArrayList<>();
		for (String dependsOnFieldValue : dependsOnFieldValues) {
			dependsOnValuesList.add(estimate.get(dependsOnFieldValue).toString());
		}
		MultiKeyMap mappingForServiceKey = new MultiKeyMap<>();
		if (dependentServiceKeyMapping.containsKey(serviceKey)) {
			mappingForServiceKey = dependentServiceKeyMapping.get(serviceKey);
		}
		mappingForServiceKey.put(new MultiKey(dependsOnValuesList.stream().toArray(String[]::new)),
				mdmKeyValuesForServiceKey);
		dependentServiceKeyMapping.put(serviceKey, mappingForServiceKey);
	}

	private Map<String, Object> prepareInnerPayloadForDependentMdmCall(String serviceKey,
			List<String> dependsOnFields) {
		Map<String, Object> innerPayload = new HashMap<>();
		innerPayload.put(DEPENDS_ON, dependsOnFields);
		innerPayload.put(SERVICE_KEY, serviceKey);
		return innerPayload;
	}

	private void validateDependentMdmDrivenFields(Map<String, Object> estimate,
			Map<String, MultiKeyMap> dependentServiceKeyMapping, List<String> dependentServiceKeys) {
		Iterator<String> iterator = dependentServiceKeys.iterator();// DEPENDENT_MDM_SERVICE_KEYS.iterator();
		while (iterator.hasNext()) {
			String serviceKey = iterator.next();
			if ((!estimate.get(FX_TO_BASE_TYPE).toString().equalsIgnoreCase(FX_TO_BASE_TYPE_CURVE))
					&& serviceKey.equalsIgnoreCase(FX_RATE))
				continue;
			if ((Objects.isNull(estimate.get("itemQtyUnitDisplayName"))
					|| estimate.get("itemQtyUnitDisplayName").toString().isEmpty())
					&& serviceKey.equals(PHYSICAL_PRODUCT_QTY_LIST))
				continue;
			String[] dependsOnFields = getDependsOnFieldsForServiceKey(serviceKey);
			if (costAppUtils.checkKeysNotAbsentOrEmptyInData(Arrays.asList(dependsOnFields), estimate)) {
				List<String> dependsOnValuesList = new ArrayList<>();
				for (String dependsOnField : dependsOnFields) {
					dependsOnValuesList.add(estimate.get(dependsOnField).toString());
				}
				MultiKey key = new MultiKey(dependsOnValuesList.stream().toArray(String[]::new));
				if (dependentServiceKeyMapping.containsKey(serviceKey)
						&& dependentServiceKeyMapping.get(serviceKey).containsKey(key)) {
					List<Map<String, Object>> mdmKeyValues = (List<Map<String, Object>>) dependentServiceKeyMapping
							.get(serviceKey).get(key);
					String fieldName = getFieldNameForDependentServiceKey(serviceKey);
					String fieldNameToPopulate = getFieldNameToPopulateForDependentServiceKey(serviceKey);
					if (mdmKeyValues.isEmpty()) {
						fieldName = FIELDNAME_TO_LABEL_MAPPING.has(fieldName)
								? FIELDNAME_TO_LABEL_MAPPING.getString(fieldName)
								: fieldName;
						throw new CostAppException("value of " + fieldName + " is invalid");
					}
					String fieldValueInId = null;
					try {
						if (serviceKey.equalsIgnoreCase(CONVERSION_FACTOR)) {
							String conversionFactorFromMdm = new JSONObject(mdmKeyValues.get(0)).getString("key");
							if (Objects.nonNull(estimate.get(CONVERSION_FACTOR)) && !(conversionFactorFromMdm
									.equalsIgnoreCase(estimate.get(CONVERSION_FACTOR).toString())))
								throw new CostAppException("value of conversionFactor is invalid");
							else if (!estimate.containsKey(CONVERSION_FACTOR))
								estimate.put(CONVERSION_FACTOR, conversionFactorFromMdm);
						} else if (serviceKey.equalsIgnoreCase("curveCurrency") && !mdmKeyValues.isEmpty()
								&& mdmKeyValues.get(0).containsKey(VALUE)) {
							String fieldValue = mdmKeyValues.get(0).get(VALUE).toString();
							estimate.put(fieldName, fieldValue);
							estimate.put(fieldNameToPopulate, fieldValue);
						} else if (Objects.nonNull(fieldName) && Objects.nonNull(fieldNameToPopulate)) {
							fieldValueInId = costAppUtils.findMatchingKeyFromValuesInMdmData(mdmKeyValues,
									estimate.get(fieldName).toString());
							estimate.put(fieldNameToPopulate, fieldValueInId);
						}
					} catch (CostAppException use) {
						fieldName = FIELDNAME_TO_LABEL_MAPPING.has(fieldName)
								? FIELDNAME_TO_LABEL_MAPPING.getString(fieldName)
								: fieldName;
						throw new CostAppException("value of " + fieldName + " is invalid");
					}
				}
			}
		}
	}

	private String getFieldNameForDependentServiceKey(String serviceKey) {
		switch (serviceKey) {
		case "costCurve":
			return "costCurveDisplayName";
		case "fxRate":
			return "fxRate";
		case "conversionFactor":
			return "conversionFactor";
		case "productCurrencyList":
			return "costPriceUnitIdDisplayName";
		case "productPriceUnit":
			return "costPriceUnitIdDisplayName";
		case PHYSICAL_PRODUCT_QTY_LIST:
			return "itemQtyUnitDisplayName";
		case COST_MONTH:
			return "costMonthDisplayName";
		case "curveCurrency":
			return "curveCurrencyDisplayName";
		}
		return null;
	}
	
	private String getFieldNameToPopulateForDependentServiceKey(String serviceKey) {
		switch (serviceKey) {
		case "costCurve":
			return "costCurve";
		case "fxRate":
			return "fxRate";
		case "conversionFactor":
			return "conversionFactor";
		case "productCurrencyList":
			return "costPriceUnitId";
		case "productPriceUnit":
			return "costPriceUnitId";
		case PHYSICAL_PRODUCT_QTY_LIST:
			return ITEM_QTY_UNIT_ID;
		case COST_MONTH:
			return COST_MONTH;
		case "curveCurrency":
			return "curveCurrency";
		}
		return null;
	}
	
	private Set<String> fetchInstrumentNamesfx(HttpServletRequest request) {
		Object getInstrumentNamesFxResponse = null;
		try {
			getInstrumentNamesFxResponse = costAppController.getInstrumentNamesFx(CostAppService.COST_APP_UUID,
					CostAppService.COST_ESTIMATE_OBJECT_UUID, request);
		} catch (Exception ex) {
			logger.error(Logger.EVENT_FAILURE, ESAPI.encoder()
					.encodeForHTML("error inside fetchInstrumentNamesfx due to :" + ex.getLocalizedMessage()));
			throw new CostAppException("error while fetching fx instrument names");
		}
		Map<String, Object> getInstrumentNamesFxResponseMap = new HashMap<>();
		if (Objects.nonNull(getInstrumentNamesFxResponse) && getInstrumentNamesFxResponse instanceof Map) {
			getInstrumentNamesFxResponseMap = (Map<String, Object>) getInstrumentNamesFxResponse;
			Set<String> allowedFxRatesForFxCurve = new HashSet<>();
			allowedFxRatesForFxCurve.addAll(getInstrumentNamesFxResponseMap.keySet());
			Set<String> reverseSetOfInstrumentNames = allowedFxRatesForFxCurve.stream()
					.filter(currencyPair -> currencyPair.contains("/")).map(currencyPair -> {
						String[] currencies = currencyPair.split("/");
						String reversedCurrencyPair = currencies[1] + "/" + currencies[0];
						return reversedCurrencyPair;
					}).collect(Collectors.toSet());
			allowedFxRatesForFxCurve.addAll(reverseSetOfInstrumentNames);
			return allowedFxRatesForFxCurve;
		}
		return Collections.EMPTY_SET;
	}

	private void validateFxToBaseTypeScenarios(Map<String, Object> estimate,
			List<String> missingFieldsForFxToBaseTypeScenarios, Set<String> allowedFxRatesForFxCurve) {
		if (Objects.nonNull(estimate.get(FX_TO_BASE_TYPE))) {
			String fxToBaseType = estimate.get(FX_TO_BASE_TYPE).toString();
			switch (fxToBaseType) {
			case FX_TO_BASE_TYPE_ABSOLUTE:
				missingFieldsForFxToBaseTypeScenarios
						.addAll(getMissingFieldsInData(estimate, MANDATORY_FIELDS_FX_ABSOLUTE));
				break;
			case FX_TO_BASE_TYPE_CURVE:
				missingFieldsForFxToBaseTypeScenarios
						.addAll(getMissingFieldsInData(estimate, MANDATORY_FIELDS_FX_CURVE));
				validateFxRate(estimate, allowedFxRatesForFxCurve);
				break;
			}
		}
	}
	
	private void validateFxRate(Map<String, Object> estimate, Set<String> allowedFxRatesForFxCurve) {
		if (Objects.nonNull(estimate.get(FX_RATE))
				&& !allowedFxRatesForFxCurve.contains(estimate.get(FX_RATE).toString())) {
			throw new CostAppException("value of FX Rate is invalid");
		}
	}

	private void setStatusAndRemarksBasedOnStatement(Map<String, Object> estimate, String statement) {
		estimate.put(STATUS, false);
		estimate.put(REMARKS, statement);
	}

	private void setSuccessStatusInEstimate(Map<String, Object> estimate) {
		estimate.put(STATUS, true);
	}
}
