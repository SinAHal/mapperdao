[bd]
create table BD (
	id int not null,
	big numeric(38,10),
	bool bit,
	nv nvarchar(50),
	tx text,
	primary key (id)
)

[obd]
create table OBD (
	id int not null,
	big numeric(38,10),
	bool bit,
	nv nvarchar(50),
	bt tinyint,
	small smallint,
	int int,
	long bigint,
	float float,
	[double] numeric(10,5),
	primary key (id)
)

[dates]
create table Dates (
	id int not null,
	localDate datetime,
	time time,
	primary key (id)
)
