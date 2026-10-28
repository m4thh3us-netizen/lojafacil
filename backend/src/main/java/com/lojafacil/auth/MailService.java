package com.lojafacil.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.lojafacil.shared.config.AppProperties;

/**
 * Envio de e-mails transacionais. Em desenvolvimento, o Docker Compose sobe o Mailpit
 * (http://localhost:8025), que captura as mensagens sem enviá-las de verdade.
 *
 * O envio é assíncrono para que a resposta HTTP não dependa do servidor SMTP.
 * Os logs nunca incluem o corpo do e-mail, pois ele contém links com tokens.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender sender;
    private final AppProperties props;

    public MailService(JavaMailSender sender, AppProperties props) {
        this.sender = sender;
        this.props = props;
    }

    @Async
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.mailFrom());
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            sender.send(msg);
        } catch (MailException e) {
            log.warn("Falha ao enviar e-mail '{}': {}", subject, e.getClass().getSimpleName());
        }
    }
}
