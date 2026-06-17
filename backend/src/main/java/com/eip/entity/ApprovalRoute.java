package com.eip.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "Approval_Routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "route_id")
    private Long routeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "form_id", nullable = false)
    @JsonIgnore
    private ApprovalForm form;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approver_id", nullable = false)
    private User approver;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "sub_step", nullable = false)
    private Integer subStep;

    @Column(name = "route_status", nullable = false, length = 20)
    private String routeStatus; // WAITING, PENDING, APPROVED, REJECTED

    @Column(name = "approver_comment", columnDefinition = "NVARCHAR(MAX)")
    private String approverComment;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
