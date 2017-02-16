package org.mind.framework.mail;

import java.io.IOException;
import java.util.Map;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.exception.VelocityException;
import org.mind.framework.ContextSupport;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.ui.velocity.VelocityEngineUtils;


public class DefaultEmailSupport extends MailAbstract  {
	
	private String templateName;
	protected VelocityEngine velocityEngine;
	
	public DefaultEmailSupport(){
		JavaMailSender sender = 
			(JavaMailSender)
			ContextSupport.getBean("mailSender", JavaMailSender.class);
		
		this.velocityEngine = 
			(VelocityEngine)
			ContextSupport.getBean("velocityEngine", VelocityEngine.class);
		
		this.setSender(sender);
	}
	
	
	@Override
	@SuppressWarnings("unchecked")
	public String loadContent(SendMailType mailType) throws VelocityException, IOException {
		
		switch(mailType){
			case HTML:
				if(logger.isDebugEnabled())
					logger.debug("Loading email template...");
				
				return
					VelocityEngineUtils.mergeTemplateIntoString(
							this.velocityEngine, 
							templateName, 
							defaultCharset, 
							(Map<String, Object>)this.getModel());
			case TEXT:
				if(logger.isDebugEnabled())
					logger.debug("Loading email text planning...");
				
				return (String)this.getModel();
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
