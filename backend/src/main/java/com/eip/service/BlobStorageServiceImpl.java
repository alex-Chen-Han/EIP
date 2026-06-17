package com.eip.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class BlobStorageServiceImpl implements BlobStorageService {

    @Value("${app.storage.connection-string}")
    private String connectionString;

    @Value("${app.storage.container-name:eipattachments}")
    private String containerName;

    @Value("${app.storage.local-dir:c:/servlet/EIP/uploads}")
    private String localDir;

    private boolean isLocalMode;
    private BlobContainerClient azureContainerClient;

    @PostConstruct
    public void init() {
        if (connectionString == null || connectionString.trim().isEmpty() || connectionString.startsWith("local:")) {
            isLocalMode = true;
            log.info("偵測到本地儲存連線字串。啟用本地模擬儲存模式，存放路徑: {}", localDir);
            File dir = new File(localDir);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                log.info("建立本地儲存目錄: {}, 結果: {}", localDir, created);
            }
        } else {
            isLocalMode = false;
            log.info("偵測到雲端儲存連線字串。啟用 Azure Blob Storage 模式，Container: {}", containerName);
            try {
                azureContainerClient = new BlobServiceClientBuilder()
                        .connectionString(connectionString)
                        .buildClient()
                        .getBlobContainerClient(containerName);
                if (!azureContainerClient.exists()) {
                    azureContainerClient.create();
                    log.info("建立 Azure Blob Storage Container: {}", containerName);
                }
            } catch (Exception e) {
                log.error("初始化 Azure Blob Storage 失敗，降級為本地模擬模式", e);
                isLocalMode = true;
            }
        }
    }

    @Override
    public String uploadFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueName = UUID.randomUUID().toString() + extension;

        if (isLocalMode) {
            Path targetPath = Paths.get(localDir, uniqueName);
            Files.write(targetPath, file.getBytes());
            log.info("檔案已上傳至本地：{}", targetPath.toString());
            return "local:" + uniqueName;
        } else {
            try {
                BlobClient blobClient = azureContainerClient.getBlobClient(uniqueName);
                blobClient.upload(file.getInputStream(), file.getSize(), true);
                log.info("檔案已上傳至 Azure Blob：{}", uniqueName);
                return "azure:" + uniqueName;
            } catch (Exception e) {
                log.error("上傳至 Azure Blob 失敗，嘗試寫入本地備份", e);
                Path targetPath = Paths.get(localDir, uniqueName);
                Files.write(targetPath, file.getBytes());
                return "local:" + uniqueName;
            }
        }
    }

    @Override
    public byte[] downloadFile(String filePath) throws IOException {
        if (filePath == null) {
            throw new IllegalArgumentException("檔案路徑不能為空");
        }

        if (filePath.startsWith("local:")) {
            String fileName = filePath.substring("local:".length());
            Path targetPath = Paths.get(localDir, fileName);
            if (!Files.exists(targetPath)) {
                log.warn("找不到本地檔案：{}", targetPath);
                throw new IOException("找不到指定檔案：" + fileName);
            }
            return Files.readAllBytes(targetPath);
        } else if (filePath.startsWith("azure:")) {
            String blobName = filePath.substring("azure:".length());
            try {
                BlobClient blobClient = azureContainerClient.getBlobClient(blobName);
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                blobClient.downloadStream(outputStream);
                return outputStream.toByteArray();
            } catch (Exception e) {
                log.error("從 Azure Blob 下載檔案失敗: {}", blobName, e);
                // 嘗試從本地尋找（備份降級）
                Path targetPath = Paths.get(localDir, blobName);
                if (Files.exists(targetPath)) {
                    return Files.readAllBytes(targetPath);
                }
                throw new IOException("無法下載 Azure 檔案並無本地備份", e);
            }
        } else {
            // 沒有標記，預設為本地檔名處理
            Path targetPath = Paths.get(localDir, filePath);
            if (Files.exists(targetPath)) {
                return Files.readAllBytes(targetPath);
            }
            throw new IOException("無法識別的檔案路徑格式且找不到檔案：" + filePath);
        }
    }
}
