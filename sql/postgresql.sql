-- 测试初始化脚本
CREATE TABLE "public"."user"
(
    "id"      varchar(32) COLLATE "pg_catalog"."default" NOT NULL,
    "name"    varchar(255) COLLATE "pg_catalog"."default",
    "phone"   varchar(255) COLLATE "pg_catalog"."default",
    "address" varchar(255) COLLATE "pg_catalog"."default",
    CONSTRAINT "user_pkey" PRIMARY KEY ("id")
);

ALTER TABLE "public"."user"   OWNER TO "basicframe";

INSERT INTO "user"(id, name, phone, address) VALUES (1, 'admin', '13212145789', '123456');
INSERT INTO "user"(id, name, phone, address) VALUES (2, 'testadmin', '13545421259', '12345');
INSERT INTO "user"(id, name, phone, address) VALUES (3, 'sysadmin', '15878945543', '12345');
