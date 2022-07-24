package com.eka.costapp.controllers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;

import java.io.FileInputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.util.ResourceUtils;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;

public class CostControllerTest {

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
	private static final String RANDOM_FIELD_VALUE = "randomFieldValue";

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

	@Test(enabled=false)
	public void testGetAllEstimatesToFetchOnlyNonDeletedEstimates() throws MalformedURLException {
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		String payloadString = "{\"itemDetails\":[{\"entityType\":\"Contract Item\",\"entityRefNo\":\"PCI-2832\",\"applicableDate\":\"01-02-2020\"}],\"getDeletedData\":\"N\"}";
		JSONObject payloadJson = new JSONObject(payloadString);
		Response resp = given().header("X-TenantID", tenant).header("Content-Type", "application/json")
				.header("Authorization", token).with().body(payloadJson.toMap()).when()
				.request("POST", "/costapp/getAllEstimates");
		resp.then().assertThat().body("size()", is(5));
	}
	
	@Test(enabled = true)
	public void testGetAllEstimatesFor_id(String applicableDate, Object costAmountInBaseCurrency) {
		String path = "/getAllEstimates";
		String payloadAsString = "{\"itemDetails\":[{\"entityType\":\"Contract Item\",\"entityRefNo\":\"PCI-2525\",\"applicableDate\":\"22-02-2020\"}]}";
		JSONObject payloadJson = new JSONObject(payloadAsString);
		given().log().all().header("X-TenantID", tenant).header("Content-Type", "application/json")
				.header("Authorization", token).with().body(payloadJson.toMap()).when().request("POST", path).then()
				.assertThat().statusCode(200).body("[0]", containsString("_id"));
	}
	
	@Test(dataProvider = "getAllEstimatesTestParamsForCostCurve", enabled = true)
	public void testGetAllEstimatesApiForCostCurve(String applicableDate, Object costAmountInBaseCurrency)
			throws MalformedURLException {
		// save estimate--
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "";
		String saveEstimatePayload = "{\"itemQty\":\"1000\",\"isPopUp\":\"true\",\"contractType\":\"P\",\"costComponent\":\"SCM-M2-81\",\"costComponentDisplayName\":\"FREIGHT\",\"curveCurrencyDisplayName\":\"USD/MT\",\"entityActualNo\":\"PC-1632-SWA\",\"curveCurrency\":\"USD/MT\",\"draftEstimateNo\":\"158883855466526\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-24\",\"entityType\":\"Contract Item\",\"counterpartyGroupName\":\"PHD-M1-3468\",\"costCurveDisplayName\":\"PanamaxAarhusAarhusJan-2020\",\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"CIF\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-testCurve\",\"corporateCurrency\":\"USD\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"counterpartyGroupNameDisplayName\":\"05266880\",\"rateTypePriceDisplayName\":\"Curve\",\"conversionFactor\":\"1\",\"fxToBaseType\":\"Absolute\",\"itemQtyUnitId\":\"QUM-M0-1\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"costMonth\":\"Jan-2020\",\"shipmentDate\":\"31-May-2020\",\"costMonthDisplayName\":\"Jan-2020\",\"comments\":\"pr-test-1\",\"fxToBase\":1,\"priceType\":\"Flat\",\"curveCurrencySplit\":\"USD\",\"contractPrice\":\"10.00 USD/MT\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"curve\",\"costCurve\":\"PanamaxAarhusAarhusJan-2020\",\"costPriceUnitId\":\"PPU-M2-15\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"costValue\":90,\"costPriceUnitIdDisplayName\":\"USD/MT\",\"costAmountInBaseCurrency\":90000}";
		given().header("Content-Type", "application/json").header("Authorization", token).header("X-TenantID", tenant)
				.with().body(new JSONObject(saveEstimatePayload).toMap()).when()
				.post("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90").then()
				.assertThat().statusCode(200);
		URL eka_utility_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_utility_host_url.getHost();
		RestAssured.port = eka_utility_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String path = "/getAllEstimates";
		String payloadAsString = "{\"itemDetails\":[{\"entityType\":\"Contract Item\",\"entityRefNo\":\"PCI-testCurve\",\"applicableDate\":\"07-05-2020\"}],\"getDeletedData\":\"Y\"}";
		JSONObject payloadJson = new JSONObject(payloadAsString);
		payloadJson.getJSONArray("itemDetails").getJSONObject(0).put("applicableDate", applicableDate);
		Response actualResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(payloadJson.toMap()).when().request("POST", path);
		JsonPath jsonPath = new JsonPath(actualResponse.asString());
		Object costAmountInResponse = null;
		costAmountInResponse = jsonPath.getDouble("[0].costAmountInBaseCurrency");
		Assert.assertEquals(costAmountInResponse, costAmountInBaseCurrency);
	}

	@DataProvider(name = "getAllEstimatesTestParamsForCostCurve")
	private Object[][] getParamsForGetAllEstimatesForCostCurve() {
		return new Object[][] { { "07-05-2020", 90000.0 } };
	}
	
	@Test(dataProvider = "getAllEstimatesTestParamsForCostCurveCalculation", enabled = true)
	public void testGetAllEstimatesApiForCalculationInCostCurve(String applicableDate, Object costAmountInBaseCurrency,
			String costPriceUnitId) {
		//this test was written with alveanqa319 as platform.
		//data with entityRefNo PCI-834, rateType curve must be already available, otherwise will save again and again.
		String path = "/getAllEstimates";
		String payloadAsString = "{\"itemDetails\":[{\"entityType\":\"contractitem\",\"entityRefNo\":\"PCI-834\"}]}";
		JSONObject payloadJson = new JSONObject(payloadAsString);
		payloadJson.getJSONArray("itemDetails").getJSONObject(0).put("applicableDate", applicableDate);
		Response actualResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(payloadJson.toMap()).when().request("POST", path);
		JsonPath jsonPath = new JsonPath(actualResponse.asString());
		Object costAmountInResponse = null;
		costAmountInResponse = jsonPath.getDouble("[0].costAmountInBaseCurrency");
		Assert.assertEquals(costAmountInResponse, costAmountInBaseCurrency);
		Object costPriceUnitIdInResponse = null;
		costPriceUnitIdInResponse = jsonPath.getString("[0].costPriceUnitId");
		Assert.assertEquals(costPriceUnitId, costPriceUnitIdInResponse);
	}

	@DataProvider(name = "getAllEstimatesTestParamsForCostCurveCalculation")
	private Object[][] getParamsForGetAllEstimatesForCalculationInCostCurve() {
		return new Object[][] { { "30-12-2019", 2.7215543, "CM-M0-9" } };
	}

	@Test(dataProvider = "getAllEstimatesTestParamsForFxCurve", enabled = true)
	public void testGetAllEstimatesApiForFxCurve(String applicableDate, Object costAmountInBaseCurrency) {
		String path = "/getAllEstimates";
		String payloadAsString = "{\"itemDetails\":[{\"entityType\":\"contractitem\",\"entityRefNo\":\"PCI-DE-11\"}]}";
		JSONObject payloadJson = new JSONObject(payloadAsString);
		payloadJson.getJSONArray("itemDetails").getJSONObject(0).put("applicableDate", applicableDate);
		Response actualResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(payloadJson.toMap()).when().request("POST", path);
		JsonPath jsonPath = new JsonPath(actualResponse.asString());
		Object costAmountInResponse = null;
		costAmountInResponse = jsonPath.getDouble("[0].fxToBase");
		Assert.assertEquals(costAmountInResponse, costAmountInBaseCurrency);
	}

	@DataProvider(name = "getAllEstimatesTestParamsForFxCurve")
	private Object[][] getParamsForGetAllEstimatesForFxCurve() {
		return new Object[][] { { "02-01-2018", 1.26 }, { "06-01-2018", 1.26 }, { "03-01-2018", 1.26 },
				{ "01-01-2018", 11.0 } };
	}
	
	@Test(enabled = true)
	public void testGetAllEstimatesApiToUpdateCostCurveCalculation() throws MalformedURLException {
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "";
		String saveEstimatePayload = "{\"itemQty\":\"1000\",\"costComponent\":\"SCM-M2-84\",\"refType\":\"app\",\"costComponentDisplayName\":\"BAGGING\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"costMonth\":\"Nov-2019\",\"costMonthDisplayName\":\"Nov-2019\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"restassured-test\",\"productId\":\"PDM-M2-25\",\"entityType\":\"contractitem\",\"counterpartyGroupName\":\"PHD-M1-3468\",\"priceType\":\"On Call Basis Fixed\",\"costCurveDisplayName\":\"CBOT-Wheat-Future\",\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"entityRefNo\":\"PCI-835\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"curve\",\"costCurve\":\"CBOT-Wheat-Future\",\"paymentTerm\":\"C18B - Cash 180 days from b/l date\",\"counterpartyGroupNameDisplayName\":\"05266880\",\"rateTypePriceDisplayName\":\"Curve\",\"itemQtyUnitId\":\"QUM-M0-2\"}";
		given().header("Content-Type", "application/json").header("Authorization", token).header("X-TenantID", tenant)
				.with().body(new JSONObject(saveEstimatePayload).toMap()).when()
				.post("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90").then()
				.assertThat().statusCode(200);

		// call getAllEstimates--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String path = "/getAllEstimates";
		String payloadAsString = "{\"itemDetails\":[{\"entityType\":\"contractitem\",\"entityRefNo\":\"PCI-835\",\"applicableDate\":\"30-12-2019\"}]}";
		JSONObject payloadJson = new JSONObject(payloadAsString);
		given().log().all().header("X-TenantID", tenant).header("Content-Type", "application/json")
				.header("Authorization", token).with().body(payloadJson.toMap()).when().request("POST", path).then()
				.assertThat().statusCode(200);
		// fetch data from connect directly and see whether updated there--
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "";
		String fetchConnectDataPayload = "{\"filterData\":{\"filter\":[{\"fieldName\":\"entityRefNo\",\"value\":\"PCI-835\",\"operator\":\"eq\"}]}}";
		Response connectResponse = given().header("Content-Type", "application/json").header("Authorization", token)
				.header("X-TenantID", tenant).with().body(new JSONObject(fetchConnectDataPayload).toMap()).when()
				.get("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90");
		Assert.assertEquals(connectResponse.getStatusCode(), 200);
		JsonPath jsonPath = new JsonPath(connectResponse.asString());
		Assert.assertEquals(jsonPath.getDouble("[0].costValue"), 27.215542);
		Assert.assertEquals(jsonPath.getDouble("[0].costAmountInBaseCurrency"), 27.215542);
		Assert.assertEquals(jsonPath.getString("[0].costPriceUnitId"), "CM-M0-9");
	}
	
