package com.eka.costapp.controllers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.greaterThan;

import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.util.ResourceUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

public class CostV2ControllerTest {
	
	String token = null;
	String tenant = null;
	String userName = null;
	String password = null;
	String eka_connect_host = null;
	String eka_cost_host = null;
	Map<String, Object> requestPayload = new HashMap<String, Object>();

	private static final String tokenGenerationApiPath = "/api/authenticate";
	private static final String COST_APP_UUID = "d33143ac-4164-4a3f-8d30-61d845c9eeed";
	private static final String DRAFT_COST_ESTIMATE_OBJECT_UUID = "00189ca9-cfc1-4327-95ac-f937f22deb60";
	private static final String COST_ESTIMATE_OBJECT_UUID = "f3d6ff89-b541-4dc0-b88d-12065d10cc90";

	@BeforeTest
	public void setUp() throws Exception {

		Properties prop = new Properties();
		prop.load(new FileInputStream(ResourceUtils.getFile("classpath:RestAssuredTest.properties")));
		tenant = prop.getProperty("tenant");
		userName = prop.getProperty("userName");
		password = prop.getProperty("password");
		eka_connect_host = prop.getProperty("eka_connect_host");
		eka_cost_host = prop.getProperty("eka_cost_host");
		token = authenticateUser(userName, password);
	}
	
