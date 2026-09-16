package it.abc.musical.controllers.api;

import it.abc.musical.dto.UploadDtos.CompleteUploadResponse;
import it.abc.musical.dto.UploadDtos.InitUploadRequest;
import it.abc.musical.dto.UploadDtos.InitUploadResponse;
import it.abc.musical.services.ChunkedUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/uploads")
@RequiredArgsConstructor
public class AdminUploadController {

    private final ChunkedUploadService chunkedUploadService;

    @PostMapping
    public ResponseEntity<InitUploadResponse> init(@Valid @RequestBody InitUploadRequest request) {
        InitUploadResponse response = chunkedUploadService.init(
                request.targetType(), request.filename(), request.totalSize());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{uploadId}/chunks/{index}")
    public ResponseEntity<Void> chunk(@PathVariable UUID uploadId, @PathVariable int index,
                                      @RequestBody byte[] data) {
        chunkedUploadService.writeChunk(uploadId, index, data);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{uploadId}/complete")
    public CompleteUploadResponse complete(@PathVariable UUID uploadId) {
        return chunkedUploadService.complete(uploadId);
    }

    @DeleteMapping("/{uploadId}")
    public ResponseEntity<Void> cancel(@PathVariable UUID uploadId) {
        chunkedUploadService.cancel(uploadId);
        return ResponseEntity.noContent().build();
    }
}
