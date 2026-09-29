package com.example.board.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.example.board.entity.Provider;
import com.example.board.entity.Role;
import com.example.board.entity.SmsNotificationLog;
import com.example.board.entity.SmsStatus;
import com.example.board.entity.User;
import com.example.board.repository.SmsNotificationLogRepository;

@ExtendWith(MockitoExtension.class)
class TwilioSmsSenderTest {

    private static final String ACCOUNT_SID = "AC_test_sid";
    private static final String AUTH_TOKEN = "test_token";
    private static final String FROM_NUMBER = "+15005550006";

    @Mock
    private SmsNotificationLogRepository smsNotificationLogRepository;

    private MockRestServiceServer mockServer;
    private User recipient;

    @BeforeEach
    void setUp() {
        recipient = User.builder()
                .email("user@example.com")
                .name("홍길동")
                .provider(Provider.LOCAL)
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(recipient, "id", 1L);
        ReflectionTestUtils.setField(recipient, "phoneNumber", "+821012345678");
    }

    private TwilioSmsSender createSender(boolean enabled) {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        return new TwilioSmsSender(builder, smsNotificationLogRepository, enabled, ACCOUNT_SID, AUTH_TOKEN, FROM_NUMBER);
    }

    @Test
    void sms가_비활성화_상태면_API를_호출하지_않는다() {
        TwilioSmsSender sender = createSender(false);

        sender.send(recipient, "댓글 알림");

        mockServer.verify();
        verify(smsNotificationLogRepository, never()).save(any());
    }

    @Test
    void 발송에_성공하면_SUCCESS_로그를_저장한다() {
        TwilioSmsSender sender = createSender(true);
        mockServer.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        sender.send(recipient, "댓글 알림");

        mockServer.verify();
        verify(smsNotificationLogRepository).save(argThatStatus(SmsStatus.SUCCESS));
    }

    @Test
    void 발송이_계속_실패하면_재시도_후_FAILED_로그를_저장한다() {
        TwilioSmsSender sender = createSender(true);
        mockServer.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());
        mockServer.expect(requestTo("https://api.twilio.com/2010-04-01/Accounts/" + ACCOUNT_SID + "/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        sender.send(recipient, "댓글 알림");

        mockServer.verify();
        verify(smsNotificationLogRepository, times(1)).save(argThatStatus(SmsStatus.FAILED));
    }

    private SmsNotificationLog argThatStatus(SmsStatus status) {
        return org.mockito.ArgumentMatchers.argThat(log -> log != null && log.getStatus() == status);
    }
}