	@Test(enabled = true)
	public void testGetAllEstimatesApiToUpdateFxCurveCalculation() throws MalformedURLException {
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "";
		String saveEstimatePayload = "{\"itemQty\":\"1000\",\"fxRate\":\"USD/CNY\",\"isPopUp\":\"true\",\"costComponent\":\"SCM-M2-84\",\"costPriceUnitId\":\"CM-M0-3\",\"costComponentDisplayName\":\"BAGGING\",\"draftEstimateNo\":\"158132007462710\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"counterpartyGroupName\":\"PHD-M1-3455\",\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-1301\",\"corporateCurrency\":\"USD\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"counterpartyGroupNameDisplayName\":\"05.620.523\",\"rateTypePriceDisplayName\":\"Absolute\",\"costAmountInBaseCurrencyUnitId\":\"CNY\",\"costValue\":\"2\",\"fxToBaseType\":\"curve\",\"refType\":\"app\",\"itemQtyUnitId\":\"QUM-M0-1\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"shipmentDate\":\"29-Feb-2020\",\"costPriceUnitIdDisplayName\":\"CNY\",\"fxValueDate\":\"2020-01-22\",\"comments\":\"restassured-test\",\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":\"2\",\"userId\":\"2924\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"absolute\"}";
		given().header("Content-Type", "application/json").header("Authorization", token).header("X-TenantID", tenant)
				.with().body(new JSONObject(saveEstimatePayload).toMap()).when()
				.post("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90").then()
				.assertThat().statusCode(200);

		// call getAllEstimates--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String path = "/getAllEstimates";
		String payloadAsString = "{\"itemDetails\":[{\"entityType\":\"Contract Item\",\"entityRefNo\":\"PCI-1301\",\"applicableDate\":\"22-01-2020\"}]}";
		JSONObject payloadJson = new JSONObject(payloadAsString);
		given().log().all().header("X-TenantID", tenant).header("Content-Type", "application/json")
				.header("Authorization", token).with().body(payloadJson.toMap()).when().request("POST", path).then()
				.assertThat().statusCode(200);
		// fetch data from connect directly and see whether updated there--
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "";
		String fetchConnectDataPayload = "{\"filterData\":{\"filter\":[{\"fieldName\":\"entityRefNo\",\"value\":\"PCI-1301\",\"operator\":\"eq\"}]}}";
		Response connectResponse = given().header("Content-Type", "application/json").header("Authorization", token)
				.header("X-TenantID", tenant).with().body(new JSONObject(fetchConnectDataPayload).toMap()).when()
				.get("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90");
		Assert.assertEquals(connectResponse.getStatusCode(), 200);
		JsonPath jsonPath = new JsonPath(connectResponse.asString());
		connectResponse.getBody().asString();
		jsonPath.get("[0].fxToBase");
		Assert.assertEquals(jsonPath.getDouble("[0].fxToBase"), 0.64102566);
	}

