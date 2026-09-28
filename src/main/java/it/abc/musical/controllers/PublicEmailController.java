package it.abc.musical.controllers;

import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.services.EmailPosterService;
import it.abc.musical.services.EmailPosterService.Variant;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Immagini delle email automatiche, senza login: i programmi di posta (e il proxy di Gmail) le
 * scaricano senza credenziali. Cache pubblica lunga perche' Gmail le tiene nel suo proxy.
 */
@RestController
@RequestMapping("/api/public/email")
@RequiredArgsConstructor
public class PublicEmailController {

    private static final CacheControl CACHE = CacheControl.maxAge(30, TimeUnit.DAYS).cachePublic();

    private final EmailPosterService emailPosterService;

    /** v=side: colonna a destra della testata; v=band: fascia in alto sul telefono. */
    @GetMapping("/poster.jpg")
    public ResponseEntity<Resource> poster(@RequestParam("show") long showId, @RequestParam("v") String variant) {
        Variant parsed = Variant.parse(variant);
        return emailPosterService.poster(showId, parsed)
                .map(file -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_JPEG)
                        .cacheControl(CACHE)
                        .body((Resource) new FileSystemResource(file)))
                .orElseThrow(() -> new NotFoundException("Locandina non disponibile"));
    }
}
