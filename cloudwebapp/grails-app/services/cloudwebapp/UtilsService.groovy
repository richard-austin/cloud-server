package cloudwebapp

import grails.gorm.transactions.Transactional

import java.util.regex.Pattern

@Transactional
class UtilsService {
    String passwordRegex = /^[A-Za-z0-9][A-Za-z0-9(){\[1*£$\\\]}=@~?^]{7,31}$/
}