	@Test(enabled = true)
	public void testGetAllEstimatesApiToGetDeletedData() throws MalformedURLException {

		// save cost estimate data by calling connect
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "";
		Response saveCostEstimateResponse = given().header("Content-Type", "application/json")
				.header("Authorization", token).header("X-TenantID", tenant).with()
				.body(payloadForSavingCostEstimateData()).when()
				.post("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90");
		saveCostEstimateResponse.getBody().asString();
		JsonPath jsonPath = new JsonPath(saveCostEstimateResponse.asString());
		String _id = jsonPath.getString("_id");

		// delete this data by using its _id-
		given().header("Content-Type", "application/json").header("Authorization", token).header("X-TenantID", tenant)
				.when().delete("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90/" + _id)
				.then().assertThat().statusCode(200);

		// call getAllEstimates and verify that the list of data contains any data with state Delete--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String path = "/getAllEstimates";
		Response getAllEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).when().request("POST", path);
		List dataList = getAllEstimatesResponse.getBody().as(List.class);
		boolean deletedDataPresent = false;
		if (!dataList.isEmpty()) {
			Iterator<Object> iterator = dataList.iterator();
			while (iterator.hasNext()) {
				Map<String, Object> individualData = (Map<String, Object>) iterator.next();
				if (individualData.containsKey("sys__data__state")
						&& individualData.get("sys__data__state").toString().equals("Delete"))
					deletedDataPresent = true;
			}
		}
		Assert.assertEquals(deletedDataPresent, true);
	}

	private Map<String, Object> payloadForSavingCostEstimateData() {
		String payloadJsonString = "{\"itemQty\":\"10000\",\"fxRate\":\"\",\"costAmountInBaseCurrencyUnitId\":\"BRL\",\"costValue\":\"12\",\"costComponent\":\"SCM-M0-2289\",\"fxToBaseType\":\"absolute\",\"costPriceUnitId\":\"CM-M0-2413\",\"costComponentDisplayName\":\"Container cleaning fee\",\"incExpense\":\"CostExpense\",\"estimateForDisplayName\":\"Valuation\",\"costMonth\":\"\",\"fxToPosition\":\"1\",\"costPriceUnitIdDisplayName\":\"BRL\",\"fxValueDate\":\"2019-12-06\",\"draftEstimateNo\":\"157561468376378\",\"group\":\"\",\"incExpenseDisplayName\":\"Expense\",\"comments\":\"P33\",\"entityType\":\"contractitem\",\"counterpartyGroupName\":\"PHD-M0-105768\",\"costAmountInAccuralEstimateCurrency\":\"\",\"fxToBase\":\"1\",\"costAmountInBaseCurrency\":\"12\",\"estimateFor\":\"Valuation\",\"contractPrice\":\"190.00 USD/KG\",\"entityRefNo\":\"PCI-13024\",\"corporateCurrency\":\"USD\",\"rateTypePrice\":\"absolute\",\"costCurve\":\"\",\"counterpartyGroupNameDisplayName\":\"21St Century Textiles Limited\",\"rateTypePriceDisplayName\":\"Absolute\",\"status\":\"\"}";
		JSONObject payloadJson = new JSONObject(payloadJsonString);
		return payloadJson.toMap();
	}

	@Test(enabled = true)
	public void testGetInstrumentNames() {
		String path = COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID + "/instrumentNames";
		Response actualResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).when().request("GET", path);
		Map<String, List<Map<String, String>>> responseJson = actualResponse.getBody().as(Map.class);
		Map<String, String> responseObject = responseJson.get("ICE-Raw Sugar No. 11 Futures").get(0);
		Assert.assertEquals(responseObject.get("Instrument Name"), "ICE-Raw Sugar No. 11 Futures");
		Assert.assertEquals(responseObject.get("Exchange"), "IEU");
		Assert.assertEquals(responseObject.get("Month/Year"), "AUG2021");
		Assert.assertEquals(responseObject.get("Pricing Date"), "2020-02-01T00:00:00");
	}
	
	@Test(enabled = true)
	public void testGetInstrumentNamesWithPassingCostComponent() {
		String path = COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID + "/instrumentNames";
		Response actualResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token)
				.param("costComponent", "FREIGHT").when().request("GET", path);
		Map<String, List<Map<String, String>>> responseJson = actualResponse.getBody().as(Map.class);
		Map<String, String> responseObject = responseJson.get("NABrazilBrazilDec-2023").get(0);
		Assert.assertEquals(responseObject.get("Instrument Name"), "NABrazilBrazilDec-2023");
		Assert.assertEquals(responseObject.get("Month/Year"), "Dec-2023");
		Assert.assertEquals(responseObject.get("Pricing Date"), "2019-02-18T00:00:00");
	}

	@Test(enabled = true)
	public void testGetInstrumentNamesFx() {
		String path = COST_APP_UUID + "/" + COST_ESTIMATE_OBJECT_UUID + "/instrumentNamesFx";
		Response actualResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with().when()
				.request("GET", path);
		actualResponse.getBody().asString();
		JSONObject responseJson = new JSONObject(actualResponse.getBody().asString());
		Assert.assertEquals(responseJson.getJSONArray("USD/EUR").getJSONObject(0).get("Value Date String"), "Aug-2019");
		Assert.assertEquals(responseJson.getJSONArray("USD/EUR").getJSONObject(0).get("Period End Date"),
				"2018-01-03T00:00:00");
		Assert.assertEquals(responseJson.getJSONArray("USD/EUR").getJSONObject(0).get("Rate Type"), "SPOT");
	}

	@Test(enabled = true)
	public void testAssociateApi() throws MalformedURLException {
		// save draft estimate
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveDraftEstimatePayloadString = "[{\"costType\":\"estimate\",\"itemQty\":\"1001\",\"costAmountInBaseCurrencyUnitId\":\"CNY\",\"costValue\":3,\"isPopUp\":\"true\",\"costComponent\":\"SCM-89\",\"fxToBaseType\":\"Absolute\",\"costPriceUnitId\":\"CM-M0-3\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"COG PD Schedule\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution & Valuation\",\"shipmentDate\":\"31-Mar-2020\",\"costPriceUnitIdDisplayName\":\"CNY\",\"draftEstimateNo\":\"157304692968537\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"fxToBase\":1.2,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":3,\"estimateFor\":\"Execution & Valuation\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"absolute\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"rateTypePriceDisplayName\":\"Absolute\"}]";
		JSONArray saveDraftPayloadJson = new JSONArray(saveDraftEstimatePayloadString);
		String saveDraftPath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/00189ca9-cfc1-4327-95ac-f937f22deb60";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveDraftPayloadJson.toList()).when().request("POST", saveDraftPath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String path = "/associateEstimatesWithEntity";
		String payloadAsString = "{\"157304692968537\":[\"PCI-testAssociate\"]}";
		JSONObject payloadJson = new JSONObject(payloadAsString);
		Response actualResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(payloadJson.toMap()).when().request("POST", path);
		JsonPath jsonPath = new JsonPath(actualResponse.asString());
		Object entityRefNoInResponse = null;
		entityRefNoInResponse = jsonPath.getString("[0].entityRefNo");
		Assert.assertEquals(entityRefNoInResponse, "PCI-testAssociate");
	}
	
	@Test(enabled = true)
	public void testCostPriceUnitIdHandlingForCostCurve() throws MalformedURLException {
		// this test was written with alveanqa318 as platform
		// save cost estimate with rateTypePrice=curve
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveCostEstimatePayloadString = "{\"itemQty\":\"1000\",\"fxRate\":\"\",\"costAmountInBaseCurrencyUnitId\":\"\",\"costValue\":\"\",\"costComponent\":\"SCM-M0-2290\",\"fxToBaseType\":\"\",\"secondaryCost\":\"\",\"costPriceUnitId\":\"\",\"costComponentDisplayName\":\"Broker\",\"payInCurId\":\"CM-M0-2408\",\"sys__state\":{\"itemQty\":{\"show\":true,\"disable\":false},\"fxRate\":{\"show\":true,\"disable\":false},\"costAmountInBaseCurrencyUnitId\":{\"show\":true,\"disable\":false},\"costValue\":{\"show\":true,\"disable\":false},\"costComponent\":{\"show\":true,\"disable\":false},\"fxToBaseType\":{\"show\":true,\"disable\":false},\"secondaryCost\":{\"show\":true,\"disable\":false},\"costPriceUnitId\":{\"show\":true,\"disable\":false},\"costComponentDisplayName\":{\"show\":true,\"disable\":false},\"payInCurId\":{\"show\":true,\"disable\":false},\"incExpense\":{\"show\":true,\"disable\":false},\"estimateForDisplayName\":{\"show\":true,\"disable\":false},\"costMonth\":{\"show\":true,\"disable\":false},\"fxToPosition\":{\"show\":true,\"disable\":false},\"costPriceUnitIdDisplayName\":{\"show\":true,\"disable\":false},\"fxValueDate\":{\"show\":true,\"disable\":false},\"draftEstimateNo\":{\"show\":true,\"disable\":false},\"group\":{\"show\":true,\"disable\":false},\"incExpenseDisplayName\":{\"show\":true,\"disable\":false},\"comments\":{\"show\":true,\"disable\":false},\"productId\":{\"show\":true,\"disable\":false},\"entityType\":{\"show\":true,\"disable\":false},\"counterpartyGroupName\":{\"show\":true,\"disable\":false},\"costAmountInAccuralEstimateCurrency\":{\"show\":true,\"disable\":false},\"fxToBase\":{\"show\":true,\"disable\":false},\"priceType\":{\"show\":true,\"disable\":false},\"costAmountInBaseCurrency\":{\"show\":true,\"disable\":false},\"estimateFor\":{\"show\":true,\"disable\":false},\"contractIncoTerm\":{\"show\":true,\"disable\":false},\"entityRefNo\":{\"show\":true,\"disable\":false},\"contractPrice\":{\"show\":true,\"disable\":false},\"corporateCurrency\":{\"show\":true,\"disable\":false},\"valuationIncoTerm\":{\"show\":true,\"disable\":false},\"rateTypePrice\":{\"show\":true,\"disable\":false},\"costCurve\":{\"show\":true,\"disable\":false},\"paymentTerm\":{\"show\":true,\"disable\":false},\"counterpartyGroupNameDisplayName\":{\"show\":true,\"disable\":false},\"rateTypePriceDisplayName\":{\"show\":true,\"disable\":false},\"status\":{\"show\":true,\"disable\":false}},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"costMonth\":\"Nov-2019\",\"fxToPosition\":\"\",\"costPriceUnitIdDisplayName\":\"\",\"draftEstimateNo\":\"\",\"group\":\"\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"restassuredtest\",\"productId\":\"PDM-10658\",\"entityType\":\"contractitem\",\"counterpartyGroupName\":\"PHD-M0-111905\",\"costAmountInAccuralEstimateCurrency\":\"\",\"fxToBase\":\"\",\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":\"\",\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"entityRefNo\":\"PCI-8004\",\"contractPrice\":\"\",\"corporateCurrency\":\"EUR\",\"valuationIncoTerm\":\"FAS\",\"rateTypePrice\":\"curve\",\"costCurve\":\"CBOT-Wheat-Future\",\"paymentTerm\":\"PDP At Sight\",\"counterpartyGroupNameDisplayName\":\"24Vision Legal And Claims Solutions B.V.\",\"rateTypePriceDisplayName\":\"Curve\",\"status\":\"\"}";
		JSONObject saveEstimatePayloadJson = new JSONObject(saveCostEstimatePayloadString);
		String saveEstimatePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveEstimatePayloadJson.toMap()).when().request("POST", saveEstimatePath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		JsonPath saveEstimateJsonPath = new JsonPath(saveEstimateResponse.asString());
		Assert.assertEquals(saveEstimateJsonPath.get("entityRefNo"), "PCI-8004");
		// call getAllEstimates API to check CostPriceUnitId--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String getAllEstimatesPayload = "{\"itemDetails\":[{\"entityType\":\"contractitem\",\"entityRefNo\":\"PCI-8004\",\"applicableDate\":\"17-06-2019\"}]}";
		JSONObject getAllEstimatesPayloadJson = new JSONObject(getAllEstimatesPayload);
		String getAllEstimatesPath = "/getAllEstimates";
		Response getAllEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(getAllEstimatesPayloadJson.toMap()).when().request("POST", getAllEstimatesPath);
		Assert.assertEquals(getAllEstimatesResponse.getStatusCode(), 200);
		JsonPath getAllEstimatesJsonPath = new JsonPath(getAllEstimatesResponse.asString());
		getAllEstimatesResponse.then().assertThat().body("size()", greaterThan(0));
		Assert.assertEquals(getAllEstimatesJsonPath.get("[0].entityRefNo"), "PCI-8004");
		Assert.assertEquals(getAllEstimatesJsonPath.get("[0].costPriceUnitId"), "PPU-18953");
		Assert.assertEquals(getAllEstimatesJsonPath.get("[0].costPriceUnitIdDisplayName"), "USD/MT");
		Assert.assertEquals(getAllEstimatesJsonPath.getInt("[0].costValue"), 0);
	}
	
	@Test(enabled = true)
	public void testPercentOfPriceTypeHandling() throws MalformedURLException {
		// this test was written with alveanqa318 as platform
		// save cost estimate with rateTypePrice=% of price
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveCostEstimatePayloadString = "{\"itemQty\":\"1500\",\"fxRate\":\"\",\"costAmountInBaseCurrencyUnitId\":\"5\",\"costValue\":\"12\",\"costComponent\":\"SCM-M0-2290\",\"fxToBaseType\":\"absolute\",\"secondaryCost\":\"\",\"costPriceUnitId\":\"\",\"costComponentDisplayName\":\"Broker\",\"payInCurId\":\"CM-M0-2408\",\"sys__state\":{\"itemQty\":{\"show\":true,\"disable\":false},\"fxRate\":{\"show\":true,\"disable\":false},\"costAmountInBaseCurrencyUnitId\":{\"show\":true,\"disable\":false},\"costValue\":{\"show\":true,\"disable\":false},\"costComponent\":{\"show\":true,\"disable\":false},\"fxToBaseType\":{\"show\":true,\"disable\":false},\"secondaryCost\":{\"show\":true,\"disable\":false},\"costPriceUnitId\":{\"show\":true,\"disable\":false},\"costComponentDisplayName\":{\"show\":true,\"disable\":false},\"payInCurId\":{\"show\":true,\"disable\":false},\"incExpense\":{\"show\":true,\"disable\":false},\"estimateForDisplayName\":{\"show\":true,\"disable\":false},\"costMonth\":{\"show\":true,\"disable\":false},\"fxToPosition\":{\"show\":true,\"disable\":false},\"costPriceUnitIdDisplayName\":{\"show\":true,\"disable\":false},\"fxValueDate\":{\"show\":true,\"disable\":false},\"draftEstimateNo\":{\"show\":true,\"disable\":false},\"group\":{\"show\":true,\"disable\":false},\"incExpenseDisplayName\":{\"show\":true,\"disable\":false},\"comments\":{\"show\":true,\"disable\":false},\"productId\":{\"show\":true,\"disable\":false},\"entityType\":{\"show\":true,\"disable\":false},\"counterpartyGroupName\":{\"show\":true,\"disable\":false},\"costAmountInAccuralEstimateCurrency\":{\"show\":true,\"disable\":false},\"fxToBase\":{\"show\":true,\"disable\":false},\"priceType\":{\"show\":true,\"disable\":false},\"costAmountInBaseCurrency\":{\"show\":true,\"disable\":false},\"estimateFor\":{\"show\":true,\"disable\":false},\"contractIncoTerm\":{\"show\":true,\"disable\":false},\"entityRefNo\":{\"show\":true,\"disable\":false},\"contractPrice\":{\"show\":true,\"disable\":false},\"corporateCurrency\":{\"show\":true,\"disable\":false},\"valuationIncoTerm\":{\"show\":true,\"disable\":false},\"rateTypePrice\":{\"show\":true,\"disable\":false},\"costCurve\":{\"show\":true,\"disable\":false},\"paymentTerm\":{\"show\":true,\"disable\":false},\"counterpartyGroupNameDisplayName\":{\"show\":true,\"disable\":false},\"rateTypePriceDisplayName\":{\"show\":true,\"disable\":false},\"status\":{\"show\":true,\"disable\":false}},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"costMonth\":\"\",\"fxToPosition\":\"2\",\"costPriceUnitIdDisplayName\":\"\",\"fxValueDate\":\"2020-01-09\",\"draftEstimateNo\":\"\",\"group\":\"\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"restassuredtest\",\"productId\":\"PDM-10658\",\"entityType\":\"contractitem\",\"counterpartyGroupName\":\"PHD-M0-105768\",\"costAmountInAccuralEstimateCurrency\":\"\",\"fxToBase\":\"2\",\"priceType\":\"\",\"costAmountInBaseCurrency\":\"5\",\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"entityRefNo\":\"PCI-8081\",\"contractPrice\":\"10.00000 USD\\/MT\",\"corporateCurrency\":\"EUR\",\"valuationIncoTerm\":\"FAS\",\"rateTypePrice\":\"% of Price\",\"costCurve\":\"\",\"paymentTerm\":\"PLC90 days from FCR Date\",\"counterpartyGroupNameDisplayName\":\"21St Century Textiles Limited\",\"rateTypePriceDisplayName\":\"% of Price\",\"status\":\"\"}";
		JSONObject saveEstimatePayloadJson = new JSONObject(saveCostEstimatePayloadString);
		String saveEstimatePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveEstimatePayloadJson.toMap()).when().request("POST", saveEstimatePath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		JsonPath saveEstimateJsonPath = new JsonPath(saveEstimateResponse.asString());
		Assert.assertEquals(saveEstimateJsonPath.get("entityRefNo"), "PCI-8081");
		// call getAllEstimates API to check CostPriceUnitId--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String getAllEstimatesPayload = "{\"itemDetails\":[{\"entityType\":\"contractitem\",\"entityRefNo\":\"PCI-8081\",\"applicableDate\":\"17-06-2019\"}]}";
		JSONObject getAllEstimatesPayloadJson = new JSONObject(getAllEstimatesPayload);
		String getAllEstimatesPath = "/getAllEstimates";
		Response getAllEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(getAllEstimatesPayloadJson.toMap()).when().request("POST", getAllEstimatesPath);
		Assert.assertEquals(getAllEstimatesResponse.getStatusCode(), 200);
		JsonPath getAllEstimatesJsonPath = new JsonPath(getAllEstimatesResponse.asString());
		getAllEstimatesResponse.then().assertThat().body("size()", greaterThan(0));
		Assert.assertEquals(getAllEstimatesJsonPath.get("[0].entityRefNo"), "PCI-8081");
		Assert.assertEquals(getAllEstimatesJsonPath.getDouble("[0].costValue"), 1.2);
		Assert.assertEquals(getAllEstimatesJsonPath.get("[0].costPriceUnitId"), "PPU-18953");
		Assert.assertEquals(getAllEstimatesJsonPath.get("[0].costPriceUnitIdDisplayName"), "USD/MT");
	}
	
	@Test(enabled = true)
	public void testReverseAssociateAPI() throws MalformedURLException {
		// save cost estimate
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveCostEstimatePayloadString = "{  \"itemQty\": \"1000\",  \"fxRate\": \"\",  \"costComponent\": \"SCM-2292\",  \"costPriceUnitId\": \"CM-M0-2414\",  \"costComponentDisplayName\": \"Calculated product price correction\",  \"fxToPosition\": \"1\",  \"group\": \"\",  \"incExpenseDisplayName\": \"Expense\",  \"productId\": \"PDM-10658\",  \"entityType\": \"contractitem\",  \"counterpartyGroupName\": \"PHD-M0-105768\",  \"estimateFor\": \"Execution\",  \"contractIncoTerm\": \"CIF\",  \"entityRefNo\": \"PCI-8081\",  \"corporateCurrency\": \"EUR\",  \"paymentTerm\": \"PLC At Sight against presentation of B/L\",  \"counterpartyGroupNameDisplayName\": \"21St Century Textiles Limited\",  \"rateTypePriceDisplayName\": \"Absolute\",  \"status\": \"\",  \"costAmountInBaseCurrencyUnitId\": \"CAD\",  \"costValue\": \"12\",  \"fxToBaseType\": \"absolute\",  \"refType\": \"app\",  \"payInCurId\": \"CM-M0-2434\",  \"incExpense\": \"CostExpense\",  \"estimateForDisplayName\": \"Execution\",  \"costMonth\": \"\",  \"costPriceUnitIdDisplayName\": \"CAD\",  \"fxValueDate\": \"2020-01-10\",  \"comments\": \"retre\",  \"costAmountInAccuralEstimateCurrency\": \"\",  \"fxToBase\": \"1\",  \"priceType\": \"On Call Basis Fixed\",  \"costAmountInBaseCurrency\": \"12\",  \"contractPrice\": \"\",  \"valuationIncoTerm\": \"Select\",  \"rateTypePrice\": \"absolute\",  \"costCurve\": \"\"}";
		JSONObject saveEstimatePayloadJson = new JSONObject(saveCostEstimatePayloadString);
		String saveEstimatePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveEstimatePayloadJson.toMap()).when().request("POST", saveEstimatePath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		JsonPath saveEstimateJsonPath = new JsonPath(saveEstimateResponse.asString());
		Assert.assertEquals(saveEstimateJsonPath.get("entityRefNo"), "PCI-8081");
		// call reverseAssociate API to check daftEstimateNo--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String reverseAssociatePayload = "{\"PCI-8081\":[\"157304692968536\"]}";
		JSONObject reverseAssociatePayloadJson = new JSONObject(reverseAssociatePayload);
		String reverseAssociatePath = "/pushToDraft";
		Response reverseAssociateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(reverseAssociatePayloadJson.toMap()).when().request("POST", reverseAssociatePath);
		Assert.assertEquals(reverseAssociateResponse.getStatusCode(), 200);
		JsonPath reverseAssociateJsonPath = new JsonPath(reverseAssociateResponse.asString());
		reverseAssociateResponse.then().assertThat().body("size()", greaterThan(0));
		Assert.assertEquals(reverseAssociateJsonPath.get("[0].draftEstimateNo"), "157304692968536");
	}
	
	/**
	 * Test save estimates.
	 * JIRA EPSCM-20677
	 * PBS link contract item is showing error message when voyage charter is linked to PBS
	 *
	 * @throws MalformedURLException the malformed URL exception
	 */
	@Test(enabled = true)
	public void testSaveEstimates() throws MalformedURLException {
		// save one estimate and verify its entityrefno in response--
		URL eka_utility_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_utility_host_url.getHost();
		RestAssured.port = eka_utility_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String saveEstimatePayloadString = "{\"data\":[{\"itemQty\":\"18950\",\"incExpenseDisplayName\":\"Expense\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"costValue\":\"39\",\"sys__version\":0,\"entityType\":\"Planned Shipment\",\"counterpartyGroupName\":\"PHD-M1-3493\",\"costComponent\":\"SCM-M2-81\",\"fxToBaseType\":\"Absolute\",\"fxToBase\":\"1.00000000\",\"costPriceUnitId\":\"PPU-M2-22\",\"costComponentDisplayName\":\"Freight - Ocean\",\"costAmountInBaseCurrency\":\"739050.00000000\",\"entityActualNo\":\"PBS-798-SWA\",\"incExpense\":\"CostExpense\",\"estimateFor\":\"Execution\",\"estimateForDisplayName\":\"Execution\",\"entityRefNo\":\"CVB-2200\",\"corporateCurrency\":\"USD\",\"rateTypePrice\":\"rate\",\"costPriceUnitIdDisplayName\":\"USD/MT\",\"counterpartyGroupNameDisplayName\":\"ZIM (THAILAND)\",\"rateTypePriceDisplayName\":\"Rate\"}]}";
		JSONObject saveEstimatesPayloadJson = new JSONObject(saveEstimatePayloadString);
		String saveEstimatesPath = "/saveEstimates";
		Response saveEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveEstimatesPayloadJson.toMap()).when().request("POST", saveEstimatesPath);
		saveEstimatesResponse.then().assertThat().statusCode(200).and().body("size()", is(1)).and()
				.body("[0].entityRefNo", is("CVB-2200"));
	}
	
	@Test(enabled = true)
	public void testCloneDraftEstimateAPI() throws MalformedURLException {
		// save draft estimate
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveDraftEstimatePayloadString = "[{\"costType\":\"estimate\",\"itemQty\":\"1001\",\"costAmountInBaseCurrencyUnitId\":\"CNY\",\"costValue\":3,\"isPopUp\":\"true\",\"costComponent\":\"SCM-89\",\"fxToBaseType\":\"Absolute\",\"costPriceUnitId\":\"CM-M0-3\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"COG PD Schedule\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution & Valuation\",\"shipmentDate\":\"31-Mar-2020\",\"costPriceUnitIdDisplayName\":\"CNY\",\"draftEstimateNo\":\"123456\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"fxToBase\":1.2,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":3,\"estimateFor\":\"Execution & Valuation\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"absolute\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"rateTypePriceDisplayName\":\"Absolute\"}]";
		JSONArray saveDraftPayloadJson = new JSONArray(saveDraftEstimatePayloadString);
		String saveDraftPath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/00189ca9-cfc1-4327-95ac-f937f22deb60";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveDraftPayloadJson.toList()).when().request("POST", saveDraftPath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		JsonPath saveEstimateJsonPath = new JsonPath(saveEstimateResponse.asString());
		Assert.assertEquals(saveEstimateJsonPath.getString("[0].draftEstimateNo"), "123456");
		// call cloneDraftEstimate API to check daftEstimateNo--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String cloneDraftPayload = "{\"123456\":[\"123456_MOD\"]}";
		JSONObject cloneDraftPayloadJson = new JSONObject(cloneDraftPayload);
		String cloneDraftAPIPath = "/cloneDraftEstimate";
		Response cloneDraftEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(cloneDraftPayloadJson.toMap()).when().request("POST", cloneDraftAPIPath);
		Assert.assertEquals(cloneDraftEstimateResponse.getStatusCode(), 200);
		JsonPath cloneDraftJsonPath = new JsonPath(cloneDraftEstimateResponse.asString());
		cloneDraftEstimateResponse.then().assertThat().body("size()", greaterThan(0));
		Assert.assertEquals(cloneDraftJsonPath.get("[0].draftEstimateNo"), "123456_MOD");
	}
	
	

	@Test(enabled = true)
	public void testErrorOnInvalidCostValueAndAmount() throws MalformedURLException {
		// save cost estimate without cost value and cost amount--
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveEstimatePayloadString = "[{\"costValue\":0,\"itemQty\":\"1000\",\"isPopUp\":\"true\",\"costComponent\":\"SCM-M2-84\",\"fxToBaseType\":\"Absolute\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"BAGGING\",\"curveCurrencyDisplayName\":\"US cents/LB\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostExpense\",\"curveCurrency\":\"US cents/LB\",\"estimateForDisplayName\":\"Valuation\",\"costMonth\":\"AUG2021\",\"shipmentDate\":\"31-Mar-2020\",\"costMonthDisplayName\":\"AUG2021\",\"draftEstimateNo\":\"158521458441164\",\"incExpenseDisplayName\":\"Expense\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"fxToBase\":1.2,\"priceType\":\"On Call Basis Fixed\",\"costCurveDisplayName\":\"ICE-Raw Sugar No. 11 Futures\",\"estimateFor\":\"Valuation\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"curve\",\"costCurve\":\"ICE-Raw Sugar No. 11 Futures\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"rateTypePriceDisplayName\":\"Curve\",\"entityRefNo\":\"PCI-1234\"}]";
		JSONArray saveEstimatePayloadJson = new JSONArray(saveEstimatePayloadString);
		String saveEstimatePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveEstimatePayloadJson.toList()).when().request("POST", saveEstimatePath);
		Assert.assertEquals(saveEstimateResponse.getStatusCode(), 200);
		JsonPath saveEstimateJsonPath = new JsonPath(saveEstimateResponse.asString());
		Assert.assertEquals(saveEstimateJsonPath.get("[0].entityRefNo"), "PCI-1234");
		// call getAllEstimates API and check response code 500 and errors in response
		// --
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String getAllEstimatesPayload = "{\"itemDetails\":[{\"entityType\":\"Contract Item\",\"entityRefNo\":\"PCI-1234\",\"applicableDate\":\"07-03-2020\"}],\"getDeletedData\":\"Y\"}";
		JSONObject getAllEstimatesPayloadJson = new JSONObject(getAllEstimatesPayload);
		String getAllEstimatesPath = "/getAllEstimates";
		Response getAllEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(getAllEstimatesPayloadJson.toMap()).when().request("POST", getAllEstimatesPath);
		Assert.assertEquals(getAllEstimatesResponse.getStatusCode(), 500);
		JsonPath reverseAssociateJsonPath = new JsonPath(getAllEstimatesResponse.asString());
		Assert.assertEquals(reverseAssociateJsonPath.get("status"), "INTERNAL_SERVER_ERROR");
		Assert.assertEquals(reverseAssociateJsonPath.get("message"),
				"Mandatory fields missing, please update and proceed.");
		getAllEstimatesResponse.then().assertThat().body("connectErrors.size()", greaterThan(0));
	}
	
	@Test(enabled = true)
	public void testCopyEstimatesAPI() throws MalformedURLException {
		// save 4 estimates ,two of which are purchase, two are sales, and one of the
		// sales is Valuation--
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String saveEstimatePayloadString = "[{\"itemQty\":\"10000\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"isPopUp\":\"true\",\"costValue\":100,\"conversionFactor\":\"1\",\"costComponent\":\"SCM-89\",\"fxToBaseType\":\"Absolute\",\"costPriceUnitId\":\"CM-M0-9\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"COG PD Schedule\",\"entityActualNo\":\"PC-248-USA\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution & Valuation\",\"shipmentDate\":\"31-Mar-2020\",\"costPriceUnitIdDisplayName\":\"USD\",\"draftEstimateNo\":\"158737579985834\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":100,\"estimateFor\":\"Execution & Valuation\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-9998\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"absolute\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"rateTypePriceDisplayName\":\"Absolute\"},{\"itemQty\":\"1000\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"isPopUp\":\"true\",\"costValue\":100,\"conversionFactor\":\"1\",\"costComponent\":\"SCM-M2-78\",\"fxToBaseType\":\"Absolute\",\"costPriceUnitId\":\"PPU-M2-17\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"ADDITIVE\",\"entityActualNo\":\"PC-1594-SWA\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution & Valuation\",\"shipmentDate\":\"30-Apr-2020\",\"costPriceUnitIdDisplayName\":\"USD/MT\",\"draftEstimateNo\":\"158687534261317\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"counterpartyGroupName\":\"PHD-M1-3467\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":100000,\"estimateFor\":\"Valuation\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-9998\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"rate\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"counterpartyGroupNameDisplayName\":\"14796754\",\"rateTypePriceDisplayName\":\"Rate\"},{\"itemQty\":\"10000\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"isPopUp\":\"true\",\"costValue\":100,\"conversionFactor\":\"1\",\"costComponent\":\"SCM-89\",\"fxToBaseType\":\"Absolute\",\"costPriceUnitId\":\"CM-M0-9\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"COG PD Schedule\",\"entityActualNo\":\"PC-248-USA\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"shipmentDate\":\"31-Mar-2020\",\"costPriceUnitIdDisplayName\":\"USD\",\"draftEstimateNo\":\"158737579985834\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":100,\"estimateFor\":\"Execution & Valuation\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"SCI-9998\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"absolute\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"rateTypePriceDisplayName\":\"Absolute\"},{\"itemQty\":\"10000\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"isPopUp\":\"true\",\"costValue\":100,\"conversionFactor\":\"1\",\"costComponent\":\"SCM-89\",\"fxToBaseType\":\"Absolute\",\"costPriceUnitId\":\"CM-M0-9\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"COG PD Schedule\",\"entityActualNo\":\"PC-248-USA\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution & Valuation\",\"shipmentDate\":\"31-Mar-2020\",\"costPriceUnitIdDisplayName\":\"USD\",\"draftEstimateNo\":\"158737579985834\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":100,\"estimateFor\":\"Execution & Valuation\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"SCI-9998\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"absolute\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"rateTypePriceDisplayName\":\"Absolute\"}]";
		JSONArray saveEstimatePayloadJson = new JSONArray(saveEstimatePayloadString);
		String saveEstimatePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90";
		Response saveEstimateResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveEstimatePayloadJson.toList()).when().request("POST", saveEstimatePath);
		saveEstimateResponse.then().assertThat().statusCode(200).and().body("size()", is(4));
		// call copyEstimates API and verify that 3 estimates were saved, all have same
		// passed entityType and entityRefNo--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String copyEstimatesPayloadString = "{\"source\":{\"filter\":[{\"fieldName\":\"entityType\",\"value\":\"Contract Item\",\"operator\":\"eq\"},{\"fieldName\":\"entityRefNo\",\"value\":[\"PCI-9998\",\"SCI-9998\"],\"operator\":\"in\"},{\"fieldName\":\"estimateFor\",\"value\":\"Valuation\",\"operator\":\"ne\"}]},\"target\":{\"entityType\":\"Planned Shipment\",\"entityRefNo\":\"PBS-4354\"},\"applicableDate\":\"07-04-2020\",\"getDeletedData\":\"N\"}";
		JSONObject copyEstimatesPayloadJson = new JSONObject(copyEstimatesPayloadString);
		String copyEstimatesPath = "/copyEstimates";
		Response copyEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(copyEstimatesPayloadJson.toMap()).when().request("POST", copyEstimatesPath);
		copyEstimatesResponse.then().assertThat().statusCode(200).and().body("size()", is(3));
		Assert.assertFalse(copyEstimatesResponse.getBody().asString().contains("estimateFor\":\"Valuation"));
		List<Map<String, Object>> estimates = copyEstimatesResponse.getBody().as(List.class);
		Set<Object> entityRefNosExpected = new HashSet<>();
		entityRefNosExpected.add("PBS-4354");
		Assert.assertEquals(estimates.stream().map(estimate -> estimate.get("entityRefNo")).collect(Collectors.toSet()),
				entityRefNosExpected);
		Set<Object> entityTypesExpected = new HashSet<>();
		entityTypesExpected.add("Planned Shipment");
		Assert.assertEquals(estimates.stream().map(estimate -> estimate.get("entityType")).collect(Collectors.toSet()),
				entityTypesExpected);
	}
	
	@Test(enabled = true)
	public void testCopyEstimatesAPIWhenPayloadIncorrect() throws MalformedURLException {
		// call copyEstimates API without passing target and verify that error is received--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String copyEstimatesPayloadString = "{\"source\":{\"filter\":[{\"fieldName\":\"entityType\",\"value\":\"Contract Item\",\"operator\":\"eq\"},{\"fieldName\":\"entityRefNo\",\"value\":[\"PCI-9998\",\"SCI-9998\"],\"operator\":\"in\"},{\"fieldName\":\"estimateFor\",\"value\":\"Valuation\",\"operator\":\"ne\"}]},\"applicableDate\":\"07-04-2020\",\"getDeletedData\":\"N\"}";
		JSONObject copyEstimatesPayloadJson = new JSONObject(copyEstimatesPayloadString);
		String copyEstimatesPath = "/copyEstimates";
		Response copyEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(copyEstimatesPayloadJson.toMap()).when().request("POST", copyEstimatesPath);
		copyEstimatesResponse.then().assertThat().statusCode(501).and().body("message",
				is("Error in copying cost estimates due to:payload sent to copy estimates is not correct!"));
	}
	
	@Test(enabled = true)
	public void testSaveEstimatesAPI() throws MalformedURLException {
		// save one estimate and verify its entityrefno in response--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String saveEstimatePayloadString = "{\"data\":[{\"itemQty\":\"110\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"isPopUp\":\"true\",\"costValue\":405.9,\"costComponent\":\"SCM-M2-78\",\"fxToBaseType\":\"Absolute\",\"costPriceUnitId\":\"PPU-M2-14\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"ADDITIVE\",\"curveCurrencyDisplayName\":\"US cents/LB\",\"payInCurId\":\"CM-M0-9\",\"sys__state\":{},\"incExpense\":\"CostIncome\",\"curveCurrency\":\"US cents/LB\",\"estimateForDisplayName\":\"Execution & Valuation\",\"costMonth\":\"MAY2020\",\"shipmentDate\":\"29-Feb-2020\",\"costMonthDisplayName\":\"MAY2020\",\"costPriceUnitIdDisplayName\":\"US cents/LB\",\"draftEstimateNo\":\"158393576826430\",\"incExpenseDisplayName\":\"Income\",\"productId\":\"PDM-M2-24\",\"entityType\":\"Contract Item\",\"fxToBase\":\"1\",\"priceType\":\"Flat\",\"refTypeId\":\"d33143ac-4164-4a3f-8d30-61d845c9eeed\",\"costCurveDisplayName\":\"ICE-US Sugar No. 11 Futures\",\"costAmountInBaseCurrency\":984342.757367855,\"estimateFor\":\"Execution & Valuation\",\"contractIncoTerm\":\"CIF\",\"contractPrice\":\"100.0000 USD/MT\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-test-pr\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"curve\",\"costCurve\":\"ICE-US Sugar No. 11 Futures\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"rateTypePriceDisplayName\":\"Curve\"}]}";
		JSONObject saveEstimatesPayloadJson = new JSONObject(saveEstimatePayloadString);
		String saveEstimatesPath = "/saveEstimates";
		Response saveEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveEstimatesPayloadJson.toMap()).when().request("POST", saveEstimatesPath);
		saveEstimatesResponse.then().assertThat().statusCode(200).and().body("size()", is(1)).and()
				.body("[0].entityRefNo", is("PCI-test-pr"));
	}
	
	@Test(enabled = true)
	public void testValidateEstimatesAPI() throws MalformedURLException {
		// save one estimate and verify its entityrefno in response--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String validateEstimatePayloadString = "{\"data\":[{\"itemQty\":\"2000\",\"sys__data__state\":\"Create\",\"isPopUp\":\"true\",\"conversionFactor\":\"1\",\"costComponent\":\"SCM-M2-81\",\"fxToBaseType\":\"Absolute\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"FREIGHT\",\"curveCurrencyDisplayName\":\"USD/MT\",\"entityActualNo\":\"PC-1599-SWA\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostIncome\",\"curveCurrency\":\"USD/MT\",\"estimateForDisplayName\":\"Execution\",\"costMonth\":\"Mar-2019\",\"fxToPosition\":1,\"shipmentDate\":\"30-Apr-2020\",\"costMonthDisplayName\":\"Mar-2019\",\"draftEstimateNo\":\"158702180745025\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"pr-1\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"counterpartyGroupName\":\"PHD-M1-3466\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costCurveDisplayName\":\"PanamaxSantosAlgiersMar-2019\",\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-4264\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"curve\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"costCurve\":\"PanamaxSantosAlgiersMar-2019\",\"counterpartyGroupNameDisplayName\":\"20395096\",\"rateTypePriceDisplayName\":\"Curve\"},{\"itemQty\":\"2000\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"sys__data__state\":\"Create\",\"isPopUp\":\"true\",\"costValue\":2,\"costComponent\":\"SCM-M2-84\",\"fxToBaseType\":\"Absolute\",\"refType\":\"app\",\"costPriceUnitId\":\"PPU-M2-17\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"BAGGING\",\"entityActualNo\":\"PC-1599-SWA\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"shipmentDate\":\"30-Apr-2020\",\"costPriceUnitIdDisplayName\":\"CNY/LB\",\"draftEstimateNo\":\"158702180745025\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"pr-2\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"counterpartyGroupName\":\"PHD-M1-3467\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":4000,\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-4264\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"rate\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"counterpartyGroupNameDisplayName\":\"14796754\",\"rateTypePriceDisplayName\":\"Rate\"}]}";
		JSONObject validateEstimatesPayloadJson = new JSONObject(validateEstimatePayloadString);
		String validateEstimatesPath = "/validateCostEstimates";
		Response validateEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(validateEstimatesPayloadJson.toMap()).when().request("POST", validateEstimatesPath);
		validateEstimatesResponse.then().assertThat().statusCode(200).and().body("size()", is(2)).and()
				.body("[0].status", is(true)).and().body("[1].status", is(true));
	}

	@Test(enabled = true)
	public void testValidateAndSaveEstimatesAPI() throws MalformedURLException {
		// save one estimate and verify its entityrefno in response--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String validateEstimatePayloadString = "{\"data\":[{\"itemQty\":\"2000\",\"sys__data__state\":\"Create\",\"isPopUp\":\"true\",\"conversionFactor\":\"1\",\"costComponent\":\"SCM-M2-81\",\"fxToBaseType\":\"Absolute\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"FREIGHT\",\"curveCurrencyDisplayName\":\"USD/MT\",\"entityActualNo\":\"PC-1599-SWA\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostIncome\",\"curveCurrency\":\"USD/MT\",\"estimateForDisplayName\":\"Execution\",\"costMonth\":\"Mar-2019\",\"fxToPosition\":1,\"shipmentDate\":\"30-Apr-2020\",\"costMonthDisplayName\":\"Mar-2019\",\"draftEstimateNo\":\"158702180745025\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"pr-1\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"counterpartyGroupName\":\"PHD-M1-3466\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costCurveDisplayName\":\"PanamaxSantosAlgiersMar-2019\",\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-4264\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"curve\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"costCurve\":\"PanamaxSantosAlgiersMar-2019\",\"counterpartyGroupNameDisplayName\":\"20395096\",\"rateTypePriceDisplayName\":\"Curve\"},{\"itemQty\":\"2000\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"sys__data__state\":\"Create\",\"isPopUp\":\"true\",\"costValue\":2,\"costComponent\":\"SCM-M2-84\",\"fxToBaseType\":\"Absolute\",\"refType\":\"app\",\"costPriceUnitId\":\"PPU-M2-17\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"BAGGING\",\"entityActualNo\":\"PC-1599-SWA\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"shipmentDate\":\"30-Apr-2020\",\"costPriceUnitIdDisplayName\":\"CNY/LB\",\"draftEstimateNo\":\"158702180745025\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"pr-2\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"counterpartyGroupName\":\"PHD-M1-3467\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":4000,\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-4264\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"rate\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"counterpartyGroupNameDisplayName\":\"14796754\",\"rateTypePriceDisplayName\":\"Rate\"}]}";
		JSONObject validateEstimatesPayloadJson = new JSONObject(validateEstimatePayloadString);
		String validateEstimatesPath = "/saveCostEstimates";
		Response validateEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(validateEstimatesPayloadJson.toMap()).when().request("POST", validateEstimatesPath);
		validateEstimatesResponse.then().assertThat().statusCode(200).and().body("size()", is(2)).and()
				.body("[0].status", is(true)).and().body("[1].status", is(true));
	}
	
	@Test(enabled = true)
	public void testGetCostComponentsAPI() throws MalformedURLException {
		// save one estimate and verify its entityrefno in response--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String getCostComponentsPayloadString = "{\"attributes\":[{\"attributeName\":\"Contract Type\",\"attributeValue\":\"Purchase\"},{\"attributeName\":\"Contract Incoterm\",\"attributeValue\":\"DAF\"},{\"attributeName\":\"Valuation Incoterm\",\"attributeValue\":\"CIP\"},{\"attributeName\":\"Payment Term\",\"attributeValue\":\"C03R - Cash 30 days after release of original docs\"}]}";
		JSONObject getCostComponentsPayloadJson = new JSONObject(getCostComponentsPayloadString);
		String getCostComponentsPath = "/getCostComponents";
		Response getCostComponentsResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(getCostComponentsPayloadJson.toMap()).when().request("POST", getCostComponentsPath);
		getCostComponentsResponse.then().assertThat().statusCode(200);
	}
	
	/**
	 * Test save mandatory costs to draft.
	 * JIRA CCA-585,CCA-829
	 * Enhance /saveMandatoryCostsToDraft API so that it does not default the mandatory costs if they have already been created for a rule
	 * @throws MalformedURLException the malformed URL exception
	 */
	@Test(enabled = true)
	public void testsaveMandatoryCostsToDraft() throws MalformedURLException {
		// save two cost components(templates) testsaveMandatoryCostsToDraft1 and
		// testsaveMandatoryCostsToDraft2--
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "/data";
		String costComponentPayloadString = "[{\"costComponent\":\"SCM-M0-2948\",\"costComponentDisplayName\":\"Analysis fees\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"costType\":\"template\",\"group\":\"GRP-PR1\",\"incExpenseDisplayName\":\"Income\",\"counterpartyGroupName\":\"PHD-M0-3790\",\"templateName\":\"testsaveMandatoryCostsToDraft1\",\"estimateFor\":\"Execution\",\"rateTypePrice\":\"absolute\",\"counterpartyGroupNameDisplayName\":\"ADM Germany GMBH\",\"rateTypePriceDisplayName\":\"Absolute\"},{\"costComponent\":\"SCM-M0-2916\",\"costComponentDisplayName\":\"Bank fees\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution & Valuation\",\"costType\":\"template\",\"group\":\"GRP-PR1\",\"incExpenseDisplayName\":\"Income\",\"counterpartyGroupName\":\"PHD-M0-3765\",\"templateName\":\"testsaveMandatoryCostsToDraft2\",\"estimateFor\":\"Execution & Valuation\",\"rateTypePrice\":\"absolute\",\"counterpartyGroupNameDisplayName\":\"AGENCIA MARITIMA CONDEMINAS CADIZ S.A.\",\"rateTypePriceDisplayName\":\"Absolute\"}]";
		String saveCostComponentPath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/2f787174-8ed0-4d5d-8f93-b38ab0edc05a";
		Response saveCostComponentResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(new JSONArray(costComponentPayloadString).toList()).when().request("POST", saveCostComponentPath);
		saveCostComponentResponse.then().assertThat().statusCode(200).and().body("size()", is(2));
		// save a rule with attributes-"Contract Type":"Purchase","Contract
		// Incoterm":"FOBAvalised Draft" and templatesNames
		// testsaveMandatoryCostsToDraft1,testsaveMandatoryCostsToDraft2--
		String saveRulePayloadString = "{\"Payment Term\":\"Avalised Draft\",\"Contract Incoterm\":\"FOB\",\"rule_name\":\"testsaveMandatoryCostsToDraft\",\"contractType\":\"contractType-002\",\"contractIncoterm\":\"ITM-M0-2396\",\"Contract Type\":\"Purchase\",\"valuationIncoterm\":\"ITM-M0-2396\",\"costcomponentTemplates\":[\"testsaveMandatoryCostsToDraft1\",\"testsaveMandatoryCostsToDraft2\"],\"attributes\":[\"Contract Type\",\"Contract Incoterm\",\"Valuation Incoterm\",\"Payment Term\"],\"Valuation Incoterm\":\"FOB\",\"paymentTerm\":\"PYM-M0-2578\"}";
		String saveRulePath = "/d33143ac-4164-4a3f-8d30-61d845c9eeed/2d3221f6-0717-4f08-b380-25c7094dcd0b";
		Response saveRuleResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(new JSONObject(saveRulePayloadString).toMap()).when().request("POST", saveRulePath);
		saveRuleResponse.then().assertThat().statusCode(200).and().body("valuationIncoterm", is("ITM-M0-2396"));
		// call saveMandatoryCostsToDraft API--
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String saveMandatoryCostsToDraftPayloadString = "{\"attributes\":[{\"attributeName\":\"Contract Type\",\"attributeValue\":\"Purchase\"},{\"attributeName\":\"Contract Incoterm\",\"attributeValue\":\"FOB\"},{\"attributeName\":\"Valuation Incoterm\",\"attributeValue\":\"FOB\"},{\"attributeName\":\"Payment Term\",\"attributeValue\":\"Avalised Draft\"}],\"draftEstimateNo\":\"5678\",\"entityType\":\"Contract Item\"}{\"attributes\":[{\"attributeName\":\"Contract Type\",\"attributeValue\":\"Purchase\"},{\"attributeName\":\"Contract Incoterm\",\"attributeValue\":\"FOB\"},{\"attributeName\":\"Valuation Incoterm\",\"attributeValue\":\"FOB\"},{\"attributeName\":\"Payment Term\",\"attributeValue\":\"Avalised Draft\"}],\"draftEstimateNo\":\"testsaveMandatoryCostsToDraft\",\"entityType\":\"Contract Item\"}";
		JSONObject saveMandatoryCostsToDraftPayloadJson = new JSONObject(saveMandatoryCostsToDraftPayloadString);
		String saveMandatoryCostsToDraftPath = "/saveMandatoryCostsToDraft";
		Response saveMandatoryCostsToDraftResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveMandatoryCostsToDraftPayloadJson.toMap()).when()
				.request("POST", saveMandatoryCostsToDraftPath);
		saveMandatoryCostsToDraftResponse.then().assertThat().statusCode(200).and().body("size()", is(2)).and()
				.body("[0].incExpenseDisplayName", is("Income"));
		// call again to verify that same costs are not saved again--
		Response secondSaveMandatoryCostsToDraftResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(saveMandatoryCostsToDraftPayloadJson.toMap()).when()
				.request("POST", saveMandatoryCostsToDraftPath);
		secondSaveMandatoryCostsToDraftResponse.then().assertThat().statusCode(200).and().body("size()", is(0));
	}
	
	/**
	 * Test save mandatory costs to draft IF Already not Exists.
	 * JIRA CCA-647
	 * /saveMandatoryCostsToDraft API does not default new mandatory records if new templates have been added to an existing rule
	 * @throws MalformedURLException the malformed URL exception
	 */
	@Test(enabled = true,priority=2)
	public void testsaveMandatoryCostsToDrasftIfAlreadyNOtExists() throws MalformedURLException {
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String copyEstimatesPayloadString = "{\"attributes\":[{\"attributeName\":\"Valuation Incoterm\",\"attributeValue\":\"CIP\"},{\"attributeName\":\"Payment Term\",\"attributeValue\":\"C03R - Cash 30 days after release of original docs\"}],\"draftEstimateNo\":\"1234\",\"entityType\":\"Contract Item\"}";
		JSONObject copyEstimatesPayloadJson = new JSONObject(copyEstimatesPayloadString);
		String copyEstimatesPath = "/saveMandatoryCostsToDraft";
		Response copyEstimatesResponse1 = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(copyEstimatesPayloadJson.toMap()).when().request("POST", copyEstimatesPath);
		copyEstimatesResponse1.then().assertThat().statusCode(200).and().body("size()", is(1)).and()
		.body("[0].templateName", is("CT3"));
	}
	
	@Test(enabled = true, priority=1)
	public void testAssociateEstimatesWithEntityTemp() throws MalformedURLException {
		//priority=1 to run after testAssociateApi so that same draft estimate can be used
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String copyEstimatesPayloadString = "{\"157304692968537\":[\"PCI-testAssociate\"]}";
		JSONObject copyEstimatesPayloadJson = new JSONObject(copyEstimatesPayloadString);
		String copyEstimatesPath = "/associateEstimatesWithEntityTemp";
		Response copyEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(copyEstimatesPayloadJson.toMap()).when().request("POST", copyEstimatesPath);
		copyEstimatesResponse.then().assertThat().statusCode(200).and().body("[0].entityRefNo", is("PCI-testAssociate"));
	}
	
	@Test(dataProvider = "validateCostEstimatesPayloads", enabled = true)
	public void testvalidateCostEstimatesAPI(Object estimatesObject, String expectedErrorMessage)
			throws MalformedURLException {
		Map<String, Object> payload = new HashMap<>();
		payload.put("data", estimatesObject);
		// call API--
		URL eka_utility_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_utility_host_url.getHost();
		RestAssured.port = eka_utility_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String getAllEstimatesPath = "/validateCostEstimates";
		Response validateCostEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with().body(payload).when()
				.request("POST", getAllEstimatesPath);
		validateCostEstimatesResponse.then().assertThat().statusCode(200);
		Map<String, Object> responseMap = (Map<String, Object>) validateCostEstimatesResponse.getBody().as(List.class)
				.get(0);
		if (Objects.isNull(expectedErrorMessage)) {
			Assert.assertNull(responseMap.get("remarks"));
			Assert.assertTrue((boolean) responseMap.get("status"));
		} else {
			Assert.assertEquals(responseMap.get("remarks").toString(), expectedErrorMessage);
			Assert.assertFalse((boolean) responseMap.get("status"));
		}
	}

	@DataProvider(name = "validateCostEstimatesPayloads")
	private Object[][] validateCostEstimatesParams() {
		String curveEstimateString = "{\"Entity Name\":\"Planned Shipment\",\"Entity Ref No\":\"CVB-2047\",\"Estimate For\":\"Execution & Valuation\",\"Income/Expense\":\"Income\",\"Rate Type\":\"Curve\",\"Cost Component Name\":\"Freight - Ocean\",\"FX to Base Type\":\"Absolute\",\"FX to Base Value\":1,\"Cost Value\":41,\"Cost Month\":\"Jan-2020\",\"Cost Curve\":\"PanamaxAarhusAarhusJan-2020\",\"Cost Currency\":\"USD/MT\",\"FX Rate\":\"\",\"FX to Position\":\"\",\"FX Date\":\"\",\"Cost Value Unit\":\"\",\"Contract Price\":\"\",\"Item Quantity\":\"\",\"Product\":\"Cotton Seed\"}";
		String rateEstimateString = "{\"Entity Name\":\"Contract Item\",\"Entity Ref No\":\"CI-11\",\"Estimate For\":\"Execution\",\"Income/Expense\":\"Income\",\"Rate Type\":\"Rate\",\"Cost Component Name\":\"Freight - Ocean\",\"FX to Base Type\":\"Absolute\",\"FX to Base Value\":1,\"Cost Value\":2,\"Cost Month\":\"\",\"Cost Curve\":\"\",\"Cost Currency\":\"\",\"FX Rate\":\"\",\"FX to Position\":\"\",\"FX Date\":\"\",\"Cost Value Unit\":\"USD/MT\",\"Contract Price\":\"\",\"Quantity\":1000,\"Product\":\"Cotton Seed\",\"Quantity Unit\":\"MT\"}";
		String absoluteEstimateString = "{\"Entity Name\":\"Contract Item\",\"Entity Ref No\":\"CI-11\",\"Estimate For\":\"Execution & Valuation\",\"Income/Expense\":\"Expense\",\"Rate Type\":\"Absolute\",\"Cost Component Name\":\"Freight - Ocean\",\"FX to Base Type\":\"Absolute\",\"FX to Base Value\":6,\"Cost Value\":67,\"Cost Month\":\"\",\"Cost Curve\":\"\",\"Cost Currency\":\"\",\"FX Rate\":\"\",\"FX to Position\":\"\",\"FX Date\":\"\",\"Cost Value Unit\":\"USD\",\"Contract Price\":\"\",\"Quantity\":\"\",\"Product\":\"Cotton Seed\"}";
		String rateEstimateStringWithConversionFactorNotOne = "{\"Entity Name\":\"Contract Item\",\"Entity Ref No\":\"CI-11\",\"Estimate For\":\"Execution\",\"Income/Expense\":\"Income\",\"Rate Type\":\"Rate\",\"Cost Component Name\":\"Freight - Ocean\",\"FX to Base Type\":\"Absolute\",\"FX to Base Value\":1,\"Cost Value\":2,\"Cost Month\":\"\",\"Cost Curve\":\"\",\"Cost Currency\":\"\",\"FX Rate\":\"\",\"FX to Position\":\"\",\"FX Date\":\"\",\"Cost Value Unit\":\"USD/MT\",\"Contract Price\":\"\",\"Quantity\":1000,\"Product\":\"Cotton Seed\",\"Quantity Unit\":\"KG\"}";
		String percentOfPriceEstimateString = "{\"Cost Component Name\":\"Freight - Ocean\",\"CP Name\":\"ADM Germany GMBH\",\"Income/Expense\":\"Income\",\"Estimate For\":\"Execution\",\"Rate Type\":\"% of Price\",\"Cost Value\":2,\"FX to Base Type\":\"Absolute\",\"FX to Base Value\":1,\"Entity Name\":\"Contract Item\",\"Quantity\":\"100\",\"Contract Price\":\"10.00 USD/MT\",\"Entity Ref No\":\"PCI-1234\",\"Product\":\"Cotton Seed\",\"Quantity Unit\":\"MT\"}";
		return new Object[][] {
			    //normal estimate--
			    { new JSONArray().put(new JSONObject(curveEstimateString)).toList(), null },
			    //validate mandatory fields for any estimate without combinations--
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Rate Type", null)).toList(),
						"value of Rate Type is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Cost Component Name", null)).toList(),
				"value of Cost Component Name is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Estimate For", null)).toList(),
				"value of Estimate For is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Income/Expense", null)).toList(),
				"value of Income/Expense is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "FX to Base Value", null)).toList(),
				"value of FX to Base Value is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Entity Name", null)).toList(),
				"value of Entity Name is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Entity Ref No", null)).toList(),
				"value of Entity Ref No is missing" },
				//mandatory fields when rateTypePrice is curve--
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Cost Curve", null)).toList(),
				"value of Cost Curve is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Cost Month", null)).toList(),
				"value of Cost Month is missing" },
				//mandatory fields when rateTypePrice is rate--
				{ new JSONArray().put(new JSONObject(rateEstimateString)).toList(), null },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(rateEstimateString), "Cost Value", null)).toList(),
				"value of Cost Value is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(rateEstimateString), "Cost Value Unit", null)).toList(),
				"value of Cost Value Unit is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(rateEstimateString), "Quantity", null)).toList(),
				"value of Quantity is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(rateEstimateString), "Quantity Unit", null)).toList(),
				"value of Quantity Unit is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(rateEstimateString), "Product", RANDOM_FIELD_VALUE)).toList(),
				"value of Product is invalid" },
				//mandatory fields when rateTypePrice is absolute--
				{ new JSONArray().put(new JSONObject(absoluteEstimateString)).toList(), null },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(absoluteEstimateString), "Cost Value", null)).toList(),
				"value of Cost Value is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(absoluteEstimateString), "Cost Value Unit", null)).toList(),
				"value of Cost Value Unit is missing" },
				//mandatory fields when rateTypePrice is % of Price--
				{ new JSONArray().put(new JSONObject(percentOfPriceEstimateString)).toList(), null },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(percentOfPriceEstimateString), "Cost Value", null)).toList(),
						"value of Cost Value is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(percentOfPriceEstimateString), "Quantity", null)).toList(),
				"value of Quantity is missing" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(percentOfPriceEstimateString), "Contract Price", null)).toList(),
				"value of Contract Price is missing" },
				//mdm driven fields-
				//invalid costComponentDisplayName--
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(absoluteEstimateString), "Cost Component Name", RANDOM_FIELD_VALUE)).toList(),
				"value of Cost Component Name is invalid" },
				//invalid estimateForDisplayName--
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(absoluteEstimateString), "Estimate For", RANDOM_FIELD_VALUE)).toList(),
				"value of Estimate For is invalid" },
				//invalid incExpenseDisplayName--
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(absoluteEstimateString), "Income/Expense", RANDOM_FIELD_VALUE)).toList(),
				"value of Income/Expense is invalid" },
				//invalid costCurveDisplayName corresponding to a cost component for curve--
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Cost Curve", RANDOM_FIELD_VALUE)).toList(),
				"value of Cost Curve is invalid" },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(curveEstimateString), "Cost Curve", "BAGGING")).toList(),
				"value of Cost Curve is invalid" },
				//invalid conversionFactor for rate--
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(rateEstimateString), "conversionFactor", RANDOM_FIELD_VALUE)).toList(),
				"value of conversionFactor is invalid" },
				{ new JSONArray().put(new JSONObject(rateEstimateStringWithConversionFactorNotOne)).toList(), null },
				{ new JSONArray().put(substituteFieldInEstimate(new JSONObject(rateEstimateStringWithConversionFactorNotOne), "conversionFactor", "1")).toList(),
				"value of conversionFactor is invalid" },
		};
	}

	@Test(enabled = true)
	public void testValidateAndSaveCostEstimatesAPI() throws MalformedURLException {
		URL eka_utility_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_utility_host_url.getHost();
		RestAssured.port = eka_utility_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String getAllEstimatesPath = "/saveCostEstimates";
		String saveEstimatePayloadString = "{\"data\":[{\"itemQty\":\"2000\",\"costAmountInBaseCurrencyUnitId\":\"USD\",\"isPopUp\":\"true\",\"costValue\":2,\"fxToBaseType\":\"Absolute\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"BAGGING\",\"entityActualNo\":\"PC-1599-SWA\",\"payInCurId\":\"CM-M0-9\",\"estimateForDisplayName\":\"Execution\",\"shipmentDate\":\"30-Apr-2020\",\"costPriceUnitIdDisplayName\":\"CNY/LB\",\"draftEstimateNo\":\"158702180745025\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"pr-2\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"fxToBase\":1,\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":4000,\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"entityRefNo\":\"PCI-4264\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"rate\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"counterpartyGroupNameDisplayName\":\"14796754\",\"rateTypePriceDisplayName\":\"Rate\"}]}";
		Response saveCostEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(new JSONObject(saveEstimatePayloadString).toMap()).when().request("POST", getAllEstimatesPath);
		saveCostEstimatesResponse.then().assertThat().statusCode(200).and().body("size()", is(1)).and()
				.body("[0].status", is(true));
	}

	private JSONObject substituteFieldInEstimate(JSONObject estimate, String fieldToSubstitute, Object value) {
		if(Objects.isNull(value))
			estimate.remove(fieldToSubstitute);
		else
			estimate.put(fieldToSubstitute, value);
		return estimate;
	}
	
	@Test(enabled = true)
	public void testGetAllEstimatesFx() throws MalformedURLException {

		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "";
		String saveEstimatePayload = "[{\"itemQty\":\"100\",\"fxRate\":\"USD/CAD\",\"costAmountInBaseCurrencyUnitId\":\"CAD\",\"costValue\":\"1\",\"isPopUp\":\"true\",\"sys__version\":1,\"costComponent\":\"SCM-M2-84\",\"fxToBaseType\":\"curve\",\"refType\":\"app\",\"costPriceUnitId\":\"CM-M0-4\",\"itemQtyUnitId\":\"QUM-M0-1\",\"costComponentDisplayName\":\"BAGGING\",\"payInCurId\":\"CM-M0-9\",\"incExpense\":\"CostIncome\",\"estimateForDisplayName\":\"Execution\",\"shipmentDate\":\"31-Mar-2020\",\"costPriceUnitIdDisplayName\":\"CAD\",\"fxValueDate\":\"2020-02-03\",\"draftEstimateNo\":\"158323840402474\",\"incExpenseDisplayName\":\"Income\",\"comments\":\"prs-test2\",\"productId\":\"PDM-M2-25\",\"entityType\":\"Contract Item\",\"counterpartyGroupName\":\"PHD-M1-3468\",\"priceType\":\"On Call Basis Fixed\",\"costAmountInBaseCurrency\":\"1\",\"estimateFor\":\"Execution\",\"contractIncoTerm\":\"FOB\",\"showMenu\":\"false\",\"corporateCurrency\":\"USD\",\"valuationIncoTerm\":\"FOB\",\"rateTypePrice\":\"absolute\",\"paymentTerm\":\"CACD - Cash against email copies of docs\",\"counterpartyGroupNameDisplayName\":\"05266880\",\"rateTypePriceDisplayName\":\"Absolute\",\"fxToBase\":\"1\",\"entityRefNo\":\"PCI-pr-test\"}]";
		Response saveResponse = given().header("Content-Type", "application/json").header("Authorization", token)
				.header("X-TenantID", tenant).with().body(new JSONArray(saveEstimatePayload).toList()).when()
				.post("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90");
		saveResponse.then().assertThat().statusCode(200);
		// call getAllEstimates--
		URL eka_utility_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_utility_host_url.getHost();
		RestAssured.port = eka_utility_host_url.getPort();
		RestAssured.basePath = "/costapp";
		String getAllEstimatesPayloadString = "{\"itemDetails\":[{\"entityType\":\"Contract Item\",\"entityRefNo\":\"PCI-pr-test\",\"applicableDate\":\"04-02-2020\"}],\"getDeletedData\":\"Y\"}";
		JSONObject getAllEstimatesPayloadJson = new JSONObject(getAllEstimatesPayloadString);
		String getAllEstimatesPath = "/getAllEstimates";
		Response getAllEstimatesResponse = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(getAllEstimatesPayloadJson.toMap()).when().request("POST", getAllEstimatesPath);
		getAllEstimatesResponse.then().assertThat().statusCode(200).and().body("size()", is(1));
	}
	
	/**
	 * Test update PBS cost estimates API. CCA-719:(New Cost App API) Update Cost
	 * Estimates for linked contracts in PBS - Ability to remove already defaulted
	 * estimates if respective estimates have been removed from contract when
	 * contract linking is updated in PBS
	 * 
	 */
	@Test(enabled = true)
	public void testUpdatePBSCostEstimatesAPI() throws MalformedURLException {
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp/v1";
		String updatePBSCostEstimatesString = "{\"entityRefNo\":\"CVB-2000\"}";
		JSONObject updatePBSCostEstimatesJson = new JSONObject(updatePBSCostEstimatesString);
		String UpdatePBSCostEstimatesPath = "/updatePBSCostEstimates";
		Response response = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(updatePBSCostEstimatesJson.toMap()).when().request("POST", UpdatePBSCostEstimatesPath);
		Assert.assertEquals(response.getStatusCode(), 200);
		JsonPath jsonPath = new JsonPath(response.asString());
		Assert.assertEquals(jsonPath.getString("status"), "success");
		Assert.assertEquals(jsonPath.getString("ekaRefNo"), "CVB-2000");
		
		URL eka_connect_host_url = new URL(eka_connect_host);
		RestAssured.baseURI = "http://" + eka_connect_host_url.getHost();
		RestAssured.port = eka_connect_host_url.getPort();
		RestAssured.basePath = "";
		String fetchConnectDataPayload = "{\"filterData\":{\"filter\":[{\"fieldName\":\"entityRefNo\",\"value\":\"CVB-2000\",\"operator\":\"eq\"}]}}";
		Response connectResponse = given().header("Content-Type", "application/json").header("Authorization", token)
				.header("X-TenantID", tenant).with().body(new JSONObject(fetchConnectDataPayload).toMap()).when()
				.get("/data/d33143ac-4164-4a3f-8d30-61d845c9eeed/f3d6ff89-b541-4dc0-b88d-12065d10cc90");
		
		JsonPath jsonPath1 = new JsonPath(connectResponse.asString());
		Assert.assertEquals(jsonPath1.getDouble("[0].costValue"), 30.0);
		Assert.assertEquals(jsonPath1.getDouble("[0].quantity"),"800.0 MT");
		Assert.assertEquals(jsonPath1.getDouble("[0].costAmountInBaseCurrency"), 24000.0);
		Assert.assertEquals(jsonPath1.getString("[0].product"), "Cotton Seed");
		Assert.assertEquals(jsonPath1.getDouble("[0].quality"), "Brazil CottonSeed");
		Assert.assertEquals(jsonPath1.getDouble("[0].itemQty"),800.0);
	}
	
	/**
	 * Test update PBS cost estimates API failure IF invalid PBS ref no.
	 * CCA-1003: Cost App Defaulting logic returning 501 error when PBS is created with only linked Stocks
	 * @throws MalformedURLException the malformed URL exception
	 */
	@Test(enabled = true)
	public void testUpdatePBSCostEstimatesAPIFailureIFInvalidPBSRefNo() throws MalformedURLException {
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp/v1";
		String updatePBSCostEstimatesString = "{\"entityRefNo\":\"CVB-2000\"}";
		JSONObject updatePBSCostEstimatesJson = new JSONObject(updatePBSCostEstimatesString);
		String UpdatePBSCostEstimatesPath = "/updatePBSCostEstimates";
		Response response = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(updatePBSCostEstimatesJson.toMap()).when().request("POST", UpdatePBSCostEstimatesPath);
		Assert.assertEquals(response.getStatusCode(), 200);
	}

	/**
	 * Update contract item cost estimates.
	 *
	 * @throws MalformedURLException the malformed URL exception
	 */
	@Test(enabled = true)
	public void updateContractItemCostEstimates() throws MalformedURLException {
		URL eka_cost_host_url = new URL(eka_cost_host);
		RestAssured.baseURI = "http://" + eka_cost_host_url.getHost();
		RestAssured.port = eka_cost_host_url.getPort();
		RestAssured.basePath = "/costapp/v1";
		String updatePBSCostEstimatesString = "{\"entityType\":\"Contract Item\",\"draftEstimateNo\":\"1234\",\"itemQty\":2500,\"contractPrice\":\"100 USD/MT\"}";
		JSONObject updatePBSCostEstimatesJson = new JSONObject(updatePBSCostEstimatesString);
		String UpdatePBSCostEstimatesPath = "/updateContractItemCostEstimates";
		Response response = given().log().all().header("X-TenantID", tenant)
				.header("Content-Type", "application/json").header("Authorization", token).with()
				.body(updatePBSCostEstimatesJson.toMap()).when().request("POST", UpdatePBSCostEstimatesPath);
		Assert.assertEquals(response.getStatusCode(), 200);
		JsonPath jsonPath = new JsonPath(response.asString());
		Assert.assertEquals(jsonPath.getDouble("[0].costAmountInBaseCurrency"), 24000.0);
		Assert.assertEquals(jsonPath.getString("[0].estimateFor"), "Valuation");
		Assert.assertEquals(jsonPath.getDouble("[0].draftEstimateNo"), "1234");
		Assert.assertEquals(jsonPath.getDouble("[0].itemQty"),800.0);
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
