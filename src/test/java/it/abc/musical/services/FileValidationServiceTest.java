package it.abc.musical.services;

import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationServiceTest {

    private final FileValidationService service = new FileValidationService();

    private static final byte[] PDF_BYTES = "%PDF-1.4\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 'I', 'H', 'D', 'R'};
    private static final byte[] EXE_BYTES = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};

    @Test
    void validPdfIsAccepted() {
        var file = new MockMultipartFile("file", "partitura.pdf", "application/pdf", PDF_BYTES);

        assertThat(service.validate(file, UploadTargetType.MEDIA_DOCUMENT)).isEqualTo("application/pdf");
    }

    @Test
    void validPngIsAccepted() {
        var file = new MockMultipartFile("file", "foto.png", "image/png", PNG_BYTES);

        assertThat(service.validate(file, UploadTargetType.MEDIA_DOCUMENT)).isEqualTo("image/png");
    }

    @Test
    void disallowedExtensionIsRejected() {
        var file = new MockMultipartFile("file", "script.exe", "application/octet-stream", EXE_BYTES);

        assertThatThrownBy(() -> service.validate(file, UploadTargetType.MEDIA_DOCUMENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("non consentito");
    }

    @Test
    void executableRenamedAsPdfIsRejected() {
        var file = new MockMultipartFile("file", "innocuo.pdf", "application/pdf", EXE_BYTES);

        assertThatThrownBy(() -> service.validate(file, UploadTargetType.MEDIA_DOCUMENT))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void oversizedImageIsRejected() {
        var file = new MockMultipartFile("file", "grande.png", "image/png", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> service.validate(file, UploadTargetType.MEDIA_DOCUMENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("troppo grande");
    }
}
