package com.example.board.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.example.board.entity.SmsNotificationLog;
import com.example.board.entity.SmsStatus;
import com.example.board.entity.User;
import com.example.board.repository.SmsNotificationLogRepository;

/**
 * Twilio Programmable SMS 연동. Trial 계정 기준으로는 Twilio 콘솔에서 인증한 수신번호로만 발송 가능하다.
 */
@Component
public class TwilioSmsSender implements SmsSender {

    private static final Logger log = LoggerFactory.getLogger(TwilioSmsSender.class);
    private static final String BASE_URL = "https://api.twilio.com/2010-04-01";
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_DELAY_MS = 500;

    private final RestClient restClient;
    private final SmsNotificationLogRepository smsNotificationLogRepository;
    private final boolean enabled;
    private final String accountSid;
    private final String authToken;
    private final String fromNumber;

    public TwilioSmsSender(
            RestClient.Builder restClientBuilder,
            SmsNotificationLogRepository smsNotificationLogRepository,
            @Value("${sms.enabled:false}") boolean enabled,
            @Value("${sms.twilio.account-sid:}") String accountSid,
            @Value("${sms.twilio.auth-token:}") String authToken,
            @Value("${sms.twilio.from-number:}") String fromNumber) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.smsNotificationLogRepository = smsNotificationLogRepository;
        this.enabled = enabled;
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.fromNumber = fromNumber;
    }

    @Override
    public void send(User recipient, String message) {
        if (!enabled) {
            log.debug("SMS 발송이 비활성화되어 있어 스킵합니다. recipientUserId={}", recipient.getId());
            return;
        }

        Exception lastError = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                callTwilio(recipient.getPhoneNumber(), message);
                saveLog(recipient, message, SmsStatus.SUCCESS, null);
                return;
            } catch (Exception e) {
                lastError = e;
                log.warn("SMS 발송 실패 ({}/{}회 시도): {}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleepBeforeRetry();
                }
            }
        }
        saveLog(recipient, message, SmsStatus.FAILED, lastError.getMessage());
    }

    private void callTwilio(String to, String message) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", to);
        form.add("From", fromNumber);
        form.add("Body", message);

        restClient.post()
                .uri("/Accounts/{sid}/Messages.json", accountSid)
                .headers(headers -> headers.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    private void saveLog(User recipient, String message, SmsStatus status, String errorMessage) {
        SmsNotificationLog logEntry = SmsNotificationLog.builder()
                .recipientUserId(recipient.getId())
                .phoneNumber(recipient.getPhoneNumber())
                .message(message)
                .status(status)
                .errorMessage(errorMessage)
                .build();
        smsNotificationLogRepository.save(logEntry);
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
