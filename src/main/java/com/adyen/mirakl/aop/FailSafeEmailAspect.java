package com.adyen.mirakl.aop;

import com.adyen.mirakl.domain.EmailError;
import com.adyen.mirakl.domain.ProcessEmail;
import com.adyen.mirakl.domain.enumeration.EmailState;
import com.adyen.mirakl.repository.EmailErrorsRepository;
import com.adyen.mirakl.repository.ProcessEmailRepository;
import liquibase.util.MD5Util;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;

@Aspect
public class FailSafeEmailAspect {

    private final Logger log = LoggerFactory.getLogger(FailSafeEmailAspect.class);

    @Resource
    private ProcessEmailRepository processEmailRepository;

    @Resource
    private EmailErrorsRepository emailErrorsRepository;

    @Value("${miraklOperator.miraklOperatorEmail}")
    private String bcc;

    @Around("execution(* com.adyen.mirakl.service.MailService.sendEmail(String, String, String, String, boolean, boolean)) && args(to, bcc, subject, content, isMultipart, isHtml)")
    public Object logAround(ProceedingJoinPoint joinPoint, String to, String bcc, String subject, String content, boolean isMultipart, boolean isHtml) throws Throwable {

        String toHash = to + bcc + subject + content + isMultipart + isHtml;
        String emailIdentifier = MD5Util.computeMD5(toHash);
        final ProcessEmail email = processEmailRepository.findOneByEmailIdentifier(emailIdentifier)
            .orElseGet(() -> {
                final ProcessEmail processEmail = new ProcessEmail();
                processEmail.setEmailIdentifier(emailIdentifier);
                processEmail.setTo(to);
                processEmail.setBcc(bcc);
                processEmail.setSubject(subject);
                processEmail.setContent(content);
                processEmail.setMultipart(isMultipart);
                processEmail.setHtml(isHtml);
                return processEmail;
            });

        //save email as processing
        email.setState(EmailState.PROCESSING);
        processEmailRepository.saveAndFlush(email);

        try {
            final Object result = joinPoint.proceed();
            email.setState(EmailState.SENT);
            processEmailRepository.saveAndFlush(email);
            return result;
        }catch (Exception e){
            log.warn("Unable to send email, will save as failed and add the exception");
            addAdditionalError(email, e);
            return null;
        }

    }

    private void addAdditionalError(final ProcessEmail email, final Exception e) {
        final EmailError emailError = new EmailError();
        emailError.setError(e.getMessage());

        email.setState(EmailState.FAILED);
        email.addEmailError(emailError);

        processEmailRepository.saveAndFlush(email);
        emailErrorsRepository.saveAndFlush(emailError);
    }

}
