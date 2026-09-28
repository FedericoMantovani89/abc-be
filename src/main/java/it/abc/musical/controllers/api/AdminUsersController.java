package it.abc.musical.controllers.api;

import it.abc.musical.dto.UserDtos.UserAdminDto;
import it.abc.musical.dto.UserDtos.UserAdminListResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUsersController {

    private final AdminUserService adminUserService;

    @GetMapping
    public UserAdminListResponse list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) String name,
                                      @RequestParam(required = false) String email,
                                      @RequestParam(required = false) String role,
                                      @RequestParam(required = false) Boolean active,
                                      @RequestParam(required = false) String lastLoginFrom,
                                      @RequestParam(required = false) String lastLoginTo,
                                      @RequestParam(required = false) Boolean neverLoggedIn) {
        return adminUserService.list(page, size, name, email, role, active,
                lastLoginFrom, lastLoginTo, neverLoggedIn);
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
