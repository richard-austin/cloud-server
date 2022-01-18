const PROXY_CONFIG = [
  {  context: [
      "/application",
      "/assets",
      "/cam",
      "/motion",
      "/onvif",
      "/user",
      "/utils",
      "/recording",
      "/live",
      "/dc"
    ],
    ws: true,
    target: "http://localhost:8083/",
    changeOrigin: false,
    secure: false
  },
  {
    context: [
      "/login",
      "/logoff"
    ],
    ws: false,
    target: "http://localhost:8086",
    changeOrigin: false,
    secure: false
  }
]

module.exports = PROXY_CONFIG;
