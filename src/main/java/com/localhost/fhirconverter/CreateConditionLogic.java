package com.localhost.fhirconverter;

import org.springframework.stereotype.Component;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Reference;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.healthcare.v1.CloudHealthcare;
import com.google.api.services.healthcare.v1.CloudHealthcareScopes;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import com.google.auth.http.HttpCredentialsAdapter;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

@Component
public class CreateConditionLogic {
    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    public void createCondition(String projectId, String region, String datasetName, String fhirStoreName, String clinicalStatus, String verificationStatus, String code, String description, String patientId) throws IOException, URISyntaxException {
        // Create the Condition resource
        Condition condition = new Condition();

        // Set Clinical Status
        CodeableConcept clinicalStatusConcept = new CodeableConcept();
        clinicalStatusConcept.addCoding(new Coding().setDisplay(clinicalStatus));
        condition.setClinicalStatus(clinicalStatusConcept);

        // Set Verification Status
        CodeableConcept verificationStatusConcept = new CodeableConcept();
        verificationStatusConcept.addCoding(new Coding().setDisplay(verificationStatus));
        condition.setVerificationStatus(verificationStatusConcept);

        // Set Code
        CodeableConcept codeConcept = new CodeableConcept();
        codeConcept.addCoding(new Coding().setCode(code));
        condition.setCode(codeConcept);

        // Set Subject (Patient Reference)
        condition.setSubject(new Reference("Patient/" + patientId));

        // Set Description (Condition Note)
        condition.setNote(Collections.singletonList(new org.hl7.fhir.r4.model.Annotation().setText(description)));

        // Convert the Condition to JSON
        FhirContext fhirContext = FhirContext.forR4();
        IParser jsonParser = fhirContext.newJsonParser();
        String conditionJson = jsonParser.encodeResourceToString(condition);

        // Create the bundle
        JsonObject bundle = new JsonObject();
        JsonArray entries = new JsonArray();
        bundle.addProperty("resourceType", "Bundle");
        bundle.addProperty("type", "transaction");

        // Create the entries
        JsonObject entry = new JsonObject();
        entry.addProperty("fullUrl", "urn:uuid:" + java.util.UUID.randomUUID().toString());
        entry.add("resource", JsonParser.parseString(conditionJson).getAsJsonObject());
        JsonObject request = new JsonObject();
        request.addProperty("method", "POST");
        request.addProperty("url", "Condition");
        entry.add("request", request);

        // Add entry to entries
        entries.add(entry);
        bundle.add("entry", entries);

        // Convert bundle to JSON string
        String bundleJson = bundle.toString();

        // Upload to Google FHIR dataset
        CloudHealthcare client = createClient();
        String appendedURL = "projects/" + projectId + "/locations/" + region + "/datasets/" + datasetName + "/fhirStores/" + fhirStoreName;
        uploadToGoogleFhirStore(client, appendedURL, bundleJson);
    }

    private static CloudHealthcare createClient() throws IOException {
        String jsonCredentials = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (jsonCredentials == null || jsonCredentials.isEmpty()) {
                throw new IOException("Google Cloud credentials not present in environment variables");
        }

        try (FileInputStream inputStream = new FileInputStream(jsonCredentials)) {
        GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));

        // Create a HttpRequestInitializer, which will provide a baseline configuration to all requests.
        HttpRequestInitializer requestInitializer =
        request -> {
                new HttpCredentialsAdapter(credentials).initialize(request);
                request.setConnectTimeout(60000); // 1 minute connect timeout
                request.setReadTimeout(60000); // 1 minute read timeout
        };

        // Build the client for interacting with the service.
        return new CloudHealthcare.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
                .setApplicationName("your-application-name")
                .build();
        }
    }

     private static String getAccessToken() throws IOException {
        GoogleCredentials credential =
            GoogleCredentials.getApplicationDefault()
                .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));
    
        return credential.refreshAccessToken().getTokenValue();
      }

    private void uploadToGoogleFhirStore(CloudHealthcare client, String appendedURL, String bundleJson) throws IOException, URISyntaxException {
        HttpClient httpClient = HttpClients.createDefault();
        String baseUri = String.format("%sv1/%s/fhir", client.getRootUrl(), appendedURL);
        URIBuilder uriBuilder = new URIBuilder(baseUri).setParameter("access_token", getAccessToken());
        StringEntity requestEntity = new StringEntity(bundleJson);

        HttpUriRequest request =
                RequestBuilder.post()
                .setUri(uriBuilder.build())
                .setEntity(requestEntity)
                .addHeader("Content-Type", "application/fhir+json")
                .addHeader("Accept-Charset", "utf-8")
                .addHeader("Accept", "application/fhir+json; charset=utf-8")
                .build();

        // Execute the request and process the results.
        HttpResponse response = httpClient.execute(request);
        HttpEntity responseEntity = response.getEntity();
        if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                System.err.print(
                        String.format(
                        "Exception executing FHIR bundle: %s\n", response.getStatusLine().toString()));
                responseEntity.writeTo(System.err);
                throw new RuntimeException();
        }
    }
}
