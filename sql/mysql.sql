-- 测试初始化脚本
CREATE TABLE `user` (
                        `id` bigint(20) NOT NULL AUTO_INCREMENT,
                        `name` varchar(255) DEFAULT NULL,
                        `phone` varchar(255) DEFAULT NULL,
                        `address` varchar(255) DEFAULT NULL,
                        PRIMARY KEY (`id`)
) ENGINE=MyISAM AUTO_INCREMENT=4 DEFAULT CHARSET=latin1;

INSERT INTO `user`(`id`, `name`, `phone`, `address`) VALUES (1, 'admin', '13212145789', '123456');
INSERT INTO `user`(`id`, `name`, `phone`, `address`) VALUES (2, 'testadmin', '13545421259', '12345');
INSERT INTO `user`(`id`, `name`, `phone`, `address`) VALUES (3, 'sysadmin', '15878945543', '12345');