	@Test(enabled = true)
	public void testAssociateAPIV2ForMandatoryCosts() throws MalformedURLException {
		// save two cost components(templates) T11 and T22--
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String costComponentPayloadString = "[{\"costComponent\":\"SCM-M2-84\",\"costComponentDisplayName\":\"BAGGING\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"costType\":\"template\",\"group\":\"GRP-PR1\",\"incExpenseDisplayName\":\"Income\",\"counterpartyGroupName\":\"PHD-M1-1995\",\"templateName\":\"T11\",\"estimateFor\":\"Execution\",\"rateTypePrice\":\"absolute\",\"counterpartyGroupNameDisplayName\":\"1710 USA\",\"rateTypePriceDisplayName\":\"Absolute\"},{\"costComponent\":\"SCM-M2-84\",\"costComponentDisplayName\":\"BAGGING\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"costType\":\"template\",\"group\":\"GRP-PR1\",\"incExpenseDisplayName\":\"Income\",\"counterpartyGroupName\":\"PHD-M1-1995\",\"templateName\":\"T22\",\"estimateFor\":\"Execution\",\"rateTypePrice\":\"absolute\",\"counterpartyGroupNameDisplayName\":\"1710 USA\",\"rateTypePriceDisplayName\":\"Absolute\"}]";
		String saveCostComponentPath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/2f787174-8ed0-4d5d-8f93-b38ab0edc05a";
		Response saveCostComponentResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(new JSONArray(costComponentPayloadString).toList()).when().request("POST", saveCostComponentPath);
		saveCostComponentResponse.then().assertThat().statusCode(200).and().body("size()", is(2));
		// save a rule with attributes-"Contract Type":"Purchase","Contract
		// Incoterm":"CIP","Valuation Incoterm":"CPT","Payment Term":"C03R - Cash 30
		// days after release of original docs" and templatesNames T11,T22--
		String saveRulePayloadString = "{\"Payment Term\":\"C03R - Cash 30 days after release of original docs\",\"Contract Incoterm\":\"CIP\",\"rule_name\":\"test1\",\"contractType\":\"contractType-002\",\"contractIncoterm\":\"ITM-M0-3\",\"Contract Type\":\"Purchase\",\"valuationIncoterm\":\"ITM-M0-4\",\"costcomponentTemplates\":[\"T11\",\"T22\"],\"attributes\":[\"Contract Type\",\"Contract Incoterm\",\"Valuation Incoterm\",\"Payment Term\"],\"Valuation Incoterm\":\"CPT\",\"paymentTerm\":\"PYM-M0-17\"}";
		String saveRulePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/2d3221f6-0717-4f08-b380-25c7094dcd0b";
		Response saveRuleResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(new JSONObject(saveRulePayloadString).toMap()).when().request("POST", saveRulePath);
		saveRuleResponse.then().assertThat().statusCode(200).and().body("valuationIncoterm", is("ITM-M0-4"));
		// save one draft estimate with templateName T11 and having exact same
		// attributes as above--
		String saveDraftPayload = "{\"itemQty\":\"15000\",\"costComponent\":\"SCM-M2-78\",\"costPriceUnitId\":\"PPU-M2-17\",\"costComponentDisplayName\":\"ADDITIVE\",\"costType\":\"estimate\",\"draftEstimateNo\":\"testAssociateAPIV2ForMandatoryCosts\",\"incExpenseDisplayName\":\"Expense\",\"productId\":\"PDM-M2-25\",\"counterpartyGroupName\":\"PHD-M1-3468\",\"entityType\":\"Contract Item\",\"refTypeId\":\"d33143ac-4164-4a3f-8d30-61d845c9eeed\",\"costCurveDisplayName\":null,\"sys__createdBy\":\"robert@ekaplus.com\",\"estimateFor\":\"Execution\",\"corporateCurrency\":\"USD\",\"counterpartyGroupNameDisplayName\":\"05266880\",\"rateTypePriceDisplayName\":\"Rate\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"costValue\":100,\"conversionFactor\":\"1\",\"fxToBaseType\":\"Absolute\",\"itemQtyUnitId\":\"QUM-M0-1\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostExpense\",\"estimateForDisplayName\":\"Execution\",\"shipmentDate\":\"05-Jun-2020\",\"costPriceUnitIdDisplayName\":\"USD/MT\",\"fxToBase\":1,\"priceType\":\"Flat\",\"costAmountInBaseCurrency\":1500000,\"contractPrice\":\"100 USD/MT\",\"rateTypePrice\":\"rate\",\"templateName\":\"T11\",\"contractType\":\"P\",\"contractIncoTerm\":\"CIP\",\"valuationIncoTerm\":\"CPT\",\"paymentTerm\":\"C03R - Cash 30 days after release of original docs\"}";
		String saveDraftPath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/00189ca9-cfc1-4327-95ac-f937f22deb60";
		Response saveDraftResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(new JSONObject(saveDraftPayload).toMap()).when().request("POST", saveDraftPath);
		saveDraftResponse.then().assertThat().statusCode(200).and().body("costComponent", is("SCM-M2-78"));
		// call associate v2 API and verify error--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String associateV2Payload = "{\"testAssociateAPIV2ForMandatoryCosts\":[{\"entityRefNo\":\"PCI-testAssociateAPIV2ForMandatoryCosts\",\"entityActualNo\":\"PC-SWA-testAssociateAPIV2ForMandatoryCosts\"}]}";
		JSONObject associateV2PayloadJson = new JSONObject(associateV2Payload);
		String associateV2Path = "/v2/associateEstimatesWithEntity";
		Response associateV2Response = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(associateV2PayloadJson.toMap()).when().request("POST", associateV2Path);
		associateV2Response.then().assertThat().statusCode(500).and().body("message",
				is("Mandatory Cost Component Details Missing, please update and proceed"));
		// save another draft estimate with templateName T22--
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveSecondDraftPayload = "{\"itemQty\":\"15001\",\"costComponent\":\"SCM-M2-78\",\"costPriceUnitId\":\"PPU-M2-17\",\"costComponentDisplayName\":\"ADDITIVE\",\"costType\":\"estimate\",\"draftEstimateNo\":\"testAssociateAPIV2ForMandatoryCosts\",\"incExpenseDisplayName\":\"Expense\",\"productId\":\"PDM-M2-25\",\"counterpartyGroupName\":\"PHD-M1-3468\",\"entityType\":\"Contract Item\",\"refTypeId\":\"d33143ac-4164-4a3f-8d30-61d845c9eeed\",\"costCurveDisplayName\":null,\"sys__createdBy\":\"robert@ekaplus.com\",\"estimateFor\":\"Execution\",\"corporateCurrency\":\"USD\",\"counterpartyGroupNameDisplayName\":\"05266880\",\"rateTypePriceDisplayName\":\"Rate\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"costValue\":100,\"conversionFactor\":\"1\",\"fxToBaseType\":\"Absolute\",\"itemQtyUnitId\":\"QUM-M0-1\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostExpense\",\"estimateForDisplayName\":\"Execution\",\"shipmentDate\":\"05-Jun-2020\",\"costPriceUnitIdDisplayName\":\"USD/MT\",\"fxToBase\":1,\"priceType\":\"Flat\",\"costAmountInBaseCurrency\":1500000,\"contractPrice\":\"100 USD/MT\",\"rateTypePrice\":\"rate\",\"templateName\":\"T22\",\"contractType\":\"P\",\"contractIncoTerm\":\"CIP\",\"valuationIncoTerm\":\"CPT\",\"paymentTerm\":\"C03R - Cash 30 days after release of original docs\"}";
		Response saveSecondDraftResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(new JSONObject(saveSecondDraftPayload).toMap()).when().request("POST", saveDraftPath);
		saveSecondDraftResponse.then().assertThat().statusCode(200).and().body("itemQty", is("15001"));
		// call associate v2 API and verify no error--
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		Response secondAssociateV2Response = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(associateV2PayloadJson.toMap()).when().request("POST", associateV2Path);
		secondAssociateV2Response.then().assertThat().statusCode(200).and().body("size()", is(2));
		secondAssociateV2Response.getBody().asString();
	}
	
