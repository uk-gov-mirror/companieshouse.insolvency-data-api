package uk.gov.companieshouse.insolvency.data.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.companieshouse.api.insolvency.CompanyInsolvency;
import uk.gov.companieshouse.api.insolvency.InternalCompanyInsolvency;
import uk.gov.companieshouse.api.insolvency.InternalData;
import uk.gov.companieshouse.insolvency.data.config.WebSecurityConfig;
import uk.gov.companieshouse.insolvency.data.exceptions.BadRequestException;
import uk.gov.companieshouse.insolvency.data.exceptions.ConflictException;
import uk.gov.companieshouse.insolvency.data.exceptions.DocumentNotFoundException;
import uk.gov.companieshouse.insolvency.data.exceptions.InternalServerErrorException;
import uk.gov.companieshouse.insolvency.data.service.InsolvencyServiceImpl;

@ExtendWith(SpringExtension.class)
@WebMvcTest(controllers = InsolvencyController.class)
@ContextConfiguration(classes = {InsolvencyController.class})
@Import({WebSecurityConfig.class})
class InsolvencyControllerTest {

    private static final String COMPANY_NUMBER = "02588581";
    private static final String URL = String.format("/company/%s/insolvency", COMPANY_NUMBER);
    private static final String ERIC_ALLOWED_ORIGIN = "ERIC-Allowed-Origin";
    private static final String ERIC_IDENTITY = "ERIC-Identity";
    private static final String ERIC_IDENTITY_TYPE = "ERIC-Identity-Type";
    private static final String ORIGIN = "Origin";
    private static final String ERIC_ALLOWED_ORIGIN_VALUE = "some-origin";
    private static final String ERIC_IDENTITY_VALUE = "123";
    private static final String ERIC_IDENTITY_TYPE_VALUE = "key";
    private static final String ORIGIN_VALUE = "http://www.test.com";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InsolvencyServiceImpl insolvencyService;

    private final ObjectMapper mapper = new ObjectMapper();

