package com.localhost.fhirconverter;

import com.localhost.fhirconverter.FileTypeChecker.FileType;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;


// HAPI FHIR imports
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;

//Apache imports for csv
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.Conversion;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api")
public class ConversionLogic {
    @PostMapping("/convertingFile")
    public ResponseEntity convertFile(@RequestParam MultipartFile fileToConvert) {
        FileType uploadedFileType = FileTypeChecker.getFileType(fileToConvert);
        System.out.println(uploadedFileType);
        if (uploadedFileType == FileType.JSON) {
            try {
                System.out.println("ITS WORKING");
                String fileContent = new String(fileToConvert.getBytes(), StandardCharsets.UTF_8); //turns entire file into string
                FhirContext fhirContext = FhirContext.forR4();

                IParser jsonParser = fhirContext.newJsonParser(); //create json parser which serializes fhir elements into JSON

                IBaseResource resource = jsonParser.parseResource(fileContent); //takes the json resource, in string form, and understands how to parse it into a fhir resource
                //this resource is NOT a json string yet, it is a IBaseResource, a hapi fhir library object
                String serializedJson = jsonParser.encodeResourceToString(resource);
                //now it turns the fhir resource into a string, which means it has been serialized and can be sent as a file 

                byte[] fileBytes = serializedJson.getBytes(StandardCharsets.UTF_8);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setContentDispositionFormData("filename", "resource.json");
                headers.setContentLength(fileBytes.length);

                System.out.println("hello there");

                System.out.println(serializedJson);


                return new ResponseEntity<byte[]>(fileBytes, headers, HttpStatus.OK);
            } catch (IOException exception) {
                exception.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        } 
        else if (uploadedFileType == FileType.CSV) {
            FhirContext fhirContext = FhirContext.forR4();
            try(InputStream inputstream = fileToConvert.getInputStream(); //get file in bytes 
                InputStreamReader reader = new InputStreamReader(inputstream, StandardCharsets.UTF_8); //reader has bytes and decoding logic to transform into utf8
                CSVParser csvParser = new CSVParser(reader, CSVFormat.DEFAULT.withHeader()) //pass in the reader as first parameter for csv parser and second parameter indicates first row is headers row
                //this is all to turn csv file into a csvParser, which holds multiple records (each record is a row)
            )
            {
                System.out.println("MADE IT HERE");
                List<String> jsonResources = new ArrayList<>();
                for (CSVRecord record : csvParser) { //for each row (record) in the csvParser

                    Map<String, String> recordMap = record.toMap(); //record.toMap() maps every key value pair in that record, so there will be as many pairs as columns
                    //the type is "Map", which is essentially a collection of key value pairs stored as an object

                    ObjectMapper mapper = new ObjectMapper(); 
                    String jsonString = mapper.writeValueAsString(recordMap);
                    System.out.println(jsonString);
                    //objectmapper belongs to jackson library and it allows for us to write the collection of key value pairs as a string
    
                    IBaseResource resource = fhirContext.newJsonParser().parseResource(jsonString);
                    System.out.println(resource);
                    //takes string and parses it into a fhir resource

                    String serializedJson = fhirContext.newJsonParser().encodeResourceToString(resource);
                    System.out.println(serializedJson);
                    //turn fhir resource back into a string which is in fhir format which is compliant with json standards

                    jsonResources.add(serializedJson);
                    //adds it to the list of resources
                }
                String jsonArrayString = "[" + String.join(",", jsonResources) + "]";
                System.out.println(jsonArrayString);
                //String.join is a method that joins elements of an array (jsonResources is the array) with a delimeter provided as the first parameter
                //the square brackets are necessary to create a "json array"
                byte[] fileBytes = jsonArrayString.getBytes(StandardCharsets.UTF_8);
                //turn json array of fhir resources back into bytes
                HttpHeaders headers = new HttpHeaders(); //new HttpHeaders instance which is a class provided by Springboot
                headers.setContentType(MediaType.APPLICATION_JSON); // here we set the header of the http response to application/json, which means returned data is in json format
                headers.setContentDispositionFormData("filename", "resource.json"); //treat the data as formdata attachment with filename resource.json, this means the form will be downloaded not displayed
                headers.setContentLength(fileBytes.length); //sets length of content in bytes
                return new ResponseEntity<byte[]>(fileBytes, headers, HttpStatus.OK); //return file in bytes, its headers, and a response saying the return was okay
            }
            catch(IOException exception) {
                exception.printStackTrace();
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
        }
        else {
            return null;
        }
    }
}

