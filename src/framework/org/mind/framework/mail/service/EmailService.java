package org.mind.framework.mail.service;

import org.mind.framework.mail.MailHead;
import org.springframework.mail.javamail.JavaMailSender;

public interface EmailService {

    void send(MailHead head);

    void send(MailHead head, JavaMailSender sender);

    void send(MailHead head, JavaMailSender sender, Object velocityEngine);

}