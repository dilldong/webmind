package org.mind.framework.mail;

import org.mind.framework.mail.MailAbstract.SendMailType;
import org.mind.framework.util.PropertiesUtils;

public class MailHead {

    private static final String DEFAULT_TARGET =
            PropertiesUtils.getProperties().getProperty("email.default.target");

    private static final String DEFAULT_FROM =
            PropertiesUtils.getProperties().getProperty("email.default.from");

    private String from;
    private String address;
    private String subject;
    private Object body;
    private SendMailType mailType = SendMailType.HTML;
    private String templateName;


    public String getFrom() {
        if (from != null)
            return from;

        return DEFAULT_FROM;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getAddress() {
        if (address != null)
            return address;

        return DEFAULT_TARGET;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public MailAbstract.SendMailType getMailType() {
        return mailType;
    }

    public void setMailType(MailAbstract.SendMailType mailType) {
        this.mailType = mailType;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Object getBody() {
        return body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public String getTemplateName() {
        return templateName;
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

}
