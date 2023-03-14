package org.mind.framework.mail;

import org.mind.framework.service.queue.DelegateMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;


public abstract class MailAbstract implements DelegateMessage {
    protected static final Logger logger = LoggerFactory.getLogger(MailAbstract.class);

    private String from;
    private String personal;
    private String subject;
    private String address;
    private Object model;

    private JavaMailSender sender;
    private MailType mailType = MailType.HTML;

    protected String defaultCharset = StandardCharsets.UTF_8.name();

    public enum MailType {
        TEXT,
        HTML
    }

    @Override
    public void process() {
        if (logger.isDebugEnabled())
            logger.debug("Starting send mail service...");
        try {
            String content = this.loadContent(mailType);
            this.send(content);
            this.after();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    public abstract void after();

    public abstract String loadContent(MailType mailType) throws Exception;

    protected void send(String content) throws Exception {
        MimeMessage msg = this.sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, defaultCharset);

        helper.setFrom(from, personal);
        helper.setTo(address);
        helper.setSubject(subject);
        helper.setText(content, true);

        try {
            // javax.activation.UnsupportedDataTypeException: no object DCH for MIME type multipart/mixed;
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            sender.send(msg);
        } catch (MailException e) {
            logger.error(e.getMessage(), e);
        }
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public void setFrom(String from, String personal) {
        setFrom(from);
        this.personal = personal;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }


    public Object getModel() {
        return model;
    }

    public void setModel(Object model) {
        this.model = model;
    }

    public void setMailType(MailType mailType) {
        this.mailType = mailType;
    }

    public void setSender(JavaMailSender sender) {
        this.sender = sender;
    }

    public void setDefaultCharset(String defaultCharset) {
        this.defaultCharset = defaultCharset;
    }

}
