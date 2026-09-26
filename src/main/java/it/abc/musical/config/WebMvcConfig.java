package it.abc.musical.config;

import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.services.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Espone come risorse statiche le cartelle di upload pubbliche (locandine, galleria), dalla
 * stessa radice di StorageService. Le cartelle non pubbliche (media/) restano fuori.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final StorageService storageService;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        for (UploadTargetType target : UploadTargetType.values()) {
            if (target.publiclyServed()) {
                String location = storageService.getRoot().resolve(target.subdir()).toUri().toString();
                registry.addResourceHandler("/" + target.subdir() + "/**")
                        .addResourceLocations(location.endsWith("/") ? location : location + "/");
            }
        }
    }
}
