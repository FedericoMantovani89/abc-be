package it.abc.musical.controllers.api;

import it.abc.musical.dto.MediaDtos.DocumentAttachRequest;
import it.abc.musical.dto.MediaDtos.DocumentDto;
import it.abc.musical.dto.MediaDtos.DocumentMoveRequest;
import it.abc.musical.dto.MediaDtos.DocumentRenameRequest;
import it.abc.musical.dto.MediaDtos.FolderCreateRequest;
import it.abc.musical.dto.MediaDtos.FolderMoveRequest;
import it.abc.musical.dto.MediaDtos.FolderNodeDto;
import it.abc.musical.dto.MediaDtos.FolderPermissionsDto;
import it.abc.musical.dto.MediaDtos.FolderPermissionsRequest;
import it.abc.musical.dto.MediaDtos.FolderRenameRequest;
import it.abc.musical.dto.MediaDtos.MediaTreeDto;
import it.abc.musical.services.MediaService;
import it.abc.musical.services.MediaService.EnsuredFolder;
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
    public ResponseEntity<DocumentDto> attach(@Valid @RequestBody DocumentAttachRequest request,
                                              Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.attachDocument(request, AuthUtil.userId(authentication)));
    }

    @PatchMapping("/{id}/move")
    public DocumentDto moveDocument(@PathVariable Long id, @RequestBody DocumentMoveRequest request) {
        return mediaService.moveDocument(id, request.folderId());
    }

    @PatchMapping("/{id}")
    public DocumentDto renameDocument(@PathVariable Long id, @Valid @RequestBody DocumentRenameRequest request) {
        return mediaService.renameDocument(id, request.title());
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

    /** Trova o crea: 200 con la cartella gia' presente nel padre, 201 se creata ora. */
    @PostMapping("/folder/ensure")
    public ResponseEntity<FolderNodeDto> ensureFolder(@Valid @RequestBody FolderCreateRequest request,
                                                      Authentication authentication) {
        EnsuredFolder result = mediaService.ensureFolder(request.name(), request.parentFolderId(),
                AuthUtil.userId(authentication));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.folder());
    }

    @PatchMapping("/folder/{id}/move")
    public FolderNodeDto moveFolder(@PathVariable Long id, @RequestBody FolderMoveRequest request) {
        return mediaService.moveFolder(id, request.parentFolderId());
    }

    @PatchMapping("/folder/{id}")
    public FolderNodeDto renameFolder(@PathVariable Long id, @Valid @RequestBody FolderRenameRequest request) {
        return mediaService.renameFolder(id, request.name());
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
        return ResponseEntity.noContent().build();
    }
}
