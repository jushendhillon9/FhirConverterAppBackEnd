package com.localhost.fhirconverter;

import com.localhost.fhirconverter.FhirUploadService;
import com.localhost.fhirconverter.FhirResourceSearch;
import com.localhost.fhirconverter.CreateConditionLogic;
import com.localhost.fhirconverter.CreateMedicationLogic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.apache.http.HttpEntity;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;

@RestController
@RequestMapping("/api")
public class FhirGoogleDatasetController {

    @Autowired
    private FhirUploadService fhirUploadService;

    private FhirResourceSearch fhirResourceSearch;

    @Autowired
    private CreateConditionLogic createConditionLogic;

    @Autowired
    private CreateMedicationLogic createMedicationLogic;


    @PostMapping("/upload-fhir-bundle")
    public ResponseEntity<String> uploadFhirBundle(MultipartFile file, String projectId, String region, String datasetName, String storeName) {
        try {
            String response = fhirUploadService.fhirStoreExecuteBundle(file, projectId, region, datasetName, storeName);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(500).body("Failed to upload FHIR bundle: " + e.getMessage());
        }
        catch (URISyntaxException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Failed to upload FHIR bundle: " + e.getMessage());
        }
    }

    @PostMapping("/get-conditions")
    public String getConditions(String patientId, String requestedResource, String projectId, String region, String datasetName, String fhirStoreName) {
        try {
            return FhirResourceSearch.fhirResourceSearchGet(patientId, requestedResource, projectId, region, datasetName, fhirStoreName);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            // Handle or log the exception appropriately
            return null; // or throw a custom exception or return a default HttpEntity
        }
    }

    @PostMapping("/create-condition")
    public void createCondition(String projectId, String region, String datasetName, String fhirStoreName, String clinicalStatus, String verificationStatus, String code, String description, String patientId) {
        try {
            createConditionLogic.createCondition(projectId, region, datasetName, fhirStoreName, clinicalStatus, verificationStatus, code, description, patientId);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    @PostMapping("/create-medication")
    public void createMedication(String projectId, String region, String datasetName, String fhirStoreName, String medicationName, String medicationCode, String patientId) {
        try {
            createMedicationLogic.createAndUploadMedicationAndMedicationRequest(projectId, region, datasetName, fhirStoreName, medicationName, medicationCode, patientId);
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }
    }
}
