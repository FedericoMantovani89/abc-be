package it.abc.musical.services;

import it.abc.musical.dto.CommunicationDtos.CommunicationDto;
import it.abc.musical.dto.CommunicationDtos.CommunicationTypeDto;
import it.abc.musical.dto.CommunicationDtos.CommunicationUpsertRequest;
import it.abc.musical.entities.Communication;
import it.abc.musical.exceptions.NotFoundException;
import it.abc.musical.repositories.CommunicationRepository;
import it.abc.musical.repositories.CommunicationTypeRepository;
import it.abc.musical.util.AuthUtil;
import it.abc.musical.util.RoleCsv;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CommunicationService {

    private final CommunicationRepository communicationRepository;
    private final CommunicationTypeRepository communicationTypeRepository;
    private final AuditLogService auditLogService;

    /** Vista soci: solo pubblicate, non scadute, visibili per ruolo. */
    @Transactional(readOnly = true)
    public List<CommunicationDto> listForMember(Set<String> userRoles) {
        LocalDateTime now = LocalDateTime.now();
        return communicationRepository.findByDeletedAtIsNullOrderByPinnedDescPublishedAtDesc().stream()
                .filter(c -> c.getPublishedAt() == null || !c.getPublishedAt().isAfter(now))
                .filter(c -> c.getExpiresAt() == null || c.getExpiresAt().isAfter(now))
                .filter(c -> AuthUtil.matchesTargetRoles(c.getTargetRoles(), userRoles))
                .map(CommunicationDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunicationDto> listForAdmin() {
        return communicationRepository.findByDeletedAtIsNullOrderByPinnedDescPublishedAtDesc().stream()
                .map(CommunicationDto::from)
                .toList();
    }

    @Transactional
    public CommunicationDto create(CommunicationUpsertRequest request, Long userId) {
        Communication communication = new Communication();
        communication.setCreatedBy(userId);
        applyRequest(communication, request, userId);
        if (communication.getPublishedAt() == null) {
            communication.setPublishedAt(LocalDateTime.now());
        }
        communication = communicationRepository.save(communication);
        auditLogService.record("CREATE", "Communication", communication.getId());
        return CommunicationDto.from(communication);
    }

    @Transactional
    public CommunicationDto update(Long id, CommunicationUpsertRequest request, Long userId) {
        Communication communication = active(id);
        applyRequest(communication, request, userId);
        communication = communicationRepository.save(communication);
        auditLogService.record("UPDATE", "Communication", id);
        return CommunicationDto.from(communication);
    }

    @Transactional
    public void softDelete(Long id) {
        Communication communication = active(id);
        communication.setDeletedAt(LocalDateTime.now());
        communicationRepository.save(communication);
        auditLogService.record("DELETE", "Communication", id);
    }

    @Transactional(readOnly = true)
    public List<CommunicationTypeDto> activeTypes() {
        return communicationTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(CommunicationTypeDto::from)
                .toList();
    }

    private Communication active(Long id) {
        return communicationRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("Comunicazione non trovata"));
    }

    private void applyRequest(Communication communication, CommunicationUpsertRequest request, Long userId) {
        communication.setTitle(request.title().trim());
        communication.setContent(request.content());
        communication.setCommunicationType(request.communicationTypeId() != null
                ? communicationTypeRepository.findById(request.communicationTypeId())
                        .orElseThrow(() -> new NotFoundException("Tipo comunicazione non trovato"))
                : null);
        communication.setPriority(request.priority() != null ? request.priority() : "NORMAL");
        communication.setPinned(Boolean.TRUE.equals(request.pinned()));
        communication.setPublishedAt(request.publishedAt());
        communication.setExpiresAt(request.expiresAt());
        communication.setTargetRoles(RoleCsv.normalize(request.targetRoles()));
        communication.setUpdatedBy(userId);
    }
}
