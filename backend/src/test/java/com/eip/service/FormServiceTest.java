package com.eip.service;

import com.eip.entity.*;
import com.eip.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FormServiceTest {

    @Mock
    private ApprovalFormRepository formRepository;
    @Mock
    private ApprovalRouteRepository routeRepository;
    @Mock
    private ApprovalAttachmentRepository attachmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BlobStorageService storageService;

    @InjectMocks
    private FormServiceImpl formService;

    private User applicant;
    private User manager;
    private User director;
    private Department dept;

    @BeforeEach
    public void setup() {
        dept = Department.builder().deptId("D01").deptName("資訊部").build();
        applicant = User.builder()
                .userId("EMP001")
                .realName("小明")
                .role("EMPLOYEE")
                .department(dept)
                .build();
        manager = User.builder()
                .userId("MGR001")
                .realName("主管A")
                .role("MANAGER")
                .department(dept)
                .build();
        director = User.builder()
                .userId("DIR001")
                .realName("處長B")
                .role("MANAGER")
                .department(dept)
                .build();
    }

    @Test
    public void testSubmitForm_LeaveHoursCalculation() throws IOException {
        // 測試請假時數計算是否扣除午休 12:00 - 13:00
        // 情況 A：09:00 至 18:00 (總共 9 小時，扣除午休 12-13 應為 8 小時)
        ApprovalForm leaveForm = ApprovalForm.builder()
                .title("請假單A")
                .formType("LEAVE")
                .startTime(LocalDateTime.of(2026, 6, 15, 9, 0))
                .endTime(LocalDateTime.of(2026, 6, 15, 18, 0))
                .reason("事假")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));
        when(userRepository.findById("MGR001")).thenReturn(Optional.of(manager));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.submitForm(leaveForm, routeTemplates, new ArrayList<>(), "EMP001");

        assertNotNull(result);
        assertEquals(new BigDecimal("8.0"), result.getTotalHours());

        // 情況 B：11:30 至 13:30 (總共 2 小時，扣除 12-13 應為 1.0 小時)
        ApprovalForm leaveFormB = ApprovalForm.builder()
                .title("請假單B")
                .formType("LEAVE")
                .startTime(LocalDateTime.of(2026, 6, 15, 11, 30))
                .endTime(LocalDateTime.of(2026, 6, 15, 13, 30))
                .reason("病假")
                .build();

        ApprovalForm resultB = formService.submitForm(leaveFormB, routeTemplates, new ArrayList<>(), "EMP001");
        assertEquals(new BigDecimal("1.0"), resultB.getTotalHours());
    }

    @Test
    public void testApproveRoute_SequentialAndJointApprovals() {
        // 測試串簽與會辦推進
        // 建立表單，目前在 Step 2
        ApprovalForm form = ApprovalForm.builder()
                .formId(100L)
                .applicant(applicant)
                .formType("ADVANCE")
                .amount(new BigDecimal("5000.00"))
                .currentStep(2)
                .finalStatus("UNDER_REVIEW")
                .build();

        // Step 2 設定兩個會辦審核人 (MGR001 和 DIR001)
        ApprovalRoute route1 = ApprovalRoute.builder()
                .routeId(1L).form(form).approver(manager).stepNumber(2).subStep(1).routeStatus("PENDING").build();
        ApprovalRoute route2 = ApprovalRoute.builder()
                .routeId(2L).form(form).approver(director).stepNumber(2).subStep(2).routeStatus("PENDING").build();

        when(formRepository.findById(100L)).thenReturn(Optional.of(form));
        when(routeRepository.findByFormFormIdAndApproverUserIdAndRouteStatus(100L, "MGR001", "PENDING"))
                .thenReturn(Optional.of(route1));

        // 當前關卡所有節點
        when(routeRepository.findByFormFormIdAndStepNumber(100L, 2))
                .thenReturn(Arrays.asList(route1, route2));

        // 1. MGR001 同意，但 DIR001 尚未同意。流程應停在原地，currentStep 仍為 2
        ApprovalForm step1Result = formService.approveRoute(100L, "MGR001", "第一關同意");
        assertEquals(2, step1Result.getCurrentStep());
        assertEquals("UNDER_REVIEW", step1Result.getFinalStatus());
        assertEquals("APPROVED", route1.getRouteStatus());

        // 2. 接下來 DIR001 也同意。所有 Step 2 均同意。流程應遞增，因為沒有 Step 3，應結案為 APPROVED
        route1.setRouteStatus("APPROVED"); // 模擬已儲存狀態
        when(routeRepository.findByFormFormIdAndApproverUserIdAndRouteStatus(100L, "DIR001", "PENDING"))
                .thenReturn(Optional.of(route2));

        // 已無大於 2 的 Step
        when(routeRepository.findByFormFormIdOrderByStepNumberAscSubStepAsc(100L))
                .thenReturn(Arrays.asList(
                        ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).routeStatus("APPROVED").build(),
                        route1, route2
                ));

        ApprovalForm step2Result = formService.approveRoute(100L, "DIR001", "第二關會辦同意");
        assertEquals("APPROVED", step2Result.getFinalStatus());
    }

    @Test
    public void testRejectRoute_WorkflowTerminated() {
        // 測試駁回：任何關卡被駁回，整張單立即 REJECTED 終止
        ApprovalForm form = ApprovalForm.builder()
                .formId(200L)
                .applicant(applicant)
                .formType("PAYMENT")
                .amount(new BigDecimal("12000.00"))
                .currentStep(2)
                .finalStatus("UNDER_REVIEW")
                .build();

        ApprovalRoute route = ApprovalRoute.builder()
                .routeId(5L).form(form).approver(manager).stepNumber(2).subStep(1).routeStatus("PENDING").build();

        when(formRepository.findById(200L)).thenReturn(Optional.of(form));
        when(routeRepository.findByFormFormIdAndApproverUserIdAndRouteStatus(200L, "MGR001", "PENDING"))
                .thenReturn(Optional.of(route));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.rejectRoute(200L, "MGR001", "金額太大駁回");
        
        assertEquals("REJECTED", result.getFinalStatus());
        assertEquals("REJECTED", route.getRouteStatus());
    }

    @Test
    public void testWithdrawForm_Rules() {
        // 測試抽單規則
        ApprovalForm form = ApprovalForm.builder()
                .formId(300L)
                .applicant(applicant)
                .formType("LEAVE")
                .currentStep(2)
                .finalStatus("UNDER_REVIEW")
                .build();

        // 情況 A：主管尚未審核，可以抽單
        List<ApprovalRoute> routesNoReview = Arrays.asList(
                ApprovalRoute.builder().stepNumber(1).routeStatus("APPROVED").build(),
                ApprovalRoute.builder().stepNumber(2).routeStatus("PENDING").build()
        );

        when(formRepository.findById(300L)).thenReturn(Optional.of(form));
        when(routeRepository.findByFormFormIdOrderByStepNumberAscSubStepAsc(300L)).thenReturn(routesNoReview);
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.withdrawForm(300L, "EMP001");
        assertEquals("WITHDRAWN", result.getFinalStatus());

        // 情況 B：已有主管審核同意過，無法抽單
        ApprovalForm formB = ApprovalForm.builder()
                .formId(301L)
                .applicant(applicant)
                .formType("LEAVE")
                .currentStep(3)
                .finalStatus("UNDER_REVIEW")
                .build();

        List<ApprovalRoute> routesWithReview = Arrays.asList(
                ApprovalRoute.builder().stepNumber(1).routeStatus("APPROVED").build(),
                ApprovalRoute.builder().stepNumber(2).routeStatus("APPROVED").build(), // 主管已核准
                ApprovalRoute.builder().stepNumber(3).routeStatus("PENDING").build()
        );

        when(formRepository.findById(301L)).thenReturn(Optional.of(formB));
        when(routeRepository.findByFormFormIdOrderByStepNumberAscSubStepAsc(301L)).thenReturn(routesWithReview);

        assertThrows(IllegalStateException.class, () -> {
            formService.withdrawForm(301L, "EMP001");
        });
    }
}
