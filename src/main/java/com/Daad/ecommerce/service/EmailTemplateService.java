package com.Daad.ecommerce.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.azure.identity.*;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;

@Service
@Slf4j
public class EmailTemplateService {
    private static GraphServiceClient _userClient;

    @Value("${microsoft.graph.client-id}")
    private String clientId;

    @Value("${microsoft.graph.tenant-id}")
    private String tenantId;

    @Value("${microsoft.graph.secret}")
    private String clientSecret;

    @Value("${microsoft.graph.sender-email}")
    private String userEmail;

    @Autowired
    private TemplateEngine templateEngine;

    public void initializeGraphForUserAuth() {
        final String[] graphUserScopes = new String[] { "https://graph.microsoft.com/.default" };

        // Authenticate and get a credential object
        ClientSecretCredential credential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build();

        _userClient = new GraphServiceClient(credential, graphUserScopes);
    }


    public void sendHtmlEmail(String to, String subject, String templateName, Map<String, Object> variables)  {
        try {

            if(_userClient == null) {
                initializeGraphForUserAuth();
            }

            Context context = new Context();
            if (variables != null) {
                context.setVariables(variables);
            }

            String htmlContent = templateEngine.process("emails/" + templateName, context);

            // Create the email message
            Message message = new Message();
            message.setSubject(subject);

            ItemBody body = new ItemBody();
            body.setContentType(BodyType.Html);
            body.setContent(htmlContent);
            message.setBody(body);

            // Set recipient
            Recipient toRecipient = new Recipient();
            EmailAddress emailAddress = new EmailAddress();
            emailAddress.setAddress(to); // The actual recipient email address
            toRecipient.setEmailAddress(emailAddress);
            message.setToRecipients(List.of(toRecipient));

            // Create the request body for the sendMail action
            SendMailPostRequestBody sendMailPostRequestBody = new SendMailPostRequestBody();
            sendMailPostRequestBody.setMessage(message);
            sendMailPostRequestBody.setSaveToSentItems(true); // Optional: saves a copy to the Sent Items folder

        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }


    public void sendTextEmail(String to, String subject, String content) throws Exception {

        if(_userClient == null) {
            initializeGraphForUserAuth();
        }

        // Create the email message
        Message message = new Message();
        message.setSubject(subject);

        ItemBody body = new ItemBody();
        body.setContentType(BodyType.Text);
        body.setContent(content);
        message.setBody(body);

        // Set recipient
        Recipient toRecipient = new Recipient();
        EmailAddress emailAddress = new EmailAddress();
        emailAddress.setAddress(to); // The actual recipient email address
        toRecipient.setEmailAddress(emailAddress);
        message.setToRecipients(List.of(toRecipient));

        // Create the request body for the sendMail action
        SendMailPostRequestBody sendMailPostRequestBody = new SendMailPostRequestBody();
        sendMailPostRequestBody.setMessage(message);
        sendMailPostRequestBody.setSaveToSentItems(true); // Optional: saves a copy to the Sent Items folder

        try {
            // Send the message using the specific user's mailbox
            _userClient.users().byUserId(userEmail).sendMail().post(sendMailPostRequestBody);
        } catch (Exception e) {
            log.error("Failed to send email: {}", e.getMessage());
        }
    }
}
