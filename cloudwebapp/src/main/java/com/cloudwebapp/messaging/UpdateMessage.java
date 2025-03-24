package com.cloudwebapp.messaging;

public class UpdateMessage {
    public final String message = "update";
    public String productId;
    public String field;
    public Object value;
    public UpdateMessage(String productId, String field, Object value) {
        this.productId = productId;
        this.field = field;
        this.value = value;
    }
}
