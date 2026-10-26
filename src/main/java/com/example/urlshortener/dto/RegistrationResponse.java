package com.example.urlshortener.dto;

/**
 * Registration response.  The accessKey is shown once — the user must save it.
 * Both the access key and the password are required for future sign-in.
 */
public class RegistrationResponse {

    private String owner;
    private String accessKey;
    private String message;

    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
