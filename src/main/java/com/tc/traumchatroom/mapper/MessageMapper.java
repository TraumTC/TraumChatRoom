package com.tc.traumchatroom.mapper;

import com.tc.traumchatroom.entity.Message;
import org.apache.ibatis.annotations.Param;

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
    public List<Message> findByPrivateThreeDays(@Param("name1") String name1, @Param("name2") String name2);
    public void insertMessage(Message message);
}
