package com.cloudwebapp.messaging;

public class ExtendedMessage extends UpdateMessage{
    public String field2;
    public String value2;
    public ExtendedMessage(String productId, String field, Object value, String field2, String value2) {
        super(productId, field, value);
        this.field2 = field2;
        this.value2 = value2;
    }
}
