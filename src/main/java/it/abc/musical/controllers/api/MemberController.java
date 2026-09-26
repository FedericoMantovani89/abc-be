package it.abc.musical.controllers.api;

import it.abc.musical.dto.CalendarDtos.CalendarEventDto;
import it.abc.musical.dto.CommunicationDtos.CommunicationDto;
import it.abc.musical.dto.MediaDtos.MediaTreeDto;
import it.abc.musical.services.CalendarService;
import it.abc.musical.services.CommunicationService;
import it.abc.musical.services.MediaService;
import it.abc.musical.util.AuthUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/member")
@RequiredArgsConstructor
public class MemberController {

    private final CalendarService calendarService;
    private final CommunicationService communicationService;
    private final MediaService mediaService;

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
}
