package com.eip.repository;

import com.eip.entity.ApprovalForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalFormRepository extends JpaRepository<ApprovalForm, Long> {

    // tab 1: 待簽核清單 (我當前要審核，且節點狀態為 PENDING)
    @Query("SELECT DISTINCT f FROM ApprovalForm f JOIN f.routes r WHERE r.approver.userId = :userId AND r.routeStatus = 'PENDING' ORDER BY f.createdAt DESC")
    List<ApprovalForm> findPendingApprovals(@Param("userId") String userId);

    // tab 2: 已簽核紀錄 (我審核過，且該節點狀態為 APPROVED 或 REJECTED)
    @Query("SELECT DISTINCT f FROM ApprovalForm f JOIN f.routes r WHERE r.approver.userId = :userId AND r.routeStatus IN ('APPROVED', 'REJECTED') ORDER BY f.createdAt DESC")
    List<ApprovalForm> findReviewedApprovals(@Param("userId") String userId);

    // tab 3: 我發起的簽呈
    List<ApprovalForm> findByApplicantUserIdOrderByCreatedAtDesc(String applicantId);
}
