package com.localhost.fhirconverter;
import com.localhost.fhirconverter.FileTypeChecker.FileType; //import the enum...
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity; 

//need these import statements to setup the post routes that will provide the file here
//provided by the Spring framework
@RestController
@RequestMapping("/api")
// Indicates that this class will handle HTTP requests and produce HTTP responses.
public class ConversionController {

    private static FileType filetype;

    @PostMapping("/uploadingFile")
    // indicates that the uploadFile() method will handle HTTP POST requests to the "/uploadFile" endpoint.
    public ResponseEntity<String> uploadFile(@RequestParam MultipartFile file) { //request parameter is sent from the client, it will be the file here!
        // Indicates that this method expects a request parameter named "file",
        // which will be bound to the MultipartFile parameter.

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty");
        }

        //check if file is empty
        filetype = FileTypeChecker.getFileType(file);
        //gets file type and stores it as variable of the enum which enum it can be of three options listed in the enum
        switch (filetype) {
            case CSV:
                return ResponseEntity.ok("CSV file received!");
                //enable
            case JSON:
                return ResponseEntity.ok("JSON file received!");
                //enable
            case UNKNOWN: return ResponseEntity.badRequest().body("Unsupported filetype");
            default:
                return ResponseEntity.badRequest().body("Unknown error occured please try again!");
        }
    }

    public static FileType getFileType() {
        return filetype; 
    }
}