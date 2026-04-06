create table user(
                     id int primary key auto_increment,
                     username varchar (20) not null unique,
                     name varchar (20) not null unique,
                     password varchar (120) not null,
                     role varchar(20)not null default 'ROLE_USER');

create table message(
                        id int primary key auto_increment,
                        sender_id int not null,
                        sender varchar (20) not null,
                        receiver varchar (20) ,
                        message text not null,
                        send_time timestamp not null,
                        file_name VARCHAR(255) DEFAULT NULL,
                        file_path VARCHAR(500) DEFAULT NULL,
                        file_size BIGINT DEFAULT NULL,
                        message_type VARCHAR(20) DEFAULT NULL,
                        foreign key (sender_id) references user(id));

