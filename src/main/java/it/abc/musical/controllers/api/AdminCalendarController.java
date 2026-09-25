package it.abc.musical.controllers.api;

import it.abc.musical.dto.CalendarDtos.CalendarEventDto;
import it.abc.musical.dto.CalendarDtos.CalendarEventTypeDeleteResult;
import it.abc.musical.dto.CalendarDtos.CalendarEventTypeDto;
import it.abc.musical.dto.CalendarDtos.CalendarEventTypeUpsertRequest;
import it.abc.musical.dto.CalendarDtos.CalendarEventUpsertRequest;
import it.abc.musical.services.CalendarService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCalendarController {

    private final CalendarService calendarService;

    @GetMapping("/calendar-events")
    public List<CalendarEventDto> month(@RequestParam int year, @RequestParam int month) {
        return calendarService.monthEvents(year, month, null);
    }

    @GetMapping("/calendar-events/{id}")
    public CalendarEventDto detail(@PathVariable Long id) {
        return calendarService.getDetail(id);
    }

    @PostMapping("/calendar-events")
    public ResponseEntity<CalendarEventDto> create(@Valid @RequestBody CalendarEventUpsertRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(calendarService.create(request, AuthUtil.userId(authentication)));
    }

    @PutMapping("/calendar-events/{id}")
    public CalendarEventDto update(@PathVariable Long id,
                                   @Valid @RequestBody CalendarEventUpsertRequest request,
                                   Authentication authentication) {
        return calendarService.update(id, request, AuthUtil.userId(authentication));
    }

    @DeleteMapping("/calendar-events/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        calendarService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/calendar-event-types")
    public List<CalendarEventTypeDto> activeTypes() {
        return calendarService.activeTypes();
    }

    @GetMapping("/calendar-event-types/all")
    public List<CalendarEventTypeDto> allTypes() {
        return calendarService.allTypes();
    }

    @PostMapping("/calendar-event-types")
    public ResponseEntity<CalendarEventTypeDto> createType(
            @Valid @RequestBody CalendarEventTypeUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(calendarService.createType(request));
    }

    @PutMapping("/calendar-event-types/{id}")
    public CalendarEventTypeDto updateType(@PathVariable Long id,
                                           @Valid @RequestBody CalendarEventTypeUpsertRequest request) {
        return calendarService.updateType(id, request);
    }

    @DeleteMapping("/calendar-event-types/{id}")
    public CalendarEventTypeDeleteResult deleteType(@PathVariable Long id) {
        return calendarService.deleteType(id);
    }
}
