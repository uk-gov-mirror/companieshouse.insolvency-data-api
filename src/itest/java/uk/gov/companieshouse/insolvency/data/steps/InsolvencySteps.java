package uk.gov.companieshouse.insolvency.data.steps;

import static com.github.tomakehurst.wiremock.client.WireMock.moreThanOrExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertTrue;
import static uk.gov.companieshouse.insolvency.data.config.AbstractMongoConfig.mongoDBContainer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import uk.gov.companieshouse.api.insolvency.CompanyInsolvency;
import uk.gov.companieshouse.api.insolvency.InternalCompanyInsolvency;
import uk.gov.companieshouse.api.insolvency.InternalData;
import uk.gov.companieshouse.insolvency.data.api.InsolvencyApiService;
import uk.gov.companieshouse.insolvency.data.config.CucumberContext;
import uk.gov.companieshouse.insolvency.data.config.WiremockTestConfig;
import uk.gov.companieshouse.insolvency.data.model.InsolvencyDocument;
import uk.gov.companieshouse.insolvency.data.repository.InsolvencyRepository;
import wiremock.org.apache.commons.io.FileUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
public class InsolvencySteps {

    private String companyNumber;
    private String contextId;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private InsolvencyRepository insolvencyRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    public InsolvencyApiService insolvencyApiService;

    @Before
    public void dbCleanUp() {
        WiremockTestConfig.setupWiremock();

        if (mongoDBContainer.getContainerId() == null) {
            mongoDBContainer.start();
        }
        insolvencyRepository.deleteAll();
    }

    @Given("the CHS Kafka API is reachable")
    public void the_chs_kafka_api_is_reachable() {
        WiremockTestConfig.stubKafkaApi(HttpStatus.OK.value());
    }

    @Given("the insolvency information exists for {string}")
    public void the_insolvency_information_exists_for(String companyNumber) throws IOException {
        File file = new ClassPathResource("/json/input/case_type_compulsory_liquidation.json").getFile();
        InternalCompanyInsolvency companyInsolvency = objectMapper.readValue(file, InternalCompanyInsolvency.class);

        InternalData internalData = companyInsolvency.getInternalData();
        InsolvencyDocument insolvencyDocument = new InsolvencyDocument(companyNumber,
                companyInsolvency.getExternalData(),
                internalData.getDeltaAt(),
                LocalDateTime.now(),
                internalData.getUpdatedBy());

        mongoTemplate.save(insolvencyDocument);
    }

    @Given("the insolvency database is down")
    public void the_insolvency_db_is_down() {
        mongoDBContainer.stop();
    }

    @When("I send GET request with company number {string}")
    public void i_send_get_request_with_company_number(String companyNumber) throws JsonProcessingException {
        String uri = "/company/{companyNumber}/insolvency";

        HttpHeaders headers = new HttpHeaders();
        headers.add("ERIC-Identity", "SOME_IDENTITY");
        headers.add("ERIC-Identity-Type", "key");

        HttpEntity<?> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, request, String.class,
                companyNumber);

