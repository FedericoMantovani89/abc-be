package it.abc.musical.dto;

import it.abc.musical.entities.Document;
import it.abc.musical.entities.Folder;
import it.abc.musical.enums.MediaFileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MediaDtos {

    private MediaDtos() {
    }

    public record DocumentDto(
            Long id, UUID uuid, Long folderId, String title, String description,
            String fileName, Long fileSizeBytes, String mimeType,
            String documentCategory, String visibility, MediaFileType mediaType,
            Integer downloadCount) {

        public static DocumentDto from(Document d) {
            return new DocumentDto(d.getId(), d.getUuid(),
                    d.getFolder() != null ? d.getFolder().getId() : null,
                    d.getTitle(), d.getDescription(), d.getFileName(), d.getFileSizeBytes(),
                    d.getMimeType(), d.getDocumentCategory(), d.getVisibility(),
                    d.getMediaType(), d.getDownloadCount());
        }
    }

    /**
     * Aggancio di un file caricato a pezzi. mimeType e fileSizeBytes sono facoltativi e ignorati:
     * il server li ricava dal file su disco (restano per non rompere chi li manda).
     */
    public record DocumentAttachRequest(
            @NotBlank String filePath,
            @NotBlank @Size(max = 255) String originalFilename,
            String mimeType,
            Long fileSizeBytes,
            Long folderId,
            @Size(max = 255) String title,
            String documentCategory,
            String visibility) {
    }

    /** Nodo dell'albero cartelle: figli e documenti diretti. */
    public record FolderNodeDto(
            Long id, String name, Long parentFolderId, String allowedRoles,
            List<FolderNodeDto> children, List<DocumentDto> documents) {

        public static FolderNodeDto of(Folder f) {
            return new FolderNodeDto(f.getId(), f.getName(),
                    f.getParentFolder() != null ? f.getParentFolder().getId() : null,
                    f.getAllowedRoles(), new ArrayList<>(), new ArrayList<>());
        }
    }

    /** Radice dell'albero: cartelle top-level + documenti senza cartella. */
    public record MediaTreeDto(List<FolderNodeDto> folders, List<DocumentDto> rootDocuments) {
    }

    public record FolderCreateRequest(@NotBlank @Size(max = 255) String name, Long parentFolderId) {
    }

    public record FolderPermissionsDto(List<String> allowedRoles) {
    }

    public record FolderPermissionsRequest(@NotNull List<String> allowedRoles) {
    }
}
