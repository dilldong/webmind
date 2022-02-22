package org.mind.framework.mail;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.mind.framework.ContextSupport;
import org.mind.framework.exception.NotSupportedException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.ui.velocity.VelocityEngineUtils;

import java.io.IOException;
import java.util.Map;


public class DefaultEmailSupport extends MailAbstract {

    private String templateName;
    protected Object velocityEngine;

    public DefaultEmailSupport() {
        JavaMailSender sender =
                (JavaMailSender)
                        ContextSupport.getBean("mailSender", JavaMailSender.class);

        try {
            this.velocityEngine = ContextSupport.getBean("velocityEngine");
        } catch (NoSuchBeanDefinitionException e) {
            logger.warn(e.getMessage());
        }

        this.setSender(sender);
    }


    @SuppressWarnings({"unchecked"})
    @Override
    public String loadContent(SendMailType mailType) throws VelocityException, IOException {

        switch (mailType) {
            case HTML:
                if (logger.isDebugEnabled())
                    logger.debug("Loading email template...");

                if (this.velocityEngine == null)
                    throw new NotSupportedException("VelocityEngine spring bean is not defined");

                return
                        VelocityEngineUtils.mergeTemplateIntoString(
                                (VelocityEngine) velocityEngine,
                                templateName,
                                defaultCharset,
                                (Map<String, Object>) this.getModel());
            case TEXT:
                if (logger.isDebugEnabled())
                    logger.debug("Loading email text planning...");

                return (String) this.getModel();
        }

        return "";
    }

    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }

    @Override
    public void after() {
        this.templateName = null;
        this.setSubject(null);
        this.setAddress(null);
    }


    public void setVelocityEngine(VelocityEngine velocityEngine) {
        this.velocityEngine = velocityEngine;
    }


}
