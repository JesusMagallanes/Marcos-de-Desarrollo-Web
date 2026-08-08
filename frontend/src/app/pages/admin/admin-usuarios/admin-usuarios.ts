import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import {
  AuthService,
  ErrorApi,
  EstadoPeticion,
  Rol,
  Usuario,
  UsuarioService,
} from '../../../core';

@Component({
  selector: 'app-admin-usuarios',
  imports: [],
  templateUrl: './admin-usuarios.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminUsuarios implements OnInit, OnDestroy {
  private usuarios = inject(UsuarioService);
  private auth = inject(AuthService);

  /** Cargando, error y aviso en un solo objeto; ver EstadoPeticion. */
  protected estado = new EstadoPeticion();
  protected lista = signal<Usuario[]>([]);
  protected filtro = signal('');
  protected confirmandoId = signal<number | null>(null);

  protected readonly roles: Rol[] = ['CLIENTE', 'EMPLEADO', 'ADMINISTRADOR'];

  protected visibles = computed(() => {
    const q = this.filtro().trim().toLowerCase();
    if (!q) return this.lista();
    return this.lista().filter(
      (u) =>
        `${u.name} ${u.lastname}`.toLowerCase().includes(q) ||
        u.emailAddress.toLowerCase().includes(q),
    );
  });

  /** El admin en sesión no puede degradarse ni borrarse a sí mismo. */
  protected esYo(u: Usuario): boolean {
    return this.auth.usuario()?.id === u.id;
  }

  ngOnInit(): void {
    this.cargar();
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

  protected cambiarRol(u: Usuario, rol: string): void {
    if (this.esYo(u) || rol === u.rol) return;
    this.usuarios.cambiarRol(u.id, rol as Rol).subscribe({
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

  protected claseRol(rol: Rol): string {
    switch (rol) {
      case 'ADMINISTRADOR':
        return 'bg-danger';
      case 'EMPLEADO':
        return 'bg-primary';
      default:
        return 'bg-secondary';
    }
  }

}
