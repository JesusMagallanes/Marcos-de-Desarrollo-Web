import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_USUARIOS } from '../usuarios.routes';
import { PermisoInfo, RolCreate, RolResponse, RolUpdate } from '../models';

/** Gestión de roles — servicio `usuarios` (:8082). Requiere PERMISO_ROLES_GESTIONAR. */
@Injectable({ providedIn: 'root' })
export class RolService {
  private readonly http = inject(HttpClient);

  /** GET /api/roles — ordenados por tipo y nombre. */
  listar(): Observable<RolResponse[]> {
    return this.http.get<RolResponse[]>(RUTAS_USUARIOS.roles.base);
  }

  /** GET /api/roles/permisos — catálogo completo de permisos disponibles. */
  catalogoPermisos(): Observable<PermisoInfo[]> {
    return this.http.get<PermisoInfo[]>(RUTAS_USUARIOS.roles.permisos);
  }

  /** POST /api/roles — 409 si ya existe un rol con ese nombre. */
  crear(dto: RolCreate): Observable<RolResponse> {
    return this.http.post<RolResponse>(RUTAS_USUARIOS.roles.base, dto);
  }

  /** PUT /api/roles/{nombre} — el nombre no se puede cambiar. */
  actualizar(nombre: string, dto: RolUpdate): Observable<RolResponse> {
    return this.http.put<RolResponse>(RUTAS_USUARIOS.roles.porNombre(nombre), dto);
  }

  /** DELETE /api/roles/{nombre} — 409 si el rol lo usan usuarios o es de sistema. */
  eliminar(nombre: string): Observable<void> {
    return this.http.delete<void>(RUTAS_USUARIOS.roles.porNombre(nombre));
  }
}
