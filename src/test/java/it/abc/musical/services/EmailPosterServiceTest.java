package it.abc.musical.services;

import it.abc.musical.exceptions.BadRequestException;
import it.abc.musical.repositories.ShowRepository;
import it.abc.musical.services.EmailPosterService.Variant;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Scelta a caso dello spettacolo e disegno della locandina sfumata. */
class EmailPosterServiceTest {

    private final ShowRepository showRepository = mock(ShowRepository.class);
    private final EmailPosterService service = new EmailPosterService(showRepository, mock(StorageService.class));

    @Test
    void noShowWithPosterMeansNoImage() {
        when(showRepository.findIdsWithPosterAndNotDeleted()).thenReturn(List.of());

        assertThat(service.randomShowId()).isEmpty();
    }

    @Test
    void oneShowIsAlwaysTheOne() {
        when(showRepository.findIdsWithPosterAndNotDeleted()).thenReturn(List.of(42L));

        for (int i = 0; i < 20; i++) {
            assertThat(service.randomShowId()).contains(42L);
        }
    }

    @Test
    void manyShowsAreAllPickedSoonerOrLater() {
        List<Long> ids = List.of(1L, 2L, 3L, 4L);
        when(showRepository.findIdsWithPosterAndNotDeleted()).thenReturn(ids);

        Set<Long> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            Optional<Long> id = service.randomShowId();
            assertThat(id).isPresent();
            assertThat(ids).contains(id.get());
            seen.add(id.get());
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void variantParameterIsSideOrBand() {
        assertThat(Variant.parse("side")).isEqualTo(Variant.SIDE);
        assertThat(Variant.parse("BAND")).isEqualTo(Variant.BAND);
        assertThatThrownBy(() -> Variant.parse("../../etc")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> Variant.parse("")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void sideFadesToTheHeaderColourOnTheLeftAndAtTheBottom() throws Exception {
        BufferedImage out = EmailPosterService.render(solid(300, 400, Color.WHITE), Variant.SIDE);

        assertThat(out.getWidth()).isEqualTo(440);
        assertThat(out.getHeight()).isEqualTo(586);
        assertThat(rgb(out, 0, 200)).isEqualTo(0x0F0F23);        // bordo sinistro: tutto testata
        assertThat(rgb(out, 439, 585)).isEqualTo(0x0F0F23);      // ultima riga: tutto testata
        assertThat(rgb(out, 400, 200)).isEqualTo(0xFFFFFF);      // destra, a meta' altezza: locandina piena
        assertThat(rgb(out, 439, 0)).isEqualTo(0xF1F0F7);        // angolo in alto a destra arrotondato
        assertThat(rgb(out, 0, 0)).isEqualTo(0x0F0F23);          // angolo in alto a sinistra no
    }

    @Test
    void bandFadesDownAndRoundsBothTopCorners() throws Exception {
        BufferedImage out = EmailPosterService.render(solid(1600, 2139, Color.WHITE), Variant.BAND);

        assertThat(out.getWidth()).isEqualTo(702);
        assertThat(out.getHeight()).isEqualTo(260);
        assertThat(rgb(out, 350, 20)).isEqualTo(0xFFFFFF);
        assertThat(rgb(out, 350, 259)).isEqualTo(0x0F0F23);
        assertThat(rgb(out, 0, 0)).isEqualTo(0xF1F0F7);
        assertThat(rgb(out, 701, 0)).isEqualTo(0xF1F0F7);
    }

    private static BufferedImage solid(int w, int h, Color color) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }

    private static int rgb(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) & 0xFFFFFF;
    }
}
