package com.eip.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface BlobStorageService {
    String uploadFile(MultipartFile file) throws IOException;
    byte[] downloadFile(String filePath) throws IOException;
}
