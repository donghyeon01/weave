package app.weave.auth.domain;

import app.weave.common.domain.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

    /**
     * 가입 경로. DB에는 소문자로 저장된다 — users.provider CHECK ('kakao','demo').
     * 변환은 ProviderConverter가 담당하므로 @Enumerated(STRING)을 쓰면 CHECK 위반.
     */
    public enum Provider {
        KAKAO, DEMO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(name = "profile_image", length = 500)
    private String profileImage;

    /** 소문자 'kakao'/'demo'로 저장 — ProviderConverter 참조. */
    @Column(nullable = false, length = 10)
    private Provider provider;

    protected User() {
    }

    public User(String email, String name, String nickname, String profileImage,
            Provider provider) {
        this.email = email;
        this.name = name;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.provider = provider;
    }

    /** OAuth 자동 가입 — 별도 실명 수집이 없어 표시명을 name에도 사용한다. */
    public static User signup(String email, String nickname, String profileImage,
            Provider provider) {
        return new User(email, nickname, nickname, profileImage, provider);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public String getNickname() {
        return nickname;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public Provider getProvider() {
        return provider;
    }
}
