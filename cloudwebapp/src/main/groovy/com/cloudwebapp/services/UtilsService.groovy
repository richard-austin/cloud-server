package com.cloudwebapp.services

import cloudservice.commands.SMTPData
import cloudservice.commands.SetupSMTPAccountCommand
import cloudservice.enums.PassFail
import cloudservice.interfaceobjects.ObjectCommandResponse
import com.google.gson.GsonBuilder
import grails.config.Config
import grails.core.GrailsApplication
import grails.gorm.transactions.Transactional

import java.nio.charset.StandardCharsets

@Transactional
class UtilsService {
    public final String passwordRegex = /^[A-Za-z0-9][A-Za-z0-9(){\[1*£$\\\]}=@~?^]{7,31}$/
    public final String emailRegex = /^([a-zA-Z0-9_\-\.]+)@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.)|(([a-zA-Z0-9\-]+\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\]?)$/
    LogService logService
    GrailsApplication grailsApplication

    ObjectCommandResponse setupSMTPClient(SetupSMTPAccountCommand cmd) {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            def gson = new GsonBuilder()
                    .setPrettyPrinting()
                    .create()

            String res = gson.toJson(cmd.getData())
            def writer = new BufferedWriter(new FileWriter("/var/cloud-server/smtp.json"))
            writer.write(res)
            writer.close()
        }
        catch (Exception e) {
            logService.cloud.error "${e.getClass().getName()} in setupSMTPClient: ${e.getMessage()}"
            result.status = PassFail.FAIL
            result.error = "${e.getClass().getName()} -- ${e.getMessage()}"
        }
        return result
    }

    def getSMTPConfigData() {
        Config config = grailsApplication.getConfig()
        def configFileName = config.getProperty("cloud.mail.smtp.configFile")
        File file = new File(configFileName)
        byte[] bytes = file.readBytes()
        String json = new String(bytes, StandardCharsets.UTF_8)
        def gson = new GsonBuilder().create()
        def smtpData = gson.fromJson(json, SMTPData)
        smtpData.password = ""
        return smtpData
    }

    ObjectCommandResponse getSMTPClientParams() {
        ObjectCommandResponse result = new ObjectCommandResponse()
        try {
            result.responseObject = getSMTPConfigData()
        }
        catch (FileNotFoundException fnf) {
            final String noConfigFile = "No existing config file for SMTP client. It will be created when the SMTP parameters are entered and saved."
            logService.cloud.warn(noConfigFile)
            result.status = PassFail.PASS
            result.response = noConfigFile
            result.error = "${fnf.getClass().getName()} -- ${fnf.getMessage()}"
        }
        catch (Exception e) {
            logService.cloud.error "${e.getClass().getName()} in getSMTPClientParams: ${e.getMessage()}"
            result.status = PassFail.FAIL
            result.response = null
            result.error = "${e.getClass().getName()} -- ${e.getMessage()}"
        }
        return result
    }
}

