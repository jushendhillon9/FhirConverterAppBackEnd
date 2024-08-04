package com.localhost.fhirconverter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.AccessToken;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Blob;
import com.google.api.gax.paging.Page;

import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.FileItem;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import com.localhost.fhirconverter.ConversionLogic;



@RestController
@RequestMapping("/api")
public class StorageController {
    @PostMapping("/listBucketsAndObjects")
    public String listBucketsAndObjects(@RequestBody String requestBody) {
        try {
            String[] parts = requestBody.split(",");
            String accessToken = parts[0].trim();
            String projectId = parts[1].trim();

            // Initialize AccessToken object
            AccessToken token = new AccessToken(accessToken, null);

            // Create GoogleCredentials using the AccessToken
            GoogleCredentials credentials = GoogleCredentials.create(token);

            // Initialize the Storage client
            Storage storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build().getService();

            // StringBuilder to store output
            StringBuilder output = new StringBuilder();


            // List all buckets in the project
            for (Bucket bucket : storage.list().iterateAll()) {
                output.append("Bucket: ").append(bucket.getName()).append("\n");
                System.out.println("its working");

                // List objects in the bucket
                output.append("Objects in ").append(bucket.getName()).append(":\n");
                Page<Blob> blobs = bucket.list();
                for (Blob blob : blobs.iterateAll()) {
                    output.append(blob.getName()).append("\n");
                }
                output.append("\n");
            }

            return output.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error occurred: " + e.getMessage();
        }
    }

    @PostMapping("/objectToConvert")
    public ResponseEntity objectToConvert(@RequestBody String requestBody) {
        String[] parts = requestBody.split(",");
        String projectId = parts[0].trim();
        String nameOfObject = parts[1].trim();
        String nameOfBucket = parts[2].trim();
        String accessToken = parts[3].trim();

        // Initialize AccessToken object
        AccessToken token = new AccessToken(accessToken, null);
        //create a google AccessToken using the accessToken from the logged in user

        // Create GoogleCredentials using the AccessToken
        GoogleCredentials credentials = GoogleCredentials.create(token);
        //creates credentials to authorize google cloud api calls 
        //using it to provide the credentials that the storage object needs,

        // Initialize the Storage client
        Storage storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(projectId).build().getService();
        // the Storage object has ability to interact with the buckets and objects within a specific Google Cloud project.

        Blob blob = storage.get(nameOfBucket, nameOfObject);
        System.out.println(blob);

        if (blob != null) {
            byte[] content = blob.getContent();

            System.out.println(blob.getName());

            // Create a MultipartFile instance using the custom implementation
            MultipartFile multipartFile = new ByteArrayMultipartFile(
                    content,             // Byte array content of the blob
                    blob.getName());     // Name of the blob
            ConversionLogic conversionLogic = new ConversionLogic();
            ResponseEntity<byte[]> responseEntity = conversionLogic.convertFile(multipartFile);
            return responseEntity;
        } 


        else {
            // Handle case where blob is not found
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}