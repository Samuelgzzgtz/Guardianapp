-- =============================================================================
-- GuardianApp – Script para proyecto Supabase NUEVO
-- Todos los identificadores en minúsculas para evitar problemas de case en PostgreSQL
-- Ejecutar en: Supabase → SQL Editor → New query → pegar todo → Run
-- =============================================================================

-- =============================================================================
-- SECCIÓN 1: LIMPIEZA
-- =============================================================================
drop table if exists notificacion    cascade;
drop table if exists areacomun       cascade;
drop table if exists tarealimpieza   cascade;
drop table if exists accesolog       cascade;
drop table if exists reserva         cascade;
drop table if exists amenidad        cascade;
drop table if exists cuota           cascade;
drop table if exists aviso           cascade;
drop table if exists reporte         cascade;
drop table if exists usuario         cascade;
drop table if exists unidad          cascade;
drop table if exists rolusuario      cascade;

-- =============================================================================
-- SECCIÓN 2: TABLAS (en orden de dependencia)
-- =============================================================================

create table rolusuario (
    id     serial       primary key,
    nombre varchar(50)  not null
);

create table unidad (
    id     serial       primary key,
    numero varchar(20)  not null,
    torre  varchar(20),
    tipo   varchar(10)  default 'depto' check (tipo in ('casa', 'depto'))
);

create table usuario (
    id            serial        primary key,
    nombre        varchar(200),
    email         varchar(200)  unique,
    fkrolusuario  integer       references rolusuario(id),
    fkunidad      integer       references unidad(id),
    fcmtoken      text,
    fotourl       text
);

create table reporte (
    id            serial        primary key,
    fkusuario     integer       references usuario(id),
    titulo        varchar(200)  not null,
    descripcion   text,
    categoria     varchar(50)   default 'General',
    esurgente     boolean       default false,
    fotourl       text,
    estatus       varchar(20)   default 'Pendiente'
                                check (estatus in ('Pendiente', 'En proceso', 'Resuelto')),
    fechacreacion timestamp     default now()
);

create table aviso (
    id          serial        primary key,
    titulo      varchar(200)  not null,
    descripcion text,
    tono        varchar(20)   default 'primary'
                              check (tono in ('warn', 'primary', 'success'))
);

create table cuota (
    id               serial        primary key,
    fkusuario        integer       references usuario(id),
    monto            numeric(10,2),
    estatus          varchar(20)   default 'pendiente'
                                   check (estatus in ('pendiente', 'pagado', 'vencido')),
    fechavencimiento date,
    periodo          varchar(20)   default 'mensual'
);

create table amenidad (
    id        serial        primary key,
    nombre    varchar(200)  not null,
    horario   varchar(100),
    capacidad integer       default 10
);

create table reserva (
    id               serial      primary key,
    fkusuario        integer     references usuario(id),
    fkamenidad       integer     references amenidad(id),
    fechareservacion date,
    horarioslot      varchar(50),
    estatus          varchar(20) default 'activa'
);

create table accesolog (
    id           serial       primary key,
    fkresidente  integer      references usuario(id),
    fkguardia    integer      references usuario(id),
    direccion    varchar(10)  default 'ENTRADA'
                              check (direccion in ('ENTRADA', 'SALIDA')),
    horaregistro time,
    timestamp    timestamp    default now()
);

create table tarealimpieza (
    id             serial        primary key,
    fkasignado     integer       references usuario(id),
    titulo         varchar(200)  not null,
    area           varchar(100),
    horarioslot    varchar(50),
    prioridad      varchar(10)   default 'normal'
                                 check (prioridad in ('normal', 'alta')),
    estacompletada boolean       default false,
    fecha          date          default current_date
);

create table areacomun (
    id             serial        primary key,
    nombre         varchar(100)  not null,
    sector         varchar(50),
    estatus        varchar(20)   default 'pendiente'
                                 check (estatus in ('listo', 'en_curso', 'pendiente')),
    ultimalimpieza timestamp
);

create table notificacion (
    id            serial        primary key,
    fkusuario     integer       references usuario(id),
    titulo        varchar(200)  not null,
    cuerpo        text,
    estaleida     boolean       default false,
    fechacreacion timestamp     default now()
);

-- =============================================================================
-- SECCIÓN 3: DATOS DE PRUEBA
-- =============================================================================

insert into rolusuario (id, nombre) values
    (1, 'Residente'),
    (2, 'Seguridad'),
    (3, 'Administrador'),
    (4, 'Limpieza');

insert into unidad (numero, torre, tipo) values
    ('Casa #12',  'B', 'casa'),
    ('Depto #8',  'A', 'depto'),
    ('Depto #15', 'C', 'depto'),
    ('Casa #3',   'B', 'casa'),
    ('Depto #22', 'A', 'depto');

insert into amenidad (nombre, horario, capacidad) values
    ('Alberca',  '8am-8pm',   15),
    ('Asador',   '10am-10pm', 20),
    ('Gimnasio', '6am-10pm',  10);

insert into aviso (titulo, descripcion, tono) values
    ('Mantenimiento elevadores',     'Los elevadores estarán fuera de servicio el viernes de 8am a 12pm.', 'warn'),
    ('Bienvenida nuevos residentes', 'Damos la bienvenida a los nuevos residentes del bloque C.',           'primary'),
    ('Pago de cuotas disponible',    'Ya puedes realizar el pago de tu cuota mensual desde la app.',        'success');

-- IMPORTANTE: estos emails deben coincidir con los que crees en Supabase Auth
insert into usuario (id, nombre, email, fkrolusuario) values
    (1, 'Carlos Residente',   'residente@guardianapp.test', 1),
    (2, 'Luis Seguridad',     'seguridad@guardianapp.test', 2),
    (3, 'Ana Administradora', 'admin@guardianapp.test',     3),
    (4, 'Rosa Limpieza',      'limpieza@guardianapp.test',  4);

