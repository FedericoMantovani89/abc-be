package it.abc.musical.controllers.api;

import it.abc.musical.dto.AdminEventDtos.EventUpsertRequest;
import it.abc.musical.dto.EventDtos.EventDetailDto;
import it.abc.musical.dto.EventDtos.EventSummaryDto;
import it.abc.musical.entities.Event;
import it.abc.musical.services.AuditLogService;
import it.abc.musical.services.EventService;
import it.abc.musical.util.AuthUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class AdminEventsController {

    private final EventService eventService;
    private final AuditLogService auditLogService;

    @GetMapping
    public List<EventSummaryDto> list(@RequestParam(required = false) String status) {
        return eventService.listAdmin(status);
    }

    @GetMapping("/types")
    public List<EventService.EventTypeDto> types() {
        return eventService.types();
    }

    @GetMapping("/{id}")
    public EventDetailDto detail(@PathVariable Long id) {
        return eventService.getAdminDetail(id);
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Long>> create(
            @Valid @RequestPart("event") EventUpsertRequest request,
            @RequestPart(value = "poster", required = false) MultipartFile poster,
            Authentication authentication) {
        Event event = eventService.create(request, poster, AuthUtil.userId(authentication));
        auditLogService.record("CREATE", "Event", event.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", event.getId()));
    }

    @PutMapping(value = "/{id}", consumes = "multipart/form-data")
    public Map<String, Long> update(
            @PathVariable Long id,
            @Valid @RequestPart("event") EventUpsertRequest request,
            @RequestPart(value = "poster", required = false) MultipartFile poster,
            Authentication authentication) {
        Event event = eventService.update(id, request, poster, AuthUtil.userId(authentication));
        auditLogService.record("UPDATE", "Event", event.getId());
        return Map.of("id", event.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        eventService.softDelete(id);
        auditLogService.record("DELETE", "Event", id);
        return ResponseEntity.noContent().build();
    }
}
