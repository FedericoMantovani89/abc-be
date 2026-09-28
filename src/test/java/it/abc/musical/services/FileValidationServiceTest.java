package it.abc.musical.services;

import it.abc.musical.enums.UploadTargetType;
import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.enums.MediaFileType;
import it.abc.musical.util.FileTypeUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static it.abc.musical.TestFixtures.zipBytes;
import static it.abc.musical.TestFixtures.zipDeclaringHugeEntry;
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

    @ParameterizedTest
    @ValueSource(strings = {"verbale.doc", "bilancio.xls", "costumi.rar"})
    void oldOfficeAndRarAreNoLongerAccepted(String filename) {
        var file = new MockMultipartFile("file", filename, "application/octet-stream", new byte[]{1, 2, 3});

        assertThatThrownBy(() -> service.validate(file, UploadTargetType.MEDIA_DOCUMENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Tipo di file ." + filename.substring(filename.lastIndexOf('.') + 1)
                        + " non consentito")
                .hasMessageNotContaining(".doc,")
                .hasMessageContaining(".docx");
    }

    @Test
    void documentsAlreadyStoredAsDocKeepTheirMediaType() {
        assertThat(FileTypeUtil.mediaTypeOf("doc")).isEqualTo(MediaFileType.DOCUMENT);
    }

    @Test
    void executableRenamedAsPdfIsRejected() {
        var file = new MockMultipartFile("file", "innocuo.pdf", "application/pdf", EXE_BYTES);

        assertThatThrownBy(() -> service.validate(file, UploadTargetType.MEDIA_DOCUMENT))
                .isInstanceOf(BadRequestException.class);
    }

    // ------------------------------------------------------------------ ZIP

    private String validateZip(byte[] zip) {
        return service.validate(new MockMultipartFile("file", "materiale.zip", "application/zip", zip),
                UploadTargetType.MEDIA_DOCUMENT);
    }

    @Test
    void cleanZipIsAccepted() {
        assertThat(validateZip(zipBytes("copioni/", "copioni/atto1.pdf", "foto.jpg", "spartito.docx")))
                .isEqualTo("application/zip");
    }

    @ParameterizedTest
    @ValueSource(strings = {"setup.exe", "cartella/Virus.EXE", "trucco.exe. ", "avvio.bat", "macro.xlsm",
            "presentazione.pptm", "script.ps1", "collegamento.lnk", "app.jar", "pagina.hta", "lib.dll"})
    void zipWithExecutableOrMacroIsRejectedNamingTheFile(String inner) {
        assertThatThrownBy(() -> validateZip(zipBytes("leggimi.txt", inner)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'archivio ZIP contiene «" + inner + "», un file eseguibile o con macro: "
                        + "non e' ammesso. Toglilo dall'archivio e riprova.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dentro/altro.zip", "backup.rar", "vecchio.7z", "log.tar.gz"})
    void zipInsideZipIsRejected(String inner) {
        assertThatThrownBy(() -> validateZip(zipBytes(inner)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("«" + inner + "», che e' a sua volta un archivio compresso");
    }

    @Test
    void zipWithTooManyEntriesIsRejected() {
        String[] names = new String[2001];
        for (int i = 0; i < names.length; i++) {
            names[i] = "f" + i + ".txt";
        }
        assertThatThrownBy(() -> validateZip(zipBytes(names)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'archivio ZIP contiene piu' di 2000 file: dividilo in archivi piu' piccoli.");
        assertThat(validateZip(zipBytes(java.util.Arrays.copyOf(names, 2000)))).isEqualTo("application/zip");
    }

    @Test
    void zipBombDeclaringMoreThan2GbIsRejected() {
        assertThatThrownBy(() -> validateZip(zipDeclaringHugeEntry()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'archivio ZIP una volta estratto supera i 2 GB: dividilo in archivi piu' piccoli.");
    }

    @Test
    void truncatedZipIsRejected() {
        byte[] zip = zipBytes("a.txt");
        assertThatThrownBy(() -> validateZip(java.util.Arrays.copyOf(zip, zip.length - 10)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("L'archivio ZIP e' illeggibile o danneggiato.");
    }

    @Test
    void oversizedImageIsRejected() {
        var file = new MockMultipartFile("file", "grande.png", "image/png", new byte[6 * 1024 * 1024]);

        assertThatThrownBy(() -> service.validate(file, UploadTargetType.MEDIA_DOCUMENT))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("troppo grande");
    }
}
