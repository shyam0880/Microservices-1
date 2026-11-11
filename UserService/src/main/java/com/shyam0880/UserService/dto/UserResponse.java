package com.shyam0880.UserService.dto;

public class UserResponse {
    private Long id;
    private String name;
    private String phoneNo;
    private String address;

    public UserResponse() {
    }

    public UserResponse(Long id, String name, String phoneNo, String address) {
        this.id = id;
        this.name = name;
        this.phoneNo = phoneNo;
        this.address = address;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNo() {
        return phoneNo;
    }

    public void setPhoneNo(String phoneNo) {
        this.phoneNo = phoneNo;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
