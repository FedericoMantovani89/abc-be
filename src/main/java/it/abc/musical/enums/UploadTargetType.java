package it.abc.musical.enums;

import it.abc.musical.util.FileTypeUtil.Category;

import java.util.EnumSet;
import java.util.Set;

/**
 * Destinazione di un file caricato: sottocartella di storage, categorie di file ammesse e se la
 * cartella e' servita pubblicamente come risorsa statica. Aggiungere una destinazione qui basta
 * a StorageService (crea la cartella) e WebMvcConfig (la serve, se pubblica).
 */
public enum UploadTargetType {
    /** Locandine di spettacoli ed eventi: stessa cartella, stesse regole. */
    SHOW_POSTER("posters", EnumSet.of(Category.IMAGE), true),
    SHOW_GALLERY_IMAGE("show_gallery", EnumSet.of(Category.IMAGE), true),
    /** Archivio documenti: MAI pubblico, esce solo da MemberFileController che controlla i permessi. */
    MEDIA_DOCUMENT("media", EnumSet.allOf(Category.class), false);

    private final String subdir;
    private final Set<Category> allowedCategories;
    private final boolean publiclyServed;

    UploadTargetType(String subdir, Set<Category> allowedCategories, boolean publiclyServed) {
        this.subdir = subdir;
        this.allowedCategories = allowedCategories;
        this.publiclyServed = publiclyServed;
    }

    public String subdir() {
        return subdir;
    }

    public Set<Category> allowedCategories() {
        return allowedCategories;
    }

    public boolean publiclyServed() {
        return publiclyServed;
    }
}
