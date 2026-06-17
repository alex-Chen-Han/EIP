package com.eip.service;

import com.eip.entity.ApprovalForm;
import com.eip.entity.ApprovalRoute;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface FormService {
    ApprovalForm submitForm(ApprovalForm form, List<ApprovalRoute> routeTemplates, List<MultipartFile> files, String applicantId) throws IOException;
    ApprovalForm withdrawForm(Long formId, String applicantId);
    ApprovalForm approveRoute(Long formId, String approverId, String comment);
    ApprovalForm rejectRoute(Long formId, String approverId, String comment);
    
    List<ApprovalForm> getPendingForms(String userId);
    List<ApprovalForm> getReviewedForms(String userId);
    List<ApprovalForm> getMyForms(String applicantId);
    ApprovalForm getFormDetail(Long formId, String userId);
}
