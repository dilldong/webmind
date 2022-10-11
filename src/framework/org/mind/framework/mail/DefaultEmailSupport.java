package org.mind.framework.mail;

import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.velocity.spring.VelocityEngineUtils;
import org.mind.framework.ContextSupport;
import org.mind.framework.exception.NotSupportedException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Map;
import java.util.Objects;

public class DefaultEmailSupport extends MailAbstract {

    @Setter
    private String templateName;

    @Setter
    protected Object velocityEngine;

    public DefaultEmailSupport() {
        this((JavaMailSender) ContextSupport.getBean("mailSender", JavaMailSender.class));
    }

    public DefaultEmailSupport(JavaMailSender sender) {
        this(sender, null);
    }

    public DefaultEmailSupport(JavaMailSender sender, Object velocityEngine) {
        super.setSender(sender);
        this.velocityEngine = velocityEngine;
    }


    @SuppressWarnings({"unchecked"})
    @Override
    public String loadContent(MailType mailType) throws Exception {

        switch (mailType) {
            case HTML:
                if (logger.isDebugEnabled())
                    logger.debug("Loading email template...");

                if (Objects.isNull(this.velocityEngine)) {
                    this.velocityEngine = ContextSupport.getBean("velocityEngine");
                    if (Objects.isNull(this.velocityEngine))
                        throw new NotSupportedException("VelocityEngine spring bean is not defined");
                }

                return
                        VelocityEngineUtils.mergeTemplateIntoString(
                                (org.apache.velocity.app.VelocityEngine) velocityEngine,
                                templateName,
                                defaultCharset,
                                (Map<String, Object>) this.getModel());
            case TEXT:
                if (logger.isDebugEnabled())
                    logger.debug("Loading email text planning...");

                return (String) this.getModel();
        }

        return StringUtils.EMPTY;
    }

    @Override
    public void after() {
        this.templateName = null;
        this.setSubject(null);
        this.setAddress(null);
    }
}
