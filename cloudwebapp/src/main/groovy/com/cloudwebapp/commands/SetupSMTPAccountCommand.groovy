package com.cloudwebapp.commands

import com.cloudwebapp.services.UtilsService

class SMTPData {
    boolean auth
    String username
    String password
    String confirmPassword
    boolean enableStartTLS
    String sslProtocols
    String sslTrust
    String host
    int port
    String fromAddress
}

class SetupSMTPAccountCommand {
    UtilsService utilsService
    boolean auth
    String username
    String password
    String confirmPassword
    boolean enableStartTLS
    String sslProtocols
    String sslTrust
    String host
    int port
    String fromAddress

    SMTPData getData() {
        return new SMTPData(auth: auth,
                username: username,
                password: password,
                enableStartTLS: enableStartTLS,
                sslProtocols: sslProtocols,
                sslTrust: sslTrust,
                host: host,
                port: port,
                fromAddress: fromAddress)
    }
}
