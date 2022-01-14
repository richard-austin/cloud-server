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
    ],
    ws: true,
    target: "http://localhost:8083/",
    changeOrigin: true,
    secure: false
  },
  {
    context: ["/"],
    changeOrigin: true,
    target: "http://localhost:8086/",
    secure: false
  }
]

module.exports = PROXY_CONFIG;
