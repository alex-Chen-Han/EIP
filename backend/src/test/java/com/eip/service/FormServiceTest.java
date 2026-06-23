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
        // 情況 A：08:00 至 17:00 (總共 9 小時，扣除午休 12-13 應為 8 小時)
        ApprovalForm leaveForm = ApprovalForm.builder()
                .title("請假單A")
                .formType("LEAVE")
                .startTime(LocalDateTime.of(2026, 6, 15, 8, 0))
                .endTime(LocalDateTime.of(2026, 6, 15, 17, 0))
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

    @Test
    public void testSubmitForm_InvalidTimeAlignment() {
        // 測試開始時間未對齊半小時 (如 09:15)
        ApprovalForm leaveFormInvalidStart = ApprovalForm.builder()
                .title("請假單-開始未對齊")
                .formType("LEAVE")
                .startTime(LocalDateTime.of(2026, 6, 15, 9, 15))
                .endTime(LocalDateTime.of(2026, 6, 15, 12, 0))
                .reason("事假")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(leaveFormInvalidStart, routeTemplates, new ArrayList<>(), "EMP001");
        });

        // 測試結束時間未對齊半小時 (如 12:45)
        ApprovalForm leaveFormInvalidEnd = ApprovalForm.builder()
                .title("請假單-結束未對齊")
                .formType("LEAVE")
                .startTime(LocalDateTime.of(2026, 6, 15, 9, 0))
                .endTime(LocalDateTime.of(2026, 6, 15, 12, 45))
                .reason("事假")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(leaveFormInvalidEnd, routeTemplates, new ArrayList<>(), "EMP001");
        });
    }

    @Test
    public void testSubmitForm_InvalidTimeOrder() {
        // 測試開始時間等於結束時間
        ApprovalForm leaveFormEqual = ApprovalForm.builder()
                .title("請假單-時間相同")
                .formType("LEAVE")
                .startTime(LocalDateTime.of(2026, 6, 15, 9, 0))
                .endTime(LocalDateTime.of(2026, 6, 15, 9, 0))
                .reason("事假")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(leaveFormEqual, routeTemplates, new ArrayList<>(), "EMP001");
        });

        // 測試開始時間晚於結束時間
        ApprovalForm leaveFormAfter = ApprovalForm.builder()
                .title("請假單-開始晚於結束")
                .formType("LEAVE")
                .startTime(LocalDateTime.of(2026, 6, 15, 10, 0))
                .endTime(LocalDateTime.of(2026, 6, 15, 9, 0))
                .reason("事假")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(leaveFormAfter, routeTemplates, new ArrayList<>(), "EMP001");
        });
    }

    @Test
    public void testSubmitForm_OvertimeCalculation() throws IOException {
        // 測試加班時數計算：09:00 到 12:30 (週六，扣除 12:00-12:30 午休半小時，共 3.0 小時)
        ApprovalForm overtimeForm = ApprovalForm.builder()
                .title("加班單A")
                .formType("OVERTIME")
                .startTime(LocalDateTime.of(2026, 6, 13, 9, 0)) // 2026-06-13 是週六 (假日)
                .endTime(LocalDateTime.of(2026, 6, 13, 12, 30))
                .reason("專案加班")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));
        when(userRepository.findById("MGR001")).thenReturn(Optional.of(manager));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.submitForm(overtimeForm, routeTemplates, new ArrayList<>(), "EMP001");

        assertNotNull(result);
        assertEquals(new BigDecimal("3.0"), result.getTotalHours());
    }

    @Test
    public void testSubmitForm_LeaveWeekendAndOffHourExclusion() throws IOException {
        // 測試跨日與跨週末請假時數排除：週五 16:00 到 下週一 09:00
        // 週五 16:00-17:00 (計 1.0 小時，17:00之後為下班時間不計)
        // 週六、週日 (週末不計)
        // 週一 08:00-09:00 (計 1.0 小時)
        // 預期總時數為 2.0 小時
        ApprovalForm weekendLeaveForm = ApprovalForm.builder()
                .title("跨週末請假")
                .formType("LEAVE")
                .startTime(LocalDateTime.of(2026, 6, 12, 16, 0)) // 2026-06-12 是週五
                .endTime(LocalDateTime.of(2026, 6, 15, 9, 0))    // 2026-06-15 是週一
                .reason("特休")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));
        when(userRepository.findById("MGR001")).thenReturn(Optional.of(manager));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.submitForm(weekendLeaveForm, routeTemplates, new ArrayList<>(), "EMP001");

        assertNotNull(result);
        assertEquals(new BigDecimal("2.0"), result.getTotalHours());
    }

    @Test
    public void testSubmitForm_OvertimeCrossDayRejection() {
        // 測試加班跨日拋出異常阻斷 (如週一 20:00 至 週二 01:00)
        ApprovalForm crossDayOvertimeForm = ApprovalForm.builder()
                .title("跨日加班單")
                .formType("OVERTIME")
                .startTime(LocalDateTime.of(2026, 6, 15, 20, 0)) // 週一
                .endTime(LocalDateTime.of(2026, 6, 16, 1, 0))   // 週二
                .reason("專案上線加班")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(crossDayOvertimeForm, routeTemplates, new ArrayList<>(), "EMP001");
        });
    }

    @Test
    public void testSubmitForm_OvertimeWeekendAllowed() throws IOException {
        // 測試假日加班不被排除：週六 09:00 至 週六 12:00 (共 3.0 小時)
        ApprovalForm weekendOvertimeForm = ApprovalForm.builder()
                .title("週六加班單")
                .formType("OVERTIME")
                .startTime(LocalDateTime.of(2026, 6, 13, 9, 0))  // 2026-06-13 是週六
                .endTime(LocalDateTime.of(2026, 6, 13, 12, 0))
                .reason("假日專案加班")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));
        when(userRepository.findById("MGR001")).thenReturn(Optional.of(manager));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.submitForm(weekendOvertimeForm, routeTemplates, new ArrayList<>(), "EMP001");

        assertNotNull(result);
        assertEquals(new BigDecimal("3.0"), result.getTotalHours());
    }

    @Test
    public void testSubmitForm_OvertimeBoundaryMidnightAllowed() throws IOException {
        // 測試加班邊界條件：第一天 20:00 至 隔天 00:00 (剛好跨入 00:00 算做第一天的 4.0 小時，不予阻斷)
        ApprovalForm midnightOvertimeForm = ApprovalForm.builder()
                .title("邊界加班單")
                .formType("OVERTIME")
                .startTime(LocalDateTime.of(2026, 6, 15, 20, 0)) // 週一
                .endTime(LocalDateTime.of(2026, 6, 16, 0, 0))    // 週二凌晨 00:00
                .reason("正常加班至午夜")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));
        when(userRepository.findById("MGR001")).thenReturn(Optional.of(manager));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.submitForm(midnightOvertimeForm, routeTemplates, new ArrayList<>(), "EMP001");

        assertNotNull(result);
        assertEquals(new BigDecimal("4.0"), result.getTotalHours());
    }

    @Test
    public void testSubmitForm_InvalidAmountNull() {
        // 測試財務表單金額為 null 時拋出異常
        ApprovalForm form = ApprovalForm.builder()
                .title("預支請款-金額為空")
                .formType("ADVANCE")
                .amount(null)
                .reason("公務採購")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(form, routeTemplates, new ArrayList<>(), "EMP001");
        });
    }

    @Test
    public void testSubmitForm_InvalidAmountNegativeOrZero() {
        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        // 測試金額為 0
        ApprovalForm formZero = ApprovalForm.builder()
                .title("預支請款-金額為零")
                .formType("ADVANCE")
                .amount(BigDecimal.ZERO)
                .reason("公務採購")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(formZero, routeTemplates, new ArrayList<>(), "EMP001");
        });

        // 測試金額為負數
        ApprovalForm formNegative = ApprovalForm.builder()
                .title("預支請款-金額為負")
                .formType("ADVANCE")
                .amount(new BigDecimal("-100.50"))
                .reason("公務採購")
                .build();

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(formNegative, routeTemplates, new ArrayList<>(), "EMP001");
        });
    }

    @Test
    public void testSubmitForm_InvalidAmountOverflow() {
        // 測試金額超出限制（例如 10 億元，大於 999,999,999.99）
        ApprovalForm formOverflow = ApprovalForm.builder()
                .title("預支請款-金額溢位")
                .formType("ADVANCE")
                .amount(new BigDecimal("1000000000.00"))
                .reason("公務採購")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(formOverflow, routeTemplates, new ArrayList<>(), "EMP001");
        });
    }

    @Test
    public void testSubmitForm_OvertimeFutureTime() {
        // 測試加班結束時間為未來時間
        ApprovalForm overtimeFormFuture = ApprovalForm.builder()
                .title("未來加班單")
                .formType("OVERTIME")
                .startTime(LocalDateTime.now().minusHours(2).withMinute(0).withSecond(0).withNano(0))
                .endTime(LocalDateTime.now().plusHours(2).withMinute(0).withSecond(0).withNano(0))
                .reason("加班")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(overtimeFormFuture, routeTemplates, new ArrayList<>(), "EMP001");
        });
        assertTrue(ex.getMessage().contains("加班結束時間不可為未來時間"));
    }

    @Test
    public void testSubmitForm_PaymentInvalidInvoiceNum() {
        // 測試發票號碼格式不正確
        ApprovalForm paymentFormInvalidInvoice = ApprovalForm.builder()
                .title("墊付簽呈-發票號碼格式錯誤")
                .formType("PAYMENT")
                .amount(new BigDecimal("100.00"))
                .invoiceNum("12345678") // 格式不符 Regex: ^[A-Z]{2}\d{8}$
                .invoiceDate(LocalDate.now())
                .reason("辦公用品")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(paymentFormInvalidInvoice, routeTemplates, new ArrayList<>(), "EMP001");
        });
        assertTrue(ex.getMessage().contains("發票號碼格式不正確"));
    }

    @Test
    public void testSubmitForm_PaymentFutureInvoiceDate() {
        // 測試發票日期不可為未來日期
        ApprovalForm paymentFormFutureDate = ApprovalForm.builder()
                .title("墊付簽呈-未來發票日期")
                .formType("PAYMENT")
                .amount(new BigDecimal("100.00"))
                .invoiceNum("AB12345678")
                .invoiceDate(LocalDate.now().plusDays(1)) // 明天
                .reason("辦公用品")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(paymentFormFutureDate, routeTemplates, new ArrayList<>(), "EMP001");
        });
        assertTrue(ex.getMessage().contains("發票日期不可為未來日期"));
    }

    @Test
    public void testSubmitForm_PaymentInvoiceDateNull() {
        // 測試發票日期為空
        ApprovalForm paymentFormNullDate = ApprovalForm.builder()
                .title("墊付簽呈-發票日期為空")
                .formType("PAYMENT")
                .amount(new BigDecimal("100.00"))
                .invoiceNum("AB12345678")
                .invoiceDate(null)
                .reason("辦公用品")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            formService.submitForm(paymentFormNullDate, routeTemplates, new ArrayList<>(), "EMP001");
        });
        assertTrue(ex.getMessage().contains("發票日期不可為空"));
    }

    @Test
    public void testSubmitForm_WeekdayOvertimeExcludesCoreHours() throws IOException {
        // 測試平日加班排除正常工時：08:00 到 20:00 (平日週一，扣除 08:00-17:00 共 9 小時，只計 17:00-20:00 的 3.0 小時)
        ApprovalForm form = ApprovalForm.builder()
                .title("平日加班單")
                .formType("OVERTIME")
                .startTime(LocalDateTime.of(2026, 6, 15, 8, 0)) // 2026-06-15 是週一 (平日)
                .endTime(LocalDateTime.of(2026, 6, 15, 20, 0))
                .reason("加班")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));
        when(userRepository.findById("MGR001")).thenReturn(Optional.of(manager));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.submitForm(form, routeTemplates, new ArrayList<>(), "EMP001");

        assertNotNull(result);
        assertEquals(new BigDecimal("3.0"), result.getTotalHours());
    }

    @Test
    public void testSubmitForm_WeekdayOvertimeBeforeAndAfterCoreHours() throws IOException {
        // 測試平日跨正常工時的前後加班：06:00 到 21:00 (平日週一，排除 08:00-17:00，只計 06-08 與 17-21，共 2 + 4 = 6.0 小時)
        ApprovalForm form = ApprovalForm.builder()
                .title("平日前後加班單")
                .formType("OVERTIME")
                .startTime(LocalDateTime.of(2026, 6, 15, 6, 0)) // 2026-06-15 是週一 (平日)
                .endTime(LocalDateTime.of(2026, 6, 15, 21, 0))
                .reason("加班")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));
        when(userRepository.findById("MGR001")).thenReturn(Optional.of(manager));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.submitForm(form, routeTemplates, new ArrayList<>(), "EMP001");

        assertNotNull(result);
        assertEquals(new BigDecimal("6.0"), result.getTotalHours());
    }

    @Test
    public void testSubmitForm_WeekendOvertimeDeductsLunch() throws IOException {
        // 測試假日加班扣除午休：08:00 到 20:00 (週六，總時數 12.0，扣除 12:00-13:00 休息 1.0 小時，為 11.0 小時)
        ApprovalForm form = ApprovalForm.builder()
                .title("假日加班扣午休")
                .formType("OVERTIME")
                .startTime(LocalDateTime.of(2026, 6, 13, 8, 0)) // 2026-06-13 是週六 (假日)
                .endTime(LocalDateTime.of(2026, 6, 13, 20, 0))
                .reason("加班")
                .build();

        List<ApprovalRoute> routeTemplates = Arrays.asList(
                ApprovalRoute.builder().approver(applicant).stepNumber(1).subStep(1).build(),
                ApprovalRoute.builder().approver(manager).stepNumber(2).subStep(1).build()
        );

        when(userRepository.findById("EMP001")).thenReturn(Optional.of(applicant));
        when(userRepository.findById("MGR001")).thenReturn(Optional.of(manager));
        when(formRepository.save(any(ApprovalForm.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalForm result = formService.submitForm(form, routeTemplates, new ArrayList<>(), "EMP001");

        assertNotNull(result);
        assertEquals(new BigDecimal("11.0"), result.getTotalHours());
    }
}

