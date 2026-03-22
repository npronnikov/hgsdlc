-- liquibase formatted sql

-- changeset auth:026
create table if not exists user_roles (
    user_id uuid not null,
    role varchar(64) not null,
    constraint pk_user_roles primary key (user_id, role),
    constraint fk_user_roles_user foreign key (user_id) references users(id) on delete cascade
);

create index if not exists idx_user_roles_role on user_roles(role);
