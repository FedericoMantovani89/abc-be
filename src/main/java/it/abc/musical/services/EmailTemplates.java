package it.abc.musical.services;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import it.abc.musical.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modelli FreeMarker delle email. Si cercano prima nella cartella esterna (app.mail.templates-dir,
 * di solito /config/email) e poi nel pacchetto (resources/email): un modello con lo stesso nome
 * messo fuori ha la precedenza, senza nuova versione. Le estensioni .ftlh escapano l'HTML da sole;
 * i modelli .ftl (solo testo) non escapano nulla.
 */
@Slf4j
@Component
public class EmailTemplates {

    private final Configuration configuration;

    public EmailTemplates(AppProperties props) {
        this.configuration = configuration(Path.of(props.getMail().getTemplatesDir()));
    }

    static Configuration configuration(Path externalDir) {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_32);
        List<TemplateLoader> loaders = new ArrayList<>();
        if (Files.isDirectory(externalDir)) {
            try {
                loaders.add(new FileTemplateLoader(externalDir.toFile()));
                log.info("Modelli email esterni: {}", externalDir.toAbsolutePath());
            } catch (IOException e) {
                throw new UncheckedIOException("Cartella dei modelli email non leggibile: " + externalDir, e);
            }
        }
        loaders.add(new ClassTemplateLoader(EmailTemplates.class, "/email"));
        cfg.setTemplateLoader(new MultiTemplateLoader(loaders.toArray(new TemplateLoader[0])));
        cfg.setDefaultEncoding(StandardCharsets.UTF_8.name());
        cfg.setOutputEncoding(StandardCharsets.UTF_8.name());
        cfg.setLocalizedLookup(false);
        cfg.setNumberFormat("computer");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        return cfg;
    }

    /** Compone il modello {@code name} (es. "verifica.ftlh") con i valori di {@code model}. */
    public String render(String name, Map<String, Object> model) {
        try (StringWriter out = new StringWriter()) {
            configuration.getTemplate(name).process(model, out);
            return out.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Modello email non leggibile: " + name, e);
        } catch (TemplateException e) {
            throw new IllegalStateException("Modello email non valido: " + name + ": " + e.getMessage(), e);
        }
    }
}
