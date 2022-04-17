package cloudwebapp

import grails.gorm.transactions.Transactional

@Transactional
class UtilsService {
    public final String passwordRegex = /^[A-Za-z0-9][A-Za-z0-9(){\[1*£$\\\]}=@~?^]{7,31}$/
    public final String emailRegex = /^([a-zA-Z0-9_\-\.]+)@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.)|(([a-zA-Z0-9\-]+\.)+))([a-zA-Z]{2,4}|[0-9]{1,3})(\]?)$/
}
