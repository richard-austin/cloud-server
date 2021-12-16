package cloudwebapp

import javax.servlet.http.HttpServletRequest
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class LinkController {

    RestfulInterfaceService restfulInterfaceService

    def proxy() {
        restfulInterfaceService.authenticate("https://192.168.0.29")

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
