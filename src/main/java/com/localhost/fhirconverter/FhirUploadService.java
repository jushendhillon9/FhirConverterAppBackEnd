package com.localhost.fhirconverter;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.healthcare.v1.CloudHealthcare;
import com.google.api.services.healthcare.v1.CloudHealthcareScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;

import org.springframework.stereotype.Service;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FhirUploadService {
  private static final JsonFactory JSON_FACTORY = new GsonFactory();
  private static final NetHttpTransport HTTP_TRANSPORT = new NetHttpTransport();

  public String fhirStoreExecuteBundle(MultipartFile file, String projectId, String region, String datasetName, String storeName) throws IOException, URISyntaxException {
    String fhirStoreName = "projects/" + projectId + "/locations/" + region + "/datasets/" + datasetName + "/fhirStores/" + storeName;
    //need to bundleify the inputted file 
    String fhirBundle = createFhirBundle(file);

    // Initialize the client, which will be used to interact with the service.
    CloudHealthcare client = createClient();
    HttpClient httpClient = HttpClients.createDefault();
    String baseUri = String.format("%sv1/%s/fhir", client.getRootUrl(), fhirStoreName);
    URIBuilder uriBuilder = new URIBuilder(baseUri).setParameter("access_token", getAccessToken());
    StringEntity requestEntity = new StringEntity(fhirBundle);

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
    System.out.print("FHIR bundle executed: ");
    responseEntity.writeTo(System.out);

    return "FHIR bundle uploaded successfully!";
  }

  private static CloudHealthcare createClient() throws IOException {
    // Use Application Default Credentials (ADC) to authenticate the requests
    // For more information see https://cloud.google.com/docs/authentication/production

    String credentialsJson = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON");
    if (credentialsJson == null || credentialsJson.isEmpty()) {
        throw new IOException("Google Cloud credentials not found in environment variables");
    }

    GoogleCredentials credential = GoogleCredentials.fromStream(
      new ByteArrayInputStream(credentialsJson.getBytes(StandardCharsets.UTF_8)))
      .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));


    // GoogleCredentials credential = GoogleCredentials.fromStream(
    //         new FileInputStream("src/main/resources/serviceKey.json"))
    //         .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));

    // Create a HttpRequestInitializer, which will provide a baseline configuration to all requests.
    HttpRequestInitializer requestInitializer =
        request -> {
            new HttpCredentialsAdapter(credential).initialize(request);
            request.setConnectTimeout(60000); // 1 minute connect timeout
            request.setReadTimeout(60000); // 1 minute read timeout
        };

    // Build the client for interacting with the service.
    return new CloudHealthcare.Builder(HTTP_TRANSPORT, JSON_FACTORY, requestInitializer)
            .setApplicationName("your-application-name")
            .build();
  }

  private static String getAccessToken() throws IOException {
    GoogleCredentials credential =
        GoogleCredentials.getApplicationDefault()
            .createScoped(Collections.singleton(CloudHealthcareScopes.CLOUD_PLATFORM));

    return credential.refreshAccessToken().getTokenValue();
  }

  private String createFhirBundle(MultipartFile file) throws IOException {
    JsonElement jsonElement = JsonParser.parseString(new String(file.getBytes()));
    //convert uploaded file into json array, contains individual fhir resources
    //parseString takes a string and converts to json element, and this json element should be a json array since
    //the fhir conversion process turns it into an array of fhir resources... look at convertedResource/goldenTicket file and can see its an array of objects in json notation

    JsonObject bundle = new JsonObject();
    bundle.addProperty("resourceType", "Bundle");
    bundle.addProperty("type", "transaction");
    //create bundle object here to put entries, an entry is a fhir resource, inside of 

    JsonArray entries = new JsonArray();
    //create a json array that will hold the entries created

    if (jsonElement.isJsonArray()) {
      JsonArray resourcesArray = jsonElement.getAsJsonArray(); 
      //if its an array turn element into array
      for (JsonElement element: resourcesArray) { 
        //for each object in array, turn it into entry for bundle
        JsonObject resource = element.getAsJsonObject();
        JsonObject entry = createBundleEntry(resource);
        entries.add(entry);
      }
    }
    else if (jsonElement.isJsonObject()) {
      JsonObject resource = jsonElement.getAsJsonObject(); 
      //if its an object, turn it into object
      JsonObject entry = createBundleEntry(resource); 
      //input single object to create single bundle with single entry
      entries.add(entry);
    }
    else {
      throw new IllegalArgumentException("Invalid JSON format: neither array nor object.");
    }

    bundle.add("entry", entries);
    //add the json array full of entries to the bundle and name it "entry"

    return bundle.toString();
  }

  private JsonObject createBundleEntry(JsonObject resource) {
    JsonObject entry = new JsonObject();
    entry.add("resource", resource);

    JsonObject request = new JsonObject();
    request.addProperty("method", "POST");
    request.addProperty("url", resource.get("resourceType").getAsString());
    entry.add("request", request);

    return entry;
  }
}