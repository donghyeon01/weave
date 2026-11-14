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
     * 가입 경로. DB에는 소문자로 저장된다 — users.provider CHECK ('kakao','demo','google').
     * 변환은 ProviderConverter가 담당하므로 @Enumerated(STRING)을 쓰면 CHECK 위반.
     */
    public enum Provider {
        KAKAO, DEMO, GOOGLE
    }

    /** 화면 테마 — 소문자 저장은 UserThemeConverter 참조 (chk_users_theme). */
    public enum Theme {
        LIGHT, DARK, SYSTEM
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

    /** 한 줄 소개 — 본인 설정 응답에만 노출 (api-contract §3.2). */
    @Column(length = 60)
    private String bio;

    /** 친구 일정 공개 수준 — TRUE=바쁜 블록만. share_titles·share_details는 확장용 예약 플래그. */
    @Column(name = "share_busy_only", nullable = false)
    private boolean shareBusyOnly = true;

    @Column(name = "share_titles", nullable = false)
    private boolean shareTitles;

    @Column(name = "share_details", nullable = false)
    private boolean shareDetails;

    @Column(nullable = false, length = 10)
    private Theme theme = Theme.SYSTEM;

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

    public String getBio() {
        return bio;
    }

    public boolean isShareBusyOnly() {
        return shareBusyOnly;
    }

    public boolean isShareTitles() {
        return shareTitles;
    }

    public boolean isShareDetails() {
        return shareDetails;
    }

    public Theme getTheme() {
        return theme;
    }

    /** 프로필·설정 부분 수정 — null 인자는 변경하지 않는다. */
    public void updateProfile(String nickname, String bio, Theme theme,
            Boolean shareBusyOnly, Boolean shareTitles, Boolean shareDetails) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (bio != null) {
            this.bio = bio;
        }
        if (theme != null) {
            this.theme = theme;
        }
        if (shareBusyOnly != null) {
            this.shareBusyOnly = shareBusyOnly;
        }
        if (shareTitles != null) {
            this.shareTitles = shareTitles;
        }
        if (shareDetails != null) {
            this.shareDetails = shareDetails;
        }
    }
}
