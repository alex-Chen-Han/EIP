package com.eip.repository;

import com.eip.entity.ApprovalAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApprovalAttachmentRepository extends JpaRepository<ApprovalAttachment, Long> {
    List<ApprovalAttachment> findByFormFormId(Long formId);
}
