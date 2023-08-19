package com.myHome.serene.controllers;

import com.myHome.serene.constants.FileConstants;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

@RequestMapping("/serene")
@RestController
@CrossOrigin(value = "*")
public class FileController {

    @PostMapping("/upload")
    public Map upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bucket") String bucket,
            @RequestParam("collection") String collection
    ){
        try {
            Path path = Path.of(FileConstants.STORAGE_BASE_PATH, bucket, collection);
            if(!Files.exists(path))
                Files.createDirectories(path);
            file.transferTo(Path.of(
                    FileConstants.STORAGE_BASE_PATH,
                    bucket,
                    collection,
                    file.getOriginalFilename())
            );
            return Map.of("success", true,"body",file.getOriginalFilename());
        }catch (Exception e){
            e.printStackTrace();
            return Map.of("success",false);
        }
    }

    @PostMapping("/createBucket")
    public Map createBucket(@RequestParam Map<String,String> body){
        String bucket = body.get("bucket");
        if(Objects.isNull(bucket))
            return Map.of(
                    "success",false,
                    "error","Bad Request!"
            );
        try {
            Path path = Path.of(FileConstants.STORAGE_BASE_PATH, bucket);
            if (!Files.exists(path))
                Files.createDirectory(path);
            return Map.of("success", true);
        }catch (Exception e){
            e.printStackTrace();
            return Map.of("success",false);
        }
    }

    @PostMapping("/delete")
    public Map delete(@RequestParam Map<String,String> body){
        String bucket = body.get("bucket");
        String collection = body.get("collection");
        String name = body.get("name");
        String deleteType = body.get("type");
        if(
                Objects.isNull(bucket) ||
                Objects.isNull(collection) ||
                Objects.isNull(name) ||
                Objects.isNull(deleteType)
        ) return Map.of(
            "success",false,
            "error","Bad Request!"
        );
        try {
            if (deleteType.equals("BUCKET"))
                Files.deleteIfExists(Path.of(FileConstants.STORAGE_BASE_PATH, bucket));
            else if (deleteType.equals("COLLECTION"))
                Files.deleteIfExists(Path.of(FileConstants.STORAGE_BASE_PATH, bucket,collection));
            else if (deleteType.equals("FILE"))
                Files.deleteIfExists(Path.of(FileConstants.STORAGE_BASE_PATH, bucket, collection, name));
            else return Map.of(
                "success", false,
                "error", "Bad Delete Type!"
            );
            return Map.of("success",true);
        }catch (Exception e){
            e.printStackTrace();
            return Map.of(
                "success",false,
                "error","Internal Server Error!"
            );
        }
    }

    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> downloadFileRange(
            @RequestHeader(value = "Range", required = false) String rangeHeader,
            @RequestParam Map<String,String> body
    ) throws IOException {
        String bucket = body.get("bucket");
        String collection = body.get("collection");
        String name = body.get("name");
        if(
                Objects.isNull(bucket) ||
                Objects.isNull(collection) ||
                Objects.isNull(name)
        ) return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        try{
            Path filePath = Path.of(FileConstants.STORAGE_BASE_PATH,bucket,collection,name);
            byte [] fileData = Files.readAllBytes(filePath);
            HttpHeaders headers = new HttpHeaders();
            ByteArrayResource resource;

            if(Objects.nonNull(rangeHeader) && rangeHeader.startsWith("bytes=")){
                String range = rangeHeader.substring(6);
                String[] ranges = range.split("-");
                long start = Long.parseLong(ranges[0]);
                long end = Math.min(Long.parseLong(ranges[1]), fileData.length - 1);

                headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end +"/" + fileData.length);
                headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
                headers.set(HttpHeaders.CONTENT_LENGTH, String.valueOf(end - start + 1));
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

                byte[] rangeData = new byte[(int) (end - start + 1)];
                System.arraycopy(fileData, (int) start, rangeData, 0, rangeData.length);
                resource = new ByteArrayResource(rangeData);
            } else resource = new ByteArrayResource(fileData);

            return ResponseEntity
                    .status(HttpStatus.PARTIAL_CONTENT)
                    .headers(headers)
                    .body(resource);
        }catch (Exception e){
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

}
