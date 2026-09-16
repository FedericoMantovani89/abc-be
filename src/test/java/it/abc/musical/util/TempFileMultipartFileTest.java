package it.abc.musical.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TempFileMultipartFileTest {

    @Test
    void exposesUnderlyingFileContentAndMetadata(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("source.txt");
        Files.writeString(file, "contenuto di prova");

        TempFileMultipartFile adapted = new TempFileMultipartFile(file.toFile(), "originale.txt", null);

        assertThat(adapted.getOriginalFilename()).isEqualTo("originale.txt");
        assertThat(adapted.getName()).isEqualTo("originale.txt");
        assertThat(adapted.getSize()).isEqualTo(Files.size(file));
        assertThat(adapted.isEmpty()).isFalse();
        assertThat(new String(adapted.getBytes())).isEqualTo("contenuto di prova");
        try (var in = adapted.getInputStream()) {
            assertThat(new String(in.readAllBytes())).isEqualTo("contenuto di prova");
        }
    }

    @Test
    void transferToCopiesFileContent(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("source.bin");
        Files.write(source, new byte[] {1, 2, 3});
        Path dest = tempDir.resolve("dest.bin");

        TempFileMultipartFile adapted = new TempFileMultipartFile(source.toFile(), "x.bin", null);
        adapted.transferTo(dest.toFile());

        assertThat(Files.readAllBytes(dest)).containsExactly(1, 2, 3);
    }
}