insert into cuota (fkusuario, monto, estatus, fechavencimiento, periodo) values
    (1, 850.00, 'pendiente', current_date + interval '15 days', 'mensual');

insert into reporte (fkusuario, titulo, descripcion, categoria, estatus) values
    (1, 'Filtración en techo', 'Hay una gotera en el baño principal del depto.', 'Mantenimiento', 'Pendiente');

insert into tarealimpieza (fkasignado, titulo, area, horarioslot, prioridad, estacompletada, fecha) values
    (4, 'Limpiar Vestíbulo',    'Vestíbulo',   '08:00-09:00', 'alta',   false, current_date),
    (4, 'Barrer Escaleras A',   'Escaleras A', '09:00-10:00', 'normal', false, current_date),
    (4, 'Barrer Escaleras B',   'Escaleras B', '10:00-11:00', 'normal', false, current_date),
    (4, 'Limpiar Parqueadero',  'Parqueadero', '11:00-12:00', 'alta',   false, current_date),
    (4, 'Regar Zona Verde',     'Zona verde',  '12:00-13:00', 'normal', false, current_date),
    (4, 'Limpiar Área Piscina', 'Piscina',     '14:00-15:00', 'alta',   false, current_date);

insert into areacomun (nombre, sector, estatus) values
    ('Vestíbulo principal', 'Entrada',  'listo'),
    ('Escaleras Bloque A',  'Bloque A', 'en_curso'),
    ('Escaleras Bloque B',  'Bloque B', 'pendiente'),
    ('Parqueadero',         'Sótano',   'pendiente'),
    ('Zona verde',          'Exterior', 'en_curso'),
    ('Área de piscina',     'Exterior', 'listo');

-- =============================================================================
-- SECCIÓN 4: ROW LEVEL SECURITY
-- =============================================================================

alter table usuario       enable row level security;
alter table reporte       enable row level security;
alter table aviso         enable row level security;
alter table cuota         enable row level security;
alter table accesolog     enable row level security;
alter table tarealimpieza enable row level security;
alter table areacomun     enable row level security;
alter table notificacion  enable row level security;
alter table amenidad      enable row level security;
alter table reserva       enable row level security;

-- usuario
create policy "usuario_select_own"
    on usuario for select
    using (auth.jwt() ->> 'email' = email);

create policy "usuario_select_admin"
    on usuario for select
    using ((auth.jwt() ->> 'role') = 'Administrador');

create policy "usuario_all_service"
    on usuario for all
    using (auth.role() = 'service_role');

-- reporte
create policy "reporte_insert_auth"
    on reporte for insert
    with check (auth.role() = 'authenticated');

create policy "reporte_select_own"
    on reporte for select
    using (fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

create policy "reporte_select_admin"
    on reporte for select
    using ((auth.jwt() ->> 'role') = 'Administrador');

create policy "reporte_update_admin"
    on reporte for update
    using ((auth.jwt() ->> 'role') = 'Administrador');

-- aviso
create policy "aviso_select_auth"
    on aviso for select
    using (auth.role() = 'authenticated');

create policy "aviso_write_admin"
    on aviso for all
    using ((auth.jwt() ->> 'role') = 'Administrador');

-- cuota
create policy "cuota_select_own"
    on cuota for select
    using (fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

create policy "cuota_select_admin"
    on cuota for select
    using ((auth.jwt() ->> 'role') = 'Administrador');

-- accesolog
create policy "accesolog_insert_auth"
    on accesolog for insert
    with check (auth.role() = 'authenticated');

create policy "accesolog_select_security"
    on accesolog for select
    using ((auth.jwt() ->> 'role') = 'Seguridad');

create policy "accesolog_select_admin"
    on accesolog for select
    using ((auth.jwt() ->> 'role') = 'Administrador');

create policy "accesolog_select_own"
    on accesolog for select
    using (fkresidente = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

-- tarealimpieza
create policy "tarealimpieza_select_own"
    on tarealimpieza for select
    using (fkasignado = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

create policy "tarealimpieza_update_own"
    on tarealimpieza for update
    using (fkasignado = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

create policy "tarealimpieza_all_admin"
    on tarealimpieza for all
    using ((auth.jwt() ->> 'role') = 'Administrador');

-- areacomun
create policy "areacomun_select_auth"
    on areacomun for select
    using (auth.role() = 'authenticated');

create policy "areacomun_update_cleaning"
    on areacomun for update
    using (
        (auth.jwt() ->> 'role') = 'Limpieza'
        or (auth.jwt() ->> 'role') = 'Administrador'
    );

-- notificacion
create policy "notificacion_select_own"
    on notificacion for select
    using (fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

create policy "notificacion_update_own"
    on notificacion for update
    using (fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

create policy "notificacion_insert_service"
    on notificacion for insert
    with check (auth.role() = 'service_role');

-- amenidad
create policy "amenidad_select_auth"
    on amenidad for select
    using (auth.role() = 'authenticated');

create policy "amenidad_write_admin"
    on amenidad for all
    using ((auth.jwt() ->> 'role') = 'Administrador');

-- reserva
create policy "reserva_select_auth"
    on reserva for select
    using (auth.role() = 'authenticated');

create policy "reserva_insert_residente"
    on reserva for insert
    with check (
        fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1)
    );

create policy "reserva_write_admin"
    on reserva for all
    using ((auth.jwt() ->> 'role') = 'Administrador');

-- =============================================================================
-- FIN DEL SCRIPT
-- =============================================================================
