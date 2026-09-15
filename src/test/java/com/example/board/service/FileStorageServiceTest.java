package com.example.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void 파일을_저장하면_경로_정보를_반환한다() throws IOException {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("files", "image.png", "image/png", "content".getBytes());

        FileStorageService.StoredFile stored = fileStorageService.store(1L, file);

        assertThat(stored.originalFilename()).isEqualTo("image.png");
        assertThat(stored.storedFilename()).endsWith("_image.png");
        assertThat(stored.fileSize()).isEqualTo(file.getSize());
        assertThat(Files.exists(Path.of(stored.filePath()))).isTrue();
    }

    @Test
    void 저장된_파일을_삭제하면_실제_파일이_제거된다() throws IOException {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString());
        MockMultipartFile file = new MockMultipartFile("files", "image.png", "image/png", "content".getBytes());
        FileStorageService.StoredFile stored = fileStorageService.store(1L, file);

        fileStorageService.delete(stored.filePath());

        assertThat(Files.exists(Path.of(stored.filePath()))).isFalse();
    }

    @Test
    void 업로드_루트를_벗어난_경로는_거부한다() {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString());

        assertThatThrownBy(() -> fileStorageService.resolve(tempDir.resolve("../outside.txt").toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
