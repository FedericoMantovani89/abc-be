package it.abc.musical.controllers;

import it.abc.musical.dto.EventDtos.EventDetailDto;
import it.abc.musical.dto.EventDtos.EventSummaryDto;
import it.abc.musical.services.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/events")
@RequiredArgsConstructor
public class PublicEventsController {

    private final EventService eventService;

    @GetMapping
    public List<EventSummaryDto> list() {
        return eventService.listPublic();
    }

    @GetMapping("/{id}")
    public EventDetailDto detail(@PathVariable Long id) {
        return eventService.getPublicDetail(id);
    }
}
