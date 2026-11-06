package app.weave.auth.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KakaoOAuthClient {

    private final RestClient restClient = RestClient.create();
    private final String clientId;
    private final String clientSecret;

    public KakaoOAuthClient(
            @Value("${weave.kakao.client-id:}") String clientId,
            @Value("${weave.kakao.client-secret:}") String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** 인가코드 → 카카오 access token → 사용자 정보. */
    public KakaoProfile fetchProfile(String code, String redirectUri) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("code", code);
            form.add("redirect_uri", redirectUri);

            TokenResponse token = restClient.post()
                    .uri("https://kauth.kakao.com/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            UserMe me = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header("Authorization", "Bearer " + token.accessToken())
                    .retrieve()
                    .body(UserMe.class);

            var account = me.kakaoAccount();
            var profile = account != null ? account.profile() : null;
            String email = account != null ? account.email() : null;
            String nickname = profile != null ? profile.nickname() : null;
            String image = profile != null ? profile.profileImageUrl() : null;
            if (email == null || email.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_KAKAO_EXCHANGE_FAILED);
            }
            return new KakaoProfile(email, nickname != null ? nickname : email.split("@")[0], image);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AUTH_KAKAO_EXCHANGE_FAILED);
        }
    }

    record TokenResponse(String access_token) {
        String accessToken() {
            return access_token;
        }
    }

    record UserMe(KakaoAccount kakao_account) {
        KakaoAccount kakaoAccount() {
            return kakao_account;
        }
    }

    record KakaoAccount(String email, Profile profile) {
    }

    record Profile(String nickname, String profile_image_url) {
        String profileImageUrl() {
            return profile_image_url;
        }
    }

    public record KakaoProfile(String email, String nickname, String profileImage) {
    }
}
