package it.abc.musical.dto;

import it.abc.musical.enums.UploadTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public final class UploadDtos {

    private UploadDtos() {
    }

    public record InitUploadRequest(
            @NotNull UploadTargetType targetType,
            @NotBlank String filename,
            @Positive long totalSize) {
    }

    public record InitUploadResponse(UUID uploadId, int chunkSizeBytes) {
    }

    public record CompleteUploadResponse(String path, String mimeType, long sizeBytes, String originalFilename) {
    }
}