	@Test(enabled = true)
	public void testAssociateAPIV2() throws MalformedURLException {
		// save draft cost estimate
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		
		String saveDraftEstimatePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/00189ca9-cfc1-4327-95ac-f937f22deb60";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveDraftEstimatePayload()).when().request("POST", saveDraftEstimatePath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		JsonPath saveEstimateJsonPath = new JsonPath(saveEstimateResponse.asString());
		Assert.assertEquals(saveEstimateJsonPath.get("[0].draftEstimateNo"), "1234");
		// call associate API and check entityRefNo--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String associateV2Payload = "{\"1234\":[{\"entityRefNo\":\"PCI-1234\",\"product\": \"PCI\",\"quantity\":\"TestQuantity\",\"quality\": \"good\",\"entityActualNo\":\"PC-1234-SWA\"}]}";
		JSONObject associateV2PayloadJson = new JSONObject(associateV2Payload);
		String associateV2Path = "/v2/associateEstimatesWithEntity";
		Response associateV2Response = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(associateV2PayloadJson.toMap()).when().request("POST", associateV2Path);
		Assert.assertEquals(associateV2Response.getStatusCode(), 200);
		JsonPath associateV2JsonResponse = new JsonPath(associateV2Response.asString());
		associateV2Response.then().assertThat().body("size()", greaterThan(0));
		Assert.assertEquals(associateV2JsonResponse.get("[0].entityRefNo"), "PCI-1234");
		Assert.assertEquals(associateV2JsonResponse.get("[0].entityActualNo"), "PC-1234-SWA");
		Assert.assertEquals(associateV2JsonResponse.get("[0].product"), "PCI");
		Assert.assertEquals(associateV2JsonResponse.get("[0].quantity"), "TestQuantity");
		Assert.assertEquals(associateV2JsonResponse.get("[0].quality"), "good");
	}
	
	@Test(enabled = true)
	public void testAssociateTempAPIV2() throws MalformedURLException {
		// save draft cost estimate
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveDraftEstimatePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/00189ca9-cfc1-4327-95ac-f937f22deb60";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveDraftEstimatePayload()).when().request("POST", saveDraftEstimatePath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		JsonPath saveEstimateJsonPath = new JsonPath(saveEstimateResponse.asString());
		Assert.assertEquals(saveEstimateJsonPath.get("[0].draftEstimateNo"), "1234");
		// call associateTemp API and check entityRefNo--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String associateTempV2Payload = "{\"1234\":[{\"entityRefNo\":\"PCI-1234\",\"product\": \"PCI\",\"quantity\":\"TestQuantity\",\"quality\": \"good\",\"entityActualNo\":\"PC-1234-SWA\"}]}";
		JSONObject associateTempV2PayloadJson = new JSONObject(associateTempV2Payload);
		String associateTempV2Path = "/v2/associateEstimatesWithEntityTemp";
		Response associateTempV2Response = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(associateTempV2PayloadJson.toMap()).when().request("POST", associateTempV2Path);
		Assert.assertEquals(associateTempV2Response.getStatusCode(), 200);
		JsonPath associateTempV2JsonPath = new JsonPath(associateTempV2Response.asString());
		associateTempV2Response.then().assertThat().body("size()", greaterThan(0));
		Assert.assertEquals(associateTempV2JsonPath.get("[0].entityRefNo"), "PCI-1234");
		Assert.assertEquals(associateTempV2JsonPath.get("[0].entityActualNo"), "PC-1234-SWA");
		Assert.assertEquals(associateTempV2JsonPath.get("[0].product"), "PCI");
		Assert.assertEquals(associateTempV2JsonPath.get("[0].quantity"), "TestQuantity");
		Assert.assertEquals(associateTempV2JsonPath.get("[0].quality"), "good");
	}
	
