package com.retrofit.backend.service.impl;

import com.retrofit.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpacesStorageServiceImpl implements StorageService {

    private final S3Client s3Client;

    @Value("${aws.spaces.endpoint}")
    private String endpoint;

    @Value("${aws.spaces.bucket}")
    private String bucketName;

    @Value("${aws.spaces.region}")
    private String region;

    @Override
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No se puede guardar un archivo vacío.");
        }

        // 1. Generar nombre único con UUID para la nube
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        try {
            // 2. Preparar la subida con lectura pública para que Angular acceda directo
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(uniqueFilename)
                    .contentType(file.getContentType())
                    .acl(ObjectCannedACL.PUBLIC_READ)
                    .build();

            // 3. Subir los bytes del archivo al Space / Bucket
            s3Client.putObject(putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // 4. Retornar la URL de internet definitiva
            String cleanEndpoint = endpoint != null ? endpoint.replaceAll("/+$", "") : "";
            return String.format("%s/%s/%s", cleanEndpoint, bucketName, uniqueFilename);

        } catch (IOException e) {
            throw new RuntimeException("Error al procesar y subir el archivo al almacenamiento S3", e);
        }
    }

    @Override
    public List<String> storeMultiple(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (!file.isEmpty()) {
                    urls.add(store(file));
                }
            }
        }
        return urls;
    }
}