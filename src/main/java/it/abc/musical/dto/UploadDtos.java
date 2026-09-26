package it.abc.musical.dto;

import it.abc.musical.enums.UploadTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class UploadDtos {

    private UploadDtos() {
    }

    /** filename max 255: stesso limite di documents.file_name, dove finisce all'aggancio. */
    public record InitUploadRequest(
            @NotNull UploadTargetType targetType,
            @NotBlank @Size(max = 255) String filename,
            @Positive long totalSize) {
    }

    public record InitUploadResponse(UUID uploadId, int chunkSizeBytes) {
    }

    public record CompleteUploadResponse(String path, String mimeType, long sizeBytes, String originalFilename) {
    }
}
