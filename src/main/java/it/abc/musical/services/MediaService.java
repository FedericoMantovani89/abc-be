package it.abc.musical.services;

import it.abc.musical.dto.MediaDtos.DocumentAttachRequest;
import it.abc.musical.dto.MediaDtos.DocumentDto;
import it.abc.musical.dto.MediaDtos.FolderNodeDto;
import it.abc.musical.dto.MediaDtos.MediaTreeDto;
import it.abc.musical.entities.Document;
import it.abc.musical.entities.Folder;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.DocumentRepository;
import it.abc.musical.repositories.FolderRepository;
import it.abc.musical.util.AuthUtil;
import it.abc.musical.util.FileTypeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private static final Set<String> STAFF_ROLES = Set.of("STAFF", "ADMIN", "GOD");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN", "GOD");

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final AuditLogService auditLogService;
    private final StorageService storageService;
    private final FileValidationService fileValidationService;

    // ------------------------------------------------------------------ alberi

    /** Albero completo per l'admin. */
    @Transactional(readOnly = true)
    public MediaTreeDto adminTree() {
        return buildTree(null);
    }

    /** Albero filtrato per i soci: cartelle per allowed_roles, documenti per visibility. */
    @Transactional(readOnly = true)
    public MediaTreeDto memberTree(Set<String> userRoles) {
        return buildTree(userRoles);
    }

    private MediaTreeDto buildTree(Set<String> userRoles) {
        List<Folder> folders = folderRepository.findByDeletedAtIsNullOrderByNameAsc();
        List<Document> documents = documentRepository.findByDeletedAtIsNullOrderByTitleAsc();

        Map<Long, FolderNodeDto> nodes = new HashMap<>();
        for (Folder folder : folders) {
            if (userRoles == null || AuthUtil.matchesTargetRoles(folder.getAllowedRoles(), userRoles)) {
                nodes.put(folder.getId(), FolderNodeDto.of(folder));
            }
        }

        MediaTreeDto tree = new MediaTreeDto(new java.util.ArrayList<>(), new java.util.ArrayList<>());
        for (Folder folder : folders) {
            FolderNodeDto node = nodes.get(folder.getId());
            if (node == null) {
                continue;
            }
            Long parentId = folder.getParentFolder() != null ? folder.getParentFolder().getId() : null;
            FolderNodeDto parent = parentId != null ? nodes.get(parentId) : null;
            if (parent != null) {
                parent.children().add(node);
            } else {
                // Cartella root, oppure figlia di una cartella non visibile: non mostrarla in quel caso.
                if (parentId == null) {
                    tree.folders().add(node);
                }
            }
        }

        for (Document document : documents) {
            if (userRoles != null && !visibleTo(document, userRoles)) {
                continue;
            }
            Long folderId = document.getFolder() != null ? document.getFolder().getId() : null;
            FolderNodeDto node = folderId != null ? nodes.get(folderId) : null;
            if (node != null) {
                node.documents().add(DocumentDto.from(document));
            } else if (folderId == null) {
                tree.rootDocuments().add(DocumentDto.from(document));
            }
        }
        return tree;
    }

    private boolean visibleTo(Document document, Set<String> userRoles) {
        String visibility = document.getVisibility() != null ? document.getVisibility() : "MEMBERS";
        return switch (visibility) {
            case "ADMIN_ONLY" -> userRoles.stream().anyMatch(ADMIN_ROLES::contains);
            case "STAFF_ONLY" -> userRoles.stream().anyMatch(STAFF_ROLES::contains);
            default -> true;
        };
    }

    // ------------------------------------------------------------------ documenti

    @Transactional(readOnly = true)
    public Document byUuidForRoles(UUID uuid, Set<String> userRoles) {
        Document document = documentRepository.findByUuidAndDeletedAtIsNull(uuid)
                .orElseThrow(() -> new NotFoundException("File non trovato"));
        if (!visibleTo(document, userRoles)
                || (document.getFolder() != null
                    && !AuthUtil.matchesTargetRoles(document.getFolder().getAllowedRoles(), userRoles))) {
            throw new NotFoundException("File non trovato");
        }
        return document;
    }

    @Transactional
    public void incrementDownloadCount(Long documentId) {
        documentRepository.findById(documentId).ifPresent(d -> {
            d.setDownloadCount((d.getDownloadCount() != null ? d.getDownloadCount() : 0) + 1);
            documentRepository.save(d);
        });
    }

    @Transactional
    public DocumentDto attachDocument(DocumentAttachRequest request, Long userId) {
        storageService.validateManagedPath(request.filePath(), StorageService.MEDIA_DIR);
        Path resolved = storageService.resolve(request.filePath());
        long fileSizeBytes;
        try {
            fileSizeBytes = Files.size(resolved);
        } catch (IOException e) {
            throw new NotFoundException("File non trovato: " + request.filePath());
        }
        String mimeType = fileValidationService.detectMimeType(resolved);
        String extension = StorageService.extensionOf(request.originalFilename());

        Document document = new Document();
        document.setFolder(request.folderId() != null ? activeFolder(request.folderId()) : null);
        document.setTitle(request.title() != null && !request.title().isBlank()
                ? request.title().trim() : request.originalFilename());
        document.setFileName(request.originalFilename());
        document.setFilePath(request.filePath());
        document.setFileSizeBytes(fileSizeBytes);
        document.setMimeType(mimeType);
        document.setDocumentCategory(request.documentCategory());
        document.setVisibility(request.visibility() != null ? request.visibility() : "MEMBERS");
        document.setMediaType(FileTypeUtil.mediaTypeOf(extension));
        document.setCreatedBy(userId);
        document = documentRepository.save(document);
        auditLogService.record("UPLOAD", "Document", document.getId());
        return DocumentDto.from(document);
    }

    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("File non trovato"));
        document.setDeletedAt(LocalDateTime.now());
        documentRepository.save(document);
        auditLogService.record("DELETE", "Document", id);
    }

    // ------------------------------------------------------------------ cartelle

    @Transactional
    public FolderNodeDto createFolder(String name, Long parentFolderId, Long userId) {
        Folder folder = new Folder();
        folder.setName(name.trim());
        folder.setParentFolder(parentFolderId != null ? activeFolder(parentFolderId) : null);
        folder.setCreatedBy(userId);
        folder = folderRepository.save(folder);
        auditLogService.record("CREATE", "Folder", folder.getId());
        return FolderNodeDto.of(folder);
    }

    /** Soft-delete ricorsivo di cartella, sottocartelle e documenti contenuti. */
    @Transactional
    public void deleteFolder(Long id) {
        Folder folder = activeFolder(id);
        deleteRecursively(folder);
        auditLogService.record("DELETE", "Folder", id);
    }

    private void deleteRecursively(Folder folder) {
        for (Folder child : folderRepository.findByParentFolderIdAndDeletedAtIsNull(folder.getId())) {
            deleteRecursively(child);
        }
        for (Document document : documentRepository.findByFolderIdAndDeletedAtIsNull(folder.getId())) {
            document.setDeletedAt(LocalDateTime.now());
            documentRepository.save(document);
        }
        folder.setDeletedAt(LocalDateTime.now());
        folderRepository.save(folder);
    }

    @Transactional(readOnly = true)
    public List<String> folderPermissions(Long id) {
        Folder folder = activeFolder(id);
        if (folder.getAllowedRoles() == null || folder.getAllowedRoles().isBlank()) {
            return List.of();
        }
        return List.of(folder.getAllowedRoles().split(",")).stream().map(String::trim).toList();
    }

    @Transactional
    public void setFolderPermissions(Long id, List<String> allowedRoles) {
        Folder folder = activeFolder(id);
        folder.setAllowedRoles(allowedRoles == null || allowedRoles.isEmpty()
                ? null : String.join(",", allowedRoles));
        folderRepository.save(folder);
        auditLogService.record("PERMISSIONS", "Folder", id);
    }

    private Folder activeFolder(Long id) {
        return folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Cartella non trovata"));
    }
}
