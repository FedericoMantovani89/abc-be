package it.abc.musical.controllers.api;

import it.abc.musical.entities.Document;
import it.abc.musical.services.MediaService;
import it.abc.musical.services.StorageService;
import it.abc.musical.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Stream file area soci con supporto Range (audio/video seek) e download.
 */
@RestController
@RequestMapping("/api/member/file")
@RequiredArgsConstructor
public class MemberFileController {

    /** Dimensione massima di un singolo chunk in risposta a una Range request. */
    private static final long MAX_CHUNK = 1024 * 1024;

    private final MediaService mediaService;
    private final StorageService storageService;

    @GetMapping("/{uuid}")
    public ResponseEntity<?> file(@PathVariable UUID uuid,
                                  @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
                                  @RequestParam(defaultValue = "false") boolean download,
                                  Authentication authentication) {
        Document document = mediaService.byUuidForRoles(uuid, AuthUtil.roles(authentication));
        FileSystemResource resource = new FileSystemResource(storageService.resolve(document.getFilePath()));
        MediaType mediaType = document.getMimeType() != null
                ? MediaType.parseMediaType(document.getMimeType())
                : MediaType.APPLICATION_OCTET_STREAM;

        if (rangeHeader == null) {
            mediaService.recordDownload(document.getId());
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes");
            if (download) {
                // Il nome viene dal client: ContentDisposition lo mette tra virgolette con escape e
                // aggiunge filename* (RFC 5987), cosi' virgolette, accenti o a capo non rompono l'intestazione.
                builder.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getFileName(), StandardCharsets.UTF_8)
                        .build().toString());
            }
            return builder.body(resource);
        }

        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        long contentLength = resource.getFile().length();
        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);
        long rangeLength = Math.min(MAX_CHUNK, end - start + 1);
        // Un lettore audio/video apre il file con "Range: bytes=0-" e poi chiede i pezzi successivi:
        // conta come accesso solo la richiesta che parte dall'inizio.
        if (start == 0) {
            mediaService.recordDownload(document.getId());
        }

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(new ResourceRegion(resource, start, rangeLength));
    }
}
