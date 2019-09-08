create table address
(
    type          varchar(10) not null comment '级别',
    id            varchar(20) not null comment '编码' primary key,
    name          varchar(32) not null comment '名称',
    province_id   varchar(20) null comment '省份编码',
    province_name varchar(32) null comment '省份名称',
    city_id       varchar(20) null comment '城市编码',
    city_name     varchar(32) null comment '城市名称',
    county_id     varchar(20) null comment '区县编码',
    county_name   varchar(32) null comment '区县名称',
    town_id       varchar(20) null comment '街道镇编码',
    town_name     varchar(32) null comment '街道镇名称'
);

