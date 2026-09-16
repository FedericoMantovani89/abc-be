package it.abc.musical.controllers.api;

import it.abc.musical.dto.CommunicationDtos.CommunicationDto;
import it.abc.musical.dto.CommunicationDtos.CommunicationTypeDto;
import it.abc.musical.dto.CommunicationDtos.CommunicationUpsertRequest;
import it.abc.musical.services.CommunicationService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminCommunicationsController {

    private final CommunicationService communicationService;

    @GetMapping("/communications")
    public List<CommunicationDto> list() {
        return communicationService.listForAdmin();
    }

    @PostMapping("/communications")
    public ResponseEntity<CommunicationDto> create(@Valid @RequestBody CommunicationUpsertRequest request,
                                                   Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(communicationService.create(request, AuthUtil.userId(authentication)));
    }

    @PutMapping("/communications/{id}")
    public CommunicationDto update(@PathVariable Long id,
                                   @Valid @RequestBody CommunicationUpsertRequest request,
                                   Authentication authentication) {
        return communicationService.update(id, request, AuthUtil.userId(authentication));
    }

    @DeleteMapping("/communications/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        communicationService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/communication-types")
    public List<CommunicationTypeDto> types() {
        return communicationService.activeTypes();
    }
}
