package com.eip.repository;

import com.eip.entity.ApprovalRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApprovalRouteRepository extends JpaRepository<ApprovalRoute, Long> {
    List<ApprovalRoute> findByFormFormIdOrderByStepNumberAscSubStepAsc(Long formId);
    List<ApprovalRoute> findByFormFormIdAndStepNumber(Long formId, Integer stepNumber);
    Optional<ApprovalRoute> findByFormFormIdAndApproverUserIdAndRouteStatus(Long formId, String approverId, String routeStatus);
}
