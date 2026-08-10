package com.minipay.infra.notify;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 邮件通知（163 SMTP）：异步发送、尽力而为，失败只告警不影响业务主链路。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.notify.email.from}")
    private String from;

    @Value("${app.notify.email.to}")
    private String to;

    @Async("emailExecutor")
    public void sendNotify(String subject, String content) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(content);
            mailSender.send(message);
            log.info("邮件发送成功 to={}, subject={}", to, subject);
        } catch (Exception e) {
            log.error("[告警] 邮件发送失败 to={}, subject={}", to, subject, e);
        }
    }
}
