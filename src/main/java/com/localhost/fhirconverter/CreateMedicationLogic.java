package com.localhost.fhirconverter;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Collections;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Reference;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

import com.google.api.services.healthcare.v1.CloudHealthcare;
import com.google.api.services.healthcare.v1.CloudHealthcareScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

@Component
public class CreateMedicationLogic {
    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    public void createAndUploadMedicationAndMedicationRequest(String projectId, String region, String datasetName, String fhirStoreName, String medicationName, String medicationCode, String patientId) throws IOException, URISyntaxException {
        // Initialize JSON parser to turn resource to string
        FhirContext fhirContext = FhirContext.forR4();
        IParser jsonParser = fhirContext.newJsonParser();

        Medication medToUpload = createMedication(medicationName, medicationCode);
        String medicationJson = jsonParser.encodeResourceToString(medToUpload); //turn into json string so that it can be inputted into to create bundle entry method

        // Create the bundle
        JsonObject bundle = createBundle();

        // Create the bundle entry and add to bundle
        JsonObject medicationEntry = createBundleEntry(medicationJson);
        bundle.getAsJsonArray("entry").add(medicationEntry); //grabs the entry from the bundle and adds the bundle entry above, which is a json object being added to the json array

        // Convert bundle to JSON string
        String bundleJsonString = bundle.toString(); //convert the bundle, which is a json object with a json array full of json objects (fhir resources which were converted to a json string then into a json object which was added to the array)

        // Upload to Google FHIR dataset
        CloudHealthcare client = createClient();
        String appendedURL = "projects/" + projectId + "/locations/" + region + "/datasets/" + datasetName + "/fhirStores/" + fhirStoreName;
        String medicationID = uploadToGoogleFhirStore(client, appendedURL, bundleJsonString, true); // Returns the created Medication ID

        // Now upload the MedicationRequest using the created Medication
        MedicationRequest medRequest = createMedicationRequest(patientId, medicationID);
        String medRequestJson = jsonParser.encodeResourceToString(medRequest);

        // Create the entry
        JsonObject medRequestEntry = createBundleEntry(medRequestJson);

        // Add to a new bundle and upload
        JsonObject medRequestBundle = createBundle();
        medRequestBundle.getAsJsonArray("entry").add(medRequestEntry);
        String medRequestBundleJsonString = medRequestBundle.toString();

        uploadToGoogleFhirStore(client, appendedURL, medRequestBundleJsonString, false);
    }

    private JsonObject createBundleEntry(String resource) {
        JsonObject entry = new JsonObject();
        JsonObject resourceObj = JsonParser.parseString(resource).getAsJsonObject(); //parse jsonString into a json Element which is then converted into json object

        //the json object can then be added to the entry object along with the request details
        //the entries array, a json array, has an entry, which is a json object, which contains a json object which is the fhir resource
        
        entry.add("resource", resourceObj);
    
        JsonObject request = new JsonObject();
        request.addProperty("method", "POST");
        request.addProperty("url", resourceObj.get("resourceType").getAsString());
        entry.add("request", request);
    
        return entry;
    }

    private JsonObject createBundle() {
        JsonObject bundle = new JsonObject();
        JsonArray entries = new JsonArray();
        bundle.addProperty("resourceType", "Bundle");
        bundle.addProperty("type", "transaction");
        bundle.add("entry", entries); //bundle holds a json array now
        return bundle;
    }

    private static CloudHealthcare createClient() throws IOException {
        GoogleCredentials credential = GoogleCredentials.fromStream(new FileInputStream("src/main/resources/serviceKey.json"))
                .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));

        HttpRequestInitializer requestInitializer = request -> {
            new HttpCredentialsAdapter(credential).initialize(request);
            request.setConnectTimeout(60000); // 1 minute connect timeout
            request.setReadTimeout(60000); // 1 minute read timeout
        };

        return new CloudHealthcare.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
                .setApplicationName("your-application-name")
                .build();
    }

    private static String getAccessToken() throws IOException {
        GoogleCredentials credential = GoogleCredentials.getApplicationDefault()
                .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));
        return credential.refreshAccessToken().getTokenValue();
    }

    private String uploadToGoogleFhirStore(CloudHealthcare client, String appendedURL, String bundleJson, boolean getMedicationID) throws IOException, URISyntaxException {
        HttpClient httpClient = HttpClients.createDefault();
        String baseUri = String.format("%sv1/%s/fhir", client.getRootUrl(), appendedURL);
        URIBuilder uriBuilder = new URIBuilder(baseUri).setParameter("access_token", getAccessToken());
        StringEntity requestEntity = new StringEntity(bundleJson);

        HttpUriRequest request = RequestBuilder.post()
                .setUri(uriBuilder.build())
                .setEntity(requestEntity)
                .addHeader("Content-Type", "application/fhir+json")
                .addHeader("Accept-Charset", "utf-8")
                .addHeader("Accept", "application/fhir+json; charset=utf-8")
                .build();

        HttpResponse response = httpClient.execute(request);
        String responseString = EntityUtils.toString(response.getEntity());
        System.out.println(responseString);

        FhirContext fhirContext = FhirContext.forR4();
        Bundle responseBundle = fhirContext.newJsonParser().parseResource(Bundle.class, responseString);

        for (Bundle.BundleEntryComponent entry : responseBundle.getEntry()) {
            if (getMedicationID && entry.getResponse() != null && entry.getResponse().getStatus().equals("201 Created")) {
                String location = entry.getResponse().getLocation();
                String medicationId = extractMedicationIdFromLocation(location);
                return medicationId;
            }
        }

        return null;
    }

    private String extractMedicationIdFromLocation(String location) {
        String[] parts = location.split("/");
        if (parts.length > 0) {
            return parts[parts.length - 3];
        }
        return null;
    }

    public MedicationRequest createMedicationRequest(String patientId, String medicationId) {
        MedicationRequest medicationRequest = new MedicationRequest();

        medicationRequest.setStatus(MedicationRequest.MedicationRequestStatus.ACTIVE);
        medicationRequest.setIntent(MedicationRequest.MedicationRequestIntent.ORDER);
        medicationRequest.setSubject(new Reference("Patient/" + patientId));
        medicationRequest.setMedication(new Reference("Medication/" + medicationId));

        return medicationRequest;
    }

    public Medication createMedication(String medicationCode, String medicationName) {
        Medication medication = new Medication();

        CodeableConcept medicationCodeConcept = new CodeableConcept();
        Coding coding = new Coding().setSystem("http://www.nlm.nih.gov/research/umls/rxnorm").setCode(medicationCode);
        medicationCodeConcept.addCoding(coding);
        medicationCodeConcept.setText(medicationName);

        medication.setCode(medicationCodeConcept);

        return medication;
    }
}
