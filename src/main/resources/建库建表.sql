create table user(
                     id int primary key auto_increment,
                     username varchar (20) not null unique,
                     name varchar (20) not null unique,
                     password varchar (120) not null,
                     role varchar(20)not null default 'ROLE_USER');

create table message(
                        id int primary key auto_increment,
                        sender_id int ,
                        sender varchar (20) not null,
                        receiver varchar (20) ,
                        message text not null,
                        sender_ip VARCHAR(45) DEFAULT NULL,
                        send_time timestamp not null,
                        file_name VARCHAR(255) DEFAULT NULL,
                        file_path VARCHAR(500) DEFAULT NULL,
                        file_size BIGINT DEFAULT NULL,
                        message_type VARCHAR(20) DEFAULT NULL);

ALTER TABLE message
    ADD CONSTRAINT message_ibfk_1
        FOREIGN KEY (sender_id) REFERENCES user(id)
            ON DELETE SET NULL
            ON UPDATE CASCADE;

DELIMITER $$
CREATE TRIGGER trg_user_name_update
    AFTER UPDATE ON user
    FOR EACH ROW
BEGIN
    IF OLD.name != NEW.name THEN
        UPDATE message SET sender = NEW.name WHERE sender = OLD.name;
    END IF;
END$$
DELIMITER ;

