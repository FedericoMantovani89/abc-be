package it.abc.musical;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDJavascriptNameTreeNode;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.filespecification.PDSimpleFileSpecification;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionLaunch;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.function.Consumer;

/**
 * PDF veri generati con PDFBox per i test. PDFBox 3 salva di default con gli object stream
 * compressi: il JavaScript finisce dentro uno stream Flate, invisibile a una ricerca di byte.
 */
public final class TestPdfs {

    private TestPdfs() {
    }

    public static byte[] clean() {
        return build(document -> {
        });
    }

    /** JavaScript eseguito all'apertura (OpenAction del catalogo). */
    public static byte[] withOpenActionJavaScript() {
        return build(document -> document.getDocumentCatalog()
                .setOpenAction(new PDActionJavaScript("app.alert('ciao')")));
    }

    /** JavaScript a livello di documento, nell'albero dei nomi /Names /JavaScript. */
    public static byte[] withJavaScriptInNameTree() {
        return build(document -> {
            PDJavascriptNameTreeNode scripts = new PDJavascriptNameTreeNode();
            scripts.setNames(Map.of("avvio", new PDActionJavaScript("this.print()")));
            PDDocumentNameDictionary names = new PDDocumentNameDictionary(document.getDocumentCatalog());
            names.setJavascript(scripts);
            document.getDocumentCatalog().setNames(names);
        });
    }

    /** Collegamento sulla pagina che avvia un programma (/S /Launch). */
    public static byte[] withLaunchLink() {
        return build(document -> {
            PDActionLaunch launch = new PDActionLaunch();
            launch.setFile(new PDSimpleFileSpecification(new org.apache.pdfbox.cos.COSString("calc.exe")));
            PDAnnotationLink link = new PDAnnotationLink();
            link.setAction(launch);
            document.getPage(0).setAnnotations(java.util.List.of(link));
        });
    }

    private static byte[] build(Consumer<PDDocument> customize) {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            customize.accept(document);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
