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
@Table(name = "refresh_entity")
@NoArgsConstructor
@AllArgsConstructor
public class RefreshEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(nullable = false, length = 512)
    private String refresh;

    @CreatedDate
    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    public static RefreshEntity create(
            String username,
            String refresh
    ) {
        RefreshEntity refreshEntity = new RefreshEntity();
        refreshEntity.username = username;
        refreshEntity.refresh = refresh;
        refreshEntity.createdDate = LocalDateTime.now();

        return refreshEntity;
    }
}
