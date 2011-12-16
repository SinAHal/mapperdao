[ddl]
create table company (
	id int not null GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
	name varchar(255) not null,
	constraint pk_company primary key (id)
)
;

create table computer (
	id int not null GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1),
	name varchar(255) not null,
	company_id int,
	constraint pk_computer primary key (id),
	constraint fk_computer_company foreign key (company_id) references company(id) on delete cascade
)
;
