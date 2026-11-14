package app.weave.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GoogleOAuthClientTest {

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private GoogleOAuthClient client;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GoogleOAuthClient(builder, "cid", "csec");
    }

    @Test
    void 인가코드_교환과_userinfo_조회로_프로필을_반환한다() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"at-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andExpect(header("Authorization", "Bearer at-1"))
                .andRespond(withSuccess(
                        "{\"email\":\"g@x.com\",\"name\":\"구글유저\",\"picture\":\"https://img/p.png\"}",
                        MediaType.APPLICATION_JSON));

        var profile = client.fetchProfile("code-1", "http://localhost/cb");

        assertThat(profile.email()).isEqualTo("g@x.com");
        assertThat(profile.nickname()).isEqualTo("구글유저");
        assertThat(profile.profileImage()).isEqualTo("https://img/p.png");
        server.verify();
    }

    @Test
    void userinfo에_name이_없으면_이메일_로컬파트를_닉네임으로_쓴다() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"at-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(withSuccess("{\"email\":\"g@x.com\"}", MediaType.APPLICATION_JSON));

        assertThat(client.fetchProfile("code-1", "uri").nickname()).isEqualTo("g");
        server.verify();
    }

    @Test
    void 토큰_교환이_실패하면_EXCHANGE_FAILED를_던진다() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.fetchProfile("bad-code", "uri"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_GOOGLE_EXCHANGE_FAILED);
    }

    @Test
    void userinfo에_email이_없으면_EXCHANGE_FAILED를_던진다() {
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"access_token\":\"at-1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://openidconnect.googleapis.com/v1/userinfo"))
                .andRespond(withSuccess("{\"name\":\"noemail\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchProfile("code-1", "uri"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_GOOGLE_EXCHANGE_FAILED);
    }
}
