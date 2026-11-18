package com.cnu.lsrs.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

	private static final Logger log = LoggerFactory.getLogger(MailService.class);

	private final JavaMailSender mailSender;

	public MailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
		this.mailSender = mailSenderProvider.getIfAvailable();
	}

	public void sendVerificationCode(String to, String code) {
		if (mailSender == null) {
			log.info("[mail] SMTP 미설정: {} 주소로 인증 코드 {}를 발송했다고 처리합니다.", to, code);
			return;
		}
		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(to);
		message.setSubject("[LSRS] 이메일 인증 코드");
		message.setText("이메일 인증 코드: " + code);
		mailSender.send(message);
	}
}
