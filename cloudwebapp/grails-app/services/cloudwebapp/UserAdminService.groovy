package cloudwebapp

import cloudservice.User
import cloudservice.commands.AdminChangeEmailCommand
import cloudservice.commands.AdminChangePasswordCommand
import cloudservice.commands.ResetPasswordCommand
import cloudservice.commands.SendResetPasswordLinkCommand
import cloudservice.commands.SetAccountEnabledStatusCommand
import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService

import javax.mail.Authenticator
import javax.mail.PasswordAuthentication
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart
import java.util.concurrent.ConcurrentHashMap


@Transactional()
class UserAdminService {
    SpringSecurityService springSecurityService
    LogService logService
    UserService userService

    final private resetPasswordParameterTimeout = 20 * 60 * 1000 // 20 minutes

    final private Map<String, String> passwordResetParameterMap = new ConcurrentHashMap<>()
    final private Map<String, Timer> timerMap = new ConcurrentHashMap<>()

    ObjectCommandResponse resetPassword(ResetPasswordCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            def principal = springSecurityService.getPrincipal()
            String userName = principal.getUsername()

            User user = User.findByUsername(userName)
            user.setPassword(cmd.getNewPassword())
            user.save()
        }
        catch(Exception ex)
        {
            logService.cloud.error("Exception in resetPassword: "+ex.getCause()+ ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }

        return result
    }

    /**
     * setAccountEnabledStatus: Enable/disable a user account
     * @param cmd: Command object containing the username and the enabled status
     * @return: Response object
     */
    ObjectCommandResponse setAccountEnabledStatus(SetAccountEnabledStatusCommand cmd) {
        ObjectCommandResponse response = new ObjectCommandResponse()
        try {
            User user = userService.findByUsername(cmd.username)
            if(user != null)
            {
                user.setEnabled(cmd.accountEnabled)
                userService.save(user)
            }
            else
            {
                response.status = PassFail.FAIL
                response.error ="Could not find user ${cmd.username}"
                logService.cloud.error("Error in setAccountEnabledStatus: ${response.error}")
            }
        }
        catch(Exception ex)
        {
            response.status = PassFail.FAIL
            response.error ="${ex.getClass().getName()} in setAccountEnabledStatus: ${ex.getMessage()}"
            logService.cloud.error("Error in setAccountEnabledStatus: ${response.error}")
        }
        return response
    }

    ObjectCommandResponse adminChangePassword(AdminChangePasswordCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            User user = User.findByUsername(cmd.username)
            user.setPassword(cmd.password)
            user.save()
        }
        catch(Exception ex)
        {
            logService.cloud.error("Exception in adminChangePassword: "+ex.getCause()+ ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    ObjectCommandResponse adminChangeEmail(AdminChangeEmailCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            User user = User.findByUsername(cmd.username)
            user.setEmail(cmd.email)
            user.save()
        }
        catch(Exception ex)
        {
            logService.cloud.error("${ex.getClass().getName()} in adminChangeEmail: ${ex.getCause()} ${ex.getMessage()}")
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    ObjectCommandResponse sendResetPasswordLink(SendResetPasswordLinkCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            String uniqueString = generateRandomString()

            passwordResetParameterMap.put(cmd.email, uniqueString)
            ResetPasswordParameterTimerTask task = new ResetPasswordParameterTimerTask(cmd.email, passwordResetParameterMap, timerMap)
            Timer timer = new Timer(uniqueString)
            timer.schedule(task, resetPasswordParameterTimeout)
            timerMap.put(cmd.email, timer)

            sendEmail("richard.david.austin@gmail.com", uniqueString)
        }
        catch(Exception ex)
        {
            logService.cloud.error("${ex.getClass().getName()} in sendResetPasswordLink: ${ex.getCause()} ${ex.getMessage()}")
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    private String generateRandomString() {
        int leftLimit = 48 // numeral '0'
        int rightLimit = 122 // letter 'z'
        int targetStringLength = 212
        Random random = new Random()

        String generatedString = random.ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(targetStringLength)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString()
        System.out.println(generatedString)
        return generatedString
    }

    private def sendEmail(String email, String idStr)
    {
        Properties prop = new Properties()
        prop.put("mail.smtp.auth", true)
        prop.put("mail.smtp.starttls.enable", "true")
        prop.put("mail.smtp.ssl.protocols", "TLSv1.2")
        prop.put("mail.smtp.host", "smtp.virginmedia.com")
        prop.put("mail.smtp.port", "587")
        prop.put("mail.smtp.ssl.trust", "smtp.virginmedia.com")

        Session session = Session.getDefaultInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication("rdaustin@virginmedia.com", "DC10plus")
            }
        })

        Message message = new MimeMessage(session)
        message.setFrom(new InternetAddress("cloud@gmail.com"))
        message.setRecipients(
                Message.RecipientType.TO, InternetAddress.parse(email))
        message.setSubject("Reset Password")

        String msg = "<h2>Reset Password</h2>"+
        "Please click <a href=\"http://localhost:4200/#/resetpassword/${idStr}\">here</a> to reset your password."

        MimeBodyPart mimeBodyPart = new MimeBodyPart()
        mimeBodyPart.setContent(msg, "text/html; charset=utf-8")

        Multipart multipart = new MimeMultipart()
        multipart.addBodyPart(mimeBodyPart)

        message.setContent(multipart)

        Transport.send(message)
    }
}
