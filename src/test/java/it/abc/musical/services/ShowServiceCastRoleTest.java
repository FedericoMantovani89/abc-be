package it.abc.musical.services;

import it.abc.musical.dto.AdminShowDtos.CastMemberRequest;
import it.abc.musical.dto.AdminShowDtos.ShowUpsertRequest;
import it.abc.musical.entities.Show;
import it.abc.musical.repositories.ShowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Normalizzazione dei ruoli di scena al salvataggio del cast: stessa grafia riusata a
 * meno di maiuscole/minuscole (niente più "corpo di ballo" / "Corpo di ballo" separati),
 * e l'endpoint dei ruoli per l'area admin che ne raggruppa le grafie residue.
 */
class ShowServiceCastRoleTest {

    private ShowRepository showRepository;
    private ShowService service;

    @BeforeEach
    void setUp() {
        showRepository = mock(ShowRepository.class);
        StorageService storageService = mock(StorageService.class);
        service = new ShowService(showRepository, storageService);
        when(showRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static ShowUpsertRequest requestWithCast(List<CastMemberRequest> cast) {
        return new ShowUpsertRequest(
                "Il Piccolo Principe", null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                cast, null, null, null, null);
    }

    /** Caso richiesto dal referto: salvando "corpo di BALLO" deve finire come "Corpo di ballo". */
    @Test
    void reusesTheSpellingAlreadyUsedElsewhereIgnoringCase() {
        // Un altro spettacolo ha già la grafia corretta in uso 3 volte.
        when(showRepository.findCastRoleNamesExcludingShow(anyLong()))
                .thenReturn(List.of("Corpo di ballo", "Corpo di ballo", "Corpo di ballo"));

        Show show = new Show();
        ShowUpsertRequest request = requestWithCast(List.of(
                new CastMemberRequest("Giulia", "Bianchi", "corpo di BALLO", null)));

        Show saved = service.create(request, 1L);

        assertThat(saved.getCast()).hasSize(1);
        assertThat(saved.getCast().get(0).getRoleName()).isEqualTo("Corpo di ballo");
    }

    @Test
    void picksTheMostFrequentSpellingWhenSeveralExistElsewhere() {
        when(showRepository.findCastRoleNamesExcludingShow(anyLong()))
                .thenReturn(List.of("Corpo di ballo", "Corpo di ballo", "corpo di ballo"));

        Show show = new Show();
        ShowUpsertRequest request = requestWithCast(List.of(
                new CastMemberRequest("Giulia", "Bianchi", "CORPO DI BALLO", null)));

        Show saved = service.create(request, 1L);

        assertThat(saved.getCast().get(0).getRoleName()).isEqualTo("Corpo di ballo");
    }

    @Test
    void trimsLeadingTrailingAndDoubleInnerSpaces() {
        when(showRepository.findCastRoleNamesExcludingShow(anyLong())).thenReturn(List.of());

        ShowUpsertRequest request = requestWithCast(List.of(
                new CastMemberRequest("Marco", "Verdi", "  Il   Principe  ", null)));

        Show saved = service.create(request, 1L);

        assertThat(saved.getCast().get(0).getRoleName()).isEqualTo("Il Principe");
    }

    @Test
    void keepsTheReadableSpellingWhenNothingExistsYet() {
        when(showRepository.findCastRoleNamesExcludingShow(anyLong())).thenReturn(List.of());

        ShowUpsertRequest request = requestWithCast(List.of(
                new CastMemberRequest("Marco", "Verdi", "Il Principe", null)));

        Show saved = service.create(request, 1L);

        // Niente maiuscolo forzato: la grafia leggibile scelta da Federico resta tale.
        assertThat(saved.getCast().get(0).getRoleName()).isEqualTo("Il Principe");
    }

    @Test
    void withinTheSameSaveTheFirstOccurrenceSetsTheSpellingForTheRest() {
        when(showRepository.findCastRoleNamesExcludingShow(anyLong())).thenReturn(List.of());

        ShowUpsertRequest request = requestWithCast(List.of(
                new CastMemberRequest("Anna", "Neri", "corpo di ballo", null),
                new CastMemberRequest("Elena", "Bruni", "Corpo Di Ballo", null)));

        Show saved = service.create(request, 1L);

        assertThat(saved.getCast()).extracting(c -> c.getRoleName())
                .containsExactly("corpo di ballo", "corpo di ballo");
    }

    @Test
    void updatingAShowExcludesItsOwnRowsFromTheElsewhereLookup() {
        Show existing = new Show();
        existing.setId(42L);
        when(showRepository.findByIdAndDeletedAtIsNull(42L)).thenReturn(Optional.of(existing));
        when(showRepository.findCastRoleNamesExcludingShow(42L)).thenReturn(List.of("Corpo di ballo"));

        ShowUpsertRequest request = requestWithCast(List.of(
                new CastMemberRequest("Giulia", "Bianchi", "corpo di BALLO", null)));

        Show saved = service.update(42L, request, 1L);

        assertThat(saved.getCast().get(0).getRoleName()).isEqualTo("Corpo di ballo");
    }

    @Test
    void castRolesEndpointCollapsesCaseVariantsIntoOneEntry() {
        when(showRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(new Show()));
        when(showRepository.findCastRoleNames(7L)).thenReturn(List.of(
                "Corpo di ballo", "Corpo di ballo", "corpo di ballo", "Il Principe"));

        List<String> roles = service.castRoles(7L);

        assertThat(roles).containsExactly("Corpo di ballo", "Il Principe");
    }
}
