package com.reapnow.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    private String nickname;

    private String phone;

    /** 所在地区，用于「附近作业」的距离口径 */
    private String region;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    /** 机手信誉分：初始 100，履约加分、爽约扣分 */
    @Column(precision = 5, scale = 1)
    private BigDecimal creditScore;

    /** 机手是否已通过实名与跨区作业证审核 */
    private Boolean verified;

    private LocalDateTime createdAt;
}
