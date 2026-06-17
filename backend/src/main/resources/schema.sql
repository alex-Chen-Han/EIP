-- =====================================================================
-- 中小企業動態電子簽核系統 — 資料庫建表腳本 (MSSQL 語法)
-- =====================================================================

-- 1. 部門資料表 (Departments)
CREATE TABLE Departments (
    dept_id VARCHAR(10) PRIMARY KEY,
    dept_name NVARCHAR(50) NOT NULL
);

-- 2. 員工資料表 (Users)
CREATE TABLE Users (
    user_id VARCHAR(20) PRIMARY KEY,
    password_hash CHAR(64) NOT NULL,
    real_name NVARCHAR(50) NOT NULL,
    id_number CHAR(10) NOT NULL,
    bank_account VARCHAR(30) NOT NULL,
    dept_id VARCHAR(10) NOT NULL,
    position NVARCHAR(30) NOT NULL,
    role VARCHAR(20) NOT NULL, -- EMPLOYEE, MANAGER, ADMIN
    manager_id VARCHAR(20) NULL,
    CONSTRAINT FK_Users_Departments FOREIGN KEY (dept_id) REFERENCES Departments(dept_id),
    CONSTRAINT FK_Users_Manager FOREIGN KEY (manager_id) REFERENCES Users(user_id)
);

-- 3. 表單主檔表 (Approval_Forms)
CREATE TABLE Approval_Forms (
    form_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    applicant_id VARCHAR(20) NOT NULL,
    title NVARCHAR(100) NOT NULL,
    form_type VARCHAR(20) NOT NULL, -- ADVANCE, PAYMENT, LEAVE, OVERTIME
    current_step INT NOT NULL DEFAULT 1,
    final_status VARCHAR(20) NOT NULL, -- UNDER_REVIEW, APPROVED, REJECTED, WITHDRAWN
    amount DECIMAL(18,2) NULL,
    invoice_num VARCHAR(30) NULL,
    invoice_date DATE NULL,
    start_time DATETIME NULL,
    end_time DATETIME NULL,
    total_hours DECIMAL(4,1) NULL,
    reason NVARCHAR(MAX) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_ApprovalForms_Users FOREIGN KEY (applicant_id) REFERENCES Users(user_id)
);

-- 4. 表單附件資料表 (Approval_Attachments)
CREATE TABLE Approval_Attachments (
    attachment_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    form_id BIGINT NOT NULL,
    file_name NVARCHAR(255) NOT NULL,
    file_path VARCHAR(1000) NOT NULL, -- 儲存 Blob Storage 的路徑或 URL
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_ApprovalAttachments_Forms FOREIGN KEY (form_id) REFERENCES Approval_Forms(form_id) ON DELETE CASCADE
);

-- 5. 流程明細表 (Approval_Routes)
CREATE TABLE Approval_Routes (
    route_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    form_id BIGINT NOT NULL,
    approver_id VARCHAR(20) NOT NULL,
    step_number INT NOT NULL,
    sub_step INT NOT NULL,
    route_status VARCHAR(20) NOT NULL, -- WAITING, PENDING, APPROVED, REJECTED
    approver_comment NVARCHAR(MAX) NULL,
    reviewed_at DATETIME NULL,
    CONSTRAINT FK_ApprovalRoutes_Forms FOREIGN KEY (form_id) REFERENCES Approval_Forms(form_id) ON DELETE CASCADE,
    CONSTRAINT FK_ApprovalRoutes_Users FOREIGN KEY (approver_id) REFERENCES Users(user_id)
);

-- 6. 審計日誌表 (Audit_Logs)
CREATE TABLE Audit_Logs (
    log_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    operator_id VARCHAR(20) NOT NULL,
    action_type VARCHAR(30) NOT NULL, -- CREATE_USER, UPDATE_ROLE, EXPORT_LOGS, etc.
    target_id VARCHAR(20) NULL,
    description NVARCHAR(MAX) NOT NULL,
    action_time DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_AuditLogs_Users FOREIGN KEY (operator_id) REFERENCES Users(user_id)
);
