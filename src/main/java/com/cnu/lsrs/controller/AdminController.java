package com.cnu.lsrs.controller;

import com.cnu.lsrs.domain.CancelReason;
import com.cnu.lsrs.domain.Role;
import com.cnu.lsrs.service.AdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final AdminService adminService;

	public AdminController(AdminService adminService) {
		this.adminService = adminService;
	}

	@GetMapping("/seats")
	public AdminService.DashboardResponse seats() {
		return adminService.getDashboard();
	}

	@GetMapping("/users")
	public List<AdminService.UserHistory> users(@RequestParam(required = false) String studentNo) {
		return adminService.findUsers(studentNo);
	}

	@GetMapping("/statistics")
	public AdminService.StatisticsResponse statistics(@RequestParam LocalDateTime startAt,
													  @RequestParam LocalDateTime endAt) {
		return adminService.getStatistics(startAt, endAt);
	}

	@PatchMapping("/{userId}/restriction")
	public AdminService.RestrictionResponse updateRestriction(@PathVariable Long userId,
															  @Valid @RequestBody RestrictionRequest request) {
		return adminService.updateRestriction(userId, request.restricted(), request.restrictionUntil());
	}

	@PatchMapping("/{userId}/no-show-count")
	public AdminService.RestrictionResponse resetNoShowCount(@PathVariable Long userId) {
		return adminService.resetNoShowCount(userId);
	}

	@PatchMapping("/{userId}/role")
	public AdminService.UserRoleResponse updateUserRole(@PathVariable Long userId,
														@Valid @RequestBody RoleRequest request) {
		return adminService.updateUserRole(userId, request.role());
	}

	@PostMapping("/reservations/{reservationId}/cancel")
	@ResponseStatus(HttpStatus.OK)
	public AdminService.CancellationResponse forceCancel(@PathVariable Long reservationId,
														 @Valid @RequestBody CancellationRequest request) {
		return adminService.forceCancel(reservationId, request.reason(), request.applyNoShowPenalty());
	}

	@GetMapping("/policies")
	public AdminService.PolicySettings policies() {
		return adminService.getPolicies();
	}

	@PutMapping("/policies")
	public AdminService.PolicyValue updateExamPolicy(@Valid @RequestBody PolicyRequest request) {
		return adminService.saveExamPolicy(request.startDate(), request.endDate());
	}

	@DeleteMapping("/policies/exam")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void clearExamPolicies() {
		adminService.clearExamPolicies();
	}

	public record RestrictionRequest(@NotNull Boolean restricted, LocalDateTime restrictionUntil) {
	}

	public record RoleRequest(@NotNull Role role) {
	}

	public record CancellationRequest(@NotNull CancelReason reason, @NotNull Boolean applyNoShowPenalty) {
	}

	public record PolicyRequest(
			@NotNull LocalDate startDate,
			@NotNull LocalDate endDate) {
	}
}
