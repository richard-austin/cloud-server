grails.plugin.springsecurity.logout.handlerNames = [
		'rememberMeServices',
		'securityContextLogoutHandler',
		'authenticationSuccessHandler',
		'authenticationFailureHandler']

//This is needed to turn-on the generation of springsecurity events so that logins and logouts may be audited
grails.plugin.springsecurity.useSecurityEventListener          = true

// Added by the Spring Security Core plugin:
grails.plugin.springsecurity.userLookup.userDomainClassName = 'cloudservice.User'
grails.plugin.springsecurity.userLookup.authorityJoinClassName = 'cloudservice.UserRole'
grails.plugin.springsecurity.authority.className = 'cloudservice.Role'
grails.plugin.springsecurity.controllerAnnotations.staticRules = [
	[pattern: '/',               access: ['permitAll']],
	[pattern: '/error',          access: ['permitAll']],
	[pattern: '/index',          access: ['permitAll']],
	[pattern: '/index.gsp',      access: ['permitAll']],
	[pattern: '/denied',         access: ['permitAll']],
	[pattern: '/shutdown',       access: ['permitAll']],
	[pattern: '/assets/**',      access: ['permitAll']],
	[pattern: '/**/js/**',       access: ['permitAll']],
	[pattern: '/**/css/**',      access: ['permitAll']],
	[pattern: '/**/images/**',   access: ['permitAll']],
	[pattern: '/**/favicon.ico', access: ['permitAll']],
	[pattern: '/**/index.html',  access: ['permitAll']],
	[pattern: '/#/**',           access: ['permitAll']],
	[pattern: '/**/*.js',        access: ['permitAll']],
	[pattern: '/**/*.css',       access: ['permitAll']],
	[pattern: '/**/*.ttf',       access: ['permitAll']],
	[pattern: '/**/*.woff2',     access: ['permitAll']]
]

grails.plugin.springsecurity.filterChain.chainMap = [
	[pattern: '/assets/**',      filters: 'none'],
	[pattern: '/**/js/**',       filters: 'none'],
	[pattern: '/**/css/**',      filters: 'none'],
	[pattern: '/**/images/**',   filters: 'none'],
	[pattern: '/**/favicon.ico', filters: 'none'],
	[pattern: '/**',             filters: 'JOINED_FILTERS']
]

