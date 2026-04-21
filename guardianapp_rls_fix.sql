-- =============================================================================
-- GuardianApp – Corrección de políticas RLS
-- Ejecutar en: Supabase → SQL Editor → New query → Run
-- =============================================================================

-- Función auxiliar que obtiene el rol del usuario autenticado sin recursión RLS
-- (SECURITY DEFINER evita que RLS se aplique dentro de la función)
create or replace function auth_user_role()
returns integer
language sql
security definer
stable
set search_path = public
as $$
    select fkrolusuario from usuario where email = auth.jwt() ->> 'email' limit 1;
$$;

-- =============================================================================
-- usuario: admin y seguridad ven a todos los residentes
-- =============================================================================
drop policy if exists "usuario_select_admin"    on usuario;
drop policy if exists "usuario_select_security" on usuario;

create policy "usuario_select_admin"
    on usuario for select
    using (auth_user_role() = 3);

create policy "usuario_select_security"
    on usuario for select
    using (auth_user_role() = 2);

-- =============================================================================
-- reporte: admin ve todo, seguridad ve incidentes, residente actualiza los suyos
-- =============================================================================
drop policy if exists "reporte_select_admin"   on reporte;
drop policy if exists "reporte_update_admin"   on reporte;
drop policy if exists "reporte_select_security" on reporte;
drop policy if exists "reporte_update_own"     on reporte;

create policy "reporte_select_admin"
    on reporte for select
    using (auth_user_role() = 3);

create policy "reporte_update_admin"
    on reporte for update
    using (auth_user_role() = 3);

create policy "reporte_select_security"
    on reporte for select
    using (auth_user_role() = 2);

create policy "reporte_update_own"
    on reporte for update
    using (fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

-- =============================================================================
-- cuota: admin ve todas, residente puede marcar la suya como pagada
-- =============================================================================
drop policy if exists "cuota_select_admin" on cuota;
drop policy if exists "cuota_update_own"   on cuota;

create policy "cuota_select_admin"
    on cuota for select
    using (auth_user_role() = 3);

create policy "cuota_update_own"
    on cuota for update
    using (fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

-- =============================================================================
-- accesolog: seguridad inserta y ve todo, admin ve todo
-- =============================================================================
drop policy if exists "accesolog_insert_auth"     on accesolog;
drop policy if exists "accesolog_select_security" on accesolog;
drop policy if exists "accesolog_select_admin"    on accesolog;

create policy "accesolog_insert_security"
    on accesolog for insert
    with check (auth_user_role() = 2);

create policy "accesolog_select_security"
    on accesolog for select
    using (auth_user_role() = 2);

create policy "accesolog_select_admin"
    on accesolog for select
    using (auth_user_role() = 3);

-- =============================================================================
-- tarealimpieza: admin gestiona todo
-- =============================================================================
drop policy if exists "tarealimpieza_all_admin" on tarealimpieza;

create policy "tarealimpieza_all_admin"
    on tarealimpieza for all
    using (auth_user_role() = 3);

-- =============================================================================
-- areacomun: limpieza y admin actualizan
-- =============================================================================
drop policy if exists "areacomun_update_cleaning" on areacomun;

create policy "areacomun_update_cleaning"
    on areacomun for update
    using (auth_user_role() = 4 or auth_user_role() = 3);

-- =============================================================================
-- reserva: residente cancela las suyas, admin gestiona todo
-- =============================================================================
drop policy if exists "reserva_write_admin"    on reserva;
drop policy if exists "reserva_update_own"     on reserva;
drop policy if exists "reserva_delete_own"     on reserva;

create policy "reserva_write_admin"
    on reserva for all
    using (auth_user_role() = 3);

create policy "reserva_update_own"
    on reserva for update
    using (fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

create policy "reserva_delete_own"
    on reserva for delete
    using (fkusuario = (select id from usuario where email = auth.jwt() ->> 'email' limit 1));

-- =============================================================================
-- FIN
-- =============================================================================
