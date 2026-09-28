package it.abc.musical.util;

import it.abc.musical.exceptions.BadRequestException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Un PDF caricato nell'archivio non deve contenere JavaScript ne' azioni che avviano programmi.
 * <p>
 * Una ricerca di byte non basta: gli oggetti possono stare compressi dentro gli object stream.
 * Qui il file lo apre PDFBox e si visita ogni oggetto raggiungibile dal trailer (catalogo,
 * OpenAction, albero dei nomi, pagine, annotazioni, campi dei moduli, azioni aggiuntive /AA),
 * dizionario per dizionario. Un PDF che PDFBox non riesce ad aprire viene rifiutato: se non lo
 * si puo' leggere non lo si puo' nemmeno verificare.
 */
public final class PdfContentRules {

    static final String JAVASCRIPT_ERROR =
            "Il PDF contiene codice JavaScript: non e' ammesso. Esportalo o stampalo di nuovo come PDF semplice e riprova.";
    static final String LAUNCH_ERROR =
            "Il PDF contiene un'azione che avvia programmi o apre file esterni: non e' ammesso.";
    static final String UNREADABLE_ERROR =
            "Il PDF e' illeggibile, danneggiato o protetto da password: non e' possibile verificarne il contenuto.";

    private static final COSName LAUNCH = COSName.getPDFName("Launch");

    private PdfContentRules() {
    }

    public static void check(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            inspect(document.getDocument().getTrailer());
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) {
                throw bad;
            }
            throw new BadRequestException(UNREADABLE_ERROR);
        }
    }

    private static void inspect(COSDictionary trailer) {
        Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<COSBase> pending = new ArrayDeque<>();
        pending.push(trailer);
        while (!pending.isEmpty()) {
            COSBase current = pending.pop();
            if (current instanceof COSObject indirect) {
                current = indirect.getObject();
            }
            if (current == null || !visited.add(current)) {
                continue;
            }
            if (current instanceof COSDictionary dictionary) {
                checkDictionary(dictionary);
                for (Map.Entry<COSName, COSBase> entry : dictionary.entrySet()) {
                    pending.push(entry.getValue());
                }
            } else if (current instanceof COSArray array) {
                for (COSBase item : array) {
                    pending.push(item);
                }
            }
        }
    }

    private static void checkDictionary(COSDictionary dictionary) {
        if (dictionary.containsKey(COSName.JS) || dictionary.containsKey(COSName.JAVA_SCRIPT)) {
            throw new BadRequestException(JAVASCRIPT_ERROR);
        }
        COSName actionType = dictionary.getCOSName(COSName.S);
        if (COSName.JAVA_SCRIPT.equals(actionType)) {
            throw new BadRequestException(JAVASCRIPT_ERROR);
        }
        if (LAUNCH.equals(actionType)) {
            throw new BadRequestException(LAUNCH_ERROR);
        }
        if (dictionary.getDictionaryObject(COSName.URI) instanceof COSString uri
                && uri.getString().strip().toLowerCase(Locale.ROOT).startsWith("javascript:")) {
            throw new BadRequestException(JAVASCRIPT_ERROR);
        }
    }
}
