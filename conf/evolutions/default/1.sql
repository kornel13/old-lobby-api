# --- !Ups

create table if not exists "USERS" ("USER_NAME" VARCHAR NOT NULL PRIMARY KEY,"PASS_HASH" VARCHAR NOT NULL,"ROLE" VARCHAR NOT NULL);
create table if not exists "TABLE" ("PRIMARY_KEY" BIGINT NOT NULL PRIMARY KEY,"ID" BIGINT NOT NULL,"NAME" VARCHAR NOT NULL,"PARTICIPANTS" INTEGER NOT NULL);

# --- !Downs

drop table if exists "USERS";
drop table if exists "TABLE";