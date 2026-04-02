package com.tc.traumchatroom;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.tc.traumchatroom.mapper")
public class TraumChatRoomApplication {

	public static void main(String[] args) {
		SpringApplication.run(TraumChatRoomApplication.class, args);
	}

}
