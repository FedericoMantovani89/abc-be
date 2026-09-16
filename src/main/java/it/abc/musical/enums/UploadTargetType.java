package it.abc.musical.enums;

import it.abc.musical.services.StorageService;
import it.abc.musical.util.FileTypeUtil.Category;

import java.util.EnumSet;
import java.util.Set;

/** Destinazione di un upload a chunk: sottocartella di storage e categorie di file ammesse. */
public enum UploadTargetType {
    SHOW_POSTER(StorageService.POSTERS_DIR, EnumSet.of(Category.IMAGE)),
    SHOW_GALLERY_IMAGE(StorageService.GALLERY_DIR, EnumSet.of(Category.IMAGE)),
    MEDIA_DOCUMENT(StorageService.MEDIA_DIR, EnumSet.allOf(Category.class));

    private final String subdir;
    private final Set<Category> allowedCategories;

    UploadTargetType(String subdir, Set<Category> allowedCategories) {
        this.subdir = subdir;
        this.allowedCategories = allowedCategories;
    }

    public String subdir() {
        return subdir;
    }

    public Set<Category> allowedCategories() {
        return allowedCategories;
    }
}
