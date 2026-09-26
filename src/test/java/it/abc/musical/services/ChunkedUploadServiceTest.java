package it.abc.musical.services;

import it.abc.musical.dto.UploadDtos.CompleteUploadResponse;
import it.abc.musical.dto.UploadDtos.InitUploadResponse;
import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkedUploadServiceTest {

    private ChunkedUploadService service;

    private static byte[] fakeJpegBytes(int size) {
        byte[] content = new byte[size];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        StorageService storageService = new StorageService(tempDir.toString());
        storageService.init();
        service = new ChunkedUploadService(storageService, new FileValidationService(), 1, 24);
        service.init();
    }

    @Test
    void writesChunksAtCorrectOffsetAndAssemblesFileOnComplete() {
        byte[] content = fakeJpegBytes(3 * 1024 * 1024 + 12345); // più di 1 chunk (1MB)
        InitUploadResponse init = service.init(UploadTargetType.SHOW_POSTER, "poster.jpg", content.length);

        int chunkSize = init.chunkSizeBytes();
        int index = 0;
        for (int offset = 0; offset < content.length; offset += chunkSize, index++) {
            int end = Math.min(offset + chunkSize, content.length);
            service.writeChunk(init.uploadId(), index, Arrays.copyOfRange(content, offset, end));
        }

        CompleteUploadResponse result = service.complete(init.uploadId());

        assertThat(result.path()).startsWith("/posters/").endsWith(".jpg");
        assertThat(result.sizeBytes()).isEqualTo(content.length);
        assertThat(result.mimeType()).isEqualTo("image/jpeg");
        assertThatThrownBy(() -> service.writeChunk(init.uploadId(), 0, content))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void retryingSameChunkIndexOverwritesInsteadOfCorrupting() {
        byte[] content = fakeJpegBytes(10);
        InitUploadResponse init = service.init(UploadTargetType.SHOW_POSTER, "poster.jpg", content.length);

        service.writeChunk(init.uploadId(), 0, new byte[] {9, 9, 9, 9, 9, 9, 9, 9, 9, 9});
        service.writeChunk(init.uploadId(), 0, content);

        CompleteUploadResponse result = service.complete(init.uploadId());
        assertThat(result.sizeBytes()).isEqualTo(10);
    }

    @Test
    void completeRejectsMismatchedDeclaredSize() {
        InitUploadResponse init = service.init(UploadTargetType.SHOW_POSTER, "poster.jpg", 100);
        service.writeChunk(init.uploadId(), 0, fakeJpegBytes(10));

        assertThatThrownBy(() -> service.complete(init.uploadId()))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void initRejectsExtensionNotAllowedForTarget() {
        assertThatThrownBy(() -> service.init(UploadTargetType.SHOW_POSTER, "documento.pdf", 100))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void initRejectsFileOverTheCategoryLimit() {
        // Immagini: limite 5 MB. Il rifiuto arriva all'avvio, prima di qualsiasi byte.
        assertThatThrownBy(() -> service.init(UploadTargetType.SHOW_POSTER, "poster.jpg", 6L * 1024 * 1024))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("File troppo grande");
    }

    @Test
    void writeChunkRejectsOffsetBeyondDeclaredTotalSize() {
        InitUploadResponse init = service.init(UploadTargetType.SHOW_POSTER, "poster.jpg", 10);
        assertThatThrownBy(() -> service.writeChunk(init.uploadId(), 5, new byte[10]))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void cancelRemovesSessionSoSubsequentChunkFails() {
        InitUploadResponse init = service.init(UploadTargetType.SHOW_POSTER, "poster.jpg", 10);
        service.cancel(init.uploadId());
        assertThatThrownBy(() -> service.writeChunk(init.uploadId(), 0, new byte[10]))
                .isInstanceOf(NotFoundException.class);
    }
}
