package app.weave.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "previous_token_hash", columnDefinition = "CHAR(64)")
    private String previousTokenHash;

    @Column(name = "user_agent", nullable = false)
    private String userAgent;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "expiry_date", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime expiryDate;

    @Column(name = "evicted_at", columnDefinition = "DATETIME")
    private LocalDateTime evictedAt;

    @Column(name = "last_accessed_at", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime lastAccessedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME")
    private LocalDateTime createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(Long userId, String tokenHash, String userAgent, String ipAddress,
            LocalDateTime expiryDate) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.expiryDate = expiryDate;
        // DDL 디폴트는 앱 우회 대비 방어선 — JPA 경유 쓰기는 엔터티가 값을 채운다
        LocalDateTime now = LocalDateTime.now();
        this.lastAccessedAt = now;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTokenHash() { return tokenHash; }
    public String getPreviousTokenHash() { return previousTokenHash; }
    public String getUserAgent() { return userAgent; }
    public LocalDateTime getExpiryDate() { return expiryDate; }
    public LocalDateTime getEvictedAt() { return evictedAt; }
    public LocalDateTime getLastAccessedAt() { return lastAccessedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isEvicted() { return evictedAt != null; }
}
