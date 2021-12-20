package com.proxy;

import java.util.*;

class HttpMessage extends HashMap<String, List<String>> {
    final byte[] httpMessage;
    final byte[] crlfcrlf = {'\r', '\n','\r', '\n'};
    final byte [] crlf = {'\r', '\n'};
    final byte[] colon = {':'};
    String firstLine;

    HttpMessage(byte[] httpMessage)
    {
        this.httpMessage = httpMessage;
    }

    boolean buildHeaders()
    {
        boolean retVal = true;

        int idxCrLf = indexOf(crlf, 0);
        if(idxCrLf != -1) {
            firstLine = new String(Arrays.copyOfRange(httpMessage, 0, idxCrLf));
            int idxEndOfHeaders = indexOf(crlfcrlf, 0);
            if (idxEndOfHeaders != -1) {
                for (int baseIdx = idxCrLf+crlf.length, i = indexOf(crlf, idxCrLf+crlf.length); i < idxEndOfHeaders+crlf.length; baseIdx = i+crlf.length, i = indexOf(crlf, i + crlf.length)) {
                    int idxOfColon = indexOf(colon, baseIdx);
                    String headerName = new String(Arrays.copyOfRange(httpMessage, baseIdx, idxOfColon));
                    String headerValue = new String(Arrays.copyOfRange(httpMessage, idxOfColon + 2, i));
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

    List<String> getHeader(String name)
    {
        return get(name);
    }

    Set<String> getHeaderNames()
    {
        return keySet();
    }

    byte[] getMessageBody()
    {
        int idxMsgBodyStart = indexOf(crlfcrlf, 0)+crlfcrlf.length;
        return Arrays.copyOfRange(httpMessage, idxMsgBodyStart, httpMessage.length);
    }

    private int indexOf(byte[] smallerArray, int startIdx) {
        for(int i = startIdx; i < httpMessage.length - smallerArray.length+1; ++i) {
            boolean found = true;
            for(int j = 0; j < smallerArray.length; ++j) {
                if (httpMessage[i+j] != smallerArray[j]) {
                    found = false;
                    break;
                }
            }
            if (found) return i;
        }
        return -1;
    }
}