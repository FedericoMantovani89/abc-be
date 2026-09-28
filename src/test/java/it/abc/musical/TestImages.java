package it.abc.musical;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;

/** Immagini di prova generate in memoria per i test delle anteprime. */
public final class TestImages {

    private TestImages() {
    }

    public static byte[] jpeg(int width, int height) {
        return encode(image(width, height, BufferedImage.TYPE_INT_RGB), "jpg");
    }

    public static byte[] png(int width, int height) {
        return encode(image(width, height, BufferedImage.TYPE_INT_ARGB), "png");
    }

    /**
     * JPEG con un segmento APP1 Exif che contiene solo il tag Orientation (0x0112): 6 = da ruotare
     * di 90 gradi in senso orario, come le foto scattate col telefono in verticale.
     */
    public static byte[] jpegWithOrientation(int width, int height, int orientation) {
        byte[] jpeg = jpeg(width, height);
        ByteBuffer tiff = ByteBuffer.allocate(26);
        tiff.put(new byte[] {'M', 'M', 0, 42}).putInt(8)       // intestazione TIFF big-endian, IFD a 8
                .putShort((short) 1)                              // una voce
                .putShort((short) 0x0112).putShort((short) 3).putInt(1) // Orientation, SHORT, 1 valore
                .putShort((short) orientation).putShort((short) 0)
                .putInt(0);                                       // nessun IFD successivo
        byte[] exifHeader = {'E', 'x', 'i', 'f', 0, 0};
        int segmentLength = 2 + exifHeader.length + tiff.capacity();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);                                    // SOI
        out.write(0xFF);
        out.write(0xE1);
        out.write(segmentLength >> 8);
        out.write(segmentLength & 0xFF);
        out.writeBytes(exifHeader);
        out.writeBytes(tiff.array());
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    public static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage image(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.ORANGE);
        g.fillRect(0, 0, width, height);
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, width / 2, height / 2);
        g.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, format, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