    private final Gson gson = new GsonBuilder().setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    @BeforeEach
    void setUp() {
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Test
    @DisplayName("Insolvency PUT request")
    void callInsolvencyPutRequest() throws Exception {
        InternalCompanyInsolvency request = new InternalCompanyInsolvency();
        request.setInternalData(new InternalData());
        request.setExternalData(new CompanyInsolvency());

        doNothing().when(insolvencyService).processInsolvency(anyString(), isA(InternalCompanyInsolvency.class));

        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app")
                        .content(gson.toJson(request)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Insolvency PUT request fails with missing ERIC-Authorised-Key-Privileges")
    void callInsolvencyPutRequestMissingAuthorisation() throws Exception {
        InternalCompanyInsolvency request = new InternalCompanyInsolvency();
        request.setInternalData(new InternalData());
        request.setExternalData(new CompanyInsolvency());

        doNothing().when(insolvencyService).processInsolvency(anyString(), isA(InternalCompanyInsolvency.class));

        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .content(gson.toJson(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Insolvency PUT request fails with wrong privileges")
    void callInsolvencyPutRequestWrongPrivileges() throws Exception {
        InternalCompanyInsolvency request = new InternalCompanyInsolvency();
        request.setInternalData(new InternalData());
        request.setExternalData(new CompanyInsolvency());

        doNothing().when(insolvencyService).processInsolvency(anyString(), isA(InternalCompanyInsolvency.class));

        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "privilege")
                        .content(gson.toJson(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Insolvency PUT request fails with oauth2 authorisation")
    void callInsolvencyPutRequestWrongAuthorisation() throws Exception {
        InternalCompanyInsolvency request = new InternalCompanyInsolvency();
        request.setInternalData(new InternalData());
        request.setExternalData(new CompanyInsolvency());

        doNothing().when(insolvencyService).processInsolvency(anyString(), isA(InternalCompanyInsolvency.class));

        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "oauth2")
                        .content(gson.toJson(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Insolvency PUT request fails with oauth2 authorisation and internal app privileges")
    void callInsolvencyPutRequestWrongAuthorisationWithPrivileges() throws Exception {
        InternalCompanyInsolvency request = new InternalCompanyInsolvency();
        request.setInternalData(new InternalData());
        request.setExternalData(new CompanyInsolvency());

        doNothing().when(insolvencyService).processInsolvency(anyString(), isA(InternalCompanyInsolvency.class));

        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "oauth2")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app")
                        .content(gson.toJson(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Insolvency PUT request - BadRequestException status code 400")
    void callInsolvencyPutRequestBadRequest() throws Exception {
        InternalCompanyInsolvency request = new InternalCompanyInsolvency();
        request.setInternalData(new InternalData());
        request.setExternalData(new CompanyInsolvency());

        doThrow(new BadRequestException("Bad request - data in wrong format"))
                .when(insolvencyService).processInsolvency(anyString(), isA(InternalCompanyInsolvency.class));

        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app")
                        .content(gson.toJson(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Insolvency PUT request - InternalServerError status code 500")
    void callInsolvencyPutRequestInternalServerError() throws Exception {
        InternalCompanyInsolvency request = new InternalCompanyInsolvency();
        request.setInternalData(new InternalData());
        request.setExternalData(new CompanyInsolvency());

        doThrow(new InternalServerErrorException("Internal Server Error - unexpected error"))
                .when(insolvencyService).processInsolvency(anyString(), isA(InternalCompanyInsolvency.class));

        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app")
                        .content(gson.toJson(request)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Insolvency PUT request - Conflict status code 409")
    void callInsolvencyPutRequestConflictException() throws Exception {
        InternalCompanyInsolvency request = new InternalCompanyInsolvency();
        request.setInternalData(new InternalData());
        request.setExternalData(new CompanyInsolvency());

        doThrow(new ConflictException("Stale delta at"))
                .when(insolvencyService).processInsolvency(anyString(), isA(InternalCompanyInsolvency.class));

        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app")
                        .content(gson.toJson(request)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Insolvency DELETE request")
    void callInsolvencyDeleteRequest() throws Exception {
        mockMvc.perform(delete(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("X-DELTA-AT", "20241010175532456123")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Insolvency DELETE request - DocumentNotFoundException status code 404 not found")
    void callInsolvencyDeleteRequestDocumentNotFound() throws Exception {
        doThrow(new DocumentNotFoundException("Document not found"))
                .when(insolvencyService).deleteInsolvency(anyString(), anyString());

        mockMvc.perform(delete(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("X-DELTA-AT", "20241010175532456123")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Insolvency DELETE request - BadRequestException status code 400")
    void callInsolvencyDeleteRequestBadRequest() throws Exception {
        doThrow(new BadRequestException("Bad request - data in wrong format"))
                .when(insolvencyService).deleteInsolvency(anyString(), anyString());

        mockMvc.perform(delete(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("X-DELTA-AT", "20241010175532456123")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Insolvency DELETE request - InternalServerError status code 500")
    void callInsolvencyDeleteRequestInternalServerError() throws Exception {

        doThrow(new InternalServerErrorException("Internal Server Error - unexpected error"))
                .when(insolvencyService).deleteInsolvency(anyString(), anyString());

        mockMvc.perform(delete(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("X-DELTA-AT", "20241010175532456123")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key")
                        .header("ERIC-Authorised-Key-Privileges", "internal-app"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Insolvency GET request")
    void callInsolvencyGetRequest() throws Exception {
        CompanyInsolvency companyInsolvency = new CompanyInsolvency();
        doReturn(companyInsolvency)
                .when(insolvencyService).retrieveCompanyInsolvency(anyString());

        mockMvc.perform(get(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Insolvency GET request with oauth2 success")
    void callInsolvencyGetRequestOauth2() throws Exception {
        CompanyInsolvency companyInsolvency = new CompanyInsolvency();
        doReturn(companyInsolvency)
                .when(insolvencyService).retrieveCompanyInsolvency(anyString());

        mockMvc.perform(get(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "oauth2"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Insolvency GET request - DocumentNotFoundException status code 404 resource not found")
    void callInsolvencyGetRequestDocumentnotFound() throws Exception {
        doThrow(new DocumentNotFoundException("Document not found"))
                .when(insolvencyService).retrieveCompanyInsolvency(anyString());

        mockMvc.perform(get(URL)
                        .contentType(APPLICATION_JSON)
                        .header("x-request-id", "5342342")
                        .header("ERIC-Identity", "SOME_IDENTITY")
                        .header("ERIC-Identity-Type", "key"))
                .andExpect(status().isNotFound());
    }

    @Test
    void optionsChargesRequestCORS() throws Exception {

        mockMvc.perform(options(URL)
                        .contentType(APPLICATION_JSON)
                        .header("Origin", "")
                )
                .andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS))
                .andExpect(header().exists(HttpHeaders.ACCESS_CONTROL_MAX_AGE))
                .andReturn();
    }

    @Test
    void whenCorsRequestWithValidMethod_thenProceed() throws Exception {
        mockMvc.perform(get(URL)
                        .header(ERIC_ALLOWED_ORIGIN, ERIC_ALLOWED_ORIGIN_VALUE)
                        .header(ERIC_IDENTITY, ERIC_IDENTITY_VALUE)
                        .header(ERIC_IDENTITY_TYPE, ERIC_IDENTITY_TYPE_VALUE)
                        .header(ORIGIN, ORIGIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    void whenCorsRequestWithInvalidMethod_thenForbidden() throws Exception {
        mockMvc.perform(put(URL)
                        .contentType(APPLICATION_JSON)
                        .header(ERIC_ALLOWED_ORIGIN, ERIC_ALLOWED_ORIGIN_VALUE)
                        .header(ERIC_IDENTITY, ERIC_IDENTITY_VALUE)
                        .header(ERIC_IDENTITY_TYPE, ERIC_IDENTITY_TYPE_VALUE)
                        .header(ORIGIN, ORIGIN_VALUE)
                        .content(gson.toJson(null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenCorsRequestWithMissingAllowedOrigin_thenForbidden() throws Exception {
        mockMvc.perform(get(URL)
                        .header(ERIC_IDENTITY, ERIC_IDENTITY_VALUE)
                        .header(ERIC_IDENTITY_TYPE, ERIC_IDENTITY_TYPE_VALUE)
                        .header(ORIGIN, ORIGIN_VALUE)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }
}
