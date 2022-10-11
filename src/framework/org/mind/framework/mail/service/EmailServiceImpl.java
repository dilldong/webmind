package org.mind.framework.mail.service;

import lombok.Setter;
import org.mind.framework.mail.DefaultEmailSupport;
import org.mind.framework.mail.MailHead;
import org.mind.framework.service.queue.DelegateMessage;
import org.mind.framework.service.queue.QueueService;
import org.springframework.mail.javamail.JavaMailSender;

public class EmailServiceImpl implements EmailService {

    @Setter
    private QueueService queueService;

    @Override
    public void send(MailHead head, JavaMailSender sender, Object velocityEngine) {
        this.queueService.producer(this.initMessage(head, new DefaultEmailSupport(sender, velocityEngine)));
    }

    @Override
    public void send(MailHead head, JavaMailSender sender) {
        this.queueService.producer(this.initMessage(head, new DefaultEmailSupport(sender)));
    }

    @Override
    public void send(MailHead head) {
        this.queueService.producer(this.initMessage(head, new DefaultEmailSupport()));
    }

    protected DelegateMessage initMessage(MailHead head, DefaultEmailSupport defaultSupport) {
        defaultSupport.setFrom(head.getFrom(), head.getPersonal());
        defaultSupport.setTemplateName(head.getTemplateName());
        defaultSupport.setAddress(head.getAddress());
        defaultSupport.setModel(head.getBody());
        defaultSupport.setSubject(head.getSubject());
        defaultSupport.setMailType(head.getMailType());
        return defaultSupport;
    }
}
