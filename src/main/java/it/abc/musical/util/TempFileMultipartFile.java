package it.abc.musical.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Adatta un file già presente su disco (es. upload a chunk riassemblato) all'interfaccia
 * MultipartFile, per riusare invariata la logica esistente di validazione/storage.
 */
public class TempFileMultipartFile implements MultipartFile {

    private final File file;
    private final String originalFilename;
    private final String contentType;

    public TempFileMultipartFile(File file, String originalFilename, String contentType) {
        this.file = file;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
    }

    @Override
    public String getName() {
        return originalFilename;
    }

    @Override
    public String getOriginalFilename() {
        return originalFilename;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public boolean isEmpty() {
        return file.length() == 0;
    }

    @Override
    public long getSize() {
        return file.length();
    }

    @Override
    public byte[] getBytes() throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return new FileInputStream(file);
    }

    @Override
    public void transferTo(File dest) throws IOException {
        Files.copy(file.toPath(), dest.toPath());
    }
}
