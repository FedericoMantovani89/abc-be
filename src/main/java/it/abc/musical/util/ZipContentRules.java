package it.abc.musical.util;

import it.abc.musical.exceptions.BadRequestException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Cosa puo' stare dentro uno ZIP caricato nell'archivio: niente eseguibili, niente documenti con
 * macro, niente archivi dentro l'archivio, e limiti contro gli zip bomb (numero di voci e
 * dimensione decompressa dichiarata nell'indice dello ZIP). Si legge solo l'indice, non si
 * estrae nulla.
 */
public final class ZipContentRules {

    public static final int MAX_ENTRIES = 2000;
    public static final long MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024;

    private static final Set<String> EXECUTABLE_OR_MACRO = Set.of(
            "exe", "dll", "bat", "cmd", "com", "scr", "msi", "msp", "ps1", "psm1", "vbs", "vbe",
            "js", "jse", "wsf", "wsh", "jar", "sh", "lnk", "hta", "pif", "cpl", "reg", "apk",
            "docm", "dotm", "xlsm", "xltm", "xlam", "pptm", "potm", "ppsm", "ppam", "sldm");

    private static final Set<String> ARCHIVES = Set.of(
            "zip", "zipx", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz2", "xz", "txz", "lz",
            "lzma", "z", "cab", "iso", "arj");

    /**
     * I nomi senza il flag UTF-8 (es. "Invia a > Cartella compressa" di Windows) sono in CP437:
     * con questa codifica ogni byte si decodifica, quelli col flag restano comunque UTF-8.
     */
    private static final Charset LEGACY_ZIP_NAMES = Charset.forName("IBM437");

    private ZipContentRules() {
    }

    public static void check(Path zip) {
        try (ZipFile zipFile = new ZipFile(zip.toFile(), LEGACY_ZIP_NAMES)) {
            if (zipFile.size() > MAX_ENTRIES) {
                throw new BadRequestException("zip.troppi.file", String.valueOf(MAX_ENTRIES));
            }
            long totalBytes = 0;
            for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements(); ) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    checkName(entry.getName());
                }
                long size = entry.getSize();
                if (size < 0) {
                    throw new BadRequestException("zip.dimensione.non.dichiarata", entry.getName());
                }
                totalBytes += size;
                if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                    throw new BadRequestException("zip.troppo.grande");
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new BadRequestException("zip.illeggibile");
        }
    }

    private static void checkName(String name) {
        String extension = extensionOf(name);
        if (EXECUTABLE_OR_MACRO.contains(extension)) {
            throw new BadRequestException("zip.eseguibile", name);
        }
        if (ARCHIVES.contains(extension)) {
            throw new BadRequestException("zip.archivio.annidato", name);
        }
    }

    /** Estensione dell'ultimo pezzo del percorso; Windows ignora punti e spazi finali ("x.exe." e' un .exe). */
    static String extensionOf(String entryName) {
        String base = entryName.substring(Math.max(entryName.lastIndexOf('/'), entryName.lastIndexOf('\\')) + 1)
                .replaceAll("[. ]+$", "");
        int dot = base.lastIndexOf('.');
        return dot < 0 ? "" : base.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
