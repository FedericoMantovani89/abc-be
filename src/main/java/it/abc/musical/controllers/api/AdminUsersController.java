package it.abc.musical.controllers.api;

import it.abc.musical.dto.UserDtos.UserAdminDto;
import it.abc.musical.dto.UserDtos.UserRoleRequest;
import it.abc.musical.dto.UserDtos.UserStatusRequest;
import it.abc.musical.services.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUsersController {

    private final AdminUserService adminUserService;

    @GetMapping
    public List<UserAdminDto> list() {
        return adminUserService.list();
    }

    @PutMapping("/{id}/status")
    public UserAdminDto setStatus(@PathVariable Long id, @Valid @RequestBody UserStatusRequest request) {
        return adminUserService.setStatus(id, request.active());
    }

    @PutMapping("/{id}/role")
    public UserAdminDto setRole(@PathVariable Long id, @Valid @RequestBody UserRoleRequest request) {
        return adminUserService.setRole(id, request.roleName());
    }
}
