package com.localhost.fhirconverter;

import org.springframework.web.multipart.MultipartFile;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//need these two imports bc they are part of springboot framework

@SpringBootApplication
public class FileTypeChecker {
    public static FileType getFileType(MultipartFile file) {
        //takes the File type
        String fileName = file.getOriginalFilename();
        if (fileName != null && fileName.endsWith(".csv")) {
            return FileType.CSV;
        } 
        else if (fileName != null && fileName.endsWith(".json")) {
            return FileType.JSON;
        }
        else {
            return FileType.UNKNOWN;
        }
    }

    public static void main(String[] args) {
            System.out.println("HellO!");
            SpringApplication.run(FileTypeChecker.class, args); //args is always second parameter
            //call the run application method in main
            // primary source is the class annotated with @SpringBootApplication, and is the primary class
    }
    
    public enum FileType {
    //convention to have all enum constants capitalized, create variables out of enums is possible
        CSV, JSON, UNKNOWN;
    }
}

