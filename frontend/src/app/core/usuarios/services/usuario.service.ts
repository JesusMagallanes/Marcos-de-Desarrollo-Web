import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { RUTAS_USUARIOS } from '../usuarios.routes';
import { CambioRol, PerfilUpdate, Usuario, UsuarioCreate } from '../models';

/** Gestión administrativa de usuarios — servicio `usuarios` (:8082). */
@Injectable({ providedIn: 'root' })
export class UsuarioService {
  private readonly http = inject(HttpClient);

  /** GET /api/usuarios — solo ADMINISTRADOR. */
  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(RUTAS_USUARIOS.usuarios.base);
  }

  /** GET /api/usuarios/{id} — el propio usuario o un ADMINISTRADOR. */
  obtener(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(RUTAS_USUARIOS.usuarios.porId(id));
  }

  /** POST /api/usuarios — ADMINISTRADOR. Es el único alta que fija el rol. */
  crear(dto: UsuarioCreate): Observable<Usuario> {
    return this.http.post<Usuario>(RUTAS_USUARIOS.usuarios.base, dto);
  }

  /** PUT /api/usuarios/{id}/perfil — sin `rol` ni `password` en el cuerpo. */
  actualizarPerfil(id: number, dto: PerfilUpdate): Observable<Usuario> {
    return this.http.put<Usuario>(RUTAS_USUARIOS.usuarios.perfil(id), dto);
  }

  /** PATCH /api/usuarios/{id}/rol — requiere PERMISO_USUARIOS_GESTIONAR. */
  cambiarRol(id: number, rol: string): Observable<Usuario> {
    const cuerpo: CambioRol = { rol };
    return this.http.patch<Usuario>(RUTAS_USUARIOS.usuarios.rol(id), cuerpo);
  }

  /** DELETE /api/usuarios/{id} — requiere PERMISO_USUARIOS_GESTIONAR. 409 si intenta borrarse a sí mismo. */
  eliminar(id: number): Observable<void> {
    return this.http.delete<void>(RUTAS_USUARIOS.usuarios.porId(id));
  }
}
