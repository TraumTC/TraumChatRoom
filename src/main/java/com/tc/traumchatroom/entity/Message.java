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
    private String sender;
    private String receiver;
    private String message;
    private LocalDateTime sendTime;



}
