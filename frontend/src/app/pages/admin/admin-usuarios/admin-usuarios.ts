import { Cargando } from '../../../shared/cargando/cargando';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import {
  AuthService,
  ErrorApi,
  EstadoPeticion,
  RolResponse,
  RolService,
  Usuario,
  UsuarioService,
  clasePorTipo,
} from '../../../core';

@Component({
  selector: 'app-admin-usuarios',
  imports: [Cargando],
  templateUrl: './admin-usuarios.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminUsuarios implements OnInit, OnDestroy {
  private usuarios = inject(UsuarioService);
  private rolService = inject(RolService);
  private auth = inject(AuthService);

  /** Cargando, error y aviso en un solo objeto; ver EstadoPeticion. */
  protected estado = new EstadoPeticion();
  protected lista = signal<Usuario[]>([]);
  /** Roles dinámicos para el selector, cargados del backend. */
  protected roles = signal<RolResponse[]>([]);
  protected filtro = signal('');
  protected confirmandoId = signal<number | null>(null);

  protected visibles = computed(() => {
    const q = this.filtro().trim().toLowerCase();
    if (!q) return this.lista();
    return this.lista().filter(
      (u) =>
        `${u.name} ${u.lastname}`.toLowerCase().includes(q) ||
        u.emailAddress.toLowerCase().includes(q),
    );
  });

  protected rolesPorTipo = computed(() => {
    const trabajador = this.roles().filter((r) => r.tipo === 'TRABAJADOR');
    const cliente = this.roles().filter((r) => r.tipo === 'CLIENTE');
    return { trabajador, cliente };
  });

  /** El admin en sesión no puede degradarse ni borrarse a sí mismo. */
  protected esYo(u: Usuario): boolean {
    return this.auth.usuario()?.id === u.id;
  }

  ngOnInit(): void {
    this.cargar();
    this.cargarRoles();
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(): void {
    this.estado.iniciar();
    this.usuarios.listar().subscribe({
      next: (u) => {
        this.lista.set(u);
        this.estado.exito();
      },
      error: (e: ErrorApi) => {
        this.estado.fallo(e);
        this.estado.exito();
      },
    });
  }

  private cargarRoles(): void {
    this.rolService.listar().subscribe({
      next: (r) => this.roles.set(r),
      error: () => void 0,
    });
  }

  protected cambiarRol(u: Usuario, rol: string): void {
    if (this.esYo(u) || rol === u.rol) return;
    this.usuarios.cambiarRol(u.id, rol).subscribe({
      next: () => {
        this.estado.mostrarAviso(`Rol de ${u.name} actualizado a ${rol}.`);
        this.cargar();
      },
      error: (e: ErrorApi) => this.estado.fallo(e),
    });
  }

  protected eliminar(id: number): void {
    this.usuarios.eliminar(id).subscribe({
      next: () => {
        this.confirmandoId.set(null);
        this.estado.mostrarAviso('Usuario eliminado.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.confirmandoId.set(null);
        this.estado.fallo(e);
      },
    });
  }

  /** Color de la etiqueta según el tipo del rol; si no se conoce, neutro. */
  protected claseRol(u: Usuario): string {
    const rol = this.roles().find((r) => r.nombre === u.rol);
    return rol ? clasePorTipo(rol.tipo) : 'bg-secondary';
  }
}
