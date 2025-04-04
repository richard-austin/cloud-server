const PROXY_CONFIG = [
  {  context: [
      "/application/",
      "/assets/",
      "/cam/",
      "/motion/",
      "/onvif/",
      "/user/",
      "/utils/",
      "/recording/",
      "/wifiUtils",
      "/cloudProxy",
      "/ptz/",
      "/stomp",
      "/ws/",
      "/dc",
      "/cua",
      "/audio"
    ],
    ws: true,
    target: "http://localhost:8083/",
    changeOrigin: false,
    secure: false
  },
  {
    context: [
      "/login",
      "/logout",
      "/cloud",
      "/cloudstomp",
    ],
    ws: true,
    target: "http://localhost:8086/",
    changeOrigin: false,
    secure: false
  }
]

module.exports = PROXY_CONFIG;
