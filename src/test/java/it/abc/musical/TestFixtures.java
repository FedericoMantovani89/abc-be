package it.abc.musical;

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
}