        CucumberContext.CONTEXT.set("statusCode", response.getStatusCode().value());
        if (response.getStatusCode().is2xxSuccessful()) {
            CompanyInsolvency companyInsolvency = objectMapper.readValue(response.getBody(), CompanyInsolvency.class);
            CucumberContext.CONTEXT.set("getResponseBody", companyInsolvency);
        } else {
            CucumberContext.CONTEXT.set("getResponseBody", response.getBody());
        }
    }

    @When("I send GET request with company number {string} without eric headers")
    public void i_send_get_request_with_company_number_without_eric_header(String companyNumber) {
        String uri = "/company/{companyNumber}/insolvency";

        HttpHeaders headers = new HttpHeaders();
        HttpEntity<?> request = new HttpEntity<>(headers);

        //Not setting Eric headers

        ResponseEntity<CompanyInsolvency> response = restTemplate.exchange(uri, HttpMethod.GET, request,
                CompanyInsolvency.class, companyNumber);

        CucumberContext.CONTEXT.set("statusCode", response.getStatusCode().value());
        CucumberContext.CONTEXT.set("getResponseBody", response.getBody());
    }

    @When("I send PUT request with payload {string} file")
    public void i_send_put_request_with_payload(String string) throws IOException {

        String coNumber = "CH5324324";
        String uri = "/company/{companyNumber}/insolvency";

        File file = new ClassPathResource("/json/input/" + string + ".json").getFile();
        InternalCompanyInsolvency companyInsolvency = objectMapper.readValue(file, InternalCompanyInsolvency.class);

        this.contextId = "5234234234";
        this.companyNumber = coNumber;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("x-request-id", "5234234234");
        headers.set("ERIC-Identity", "SOME_IDENTITY");
        headers.set("ERIC-Identity-Type", "key");
        headers.set("ERIC-Authorised-Key-Privileges", "internal-app");

        HttpEntity<InternalCompanyInsolvency> request = new HttpEntity<>(companyInsolvency, headers);
        ResponseEntity<Void> response = restTemplate.exchange(uri, HttpMethod.PUT, request, Void.class, coNumber);

        CucumberContext.CONTEXT.set("statusCode", response.getStatusCode().value());
    }

    @When("I send PUT request with payload {string} file without eric headers")
    public void i_send_put_request_with_payload_without_eric_header(String string) throws IOException {
        String coNumber = "CH5324324";
        String uri = "/company/{companyNumber}/insolvency";
        File file = new ClassPathResource("/json/input/" + string + ".json").getFile();
        InternalCompanyInsolvency companyInsolvency = objectMapper.readValue(file, InternalCompanyInsolvency.class);

        this.contextId = "5234234234";
        this.companyNumber = coNumber;
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("x-request-id", "5234234234");
        //Not setting Eric headers

        HttpEntity<InternalCompanyInsolvency> request = new HttpEntity<>(companyInsolvency, headers);
        ResponseEntity<Void> response = restTemplate.exchange(uri, HttpMethod.PUT, request, Void.class, coNumber);

        CucumberContext.CONTEXT.set("statusCode", response.getStatusCode().value());
    }

    @When("I send PUT request with raw payload {string} file")
    public void i_send_put_request_with_raw_payload(String string) throws IOException {
        File file = new ClassPathResource("/json/input/" + string + ".json").getFile();
        String rawPayload = FileUtils.readFileToString(file, StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("ERIC-Identity", "SOME_IDENTITY");
        headers.set("ERIC-Identity-Type", "key");
        headers.set("ERIC-Authorised-Key-Privileges", "internal-app");

        this.contextId = "5234234234";
        headers.set("x-request-id", this.contextId);

        HttpEntity<?> request = new HttpEntity<>(rawPayload, headers);
        String uri = "/company/{company_number}/insolvency";
        String coNumber = "CH5324324";
        ResponseEntity<Void> response = restTemplate.exchange(uri, HttpMethod.PUT, request, Void.class, coNumber);

        CucumberContext.CONTEXT.set("statusCode", response.getStatusCode().value());
    }

    @When("CHS kafka API service is unavailable")
    public void chs_kafka_service_unavailable() {
        WiremockTestConfig.stubKafkaApi(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @When("I send DELETE request with company number {string}")
    public void i_send_delete_request_with_company_number(String companyNumber) {
        sendDeleteRequestWithCompanyNumberAndDeltaAt(companyNumber, "20241010175532456123");
    }

    @When("I send DELETE request with company number {string} and delta_at {string}")
    public void iSendDeleteRequestWithCompanyNumberAndDeltaAt(String companyNumber, String deltaAt) {
        sendDeleteRequestWithCompanyNumberAndDeltaAt(companyNumber, deltaAt);
    }

    @When("I send DELETE request with company number {string} without setting eric headers")
    public void i_send_delete_request_with_company_number_no_eric_headers(String companyNumber) {
        String uri = "/company/{company_number}/insolvency";

        HttpHeaders headers = new HttpHeaders();
        this.contextId = "5234234234";
        headers.set("x-request-id", "5234234234");
        var request = new HttpEntity<>(null, headers);

        ResponseEntity<Void> response = restTemplate.exchange(uri, HttpMethod.DELETE, request, Void.class,
                companyNumber);

        CucumberContext.CONTEXT.set("statusCode", response.getStatusCode().value());
        this.companyNumber = companyNumber;
    }

    @When("I send DELETE request without x-request-id key in the header")
    public void i_send_delete_request_without_x_request_id_key_in_the_header() {
        String uri = "/company/CH1234567/insolvency";

        HttpHeaders headers = new HttpHeaders();
        this.contextId = "5234234234";
        var request = new HttpEntity<>(null, headers);

        ResponseEntity<Void> response = restTemplate.exchange(uri, HttpMethod.DELETE, request, Void.class,
                companyNumber);

        CucumberContext.CONTEXT.set("statusCode", response.getStatusCode().value());
    }

    @Then("I should receive {int} status code")
    public void i_should_receive_status_code(Integer statusCode) {
        int expectedStatusCode = CucumberContext.CONTEXT.get("statusCode");
        Assertions.assertThat(expectedStatusCode).isEqualTo(statusCode);
    }

    @Then("the expected result should match {string} file")
    public void the_expected_result_should_match(String string) throws IOException {
        File file = new ClassPathResource("/json/output/" + string + ".json").getFile();

        List<InsolvencyDocument> insolvencyDocuments = insolvencyRepository.findAll();

        Assertions.assertThat(insolvencyDocuments).hasSize(1);

        Optional<InsolvencyDocument> actual = insolvencyRepository.findById(this.companyNumber);

        assertThat(actual).isPresent();

        InsolvencyDocument expected = objectMapper.readValue(file, InsolvencyDocument.class);

        InsolvencyDocument actualDocument = actual.get();

        // Verify that the time inserted is after the input
        assertThat(actualDocument.getUpdatedAt()).isAfter(expected.getUpdatedAt());
        assertThat(actualDocument.getCompanyInsolvency().getStatus()).isEqualTo(
                expected.getCompanyInsolvency().getStatus());

        // Matching both updatedAt since it will never match the output (Uses now time)
        LocalDateTime replacedLocalDateTime = LocalDateTime.now();
        expected.setUpdatedAt(replacedLocalDateTime);
        actualDocument.setUpdatedAt(replacedLocalDateTime);
        verifyPutData(actual.get(), expected);
    }

    @Then("the Get call response body should match {string} file")
    public void the_get_call_response_body_should_match(String dataFile) throws IOException {
        File file = new ClassPathResource("/json/output/" + dataFile + ".json").getFile();

        InsolvencyDocument expectedDocument = objectMapper.readValue(file, InsolvencyDocument.class);

        CompanyInsolvency expected = expectedDocument.getCompanyInsolvency();
        CompanyInsolvency actual = CucumberContext.CONTEXT.get("getResponseBody");

        assertThat(expected.getStatus()).isEqualTo(actual.getStatus());
        assertThat(expected.getCases()).isEqualTo(actual.getCases());
    }

    @Then("the CHS Kafka API is invoked with event {string}")
    public void chs_kafka_api_invoked(String event) {
        verify(moreThanOrExactly(1), postRequestedFor(urlEqualTo("/private/resource-changed")));
    }

    @Then("the CHS Kafka API is not invoked")
    public void chs_kafka_api_not_invoked() {
        verify(0, postRequestedFor(urlEqualTo("/private/resource-changed")));
        List<ServeEvent> serverEvents = WiremockTestConfig.getServeEvents();
        assertTrue(serverEvents.isEmpty());
    }

    @Then("nothing is persisted in the database")
    public void nothing_persisted_database() {
        List<InsolvencyDocument> insolvencyDocuments = insolvencyRepository.findAll();
        Assertions.assertThat(insolvencyDocuments).isEmpty();
    }

    @Then("the company insolvency with company number {string} still exists in the database")
    public void company_insolvency_exists(String companyNumber) {
        Assertions.assertThat(insolvencyRepository.existsById(companyNumber)).isTrue();
    }

    @Then("the company insolvency with company number {string} is deleted from the database")
    public void companyInsolvencyDeleted(String companyNumber) {
        Assertions.assertThat(insolvencyRepository.existsById(companyNumber)).isFalse();
    }

    private void sendDeleteRequestWithCompanyNumberAndDeltaAt(String companyNumber, String deltaAt) {
        String uri = "/company/{company_number}/insolvency";

        HttpHeaders headers = new HttpHeaders();
        this.contextId = "5234234234";
        headers.set("x-request-id", "5234234234");
        headers.set("X-DELTA-AT", deltaAt);
        headers.set("ERIC-Identity", "SOME_IDENTITY");
        headers.set("ERIC-Identity-Type", "key");
        headers.set("ERIC-Authorised-Key-Privileges", "internal-app");

        var request = new HttpEntity<>(null, headers);

        ResponseEntity<Void> response = restTemplate.exchange(uri, HttpMethod.DELETE, request, Void.class,
                companyNumber);

        CucumberContext.CONTEXT.set("statusCode", response.getStatusCode().value());
        this.companyNumber = companyNumber;
    }

    private void verifyPutData(InsolvencyDocument actual, InsolvencyDocument expected) {
        CompanyInsolvency actualCompanyInsolvency = actual.getCompanyInsolvency();
        CompanyInsolvency expectedCompanyInsolvency = expected.getCompanyInsolvency();

        assertThat(actualCompanyInsolvency.getCases()).isEqualTo(expectedCompanyInsolvency.getCases());
        assertThat(actual.getId()).isEqualTo(expected.getId());
        assertThat(actual.getUpdatedAt()).isEqualTo(expected.getUpdatedAt());
        assertThat(actual.getUpdatedBy()).isEqualTo(expected.getUpdatedBy());
        assertThat(actual.getDeltaAt()).isEqualTo(expected.getDeltaAt());
    }

}
