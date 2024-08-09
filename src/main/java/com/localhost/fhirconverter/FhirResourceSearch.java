package com.localhost.fhirconverter;

import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Collections;

public class FhirResourceSearch {
    private static final String FHIR_NAME =
            "projects/%s/locations/%s/datasets/%s/fhirStores/%s/fhir/%s";
    private static final String API_ENDPOINT = "https://healthcare.googleapis.com";
    private static final JsonFactory JSON_FACTORY = new GsonFactory();
    private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();

    private static GoogleCredentials credentials;
    private static String baseUri;

    static {
        try {
            String jsonCredentials = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
            if (jsonCredentials == null || jsonCredentials.isEmpty()) {
                    throw new IOException("Google Cloud credentials not present in environment variables");
            }
            FileInputStream inputStream = new FileInputStream(jsonCredentials);

            GoogleCredentials credentials = GoogleCredentials.fromStream(inputStream)
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/cloud-platform"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String fhirResourceSearchGet(String patientId, String requestedResource, String projectId, String region, String datasetName, String fhirStoreName) throws IOException, URISyntaxException {
        if (credentials == null || !(credentials instanceof OAuth2Credentials)) {
            throw new RuntimeException("Google credentials are not OAuth2Credentials.");
        }

        OAuth2Credentials oauth2Credentials = (OAuth2Credentials) credentials;
        if (oauth2Credentials.getAccessToken() == null) {
            oauth2Credentials.refresh();
        }

        HttpClient httpClient = HttpClients.custom().build();

        if (requestedResource.equals("Conditions")) {
            baseUri = API_ENDPOINT + "/v1/projects/" + projectId + "/locations/" + region + "/datasets/" + datasetName + "/fhirStores/" + fhirStoreName + "/fhir/Condition?subject=Patient/" + patientId;
        }
        if (requestedResource.equals("AllergyIntolerances")) {
            baseUri = API_ENDPOINT + "/v1/projects/" + projectId + "/locations/" + region + "/datasets/" + datasetName + "/fhirStores/" + fhirStoreName + "/fhir/AllergyIntolerance?patient=Patient/" + patientId;
        }
        
        URIBuilder uriBuilder = new URIBuilder(baseUri);

        HttpUriRequest request = RequestBuilder.get()
                .setUri(uriBuilder.build())
                .setHeader("Authorization", "Bearer " + oauth2Credentials.getAccessToken().getTokenValue())
                .build();

        HttpResponse response = httpClient.execute(request);
        try {
                if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                    HttpEntity entity = response.getEntity();
                    if (entity != null) {
                        // Process the entity content
                        String responseBody = EntityUtils.toString(entity);
                        return responseBody;
                    } else {
                        throw new RuntimeException("Empty response entity");
                    }
                } else {
                    throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
                }
            } finally {
                // Ensure the response entity is fully consumed
                EntityUtils.consumeQuietly(response.getEntity());
            }
    }
}
