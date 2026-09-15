package com.example.board.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(@Value("${file.upload-dir}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 디렉토리를 생성할 수 없습니다.", e);
        }
    }

    public StoredFile store(Long boardId, MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path targetDir = uploadRoot.resolve(String.valueOf(boardId)).normalize();
        Path targetPath = targetDir.resolve(storedFilename).normalize();

        if (!targetDir.startsWith(uploadRoot) || !targetPath.startsWith(targetDir)) {
            throw new IllegalArgumentException("올바르지 않은 파일 이름입니다: " + originalFilename);
        }

        try {
            Files.createDirectories(targetDir);
            file.transferTo(targetPath);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다.", e);
        }

        return new StoredFile(originalFilename, storedFilename, targetPath.toString(), file.getSize(), file.getContentType());
    }

    public Path resolve(String filePath) {
        Path path = Path.of(filePath).normalize();
        if (!path.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("올바르지 않은 파일 경로입니다.");
        }
        return path;
    }

    public void delete(String filePath) {
        try {
            Files.deleteIfExists(resolve(filePath));
        } catch (IOException e) {
            throw new UncheckedIOException("파일 삭제에 실패했습니다.", e);
        }
    }

    public record StoredFile(String originalFilename, String storedFilename, String filePath, long fileSize,
            String contentType) {
    }
}
