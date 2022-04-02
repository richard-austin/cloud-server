package cloudwebapp

import cloudservice.Role
import cloudservice.User
import cloudservice.UserRole
import cloudservice.commands.AdminChangeEmailCommand
import cloudservice.commands.AdminChangePasswordCommand
import cloudservice.commands.ChangePasswordCommand
import cloudservice.commands.DeleteAccountCommand
import cloudservice.commands.ResetPasswordCommand
import cloudservice.commands.SendResetPasswordLinkCommand
import cloudservice.commands.SetAccountEnabledStatusCommand
import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import grails.gorm.transactions.Transactional
import grails.plugin.springsecurity.SpringSecurityService
import org.grails.web.json.JSONObject
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.context.SecurityContextHolder

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
    SimpMessagingTemplate brokerMessagingTemplate
    final String update = new JSONObject()
            .put("message", "update")
            .toString()


    final private resetPasswordParameterTimeout = 20 * 60 * 1000 // 20 minutes

    final private Map<String, String> passwordResetParameterMap = new ConcurrentHashMap<>()
    final private Map<String, Timer> timerMap = new ConcurrentHashMap<>()

    /**
     * changePassword: Change the password while logged in
     * @param cmd
     * @return
     */
    ObjectCommandResponse changePassword(ChangePasswordCommand cmd) {
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
            logService.cloud.error("Exception in changePassword: "+ex.getCause()+ ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }

        return result
    }

    /**
     * resetPassword; Reset the password from an email link
     * @param cmd
     * @return
     */
    ObjectCommandResponse resetPassword(ResetPasswordCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            String email = passwordResetParameterMap.get(cmd.getUniqueId())
            if(email != null) {
                User user = User.findByEmail(email)
                if(user != null) {
                    user.setPassword(cmd.newPassword)
                    user.save()
                    passwordResetParameterMap.remove(cmd.uniqueId)
                    Timer timer = timerMap.get(cmd.uniqueId)
                    timer.cancel()
                    timerMap.remove(cmd.uniqueId)
                }
                else
                {
                    logService.cloud.error "No user for email address ${email}"
                    result.status = PassFail.FAIL
                    result.error = "Invalid email for this password reset link"
                }
            }
            else
            {
                logService.cloud.error "No email address for uniqueId ${cmd.uniqueId}"
                result.status = PassFail.FAIL
                result.error = "Invalid password reset link"
            }
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
                brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update)
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
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update)
        }
        catch(Exception ex)
        {
            logService.cloud.error("${ex.getClass().getName()} in adminChangeEmail: ${ex.getCause()} ${ex.getMessage()}")
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    ObjectCommandResponse adminDeleteAccount(DeleteAccountCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            User user = User.findByUsername(cmd.username)
            UserRole userRole = UserRole.findByUser(user)
            userRole.delete(flush: true)
            user.delete(flush: true)
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update)
        }
        catch(Exception ex)
        {
            logService.cloud.error("${ex.getClass().getName()} in adminDeleteAccount: ${ex.getCause()} ${ex.getMessage()}")
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    ObjectCommandResponse sendResetPasswordLink(SendResetPasswordLinkCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            String uniqueId = generateRandomString()
            passwordResetParameterMap.put(uniqueId, cmd.email)
            ResetPasswordParameterTimerTask task = new ResetPasswordParameterTimerTask(uniqueId, passwordResetParameterMap, timerMap)
            Timer timer = new Timer(uniqueId)
            timer.schedule(task, resetPasswordParameterTimeout)
            timerMap.put(uniqueId, timer)

            sendEmail(cmd.getEmail(), uniqueId, cmd.getClientUri())
        }
        catch(Exception ex)
        {
            logService.cloud.error("${ex.getClass().getName()} in sendResetPasswordLink: ${ex.getCause()} ${ex.getMessage()}")
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    ObjectCommandResponse getUserAuthorities() {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            result.responseObject = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
        }
        catch (Exception ex)
        {
            logService.cloud.error("${ex.getClass().getName()} in getUserAuthorities: ${ex.getCause()} ${ex.getMessage()}")
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }

        return result
    }

    private static String generateRandomString() {
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

    private static def sendEmail(String email, String idStr, String clientUri)
    {
        User user = User.findByEmail(email)
        if(user != null) {
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
            message.setFrom(new InternetAddress("noreply@cctvcloud.com"))
            message.setRecipients(
                    Message.RecipientType.TO, InternetAddress.parse(email))
            message.setSubject("Reset Password")

            String msg = "Dear ${user.username}." +
                    "<h2>Reset Password</h2>" +
                    "A Cloud Service password reset link was requested. If this was not you, please ignore this email.<br>"+
                    "Please click <a href=\"" + clientUri + "/#/resetpassword/${idStr}\">here</a> to open your browser and reset your password."

            MimeBodyPart mimeBodyPart = new MimeBodyPart()
            mimeBodyPart.setContent(msg, "text/html; charset=utf-8")

            Multipart multipart = new MimeMultipart()
            multipart.addBodyPart(mimeBodyPart)

            message.setContent(multipart)

            Transport.send(message)
        }
    }
}
