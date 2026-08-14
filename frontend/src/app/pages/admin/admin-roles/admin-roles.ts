import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import {
  ErrorApi,
  EstadoPeticion,
  PermisoInfo,
  RolResponse,
  RolService,
  TipoRol,
  clasePorTipo,
} from '../../../core';

/** Igual que el backend: mayúsculas, números y guiones bajos. */
const PATRON_NOMBRE_ROL = /^[A-Z0-9_]{2,50}$/;

@Component({
  selector: 'app-admin-roles',
  imports: [ReactiveFormsModule],
  templateUrl: './admin-roles.html',
  styleUrl: '../admin-tabla.css',
})
export class AdminRoles implements OnInit, OnDestroy {
  private fb = inject(FormBuilder);
  private rolService = inject(RolService);

  /** Cargando, error y aviso en un solo objeto; ver EstadoPeticion. */
  protected estado = new EstadoPeticion();
  protected guardando = signal(false);

  protected roles = signal<RolResponse[]>([]);
  protected permisos = signal<PermisoInfo[]>([]);
  protected filtroTipo = signal<TipoRol>('TRABAJADOR');

  protected formAbierto = signal(false);
  protected editandoNombre = signal<string | null>(null);
  protected confirmandoNombre = signal<string | null>(null);
  protected permisosMarcados = signal<Set<string>>(new Set());

  protected form = this.fb.nonNullable.group({
    nombre: ['', [Validators.required, Validators.pattern(PATRON_NOMBRE_ROL)]],
    descripcion: ['', [Validators.required, Validators.maxLength(200)]],
    tipo: ['TRABAJADOR' as TipoRol, Validators.required],
  });

  protected visibles = computed(() =>
    this.roles().filter((r) => r.tipo === this.filtroTipo()),
  );

  /** Permisos agrupados por módulo, en el orden del catálogo del backend. */
  protected permisosPorModulo = computed(() => {
    const grupos = new Map<string, PermisoInfo[]>();
    for (const p of this.permisos()) {
      const lista = grupos.get(p.modulo) ?? [];
      lista.push(p);
      grupos.set(p.modulo, lista);
    }
    return [...grupos.entries()];
  });

  /** Un rol de sistema no puede cambiar de tipo (lo rechaza el backend). */
  protected sistemaEditando = computed(() => {
    const nombre = this.editandoNombre();
    return nombre !== null
      ? (this.roles().find((r) => r.nombre === nombre)?.sistema ?? false)
      : false;
  });

  ngOnInit(): void {
    this.cargar();
    this.cargarPermisos();
  }

  ngOnDestroy(): void {
    this.estado.destruir();
  }

  private cargar(): void {
    this.estado.iniciar();
    this.rolService.listar().subscribe({
      next: (r) => {
        this.roles.set(r);
        this.estado.exito();
      },
      error: (e: ErrorApi) => this.estado.fallo(e),
    });
  }

  private cargarPermisos(): void {
    this.rolService.catalogoPermisos().subscribe({
      next: (p) => this.permisos.set(p),
      // Si falla el catálogo el formulario sigue sirviendo para editar;
      // simplemente no habría checkboxes que marcar.
      error: () => void 0,
    });
  }

  protected filtrar(tipo: TipoRol): void {
    this.filtroTipo.set(tipo);
  }

  protected nuevo(): void {
    this.editandoNombre.set(null);
    this.form.reset({ nombre: '', descripcion: '', tipo: 'TRABAJADOR' });
    this.permisosMarcados.set(new Set());
    this.estado.limpiarError();
    this.formAbierto.set(true);
  }

  protected editar(r: RolResponse): void {
    this.editandoNombre.set(r.nombre);
    this.form.setValue({
      nombre: r.nombre,
      descripcion: r.descripcion,
      tipo: r.tipo,
    });
    this.permisosMarcados.set(new Set(r.permisos));
    this.estado.limpiarError();
    this.formAbierto.set(true);
  }

  protected cerrar(): void {
    this.formAbierto.set(false);
    this.editandoNombre.set(null);
  }

  protected alternarPermiso(codigo: string): void {
    this.permisosMarcados.update((actuales) => {
      const copia = new Set(actuales);
      if (copia.has(codigo)) {
        copia.delete(codigo);
      } else {
        copia.add(codigo);
      }
      return copia;
    });
  }

  protected marcado(codigo: string): boolean {
    return this.permisosMarcados().has(codigo);
  }

  protected guardar(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.guardando.set(true);
    const { nombre, descripcion, tipo } = this.form.getRawValue();
    const permisos = [...this.permisosMarcados()].sort();
    const nombreEdicion = this.editandoNombre();

    const peticion = nombreEdicion
      ? this.rolService.actualizar(nombreEdicion, { descripcion, tipo, permisos })
      : this.rolService.crear({ nombre, descripcion, tipo, permisos });

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.cerrar();
        this.estado.mostrarAviso(nombreEdicion ? 'Rol actualizado.' : 'Rol creado.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.guardando.set(false);
        this.estado.fallo(e);
      },
    });
  }

  protected eliminar(nombre: string): void {
    this.rolService.eliminar(nombre).subscribe({
      next: () => {
        this.confirmandoNombre.set(null);
        this.estado.mostrarAviso('Rol eliminado.');
        this.cargar();
      },
      error: (e: ErrorApi) => {
        this.confirmandoNombre.set(null);
        this.estado.fallo(e);
      },
    });
  }

  protected invalido(campo: string): boolean {
    const c = this.form.get(campo);
    return !!c && c.invalid && c.touched;
  }

  protected claseRol(tipo: TipoRol): string {
    return clasePorTipo(tipo);
  }
}
