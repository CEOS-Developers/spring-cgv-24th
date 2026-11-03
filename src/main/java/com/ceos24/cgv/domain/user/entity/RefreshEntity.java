package com.ceos24.cgv.domain.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@EntityListeners(AuditingEntityListener.class)
@Getter
@Table(
        name = "refresh_token",
        indexes = {
                @Index(
                        name = "idx_refresh_token_hash",
                        columnList = "token_hash",
                        unique = true
                ),
                @Index(
                        name = "idx_refresh_token_expires_at",
                        columnList = "expires_at"
                )
        }
)
@NoArgsConstructor
@AllArgsConstructor
public class RefreshEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(
            name = "token_hash",
            nullable = false,
            length = 64,
            unique = true
    )
    private String tokenHash;

    @Column(
            name = "expires_at",
            nullable = false
    )
    private LocalDateTime expiresAt;


    @CreatedDate
    @Column(name = "created_date", nullable = false, updatable = false)
    private LocalDateTime createdDate;

    public static RefreshEntity create(
            String username,
            String tokenHash,
            LocalDateTime expiresAt
    ) {
        RefreshEntity refreshEntity = new RefreshEntity();

        refreshEntity.username = username;
        refreshEntity.tokenHash = tokenHash;
        refreshEntity.expiresAt = expiresAt;

        return refreshEntity;
    }
}