	/**
	 * Test convert draft to actual cost.
	 * JIRA CCA-578(API - Support - Ability to view contract item ref no, product, quantity,quality description in the cost estimates listing in Cost app in PBS)
	 * @throws MalformedURLException the malformed URL exception
	 */
	@Test(enabled = true)
	public void testConvertDraftToActualCost() throws MalformedURLException {
		// save draft cost estimate
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		
		String saveDraftEstimatePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/00189ca9-cfc1-4327-95ac-f937f22deb60";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveDraftPayload()).when().request("POST", saveDraftEstimatePath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		JsonPath saveEstimateJsonPath = new JsonPath(saveEstimateResponse.asString());
		Assert.assertEquals(saveEstimateJsonPath.get("[0].draftEstimateNo"), "89");
		// call associate API and check entityRefNo--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String associateV2Payload="{\r\n" + 
				"    \"89\": [\r\n" + 
				"        {\r\n" + 
				"            \"entityRefNo\": \"PCI-1235\",\r\n" + 
				"            \"entityActualNo\": \"PC-1234-SWA\",\r\n" + 
				"            \"product\": \"Rice\",\r\n" + 
				"            \"itemQty\": \"45\",\r\n" + 
				"            \"quality\": \"Good\"\r\n" + 
				"        }\r\n" + 
				"    ]\r\n" + 
				"}";
		JSONObject associateV2PayloadJson = new JSONObject(associateV2Payload);
		String associateV2Path = "/v2/associateEstimatesWithEntity";
		Response associateV2Response = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(associateV2PayloadJson.toMap()).when().request("POST", associateV2Path);
		Assert.assertEquals(associateV2Response.getStatusCode(), 200);
		JsonPath reverseAssociateJsonPath = new JsonPath(associateV2Response.asString());
		associateV2Response.then().assertThat().body("size()", greaterThan(0));
		Assert.assertEquals(reverseAssociateJsonPath.get("[0].entityRefNo"), "PCI-1235");
		Assert.assertEquals(reverseAssociateJsonPath.get("[0].entityActualNo"), "PC-1234-SWA");
		Assert.assertEquals(reverseAssociateJsonPath.get("[0].itemQty"), "45");
		Assert.assertEquals(reverseAssociateJsonPath.get("[0].quality"), "Good");
	}
	
	private List<Object> saveDraftEstimatePayload(){
		String savedraftEstimatePayloadString = "[{\"costType\":\"estimate\",\"costType\":\"estimate\",\"itemQty\":\"1001\",\"costAmountInBaseCurrencyUnitId\":\"CNY\",\"sys__data__state\":\"Create\",\"costValue\":3,\"isPopUp\":\"true\",\"costComponent\":\"SCM-89\",\"fxToBaseType\":\"Absolute\",\"costPriceUnitId\":\"CM-M0-3\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"COG PD Schedule\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution & Valuation\",\"shipmentDate\":\"31-Mar-2020\",\"costPriceUnitIdDisplayName\":\"CNY\",\"draftEstimateNo\":\"1234\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"fxToBase\":1.2,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":3,\"estimateFor\":\"Execution & Valuation\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"absolute\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"rateTypePriceDisplayName\":\"Absolute\"}]";
		JSONArray saveEstimatePayloadJson = new JSONArray(savedraftEstimatePayloadString);
		return saveEstimatePayloadJson.toList();
	}
	
	private List<Object> saveDraftPayload(){
			String savedraftEstimatePayloadString="[{\"entityType\":\"Contract Item\",\"costComponent\":\"SCM-M2-74\",\"costComponentDisplayName\":\"CHARTERER LIABILITY INSURANCE\",\"estimateFor\":\"Execution\",\"estimateForDisplayName\":\"Execution\",\"incExpense\":\"CostIncome\",\"incExpenseDisplayName\":\"Income\",\"rateTypePrice\":\"rate\",\"rateTypePriceDisplayName\":\"Rate\",\"counterpartyGroupNameDisplayName\":\"2210 Swiss branch\",\"counterpartyGroupName\":\"PHD-M1-1997\",\"costPriceUnitId\":\"PPU-45\",\"costPriceUnitIdDisplayName\":\"EUR/MT\",\"conversionFactor\":\"1\",\"fxToBaseType\":\"Absolute\",\"fxToBase\":1.2,\"fxToPosition\":7,\"costValue\":78,\"costAmountInBaseCurrency\":7800,\"costAmountInBaseCurrencyUnitId\":\"EUR\",\"draftEstimateNo\":\"89\",\"corporateCurrency\":\"USD\",\"comments\":\"xfgf\",\"itemQty\":\"100\",\"productId\":\"PDM-M2-25\",\"payInCurId\":\"CM-M0-9\",\"priceType\":\"On Call Basis Fixed\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"contractIncoTerm\":\"FOB\",\"valuationIncoTerm\":\"FOB\",\"shipmentDate\":\"30-Apr-2020\",\"isPopUp\":\"true\",\"showMenu\":\"false\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costType\":\"estimate\"}]";
	
		JSONArray saveEstimatePayloadJson = new JSONArray(savedraftEstimatePayloadString);
		return saveEstimatePayloadJson.toList();
	}
	
	private String authenticateUser(String username, String password)
			throws UnsupportedEncodingException, MalformedURLException {
		Map<String, Object> body = new HashMap<String, Object>();
		body.put("userName", username);
		body.put("pwd", password);
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		String base64encodedUsernamePassword = Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes("utf-8"));
		Response response = given().header("Content-Type", "application/json")
				.header("Authorization", "Basic " + base64encodedUsernamePassword).header("X-TenantID", tenant)
				.body(body).when().post(tokenGenerationApiPath);
		JsonPath jsonPath = new JsonPath(response.asString());
		System.out.println(response.body().prettyPrint());
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		return jsonPath.getString("auth2AccessToken.access_token");
	}
}
