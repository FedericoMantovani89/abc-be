package it.abc.musical.controllers.api;

import it.abc.musical.dto.MediaDtos.DocumentAttachRequest;
import it.abc.musical.dto.MediaDtos.DocumentDto;
import it.abc.musical.dto.MediaDtos.FolderCreateRequest;
import it.abc.musical.dto.MediaDtos.FolderNodeDto;
import it.abc.musical.dto.MediaDtos.FolderPermissionsDto;
import it.abc.musical.dto.MediaDtos.FolderPermissionsRequest;
import it.abc.musical.dto.MediaDtos.MediaTreeDto;
import it.abc.musical.services.MediaService;
import it.abc.musical.util.AuthUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/media")
@RequiredArgsConstructor
public class AdminMediaController {

    private final MediaService mediaService;

    @GetMapping("/tree")
    public MediaTreeDto tree() {
        return mediaService.adminTree();
    }

    @PostMapping
    public DocumentDto attach(@Valid @RequestBody DocumentAttachRequest request,
                              Authentication authentication) {
        return mediaService.attachDocument(request, AuthUtil.userId(authentication));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable Long id) {
        mediaService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/folder")
    public ResponseEntity<FolderNodeDto> createFolder(@Valid @RequestBody FolderCreateRequest request,
                                                      Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.createFolder(request.name(), request.parentFolderId(),
                        AuthUtil.userId(authentication)));
    }

    @DeleteMapping("/folder/{id}")
    public ResponseEntity<Void> deleteFolder(@PathVariable Long id) {
        mediaService.deleteFolder(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/folder/{id}/permissions")
    public FolderPermissionsDto permissions(@PathVariable Long id) {
        return new FolderPermissionsDto(mediaService.folderPermissions(id));
    }

    @PatchMapping("/folder/{id}/permissions")
    public ResponseEntity<Void> setPermissions(@PathVariable Long id,
                                               @Valid @RequestBody FolderPermissionsRequest request) {
        mediaService.setFolderPermissions(id, request.allowedRoles());
        return ResponseEntity.ok().build();
    }
}
