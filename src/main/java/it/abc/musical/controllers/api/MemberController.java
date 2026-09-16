package it.abc.musical.controllers.api;

import it.abc.musical.dto.CalendarDtos.CalendarEventDto;
import it.abc.musical.dto.CommunicationDtos.CommunicationDto;
import it.abc.musical.dto.MediaDtos.MediaTreeDto;
import it.abc.musical.entities.Document;
import it.abc.musical.repositories.DocumentRepository;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.services.CalendarService;
import it.abc.musical.services.CommunicationService;
import it.abc.musical.services.MediaService;
import it.abc.musical.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final CalendarService calendarService;
    private final CommunicationService communicationService;
    private final MediaService mediaService;
    private final DocumentRepository documentRepository;

    @GetMapping("/calendar")
    public List<CalendarEventDto> calendar(@RequestParam int year, @RequestParam int month,
                                           Authentication authentication) {
        return calendarService.monthEvents(year, month, AuthUtil.roles(authentication));
    }

    @GetMapping("/communications")
    public List<CommunicationDto> communications(Authentication authentication) {
        return communicationService.listForMember(AuthUtil.roles(authentication));
    }

    @GetMapping("/documents")
    public MediaTreeDto documents(Authentication authentication) {
        return mediaService.memberTree(AuthUtil.roles(authentication));
    }

    /** Restituisce l'URL da usare per scaricare/riprodurre il documento. */
    @GetMapping("/documents/{id}/view")
    public Map<String, String> documentView(@PathVariable Long id, Authentication authentication) {
        Document document = documentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("File non trovato"));
        // Il controllo permessi avviene in byUuidForRoles al momento dello stream.
        mediaService.byUuidForRoles(document.getUuid(), AuthUtil.roles(authentication));
        return Map.of("url", "/api/stream/" + document.getUuid());
    }
}
