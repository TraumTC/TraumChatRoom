package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.Message;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageMapper {
    public List<Message> findAll();
    public List<Message> findBySender(String sender);
    public List<Message> findByReceiver(String receiver);
    public List<Message> findBySenderAndReceiver(String sender, String receiver);
    public List<Message> findByTime(LocalDateTime time);
    public int addMessage(Message message);
    public int deleteMessage(Integer id);
    public List<Message> findByThreeDays();
}
