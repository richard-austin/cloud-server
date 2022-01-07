const PROXY_CONFIG = [
  {
    context: [
      "/application",
      "/assets",
      "/cam",
      "/motion",
      "/onvif",
      "/user",
      "/utils",
      "/recording",
      "/live",
      "/login"
    ],
    target: "http://localhost:8083",
    secure: false
  }
]

module.exports = PROXY_CONFIG;
