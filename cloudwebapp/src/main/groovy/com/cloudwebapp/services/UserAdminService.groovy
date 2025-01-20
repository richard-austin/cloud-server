package com.cloudwebapp.services

import com.cloudwebapp.commands.AddOrUpdateActiveMQCredsCmd
import com.cloudwebapp.commands.AdminChangeEmailCommand
import com.cloudwebapp.commands.AdminChangePasswordCommand
import com.cloudwebapp.commands.ChangeEmailCommand
import com.cloudwebapp.commands.ChangePasswordCommand
import com.cloudwebapp.commands.DeleteAccountCommand
import com.cloudwebapp.commands.ResetPasswordCommand
import com.cloudwebapp.commands.SendResetPasswordLinkCommand
import com.cloudwebapp.commands.SetAccountEnabledStatusCommand
import com.cloudwebapp.dao.UserRepository
import com.cloudwebapp.enums.PassFail
import com.cloudwebapp.interfaceobjects.ObjectCommandResponse
import com.cloudwebapp.model.User
import com.cloudwebapp.utils.ResetPasswordParameterTimerTask
import com.google.gson.JsonObject
import com.proxy.CloudProperties
import jakarta.transaction.Transactional
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.configurationprocessor.json.JSONObject
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.Transport
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.Authenticator
import jakarta.mail.PasswordAuthentication

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

