package com.eip.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Approval_Forms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalForm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "form_id")
    private Long formId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "applicant_id", nullable = false)
    private User applicant;

    @Column(name = "title", nullable = false, columnDefinition = "NVARCHAR(100)")
    private String title;

    @Column(name = "form_type", nullable = false, length = 20)
    private String formType; // ADVANCE, PAYMENT, LEAVE, OVERTIME

    @Column(name = "current_step", nullable = false)
    @Builder.Default
    private Integer currentStep = 1;

    @Column(name = "final_status", nullable = false, length = 20)
    @Builder.Default
    private String finalStatus = "UNDER_REVIEW"; // UNDER_REVIEW, APPROVED, REJECTED, WITHDRAWN

    @Column(name = "amount", precision = 18, scale = 2, columnDefinition = "DECIMAL(18,2)")
    private BigDecimal amount; // 財務類

    @Column(name = "invoice_num", length = 30)
    private String invoiceNum; // 墊付單

    @Column(name = "invoice_date")
    private LocalDate invoiceDate; // 墊付單

    @Column(name = "start_time")
    private LocalDateTime startTime; // 差勤類

    @Column(name = "end_time")
    private LocalDateTime endTime; // 差勤類

    @Column(name = "total_hours", precision = 4, scale = 1, columnDefinition = "DECIMAL(4,1)")
    private BigDecimal totalHours; // 差勤類

    @Column(name = "reason", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ApprovalAttachment> attachments = new ArrayList<>();

    @OneToMany(mappedBy = "form", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("stepNumber ASC, subStep ASC")
    @Builder.Default
    private List<ApprovalRoute> routes = new ArrayList<>();
}
