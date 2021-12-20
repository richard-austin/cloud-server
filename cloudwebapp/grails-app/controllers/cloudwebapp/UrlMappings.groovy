package cloudwebapp

class UrlMappings {

    static mappings = {
        "/$controller/$action?/$id?(.$format)?"{
            constraints {
                // apply constraints here
            }
        }
        "/link/proxy/*/?$id?"(controller: 'link', action: 'proxy')
        "/link/proxy/*/*/?$id?"(controller: 'link', action: 'proxy')
        "/link/proxy/*/*/*/?$id?"(controller: 'link', action: 'proxy')
        "/link/proxy/*/*/*/*/?$id?"(controller: 'link', action: 'proxy')
        "/link/proxy/*/*/*/*/*/?$id?"(controller: 'link', action: 'proxy')
        "/link/proxy/*/*/*/*/*/*/?$id?"(controller: 'link', action: 'proxy')

        "/"(view:"/index")
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
