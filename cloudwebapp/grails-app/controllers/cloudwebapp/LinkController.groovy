package cloudwebapp

import java.nio.ByteBuffer

class LinkController {

    RestfulInterfaceService restfulInterfaceService

    def auth() {
        String NVRSESSIONID = restfulInterfaceService.authenticate("https://192.168.0.29:443")
        render NVRSESSIONID
    }

    def proxy() {
        restfulInterfaceService.sendRequest(request, response)
    }


    private String log(ByteBuffer buf) {
        int position = buf.position()
        byte[] dataBytes = new byte[buf.limit()];
        for (int i = 0; i < buf.limit(); ++i)
            dataBytes[i] = buf.get();
        buf.position(position);
        return new String(dataBytes);
    }
}
