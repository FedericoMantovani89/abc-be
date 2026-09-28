package it.abc.musical.dto;

import it.abc.musical.entities.Document;
import it.abc.musical.entities.Folder;
import it.abc.musical.enums.MediaFileType;
import it.abc.musical.util.RoleCsv;
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
            Long id, UUID uuid, Long folderId, String title,
            String fileName, Long fileSizeBytes, String mimeType, MediaFileType mediaType,
            Integer downloadCount) {

        public static DocumentDto from(Document d) {
            return new DocumentDto(d.getId(), d.getUuid(),
                    d.getFolder() != null ? d.getFolder().getId() : null,
                    d.getTitle(), d.getFileName(), d.getFileSizeBytes(),
                    d.getMimeType(), d.getMediaType(), d.getDownloadCount());
        }
    }

    /**
     * Aggancio di un file caricato a pezzi. originalFilename, mimeType e fileSizeBytes sono
     * facoltativi e ignorati: il nome viene dalla sessione di caricamento, MIME e dimensione dal
     * file su disco (restano per non rompere chi li manda).
     */
    public record DocumentAttachRequest(
            @NotBlank String filePath,
            @Size(max = 255) String originalFilename,
            String mimeType,
            Long fileSizeBytes,
            Long folderId,
            @Size(max = 255) String title) {
    }

    /** Nodo dell'albero cartelle: figli e documenti diretti. allowedRoles come testo "A,B" (null = tutti). */
    public record FolderNodeDto(
            Long id, String name, Long parentFolderId, String allowedRoles,
            List<FolderNodeDto> children, List<DocumentDto> documents) {

        public static FolderNodeDto of(Folder f) {
            return new FolderNodeDto(f.getId(), f.getName(),
                    f.getParentFolder() != null ? f.getParentFolder().getId() : null,
                    RoleCsv.format(f.getAllowedRoles()), new ArrayList<>(), new ArrayList<>());
        }
    }

    /** Radice dell'albero: cartelle top-level + documenti senza cartella. */
    public record MediaTreeDto(List<FolderNodeDto> folders, List<DocumentDto> rootDocuments) {
    }

    public record FolderCreateRequest(@NotBlank @Size(max = 255) String name, Long parentFolderId) {
    }

    /** Spostamento di un documento: folderId null = radice. */
    public record DocumentMoveRequest(Long folderId) {
    }

    /** Spostamento di una cartella: parentFolderId null = radice. */
    public record FolderMoveRequest(Long parentFolderId) {
    }

    /** Nuovo titolo mostrato del documento: fileName ed estensione restano come sono. */
    public record DocumentRenameRequest(@NotBlank @Size(max = 255) String title) {
    }

    /** Nuovo nome della cartella: stessa validazione della creazione. */
    public record FolderRenameRequest(@NotBlank @Size(max = 255) String name) {
    }

    public record FolderPermissionsDto(List<String> allowedRoles) {
    }

    public record FolderPermissionsRequest(@NotNull List<String> allowedRoles) {
    }
}
