package it.abc.musical;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Dati finti condivisi fra test di unita' e di integrazione. */
public final class TestFixtures {

    private TestFixtures() {
    }

    /** Contenuto che supera il controllo Tika di "e' davvero un JPEG" (magic bytes FF D8 FF). */
    public static byte[] fakeJpegBytes(int size) {
        byte[] content = new byte[size];
        content[0] = (byte) 0xFF;
        content[1] = (byte) 0xD8;
        content[2] = (byte) 0xFF;
        return content;
    }

    /** ZIP vero con una voce per nome (le voci che finiscono con "/" sono cartelle). */
    public static byte[] zipBytes(String... entryNames) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String name : entryNames) {
                zip.putNextEntry(new ZipEntry(name));
                if (!name.endsWith("/")) {
                    zip.write(("contenuto di " + name).getBytes(StandardCharsets.UTF_8));
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    /**
     * Zip bomb "dichiarato": ZIP piccolo con una sola voce, ma nell'indice (central directory)
     * la dimensione decompressa della voce dice ~4 GB. E' il numero che il controllo legge.
     */
    public static byte[] zipDeclaringHugeEntry() {
        byte[] zip = zipBytes("filmato.mp4");
        for (int i = zip.length - 4; i >= 0; i--) {
            if (zip[i] == 'P' && zip[i + 1] == 'K' && zip[i + 2] == 1 && zip[i + 3] == 2) {
                ByteBuffer.wrap(zip, i + 24, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(0xFFFFFFF0);
                return zip;
            }
        }
        throw new IllegalStateException("central directory non trovata");
    }
}
