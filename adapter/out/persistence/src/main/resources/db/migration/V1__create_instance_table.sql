create table instance
(
    id                uuid primary key,
    type              varchar(32)              not null,
    name              varchar(255)             not null,
    url               varchar(2048)            not null,
    origin            varchar(32)              not null,
    sandbox           boolean                  not null default false,
    runtime_reference varchar(255),
    registered_at     timestamp with time zone not null,
    constraint uk_instance_type_url unique (type, url)
);

comment on table instance is 'LDES servers and LDIOs managed by LDES-admin';
comment on column instance.origin is 'REGISTERED: added by URL, LOCAL: started by LDES-admin';
comment on column instance.runtime_reference is 'Container id of a LOCAL instance';
