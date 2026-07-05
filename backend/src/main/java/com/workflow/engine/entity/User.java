package com.workflow.engine.entity;

import jakarta.persistence.*;

/**
 * User entity - represents admin/staff/client accounts.
 * Persistence: single source of truth in PostgreSQL (users table).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    @jakarta.validation.constraints.NotBlank(message = "Username is required")
    private String username;

    @Column(nullable = false)
    @jakarta.validation.constraints.NotBlank(message = "Password is required")
    private String password;

    /** One of: "admin", "staff", "client" */
    @Column(nullable = false)
    @jakarta.validation.constraints.NotBlank(message = "Role is required")
    private String role;

    @jakarta.validation.constraints.NotBlank(message = "Name is required")
    private String name;

    @jakarta.validation.constraints.Email(message = "Invalid email format")
    private String email;

    private String mobile;

    private String department;

    /** Free-text availability flag, e.g. "Available", "On Leave", "Busy" */
    private String availability;

    @Column(name = "otp_code")
    private String otpCode;

    @Column(name = "otp_expiry")
    private java.time.LocalDateTime otpExpiry;

    private String designation;

    private String specialization;

    public User() {
    }

    public User(String username, String password, String role, String name, String email,
                String mobile, String department, String availability) {
        this.username = username;
        this.password = password;
        setRole(role);
        this.name = name;
        this.email = email;
        this.mobile = mobile;
        this.department = department;
        this.availability = availability;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        if (role != null) {
            role = role.toLowerCase();
            if ("client".equals(role)) {
                role = "consumer";
            }
        }
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getAvailability() {
        return availability;
    }

    public void setAvailability(String availability) {
        this.availability = availability;
    }

    public String getOtpCode() {
        return otpCode;
    }

    public void setOtpCode(String otpCode) {
        this.otpCode = otpCode;
    }

    public java.time.LocalDateTime getOtpExpiry() {
        return otpExpiry;
    }

    public void setOtpExpiry(java.time.LocalDateTime otpExpiry) {
        this.otpExpiry = otpExpiry;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }
}
