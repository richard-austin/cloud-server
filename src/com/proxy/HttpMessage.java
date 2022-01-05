package com.proxy;

import java.nio.ByteBuffer;
import java.util.*;

class HttpMessage extends HashMap<String, List<String>> {
    final ByteBuffer httpMessage;
    final byte[] crlfcrlf = {'\r', '\n','\r', '\n'};
    final byte [] crlf = {'\r', '\n'};
    final byte[] colon = {':'};
    final int headersLength;
    final boolean headersBuilt;

    String firstLine;

    HttpMessage(ByteBuffer httpMessage)
    {
        this.httpMessage = httpMessage;
        headersLength = indexOf(crlfcrlf, 0)+crlfcrlf.length;
        if(!(headersBuilt=buildHeaders()))
            System.out.println("INFO: not an HTTP message");
    }

    private boolean buildHeaders()
    {
        boolean retVal = true;

        int idxCrLf = indexOf(crlf, 0);
        if(idxCrLf != -1) {
            firstLine = new String(Arrays.copyOfRange(httpMessage.array(), 0, idxCrLf));
            int idxEndOfHeaders = indexOf(crlfcrlf, 0);
            if (idxEndOfHeaders != -1) {
                for (int baseIdx = idxCrLf+crlf.length, i = indexOf(crlf, idxCrLf+crlf.length); i < idxEndOfHeaders+crlf.length; baseIdx = i+crlf.length, i = indexOf(crlf, i + crlf.length)) {
                    int idxOfColon = indexOf(colon, baseIdx);
                    String headerName = new String(Arrays.copyOfRange(httpMessage.array(), baseIdx, idxOfColon));
                    String headerValue = new String(Arrays.copyOfRange(httpMessage.array(), idxOfColon + 2, i));
                    if(this.get(headerName) == null)
                        put(headerName, new ArrayList<>());
                    get(headerName).add(headerValue);
                }
            } else
                retVal = false;
        }
        else
            retVal = false;

        return retVal;
    }

    String getHeaders()
    {
        StringBuilder sb = new StringBuilder();
        sb.append(firstLine);
        sb.append(new String(crlf));
        forEach((headerName, headervalues)-> {
            headervalues.forEach((headerValue)-> {
                sb.append(headerName);
                sb.append(": ");
                sb.append(headerValue);
                sb.append(new String(crlf));
            });
        });
        sb.append(new String(crlf));
        return sb.toString();
    }

    int getContentLength()
    {
        var cl =this.get("Content-Length");
        if(cl != null)
            return Integer.parseInt(cl.get(0));
        else
            return 0;
    }

    List<String> getHeader(String name)
    {
        return get(name);
    }

    Set<String> getHeaderNames()
    {
        return keySet();
    }

    ByteBuffer getMessageBody()
    {
        int idxMsgBodyStart = indexOf(crlfcrlf, 0)+crlfcrlf.length;
        httpMessage.position(idxMsgBodyStart);
        return httpMessage;
    }

    private int indexOf(byte[] pattern, int startIdx) {
        for(int i = startIdx; i < httpMessage.limit() - pattern.length+1; ++i) {
            boolean found = true;
            for(int j = 0; j < pattern.length; ++j) {
                if (httpMessage.get(i+j) != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }
}
