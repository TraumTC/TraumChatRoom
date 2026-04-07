package com.tc.traumchatroom.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Message {
    private Integer id;

    private Integer senderId;
//    发送者昵称
    private String sender;
//    接收者用户名
    private String receiver;
    private String message;
    private LocalDateTime sendTime;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String messageType;
    private String senderIp;

    public Message(Integer id, Integer senderId, String sender, String receiver, String message, LocalDateTime sendTime,String senderIp) {
        this.id = id;
        this.senderId = senderId;
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
        this.sendTime = sendTime;
        this.senderIp = senderIp;
    }



}
