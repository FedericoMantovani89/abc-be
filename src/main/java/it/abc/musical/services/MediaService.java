package it.abc.musical.services;

import it.abc.musical.dto.MediaDtos.DocumentAttachRequest;
import it.abc.musical.dto.MediaDtos.DocumentDto;
import it.abc.musical.dto.MediaDtos.FolderNodeDto;
import it.abc.musical.dto.MediaDtos.MediaTreeDto;
import it.abc.musical.entities.Document;
import it.abc.musical.entities.Folder;
import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.DocumentRepository;
import it.abc.musical.repositories.FolderRepository;
import it.abc.musical.repositories.RoleRepository;
import it.abc.musical.util.AuthUtil;
import it.abc.musical.util.FileTypeUtil;
import it.abc.musical.util.RoleCsv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaService {

    private final FolderRepository folderRepository;
    private final DocumentRepository documentRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final StorageService storageService;
    private final FileValidationService fileValidationService;

    // ------------------------------------------------------------------ alberi

    /** Albero completo per l'admin. */
    @Transactional(readOnly = true)
    public MediaTreeDto adminTree() {
        return buildTree(null);
    }

    /** Albero filtrato per i soci: solo cio' che passa canAccess. */
    @Transactional(readOnly = true)
    public MediaTreeDto memberTree(Set<String> userRoles) {
        return buildTree(userRoles);
    }

    private MediaTreeDto buildTree(Set<String> userRoles) {
        List<Folder> folders = folderRepository.findByDeletedAtIsNullOrderByNameAsc();
        List<Document> documents = documentRepository.findByDeletedAtIsNullOrderByTitleAsc();

        Map<Long, FolderNodeDto> nodes = new HashMap<>();
        for (Folder folder : folders) {
            if (canAccess(folder, userRoles)) {
                nodes.put(folder.getId(), FolderNodeDto.of(folder));
            }
        }

        MediaTreeDto tree = new MediaTreeDto(new ArrayList<>(), new ArrayList<>());
        for (Folder folder : folders) {
            FolderNodeDto node = nodes.get(folder.getId());
            if (node == null) {
                continue;
            }
            if (folder.getParentFolder() == null) {
                tree.folders().add(node);
            } else {
                // canAccess(folder) implica canAccess(padre): il padre e' sempre tra i nodi.
                nodes.get(folder.getParentFolder().getId()).children().add(node);
            }
        }

        for (Document document : documents) {
            if (!canAccess(document, userRoles)) {
                continue;
            }
            if (document.getFolder() == null) {
                tree.rootDocuments().add(DocumentDto.from(document));
            } else {
                nodes.get(document.getFolder().getId()).documents().add(DocumentDto.from(document));
            }
        }
        return tree;
    }

    /**
     * Unica regola di accesso dell'archivio, usata dall'albero e dal download: la cartella e
     * TUTTE le cartelle sopra di lei devono essere attive e ammettere uno dei ruoli dell'utente
     * (nessun ruolo = tutti i soci). userRoles null = vista admin, conta solo che siano attive.
     */
    private boolean canAccess(Folder folder, Set<String> userRoles) {
        for (Folder current = folder; current != null; current = current.getParentFolder()) {
            if (current.getDeletedAt() != null) {
                return false;
            }
            if (userRoles != null && !AuthUtil.matchesRoles(current.getAllowedRoles(), userRoles)) {
                return false;
            }
        }
        return true;
    }

    /** Un documento in radice e' visibile a tutti i soci; altrimenti vale la regola della sua cartella. */
    private boolean canAccess(Document document, Set<String> userRoles) {
        return document.getFolder() == null || canAccess(document.getFolder(), userRoles);
    }

    // ------------------------------------------------------------------ documenti

    @Transactional(readOnly = true)
    public Document byUuidForRoles(UUID uuid, Set<String> userRoles) {
        return documentRepository.findByUuidAndDeletedAtIsNull(uuid)
                .filter(d -> canAccess(d, userRoles))
                .orElseThrow(() -> new NotFoundException("File non trovato"));
    }

    /**
     * Un accesso al file del socio: contatore dei download e riga nel registro attivita'.
     * Chi chiama decide cosa e' un accesso nuovo (la prima richiesta, non i Range successivi).
     */
    @Transactional
    public void recordDownload(Long documentId) {
        documentRepository.findById(documentId).ifPresent(d -> {
            d.setDownloadCount((d.getDownloadCount() != null ? d.getDownloadCount() : 0) + 1);
            documentRepository.save(d);
        });
        auditLogService.record("DOWNLOAD", "Document", documentId);
    }

    @Transactional
    public DocumentDto attachDocument(DocumentAttachRequest request, Long userId) {
        storageService.validateManagedPath(request.filePath(), UploadTargetType.MEDIA_DOCUMENT);
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
        return activeFolder(id).getAllowedRoles().stream().sorted().toList();
    }

    @Transactional
    public void setFolderPermissions(Long id, List<String> allowedRoles) {
        Folder folder = activeFolder(id);
        List<String> cleaned = RoleCsv.parse(RoleCsv.format(allowedRoles));
        for (String role : cleaned) {
            if (roleRepository.findByName(role).isEmpty()) {
                throw new BadRequestException("Ruolo non valido: " + role);
            }
        }
        folder.getAllowedRoles().clear();
        folder.getAllowedRoles().addAll(cleaned);
        folderRepository.save(folder);
        auditLogService.record("PERMISSIONS", "Folder", id);
    }

    private Folder activeFolder(Long id) {
        return folderRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Cartella non trovata"));
    }
}
