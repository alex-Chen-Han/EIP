package com.eip.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(name = "user_id", length = 20)
    private String userId;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String passwordHash;

    @Column(name = "real_name", nullable = false, columnDefinition = "NVARCHAR(50)")
    private String realName;

    @Column(name = "id_number", nullable = false, length = 10, columnDefinition = "CHAR(10)")
    private String idNumber;

    @Column(name = "bank_account", nullable = false, length = 30)
    private String bankAccount;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dept_id", nullable = false)
    private Department department;

    @Column(name = "position", nullable = false, columnDefinition = "NVARCHAR(30)")
    private String position;

    @Column(name = "role", nullable = false, length = 20)
    private String role; // EMPLOYEE, MANAGER, ADMIN

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @JsonIgnore
    private User manager;
}
