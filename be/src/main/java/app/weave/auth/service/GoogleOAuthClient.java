package app.weave.auth.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/** 인가코드 → Google access token → OpenID userinfo. KakaoOAuthClient와 동형. */
@Component
public class GoogleOAuthClient {

    private final RestClient restClient;
    private final String clientId;
    private final String clientSecret;

    public GoogleOAuthClient(
            RestClient.Builder restClientBuilder,
            @Value("${weave.google.client-id:}") String clientId,
            @Value("${weave.google.client-secret:}") String clientSecret) {
        this.restClient = restClientBuilder.build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    /** 인가코드 → Google access token → OpenID userinfo. */
    public GoogleProfile fetchProfile(String code, String redirectUri) {
        try {
            var form = new LinkedMultiValueMap<String, String>();
            form.add("grant_type", "authorization_code");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            form.add("code", code);
            form.add("redirect_uri", redirectUri);

            TokenResponse token = restClient.post()
                    .uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);

            UserInfo me = restClient.get()
                    .uri("https://openidconnect.googleapis.com/v1/userinfo")
                    .header("Authorization", "Bearer " + token.accessToken())
                    .retrieve()
                    .body(UserInfo.class);

            String email = me.email();
            String name = me.name();
            String image = me.picture();
            if (email == null || email.isBlank()) {
                throw new BusinessException(ErrorCode.AUTH_GOOGLE_EXCHANGE_FAILED);
            }
            return new GoogleProfile(email, name != null ? name : email.split("@")[0], image);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AUTH_GOOGLE_EXCHANGE_FAILED);
        }
    }

    record TokenResponse(String access_token) {
        String accessToken() {
            return access_token;
        }
    }

    record UserInfo(String email, String name, String picture) {
    }

    public record GoogleProfile(String email, String nickname, String profileImage) {
    }
}
