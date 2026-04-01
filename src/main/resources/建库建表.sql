create database ChatDB;

use ChatDB;

create table User(
                     id int primary key auto_increment,
                     username varchar (10) not null unique,
                     name varchar (20) not null unique,
                     password varchar (120) not null,
                     role varchar(20)not null default 'ROLE_USER');


create table message(
                        id int primary key auto_increment,
                        sender_id int not null,
                        sender varchar (20) not null,
                        receiver varchar (20) not null,
                        message text not null,
                        send_time timestamp not null,
                        foreign key (sender_id) references User(id));