@Service
class UserAdminService {
    @Autowired
    LogService logService
    @Autowired
    CloudService cloudService
    @Autowired
    UserRepository userRepo
    @Autowired
    UtilsService utilsService
    @Autowired
    SimpMessagingTemplate brokerMessagingTemplate
    @Autowired
    PasswordEncoder passwordEncoder

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
    @Transactional
    ObjectCommandResponse changePassword(ChangePasswordCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication()
            String userName = auth.getName()

            User user = userRepo.findByUsername(userName)
            user.setPassword(passwordEncoder.encode(cmd.getNewPassword()))
            userRepo.save(user)
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in changePassword: " + ex.getCause() + ' ' + ex.getMessage())
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
    @Transactional
    ObjectCommandResponse resetPassword(ResetPasswordCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            String email = passwordResetParameterMap.get(cmd.getUniqueId())
            if (email != null) {
                User user = userRepo.findByEmail(email)
                if (user != null) {
                    user.setPassword(passwordEncoder.encode(cmd.newPassword))
                    userRepo.save(user)
                    passwordResetParameterMap.remove(cmd.uniqueId)
                    Timer timer = timerMap.get(cmd.uniqueId)
                    timer.cancel()
                    timerMap.remove(cmd.uniqueId)
                } else {
                    logService.cloud.error "No user for email address ${email}"
                    result.status = PassFail.FAIL
                    result.error = "Invalid email for this password reset link"
                }
            } else {
                logService.cloud.error "No email address for uniqueId ${cmd.uniqueId}"
                result.status = PassFail.FAIL
                result.error = "Invalid password reset link"
            }
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in resetPassword: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    /**
     * setAccountEnabledStatus: Enable/disable a user account
     * @param cmd : Command object containing the username and the enabled status
     * @return: Response object
     */
    ObjectCommandResponse setAccountEnabledStatus(SetAccountEnabledStatusCommand cmd) {
        ObjectCommandResponse response = new ObjectCommandResponse()
        try {
            User user = userRepo.findByUsername(cmd.username)
            if (user != null) {
                user.setEnabled(cmd.accountEnabled)
                userRepo.save(user)
                brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update)
            } else {
                response.status = PassFail.FAIL
                response.error = "Could not find user ${cmd.username}"
                logService.cloud.error("Error in setAccountEnabledStatus: ${response.error}")
            }
        }
        catch (Exception ex) {
            response.status = PassFail.FAIL
            response.error = "${ex.getClass().getName()} in setAccountEnabledStatus: ${ex.getMessage()}"
            logService.cloud.error("Error in setAccountEnabledStatus: ${response.error}")
        }
        return response
    }

    ObjectCommandResponse adminChangePassword(AdminChangePasswordCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            User user = userRepo.findByUsername(cmd.username)
            user.setPassword(passwordEncoder.encode(cmd.password))
            userRepo.save(user)
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in adminChangePassword: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    ObjectCommandResponse adminChangeEmail(AdminChangeEmailCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            User user = userRepo.findByUsername(cmd.username)
            user.setEmail(cmd.email)
            userRepo.save(user)
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update)
        }
        catch (Exception ex) {
            logService.cloud.error("${ex.getClass().getName()} in adminChangeEmail: ${ex.getCause()} ${ex.getMessage()}")
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    /**
     * getEmail
     * @return: Current users email address
     */
    ObjectCommandResponse getEmail() {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            def principal = springSecurityService.getPrincipal()
            String userName = principal.getUsername()

            User user = User.findByUsername(userName)

            result.responseObject = [email: user.getEmail()]
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in getEmail: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    /**
     * changeEmail: Change the admin users email
     * @param cmd
     * @return
     */
    ObjectCommandResponse changeEmail(ChangeEmailCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication()
            String userName = auth.getName()

            User user = userRepo.findByUsername(userName)
            user.setEmail(cmd.getNewEmail())
            userRepo.save(user)
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in changeEmail: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    @Transactional
    ObjectCommandResponse adminDeleteAccount(DeleteAccountCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            User user = userRepo.findByUsername(cmd.username)

            userRepo.delete(user)
            brokerMessagingTemplate.convertAndSend("/topic/accountUpdates", update)
        }
        catch (Exception ex) {
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
        catch (Exception ex) {
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
        catch (Exception ex) {
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

    ObjectCommandResponse addOrUpdateActiveMQCreds(AddOrUpdateActiveMQCredsCmd cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            CloudProperties props = CloudProperties.getInstance()
            props.setCloudCreds(cmd.username, cmd.password, cmd.mqHost)

            // Stop and start the ActiveMQ connection so it picks up the new credentials
            cloudService.stop()
            final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1)
            executor.schedule(new Runnable() {
                @Override
                void run() {
                    cloudService.start()
                }
            }, 3, TimeUnit.SECONDS)
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in createAccount: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    ObjectCommandResponse hasActiveMQCreds() {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            CloudProperties props = CloudProperties.getInstance()
            JsonObject creds = props.getCloudCreds()
            final String mqHost = creds.get("mqHost")?.getAsString()
            result.responseObject = "{\"hasActiveMQCreds\": ${creds.get("mqUser").getAsString() != ""}, \"mqHost\": ${mqHost == null ? "\"<none>\"" : "\"$mqHost\""}}"
        }
        catch (Exception ex) {
            logService.cloud.error("Exception in hasActiveMQCreds: " + ex.getCause() + ' ' + ex.getMessage())
            result.status = PassFail.FAIL
            result.error = ex.getMessage()
        }
        return result
    }

    private def sendEmail(String email, String idStr, String clientUri) {
        def smtpData = utilsService.getSMTPConfigData()

        def auth = smtpData.auth
        def enable = smtpData.enableStartTLS
        def protocols = smtpData.sslProtocols
        def host = smtpData.host
        def port = smtpData.port
        def trust = smtpData.sslTrust
        String username = smtpData.username
        String password = smtpData.password
        String fromAddress = smtpData.fromAddress

        User user = User.findByEmail(email)
        if (user != null) {
            Properties prop = new Properties()
            prop.put("mail.smtp.auth", auth.toBoolean())
            prop.put("mail.smtp.starttls.enable", enable)
            prop.put("mail.smtp.ssl.protocols", protocols)
            prop.put("mail.smtp.host", host)
            prop.put("mail.smtp.port", port.toInteger())
            prop.put("mail.smtp.ssl.trust", trust)

            Session session = Session.getDefaultInstance(prop, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password)
                }
            })

            Message message = new MimeMessage(session)
            message.setFrom(new InternetAddress(fromAddress))
            message.setRecipients(
                    Message.RecipientType.TO, InternetAddress.parse(email))
            message.setSubject("Reset Password")

            String msg = "Dear ${user.username}." +
                    "<h2>Reset Password</h2>" +
                    "A Cloud Service password reset link was requested. If this was not you, please ignore this email.<br>" +
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
