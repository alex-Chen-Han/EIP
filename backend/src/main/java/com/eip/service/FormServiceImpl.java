package com.eip.service;

import com.eip.entity.ApprovalAttachment;
import com.eip.entity.ApprovalForm;
import com.eip.entity.ApprovalRoute;
import com.eip.entity.User;
import com.eip.repository.ApprovalAttachmentRepository;
import com.eip.repository.ApprovalFormRepository;
import com.eip.repository.ApprovalRouteRepository;
import com.eip.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormServiceImpl implements FormService {

    private final ApprovalFormRepository formRepository;
    private final ApprovalRouteRepository routeRepository;
    private final ApprovalAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;
    private final BlobStorageService storageService;

    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("999999999.99");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalForm submitForm(ApprovalForm form, List<ApprovalRoute> routeTemplates, List<MultipartFile> files, String applicantId) throws IOException {
        log.info("員工 {} 發起簽呈申請...", applicantId);

        User applicant = userRepository.findById(applicantId)
                .orElseThrow(() -> new IllegalArgumentException("申請人不存在：" + applicantId));

        form.setApplicant(applicant);
        form.setCreatedAt(LocalDateTime.now());
        form.setFinalStatus("UNDER_REVIEW");
        form.setCurrentStep(2); // 第一關為申請人(1)，第二關為直屬主管開始審核

        // 財務表單驗證
        if ("ADVANCE".equalsIgnoreCase(form.getFormType()) || "PAYMENT".equalsIgnoreCase(form.getFormType())) {
            BigDecimal amt = form.getAmount();
            if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("發起簽呈失敗：金額 {} 必須大於零", amt);
                throw new IllegalArgumentException("金額必須大於零");
            }
            if (amt.compareTo(AMOUNT_LIMIT) > 0) {
                log.warn("發起簽呈失敗：金額 {} 超出限制", amt);
                throw new IllegalArgumentException("金額太大，超出上限限制");
            }
        }

        // 差勤表單時間檢驗與時數計算
        if ("LEAVE".equalsIgnoreCase(form.getFormType()) || "OVERTIME".equalsIgnoreCase(form.getFormType())) {
            LocalDateTime start = form.getStartTime();
            LocalDateTime end = form.getEndTime();
            if (start == null || end == null) {
                log.warn("發起簽呈失敗：起迄時間不可為空");
                throw new IllegalArgumentException("起迄時間不可為空");
            }
            if (!start.isBefore(end)) {
                log.warn("發起簽呈失敗：開始時間必須早於結束時間");
                throw new IllegalArgumentException("開始時間必須早於結束時間");
            }
            if (start.getMinute() != 0 && start.getMinute() != 30) {
                log.warn("發起簽呈失敗：開始時間未對齊整點或半點：{}", start);
                throw new IllegalArgumentException("開始時間必須對齊整點或半點（00 分或 30 分）");
            }
            if (end.getMinute() != 0 && end.getMinute() != 30) {
                log.warn("發起簽呈失敗：結束時間未對齊整點或半點：{}", end);
                throw new IllegalArgumentException("結束時間必須對齊整點或半點（00 分或 30 分）");
            }

            if ("LEAVE".equalsIgnoreCase(form.getFormType())) {
                BigDecimal hours = calculateLeaveHours(start, end);
                form.setTotalHours(hours);
                log.info("請假時間段：{} 到 {}，自動計算時數（扣除午休）：{} 小時", start, end, hours);
            } else {
                // 加班防跨日驗證（排除隔天凌晨 00:00 的臨界情況）
                LocalDate startDate = start.toLocalDate();
                LocalDate endDate = end.toLocalDate();
                java.time.LocalTime endTime = end.toLocalTime();

                boolean isSameDay = startDate.isEqual(endDate);
                boolean isExactlyMidnightNextDay = endDate.isEqual(startDate.plusDays(1)) && 
                                                   endTime.equals(java.time.LocalTime.MIDNIGHT);

                if (!isSameDay && !isExactlyMidnightNextDay) {
                    log.warn("發起簽呈失敗：加班申請不可跨日，起迄時間：{} - {}", start, end);
                    throw new IllegalArgumentException("加班申請不可跨日，若跨日請拆分為多張單據申請");
                }
                BigDecimal hours = calculateOvertimeHours(start, end);
                form.setTotalHours(hours);
                log.info("加班時間段：{} 到 {}，自動計算時數：{} 小時", start, end, hours);
            }
        }

        // 儲存主表單以生成 form_id
        ApprovalForm savedForm = formRepository.save(form);

        // 儲存附件
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                if (file.getSize() > 2 * 1024 * 1024) {
                    log.warn("上傳檔案失敗：檔案 {} 超過 2MB 限制", file.getOriginalFilename());
                    throw new IllegalArgumentException("單一檔案大小不得超過 2MB");
                }
                String filePath = storageService.uploadFile(file);
                ApprovalAttachment attachment = ApprovalAttachment.builder()
                        .form(savedForm)
                        .fileName(file.getOriginalFilename())
                        .filePath(filePath)
                        .contentType(file.getContentType())
                        .fileSize(file.getSize())
                        .createdAt(LocalDateTime.now())
                        .build();
                attachmentRepository.save(attachment);
                savedForm.getAttachments().add(attachment);
            }
        }

        // 儲存流程明細並初始化狀態
        if (routeTemplates == null || routeTemplates.isEmpty()) {
            throw new IllegalArgumentException("動態簽核路徑不可為空，必須至少配置申請人與主管");
        }

        List<ApprovalRoute> savedRoutes = new ArrayList<>();
        for (ApprovalRoute template : routeTemplates) {
            User approver = userRepository.findById(template.getApprover().getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("簽核人不存在：" + template.getApprover().getUserId()));

            String initialStatus = "WAITING";
            if (template.getStepNumber() == 1) {
                initialStatus = "APPROVED"; // 申請人自己第一步自動同意
            } else if (template.getStepNumber() == 2) {
                initialStatus = "PENDING";  // 主管（第二步）自動開始處理
            }

            ApprovalRoute route = ApprovalRoute.builder()
                    .form(savedForm)
                    .approver(approver)
                    .stepNumber(template.getStepNumber())
                    .subStep(template.getSubStep())
                    .routeStatus(initialStatus)
                    .approverComment(template.getStepNumber() == 1 ? "發起申請" : null)
                    .reviewedAt(template.getStepNumber() == 1 ? LocalDateTime.now() : null)
                    .build();

            routeRepository.save(route);
            savedRoutes.add(route);
        }
        savedForm.setRoutes(savedRoutes);

        log.info("簽呈單發起成功。單號 ID: {}, 主旨: {}", savedForm.getFormId(), savedForm.getTitle());
        return savedForm;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalForm withdrawForm(Long formId, String applicantId) {
        log.info("申請人 {} 嘗試撤回（抽單）簽呈單號: {}", applicantId, formId);

        ApprovalForm form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該簽呈單：" + formId));

        if (!form.getApplicant().getUserId().equals(applicantId)) {
            log.warn("撤回失敗：使用者 {} 不是該簽呈單的申請人", applicantId);
            throw new SecurityException("您不是該簽呈單的發起人，無法撤回");
        }

        if (!"UNDER_REVIEW".equalsIgnoreCase(form.getFinalStatus())) {
            log.warn("撤回失敗：表單狀態已為 {}，不可撤回", form.getFinalStatus());
            throw new IllegalStateException("表單狀態非審核中，無法撤回");
        }

        // 檢查是否已有任何主管做過審核（除了 Step 1 之外，所有節點狀態必須全為 PENDING 或 WAITING）
        List<ApprovalRoute> routes = routeRepository.findByFormFormIdOrderByStepNumberAscSubStepAsc(formId);
        boolean hasReviewActivity = false;
        for (ApprovalRoute route : routes) {
            if (route.getStepNumber() > 1 && 
                ("APPROVED".equalsIgnoreCase(route.getRouteStatus()) || "REJECTED".equalsIgnoreCase(route.getRouteStatus()))) {
                hasReviewActivity = true;
                break;
            }
        }

        if (hasReviewActivity) {
            log.warn("撤回失敗：已有主管執行審核，無法抽單。單號: {}", formId);
            throw new IllegalStateException("已有主管進行過審核，無法抽單作廢");
        }

        // 變更主表狀態為 WITHDRAWN
        form.setFinalStatus("WITHDRAWN");
        ApprovalForm savedForm = formRepository.save(form);

        log.info("簽呈單號: {} 抽單撤回成功，狀態變更為 WITHDRAWN", formId);
        return savedForm;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalForm approveRoute(Long formId, String approverId, String comment) {
        log.info("主管 {} 審批同意簽呈單號: {}", approverId, formId);

        ApprovalForm form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該簽呈單：" + formId));

        // 取得當前關卡中，該名審核人狀態為 PENDING 的節點
        ApprovalRoute currentRoute = routeRepository
                .findByFormFormIdAndApproverUserIdAndRouteStatus(formId, approverId, "PENDING")
                .orElseThrow(() -> new IllegalArgumentException("找不到目前輪到您審核的步驟，或是您已審核過"));

        currentRoute.setRouteStatus("APPROVED");
        currentRoute.setApproverComment(comment);
        currentRoute.setReviewedAt(LocalDateTime.now());
        routeRepository.save(currentRoute);

        // 檢查當前 currentStep 的所有人是否都已是 APPROVED (會辦邏輯)
        List<ApprovalRoute> currentStepRoutes = routeRepository.findByFormFormIdAndStepNumber(formId, form.getCurrentStep());
        boolean allApproved = currentStepRoutes.stream()
                .allMatch(r -> "APPROVED".equalsIgnoreCase(r.getRouteStatus()));

        if (allApproved) {
            // 當前關卡全部同意，推進流程。尋找下一個 step_number
            List<ApprovalRoute> allRoutes = routeRepository.findByFormFormIdOrderByStepNumberAscSubStepAsc(formId);
            Integer nextStep = null;
            for (ApprovalRoute r : allRoutes) {
                if (r.getStepNumber() > form.getCurrentStep()) {
                    nextStep = r.getStepNumber();
                    break;
                }
            }

            if (nextStep != null) {
                // 有下一關，將下一關的所有節點由 WAITING 改為 PENDING
                List<ApprovalRoute> nextStepRoutes = routeRepository.findByFormFormIdAndStepNumber(formId, nextStep);
                for (ApprovalRoute r : nextStepRoutes) {
                    r.setRouteStatus("PENDING");
                    routeRepository.save(r);
                }
                form.setCurrentStep(nextStep);
                formRepository.save(form);
                log.info("簽呈單號: {} 當前關卡同意完畢，流程推進至第 {} 關", formId, nextStep);
            } else {
                // 已無下一關，表單正式核准結案 (APPROVED)
                form.setFinalStatus("APPROVED");
                formRepository.save(form);
                log.info("簽呈單號: {} 已完成最後一關審核，全單正式 APPROVED 結案！", formId);
            }
        } else {
            log.info("簽呈單號: {} 會辦中，尚有同關卡其他人員未審核...", formId);
        }

        // 輸出關鍵商務日誌：審核人、表單 ID、變更金額/假別時數、結果
        String detailInfo = getFormDetailSummary(form);
        log.info("[核准事件] 審核人: {}, 表單ID: {}, 表單類型: {}, {}, 結果: 同意 (APPROVED)", 
                approverId, formId, form.getFormType(), detailInfo);

        return form;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApprovalForm rejectRoute(Long formId, String approverId, String comment) {
        log.info("主管 {} 駁回簽呈單號: {}", approverId, formId);

        ApprovalForm form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該簽呈單：" + formId));

        // 取得當前關卡中，該名審核人狀態為 PENDING 的節點
        ApprovalRoute currentRoute = routeRepository
                .findByFormFormIdAndApproverUserIdAndRouteStatus(formId, approverId, "PENDING")
                .orElseThrow(() -> new IllegalArgumentException("找不到目前輪到您審核的步驟，或是您已審核過"));

        // 更新此節點為 REJECTED
        currentRoute.setRouteStatus("REJECTED");
        currentRoute.setApproverComment(comment);
        currentRoute.setReviewedAt(LocalDateTime.now());
        routeRepository.save(currentRoute);

        // 任何審核人駁回，主檔狀態同步改為 REJECTED，流程立即終止
        form.setFinalStatus("REJECTED");
        ApprovalForm savedForm = formRepository.save(form);

        // 輸出關鍵商務日誌：審核人、表單 ID、變更金額/假別時數、結果
        String detailInfo = getFormDetailSummary(form);
        log.info("[駁回事件] 審核人: {}, 表單ID: {}, 表單類型: {}, {}, 結果: 駁回 (REJECTED)", 
                approverId, formId, form.getFormType(), detailInfo);

        return savedForm;
    }

    @Override
    public List<ApprovalForm> getPendingForms(String userId) {
        return formRepository.findPendingApprovals(userId);
    }

    @Override
    public List<ApprovalForm> getReviewedForms(String userId) {
        return formRepository.findReviewedApprovals(userId);
    }

    @Override
    public List<ApprovalForm> getMyForms(String applicantId) {
        return formRepository.findByApplicantUserIdOrderByCreatedAtDesc(applicantId);
    }

    @Override
    public ApprovalForm getFormDetail(Long formId, String userId) {
        ApprovalForm form = formRepository.findById(formId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該簽呈單：" + formId));

        // 隱私權檢查：申請人本人可看
        if (form.getApplicant().getUserId().equals(userId)) {
            return form;
        }

        // 審核人必須是非 WAITING 狀態（即 PENDING、APPROVED、REJECTED）才能查
        List<ApprovalRoute> routes = routeRepository.findByFormFormIdOrderByStepNumberAscSubStepAsc(formId);
        boolean isAuthorizedApprover = false;
        for (ApprovalRoute r : routes) {
            if (r.getApprover().getUserId().equals(userId) && !"WAITING".equalsIgnoreCase(r.getRouteStatus())) {
                isAuthorizedApprover = true;
                break;
            }
        }

        if (!isAuthorizedApprover) {
            log.warn("權限拒絕：使用者 {} 嘗試越權讀取表單單號: {}", userId, formId);
            throw new SecurityException("您無權限檢視此簽呈單（保護隱私：未輪到您審核或不在此簽核路徑中）");
        }

        return form;
    }

    // 私有輔助方法：計算請假時數
    private BigDecimal calculateLeaveHours(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || start.isAfter(end)) {
            return BigDecimal.ZERO;
        }

        long totalHalfHours = 0;
        LocalDateTime current = start;

        while (current.isBefore(end)) {
            // 1. 排除週末假日 (週六與週日)
            boolean isWeekend = (current.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || 
                                 current.getDayOfWeek() == java.time.DayOfWeek.SUNDAY);
            
            // 2. 判定是否在工作時間內 (標準上班時間 08:00 - 17:00，且排除中午 12:00 - 13:00)
            int hour = current.getHour();
            boolean isWorkingHour = (hour >= 8 && hour < 17 && hour != 12);

            if (!isWeekend && isWorkingHour) {
                totalHalfHours++;
            }
            current = current.plusMinutes(30);
        }

        return BigDecimal.valueOf(totalHalfHours).multiply(new BigDecimal("0.5"));
    }

    // 私有輔助方法：計算加班時數
    private BigDecimal calculateOvertimeHours(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || start.isAfter(end)) {
            return BigDecimal.ZERO;
        }

        long totalHalfHours = 0;
        LocalDateTime current = start;

        while (current.isBefore(end)) {
            totalHalfHours++;
            current = current.plusMinutes(30);
        }

        return BigDecimal.valueOf(totalHalfHours).multiply(new BigDecimal("0.5"));
    }

    // 私有輔助方法：組成日誌的詳細內容摘要
    private String getFormDetailSummary(ApprovalForm form) {
        if ("ADVANCE".equalsIgnoreCase(form.getFormType()) || "PAYMENT".equalsIgnoreCase(form.getFormType())) {
            return "變更金額: " + form.getAmount();
        } else if ("LEAVE".equalsIgnoreCase(form.getFormType()) || "OVERTIME".equalsIgnoreCase(form.getFormType())) {
            return "變更時數: " + form.getTotalHours() + " 小時";
        }
        return "";
    }
}
