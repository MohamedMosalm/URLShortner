create table if not exists users (
    username varchar(64) primary key,
    password varchar(100) not null
);

create table if not exists links (
    code varchar(32) primary key,
    url varchar(2048) not null,
    owner varchar(64) not null references users(username)
);

create index if not exists links_owner_idx on links (owner);
